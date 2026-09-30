package xiao.cbra.mts.common.loot;

import minecrafttransportsimulator.entities.instances.AEntityVehicleE_Powered;
import minecrafttransportsimulator.entities.instances.EntityVehicleF_Physics;
import minecrafttransportsimulator.entities.instances.PartEngine;
import minecrafttransportsimulator.items.components.AItemPack;
import minecrafttransportsimulator.items.components.AItemPart;
import minecrafttransportsimulator.items.instances.ItemVehicle;
import minecrafttransportsimulator.jsondefs.JSONPart;
import minecrafttransportsimulator.jsondefs.JSONPartDefinition;
import minecrafttransportsimulator.mcinterface.InterfaceManager;
import minecrafttransportsimulator.packloading.PackParser;
import minecrafttransportsimulator.systems.ConfigSystem;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;
import xiao.battleroyale.BattleRoyale;
import xiao.battleroyale.api.loot.data.ILootData;
import xiao.battleroyale.api.minecraft.IMcRegistry;
import xiao.battleroyale.common.loot.LootGenerator;
import xiao.cbra.mts.CbraMts;
import xiao.cbra.mts.common.loot.data.VehicleDataProtocol;

import java.util.List;
import java.util.Map;

public class VehicleGenerator {

    // MTS 载具的外壳实体
    private static final String BUILDER_ENTITY = "mts:builder_existing";

    public static final String ENTITY_ID = "entityid";
    public static final String PACK_ID = "packID";
    public static final String SYSTEM_NAME = "systemName";
    public static final String SUB_NAME = "subName";
    public static final String POSITION_X = "positionx";
    public static final String POSITION_Y = "positiony";
    public static final String POSITION_Z = "positionz";

    public static final String ELECTRIC_POWER = "electricPower";

    public static final String FUEL_TANK = "fuelTank";
    public static final String CURRENT_FLUID = "currentFluid";
    public static final String CURRENT_FLUID_MOD = "currentFluidMod";
    public static final String FLUID_LEVEL = "fluidLevel";

    /**
     * <ul>
     *     刷新载具实体
     *     <li>在方块处刷新</li>
     *     <li>载具部件不是独立实体，{@link LootGenerator.LootContext#gameId} 写入外壳实体即可</li>
     *     <li>燃料和电池按载具配置填满，协议里指定则用指定的值</li>
     *     <li>不生成lootData数据</li>
     * </ul>
     */
    public static void generateVehicle(LootGenerator.LootContext lootContext, BlockEntity targetBlockEntity, List<ILootData> lootData,
                                       VehicleDataProtocol protocol) {
        IMcRegistry mcRegistry = BattleRoyale.getMcRegistry();

        ItemVehicle vehicleItem = resolveVehicle(protocol, lootContext);
        if (vehicleItem == null) {
            CbraMts.LOGGER.debug("VehicleGenerator: Unknown vehicle {}:{}:{}",
                    protocol.packID, protocol.systemName, protocol.subNames);
            return;
        }
        // 配置留空时会随机挑一个变体，后续一律用实际生效的 subName
        String subName = vehicleItem.subDefinition.subName;

        var builderEntityRl = mcRegistry.createResourceLocation(BUILDER_ENTITY);
        EntityType<?> entityType = mcRegistry.getEntityType(builderEntityRl);
        if (entityType == null) {
            CbraMts.LOGGER.warn("VehicleGenerator: Unknown entity type {}", BUILDER_ENTITY);
            return;
        }

        BlockPos spawnOrigin = targetBlockEntity.getBlockPos();
        // 刷在方块顶面，剩余高度交给 MTS 自身的物理沉降
        Vec3 spawnPos = new Vec3(spawnOrigin.getX() + 0.5,
                spawnOrigin.getY() + 1,
                spawnOrigin.getZ() + 0.5);

        // 载具内部坐标只认 NBT 的 positionx/y/z，外部 setPos 每tick都会被它反写覆盖，
        // 所以落点必须在这里写进 NBT
        CompoundTag data = new CompoundTag();
        // 先铺协议指定的部件，载具自身字段后写，避免被部件数据覆盖
        data.merge(protocol.parts);
        data.putString(ENTITY_ID, EntityVehicleF_Physics.class.getSimpleName());
        data.putString(PACK_ID, protocol.packID);
        data.putString(SYSTEM_NAME, protocol.systemName);
        data.putString(SUB_NAME, subName);
        data.putDouble(POSITION_X, spawnPos.x);
        data.putDouble(POSITION_Y, spawnPos.y);
        data.putDouble(POSITION_Z, spawnPos.z);
        applyPower(data, vehicleItem, protocol);

        try {
            Entity entity = entityType.create(lootContext.serverLevel);
            if (entity == null) {
                CbraMts.LOGGER.debug("VehicleGenerator: Failed to create vehicle {}:{}:{} at {}",
                        protocol.packID, protocol.systemName, subName, spawnOrigin);
                return;
            }
            entity.load(data);
            entity.setPos(spawnPos.x, spawnPos.y, spawnPos.z);
            if (!lootContext.serverLevel.addFreshEntity(entity)) {
                CbraMts.LOGGER.debug("VehicleGenerator: Failed to add vehicle {}:{}:{} at {}",
                        protocol.packID, protocol.systemName, subName, spawnOrigin);
                return;
            }
            BattleRoyale.getGameManager().getGameIdWriteApi().addGameId(entity, lootContext.gameId);
        } catch (Exception e) {
            CbraMts.LOGGER.warn("VehicleGenerator: Failed to generate vehicle {}:{}:{} at {}",
                    protocol.packID, protocol.systemName, subName, spawnOrigin, e);
            return;
        }

        CbraMts.LOGGER.debug("VehicleGenerator::generate {}:{}:{} {}", protocol.packID, protocol.systemName, subName, spawnOrigin);
    }

    /**
     * 候选是协议里的 subName 列表，每次刷新随机取一个；列表为空则用空 subName。
     * subName 是精确匹配 systemName + subName 的注册键，没有隐式回退。
     */
    private static @Nullable ItemVehicle resolveVehicle(VehicleDataProtocol protocol, LootGenerator.LootContext lootContext) {
        int size = protocol.subNames.size();
        String subName = "";
        if (size > 0) {
            float roll = lootContext.random.get();
            subName = protocol.subNames.get(Math.min(Math.max((int) (roll * size), 0), size - 1));
        }
        AItemPack<?> packItem = PackParser.getItem(protocol.packID, protocol.systemName, subName);
        return packItem instanceof ItemVehicle vehicle ? vehicle : null;
    }

    /**
     * 电池和燃料按载具自身配置填满，协议里显式指定则用指定的值。
     */
    private static void applyPower(CompoundTag data, ItemVehicle vehicleItem, VehicleDataProtocol protocol) {
        double electricPower = protocol.electricPower > 0
                ? protocol.electricPower
                : vehicleItem.definition.motorized.batteryCapacity * AEntityVehicleE_Powered.BATTERY_DEFAULT_CHARGE;
        data.putDouble(ELECTRIC_POWER, electricPower);

        String fuel = protocol.fuel.isEmpty() ? findFuel(vehicleItem, protocol) : protocol.fuel;
        if (fuel.isEmpty()) {
            CbraMts.LOGGER.debug("VehicleGenerator: No fuel resolved for {}:{}:{}",
                    protocol.packID, protocol.systemName, vehicleItem.subDefinition.subName);
            return;
        }

        CompoundTag fuelTank = new CompoundTag(); {
            fuelTank.putString(CURRENT_FLUID, fuel);
            fuelTank.putString(CURRENT_FLUID_MOD, "");
            double fuelQty = protocol.fuelQty > 0 ? protocol.fuelQty : vehicleItem.definition.motorized.fuelCapacity;
            fuelTank.putDouble(FLUID_LEVEL, fuelQty);
        }
        data.put(FUEL_TANK, fuelTank);
    }

    /**
     * 与 {@code AEntityVehicleE_Powered#addPartsPostAddition} 的取法一致：
     * 找到最终会装上的引擎，取它燃料类型下效力最高、且实际存在的流体。
     */
    private static String findFuel(ItemVehicle vehicleItem, VehicleDataProtocol protocol) {
        JSONPart.JSONPartEngine engine = findEngine(vehicleItem, protocol);
        if (engine == null) {
            return "";
        }
        if (engine.type == JSONPart.EngineType.ELECTRIC) {
            return PartEngine.ELECTRICITY_FUEL;
        }
        Map<String, Double> fuels = ConfigSystem.settings.fuel.fuels.get(engine.fuelType);
        if (fuels == null) {
            return "";
        }
        String mostPotentFluid = "";
        double mostPotentValue = 0;
        for (Map.Entry<String, Double> fuelEntry : fuels.entrySet()) {
            if (!InterfaceManager.coreInterface.isFluidValid(fuelEntry.getKey())) {
                continue;
            }
            if (mostPotentFluid.isEmpty() || mostPotentValue < fuelEntry.getValue()) {
                mostPotentFluid = fuelEntry.getKey();
                mostPotentValue = fuelEntry.getValue();
            }
        }
        return mostPotentFluid;
    }

    /**
     * 协议指定的部件可能覆盖引擎，优先看它们，其次看车辆包里该槽位的默认部件。
     */
    private static JSONPart.JSONPartEngine findEngine(ItemVehicle vehicleItem, VehicleDataProtocol protocol) {
        for (String partKey : protocol.parts.getAllKeys()) {
            CompoundTag partData = protocol.parts.getCompound(partKey);
            JSONPart.JSONPartEngine engine = getEngine(PackParser.getItem(
                    partData.getString(PACK_ID),
                    partData.getString(SYSTEM_NAME),
                    partData.getString(SUB_NAME)));
            if (engine != null) {
                return engine;
            }
        }
        if (vehicleItem.definition.parts != null) {
            for (JSONPartDefinition partDef : vehicleItem.definition.parts) {
                JSONPart.JSONPartEngine engine = getEngine(partDef.defaultPart);
                if (engine == null && partDef.conditionalDefaultParts != null) {
                    for (String conditionalPart : partDef.conditionalDefaultParts.values()) {
                        engine = getEngine(conditionalPart);
                        if (engine != null) {
                            break;
                        }
                    }
                }
                if (engine != null) {
                    return engine;
                }
            }
        }
        return null;
    }

    private static JSONPart.JSONPartEngine getEngine(String partName) {
        if (partName == null || partName.isEmpty()) {
            return null;
        }
        int split = partName.indexOf(':');
        if (split < 0) {
            return null;
        }
        return getEngine(PackParser.getItem(partName.substring(0, split), partName.substring(split + 1)));
    }

    private static JSONPart.JSONPartEngine getEngine(AItemPack<?> partItem) {
        return partItem instanceof AItemPart part ? part.definition.engine : null;
    }
}

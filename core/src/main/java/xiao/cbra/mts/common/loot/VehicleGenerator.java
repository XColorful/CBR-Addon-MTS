package xiao.cbra.mts.common.loot;

import net.minecraft.world.level.block.entity.BlockEntity;
import xiao.battleroyale.api.loot.data.ILootData;
import xiao.battleroyale.common.loot.LootGenerator;
import xiao.cbra.mts.common.loot.data.VehicleDataProtocol;

import java.util.List;

public class VehicleGenerator {

    /**
     * <ul>
     *     刷新载具实体
     *     <li>在方块处刷新</li>
     *     <li>每个载具部件(实体)都写入 {@link LootGenerator.LootContext#gameId}</li>
     *     <li>刷出来的就能直接开</li>
     *     <li>不生成lootData数据</li>
     * </ul>
     */
    public static void generateVehicle(LootGenerator.LootContext lootContext, BlockEntity targetBlockEntity, List<ILootData> lootData,
                                       VehicleDataProtocol protocol) {
    }
}

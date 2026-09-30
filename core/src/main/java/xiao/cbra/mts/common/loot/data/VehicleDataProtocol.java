package xiao.cbra.mts.common.loot.data;

import com.google.gson.JsonObject;
import net.minecraft.nbt.CompoundTag;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import xiao.cbra.mts.CbraMts;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class VehicleDataProtocol {

    // 载具包ID
    public final @NotNull String packID;

    // 载具系统名
    public final @NotNull String systemName;

    // 载具子定义名候选，每次刷新随机取一个；为空则用空 subName
    public final @NotNull List<String> subNames;

    // 指定槽位的部件，键为 part_序号
    public final @NotNull CompoundTag parts;

    // 燃料流体名，为空则按引擎配置自动挑选
    public final @NotNull String fuel;

    // 燃料量，<=0 则按载具配置加满
    public final double fuelQty;

    // 电池电量，<=0 则按载具配置充满
    public final double electricPower;


    @ApiStatus.Internal
    public VehicleDataProtocol(@NotNull String packID, @NotNull String systemName, @NotNull List<String> subNames,
                               @NotNull CompoundTag parts, @NotNull String fuel, double fuelQty, double electricPower) {
        this.packID = packID;
        this.systemName = systemName;
        this.subNames = subNames;
        this.parts = parts;
        this.fuel = fuel;
        this.fuelQty = fuelQty;
        this.electricPower = electricPower;
    }

    public static @Nullable VehicleDataProtocol getConfigFromProtocol(String protocol, @NotNull JsonObject jsonTag) {
        if (protocol == null || protocol.isEmpty()) {
            return null;
        }
        String[] parts = protocol.split(":", 2);
        if (parts.length != 2) {
            return null;
        }
        String namespace = parts[0];
        String version = parts[1];
        if (namespace.equals(CbraMts.MOD_ID)) {
            switch (version) {
                case "0.5.6" -> {
                    return VehicleDataProtocol056.fromTag(jsonTag);
                }
                default -> {
                    if (!unknownVersion.contains(version)) {
                        CbraMts.LOGGER.info("VehicleDataProtocol: unknown version {}", version);
                        unknownVersion.add(version);
                    }
                    return VehicleDataProtocol056.fromTag(jsonTag);
                }
            }
        }
        return null;
    }

    private static final Set<String> unknownVersion = new HashSet<>();
}

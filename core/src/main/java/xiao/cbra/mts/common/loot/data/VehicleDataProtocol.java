package xiao.cbra.mts.common.loot.data;

import com.google.gson.JsonObject;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import xiao.cbra.mts.CbraMts;

import java.util.HashSet;
import java.util.Set;

public class VehicleDataProtocol {


    @ApiStatus.Internal
    public VehicleDataProtocol() {
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

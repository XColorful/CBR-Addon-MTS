package xiao.cbra.mts.common.loot.data;

import com.google.gson.JsonObject;
import org.jetbrains.annotations.NotNull;
import xiao.cbra.mts.CbraMts;

// cbramts:0.5.6
public class VehicleDataProtocol056 {


    protected static VehicleDataProtocol fromTag(@NotNull JsonObject jsonTag) {
        try {

            return new VehicleDataProtocol();
        } catch (Exception e) {
            CbraMts.LOGGER.warn("VehicleDataProtocol056: Failed to parse by cbramts:0.5.6 protocol from jsonTag: {}", jsonTag, e);
            return null;
        }
    }
}

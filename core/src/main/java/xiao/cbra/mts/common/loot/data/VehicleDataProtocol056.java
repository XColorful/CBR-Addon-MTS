package xiao.cbra.mts.common.loot.data;

import com.google.gson.JsonObject;
import net.minecraft.nbt.CompoundTag;
import org.jetbrains.annotations.NotNull;
import xiao.battleroyale.util.JsonUtils;
import xiao.battleroyale.util.NBTUtils;
import xiao.cbra.mts.CbraMts;

import java.util.List;

// cbramts:0.5.6
public class VehicleDataProtocol056 {

    // 载具包ID
    public static final String PACK_ID = "packID";

    // 载具系统名
    public static final String SYSTEM_NAME = "systemName";

    // 载具子定义名，可取字符串或字符串列表
    public static final String SUB_NAME = "subName";

    // 指定槽位的部件，键为 part_序号，值为该部件的NBT字符串
    public static final String PARTS = "parts";

    // 燃料流体名
    public static final String FUEL = "fuel";

    // 燃料量
    public static final String FUEL_QTY = "fuelQty";

    // 电池电量
    public static final String ELECTRIC_POWER = "electricPower";


    protected static VehicleDataProtocol fromTag(@NotNull JsonObject jsonTag) {
        try {
            String packID = JsonUtils.getJsonString(jsonTag, PACK_ID, "");
            String systemName = JsonUtils.getJsonString(jsonTag, SYSTEM_NAME, "");
            List<String> subNames = getSubNames(jsonTag);
            CompoundTag parts = getParts(jsonTag);
            String fuel = JsonUtils.getJsonString(jsonTag, FUEL, "");
            double fuelQty = JsonUtils.getJsonDouble(jsonTag, FUEL_QTY, 0);
            double electricPower = JsonUtils.getJsonDouble(jsonTag, ELECTRIC_POWER, 0);

            return new VehicleDataProtocol(packID, systemName, subNames, parts, fuel, fuelQty, electricPower);
        } catch (Exception e) {
            CbraMts.LOGGER.warn("VehicleDataProtocol056: Failed to parse by cbramts:0.5.6 protocol from jsonTag: {}", jsonTag, e);
            return null;
        }
    }

    /**
     * subName 优先按字符串列表读，其次按单个字符串读。
     */
    private static @NotNull List<String> getSubNames(@NotNull JsonObject jsonTag) {
        List<String> subNames = JsonUtils.getJsonStringList(jsonTag, SUB_NAME);
        if (!subNames.isEmpty()) {
            return subNames;
        }
        String subName = JsonUtils.getJsonString(jsonTag, SUB_NAME, "");
        return subName.isEmpty() ? List.of() : List.of(subName);
    }

    /**
     * 把每个槽位的NBT字符串拼成实体存档里的 part_序号。留空的槽位直接跳过，交给包里的默认部件。
     */
    private static @NotNull CompoundTag getParts(@NotNull JsonObject jsonTag) {
        CompoundTag parts = new CompoundTag();
        JsonObject partsJson = JsonUtils.getJsonObject(jsonTag, PARTS, null);
        if (partsJson == null) {
            return parts;
        }
        for (String partKey : partsJson.keySet()) {
            CompoundTag partData = NBTUtils.stringToNBT(JsonUtils.getJsonString(partsJson, partKey, ""));
            if (!partData.isEmpty()) {
                parts.put(partKey, partData);
            }
        }
        return parts;
    }
}

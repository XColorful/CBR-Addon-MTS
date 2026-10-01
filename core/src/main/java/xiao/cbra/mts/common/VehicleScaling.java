package xiao.cbra.mts.common;

import minecrafttransportsimulator.jsondefs.AJSONItem;
import minecrafttransportsimulator.jsondefs.JSONPart;
import minecrafttransportsimulator.jsondefs.JSONSound;
import minecrafttransportsimulator.jsondefs.JSONVehicle;
import xiao.cbra.mts.CbraMts;
import xiao.cbra.mts.config.CbraMtsConfig;

import java.util.Map;
import java.util.WeakHashMap;

/**
 * 按配置改写 MTS 车包定义。
 * <p>
 * 定义对象由 {@code PackParser} 缓存并按包共享，改动会作用于该包件的所有实例，
 * 这正是"一键改全载具"想要的效果。原始值记在侧表里，保证重复调用不会叠加。
 */
public final class VehicleScaling {

    /**
     * 区块 → 方块
     */
    private static final double BLOCKS_PER_CHUNK = 16.0D;

    /**
     * MTS 未声明 maxDistance 时使用的兜底距离，对应 {@code SoundInstance.DEFAULT_MAX_DISTANCE}
     */
    private static final double DEFAULT_SOUND_MAX_DISTANCE = 32.0D;

    /**
     * 定义 → 原始生命上限
     */
    private static final Map<AJSONItem, Integer> ORIGINAL_HEALTH = new WeakHashMap<>();

    /**
     * 音效 → 原始的 [minDistance, middleDistance, maxDistance]
     */
    private static final Map<JSONSound, double[]> ORIGINAL_SOUND_DISTANCES = new WeakHashMap<>();

    private VehicleScaling() {
    }

    /**
     * 按 {@link CbraMtsConfig#vehicleHealthScale} 缩放生命上限。
     * <p>
     * 上限为 0 或负数的定义表示无限血量，保持原样。
     */
    public static void applyHealthScale(AJSONItem item) {
        if (item.general == null) return;

        int originalHealth = ORIGINAL_HEALTH.computeIfAbsent(item, definition -> definition.general.health);
        if (originalHealth <= 0) return;

        item.general.health = (int) Math.max(1L, Math.round(originalHealth * CbraMtsConfig.vehicleHealthScale));
        if (item instanceof JSONVehicle) {
            CbraMts.LOGGER.info("[cbramts-debug] health {} {} -> {} (scale={})",
                    item.packID, item.systemName, item.general.health, CbraMtsConfig.vehicleHealthScale);
        }
    }

    /**
     * 把引擎部件音效的可听距离改写为 {@link CbraMtsConfig#vehicleSoundDistance}，衰减曲线的形状按比例保留。
     * <p>
     * MTS 的音量按 {@code (maxDistance - distance) / maxDistance} 之类的公式衰减，maxDistance 同时是可听半径和
     * 分母，所以只改它会让 min/middle 越界，三个距离必须一起缩放。
     */
    public static void applyEngineSoundDistance(JSONPart part) {
        if (part.engine == null || part.rendering == null || part.rendering.sounds == null) return;

        double targetDistance = CbraMtsConfig.vehicleSoundDistance * BLOCKS_PER_CHUNK;
        for (JSONSound sound : part.rendering.sounds) {
            double[] original = ORIGINAL_SOUND_DISTANCES.computeIfAbsent(sound,
                    definition -> new double[]{definition.minDistance, definition.middleDistance, definition.maxDistance});

            double originalMaxDistance = original[2] != 0 ? original[2] : DEFAULT_SOUND_MAX_DISTANCE;
            double factor = targetDistance / originalMaxDistance;

            sound.maxDistance = targetDistance;
            sound.middleDistance = original[1] * factor;
            sound.minDistance = original[0] * factor;
        }
    }
}

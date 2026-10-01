package xiao.cbra.mts.common;

import net.minecraft.world.entity.EntityType;
import xiao.cbra.mts.config.CbraMtsConfig;
import xiao.cbra.mts.mixin.EntityTypeAccessor;

/**
 * 按配置调整载具的可见范围。
 * <p>
 * MTS 的载具不在世界实体上渲染：{@code mts:builder_existing} 与 {@code mts:builder_seat} 只是载体，
 * 真正渲染的是 {@code mts:builder_rendering}，而它的渲染器 {@code shouldRender} 恒为 true、
 * MTS 自己的渲染循环也不做距离剔除。所以可见范围完全由这三个实体类型的 clientTrackingRange 决定，
 * 改它就能整体推远载具的可见距离。
 */
public final class VehicleRenderDistance {

    private static final String MTS_MOD_ID = "mts";

    private static final String BUILDER_EXISTING = "builder_existing";
    private static final String BUILDER_SEAT = "builder_seat";
    private static final String BUILDER_RENDERING = "builder_rendering";

    private VehicleRenderDistance() {
    }

    /**
     * 在实体类型注册完成后调用，把三个载具实体类型的 clientTrackingRange 改为 {@link CbraMtsConfig#renderDistance} 区块。
     */
    public static void apply() {
        if (!CbraMtsConfig.modifyRenderDistance) return;

        setTrackingRange(BUILDER_EXISTING);
        setTrackingRange(BUILDER_SEAT);
        setTrackingRange(BUILDER_RENDERING);
    }

    private static void setTrackingRange(String entityName) {
        EntityType.byString(MTS_MOD_ID + ":" + entityName).ifPresent(
                entityType -> ((EntityTypeAccessor) entityType).cbramts$setClientTrackingRange(CbraMtsConfig.renderDistance));
    }
}

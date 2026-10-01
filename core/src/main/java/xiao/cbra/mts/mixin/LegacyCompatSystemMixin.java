package xiao.cbra.mts.mixin;

import minecrafttransportsimulator.jsondefs.AJSONBase;
import minecrafttransportsimulator.jsondefs.AJSONItem;
import minecrafttransportsimulator.jsondefs.JSONPart;
import minecrafttransportsimulator.packloading.LegacyCompatSystem;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import xiao.cbra.mts.common.VehicleScaling;
import xiao.cbra.mts.config.CbraMtsConfig;

/**
 * 在车包定义补齐默认值之后、注册进 {@code PackParser} 之前改写定义。
 * <p>
 * {@code PackParser.registerItem} 对每个包件都会先调一次本方法，走的是唯一的注册入口，
 * 所以这里既是生命上限的唯一挂点（早于它 {@code general.health} 会被补成 100），
 * 也是引擎音效距离的唯一挂点。
 */
@Mixin(LegacyCompatSystem.class)
public abstract class LegacyCompatSystemMixin {

    @Inject(method = "performLegacyCompats(Lminecrafttransportsimulator/jsondefs/AJSONBase;)V",
            at = @At("RETURN"), remap = false)
    private static void cbramts$applyScaling(AJSONBase definition, CallbackInfo callbackInfo) {
        if (definition instanceof AJSONItem item) {
            VehicleScaling.applyHealthScale(item);
        }
        if (CbraMtsConfig.modifyVehicleSoundDistance && definition instanceof JSONPart part) {
            VehicleScaling.applyEngineSoundDistance(part);
        }
    }
}

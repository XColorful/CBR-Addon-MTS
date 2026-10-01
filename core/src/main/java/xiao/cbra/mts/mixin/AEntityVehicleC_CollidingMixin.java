package xiao.cbra.mts.mixin;

import minecrafttransportsimulator.mcinterface.IWrapperItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;
import xiao.cbra.mts.config.CbraMtsConfig;

import java.util.List;
import java.util.function.Consumer;

/**
 * 载具被摧毁时是否散落部件（轮胎、引擎等）。
 * <p>
 * {@code destroy} 会把非永久部件收集成一个列表，最后一次性 {@code forEach} 抛到世界里。
 * 这里拦住那次遍历即可，乘客摔伤和爆炸都在它之前已经处理完，不受影响。
 * <p>
 * 目标类 {@code AEntityVehicleC_Colliding} 是包级私有的，只能用 {@code targets} 字符串引用。
 */
@Mixin(targets = "minecrafttransportsimulator.entities.instances.AEntityVehicleC_Colliding")
public abstract class AEntityVehicleC_CollidingMixin {

    @Redirect(method = "destroy", remap = false,
            at = @At(value = "INVOKE", target = "Ljava/util/List;forEach(Ljava/util/function/Consumer;)V"))
    private void cbramts$skipPartDrops(List<IWrapperItemStack> drops, Consumer<IWrapperItemStack> dropHandler) {
        if (CbraMtsConfig.vehicleDropOnDestroy) {
            drops.forEach(dropHandler);
        }
    }
}

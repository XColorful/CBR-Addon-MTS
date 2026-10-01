package xiao.cbra.mts.mixin;

import minecrafttransportsimulator.baseclasses.Point3D;
import minecrafttransportsimulator.entities.instances.AEntityVehicleE_Powered;
import minecrafttransportsimulator.mcinterface.AWrapperWorld;
import minecrafttransportsimulator.mcinterface.IWrapperItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;
import xiao.cbra.mts.config.CbraMtsConfig;

/**
 * 载具被摧毁时是否散落已装的仪器。
 * <p>
 * 与部件掉落是两个独立的落点：仪器由 {@code destroy} 自己抛，部件在父类的 {@code destroy} 里抛。
 */
@Mixin(AEntityVehicleE_Powered.class)
public abstract class AEntityVehicleE_PoweredMixin {

    @Redirect(method = "destroy", remap = false,
            at = @At(value = "INVOKE", target = "Lminecrafttransportsimulator/mcinterface/AWrapperWorld;spawnItemStack(Lminecrafttransportsimulator/mcinterface/IWrapperItemStack;Lminecrafttransportsimulator/baseclasses/Point3D;Lminecrafttransportsimulator/baseclasses/Point3D;)V"))
    private void cbramts$skipInstrumentDrops(AWrapperWorld world, IWrapperItemStack stack, Point3D position, Point3D optionalMotion) {
        if (CbraMtsConfig.vehicleDropOnDestroy) {
            world.spawnItemStack(stack, position, optionalMotion);
        }
    }
}

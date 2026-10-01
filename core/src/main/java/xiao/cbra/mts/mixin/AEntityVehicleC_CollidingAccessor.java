package xiao.cbra.mts.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/**
 * 暴露 {@code speedFactor}。它声明在 {@code AEntityVehicleC_Colliding}（包私有）上，
 * 且会被子类继承，直接 {@code @Shadow} 到 {@code AEntityVehicleD_Moving} 上静态检查认不出来，
 * 所以在声明类上开 accessor。
 */
@Mixin(targets = "minecrafttransportsimulator.entities.instances.AEntityVehicleC_Colliding")
public interface AEntityVehicleC_CollidingAccessor {

    @Accessor(value = "speedFactor", remap = false)
    double cbramts$getSpeedFactor();
}

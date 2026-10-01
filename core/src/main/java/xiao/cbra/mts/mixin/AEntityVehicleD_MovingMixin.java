package xiao.cbra.mts.mixin;

import minecrafttransportsimulator.jsondefs.JSONVehicle.VehicleMotorized;
import org.objectweb.asm.Opcodes;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.Redirect;
import xiao.cbra.mts.CbraMts;
import xiao.cbra.mts.config.CbraMtsConfig;

/**
 * 调整载具撞击方块时的摧毁门槛与伤害。
 * <p>
 * MTS 的逻辑在 {@code correctCollidingMovement} 里：定义了 {@code crashSpeedMax} 的车按碰撞速度曲线算伤害，
 * 伤害达到血量上限时就"扣满一整条"或者（速度再超过 {@code crashSpeedDestroyed} 时）直接摧毁。
 * 注意它比较用的 {@code scaledVelocity = velocity * 20} 不含 {@code speedFactor}，而载具每 tick 实际位移是
 * {@code motion * speedFactor}，所以实际速度(m/s) = {@code velocity * speedFactor * 20}，
 * 换算成 km/h 还要再乘 3.6。本类里的配置项一律按实际行驶速度（km/h）取值。
 * <p>
 * 目标类在 MTS 里是包私有的，只能用 {@code targets} 指定。
 */
@Mixin(targets = "minecrafttransportsimulator.entities.instances.AEntityVehicleD_Moving")
public abstract class AEntityVehicleD_MovingMixin {

    /**
     * km/h ÷ (m/s)
     */
    private static final double KILOMETERS_PER_HOUR_PER_METER_PER_SECOND = 3.6D;

    /**
     * 抬高直接摧毁的速度下限。只影响读 {@code crashSpeedDestroyed} 的那条分支，
     * 没定义 {@code crashSpeedMax} 的硬度分支不经过这里。
     */
    @Redirect(method = "correctCollidingMovement",
            at = @At(value = "FIELD",
                    target = "Lminecrafttransportsimulator/jsondefs/JSONVehicle$VehicleMotorized;crashSpeedDestroyed:F",
                    opcode = Opcodes.GETFIELD),
            remap = false)
    private float cbramts$raiseCrashDestroySpeed(VehicleMotorized motorized) {
        float destroySpeed = motorized.crashSpeedDestroyed;
        // scaledVelocity = 实际速度(m/s) / speedFactor，所以 km/h 的门槛要除回来
        double speedFactor = ((AEntityVehicleC_CollidingAccessor) this).cbramts$getSpeedFactor();
        if (CbraMtsConfig.vehicleCrashDestroySpeed <= 0) {
            CbraMts.LOGGER.info("[cbramts-debug] destroySpeed on {}: pack={} effective={} (config off)",
                    this.getClass().getSimpleName(), destroySpeed, destroySpeed);
            return destroySpeed;
        }
        double configuredSpeed = CbraMtsConfig.vehicleCrashDestroySpeed / (speedFactor * KILOMETERS_PER_HOUR_PER_METER_PER_SECOND);
        float effective = (float) Math.max(destroySpeed, configuredSpeed);
        CbraMts.LOGGER.info("[cbramts-debug] destroySpeed on {}: pack={} config={}km/h speedFactor={} -> ref={} effective={}",
                this.getClass().getSimpleName(), destroySpeed, CbraMtsConfig.vehicleCrashDestroySpeed, speedFactor, configuredSpeed, effective);
        return effective;
    }

    /**
     * 把碰撞伤害乘上系数。
     * <p>
     * 这个方法里的伤害要改两处，因为 MTS 编译出来的字节码把它们写成了两份：
     * 一份算进局部变量 {@code damage} 只用于 {@code damage >= 血量上限} 的判断，
     * 另一份在"扣不满"的 else 分支里就地重算一遍再传给 {@code Damage}（不读那个局部变量）。
     * 这里两个都覆盖：{@link ModifyVariable} 改判断用的那份（门槛才会跟着动），
     * 本方法改 else 分支实际扣血的那份。只改前者会看到"判断走的是新值、扣血却是旧值"。
     * <p>
     * 系数里还要除掉 {@code vehicleHealthScale}：MTS 的伤害是 {@code 血量上限 × 曲线比例}，
     * 而血量上限已经被这个配置改过，不除掉的话拉高血量会让撞击伤害同步变大，血量就白加了。
     * 除掉之后撞击伤害以车包原始血量为基准，能扛几次撞击正好等于 {@code vehicleHealthScale}。
     * <p>
     * 目标只取 else 分支那次 {@code Damage} 构造（方法内第 2 个）。扣满分支传的是血量上限本身，
     * 不该被缩放 —— 那样"扣满一条"就不再是一条了。
     */
    @ModifyArg(method = "correctCollidingMovement",
            at = @At(value = "INVOKE",
                    target = "Lminecrafttransportsimulator/baseclasses/Damage;<init>(DLminecrafttransportsimulator/baseclasses/BoundingBox;Lminecrafttransportsimulator/entities/components/AEntityB_Existing;Lminecrafttransportsimulator/mcinterface/IWrapperEntity;Lminecrafttransportsimulator/systems/LanguageSystem$LanguageEntry;)V",
                    ordinal = 1, remap = false),
            index = 0, remap = false)
    private double cbramts$scaleCrashDamageArg(double damage) {
        double result = damage * cbramts$damageMultiplier();
        CbraMts.LOGGER.info("[cbramts-debug] crashDamageArg on {}: raw={} -> {}",
                this.getClass().getSimpleName(), damage, result);
        return result;
    }

    @ModifyVariable(method = "correctCollidingMovement",
            at = @At("STORE"), name = "damage", remap = false)
    private double cbramts$scaleCrashDamage(double damage) {
        double result = damage * cbramts$damageMultiplier();
        CbraMts.LOGGER.info("[cbramts-debug] crashDamage on {}: raw={} damageScale={} healthScale={} -> {}",
                this.getClass().getSimpleName(), damage, CbraMtsConfig.vehicleDestructionDamageScale,
                CbraMtsConfig.vehicleHealthScale, result);
        return result;
    }

    /**
     * 撞击伤害的倍率：配置系数除以 {@code vehicleHealthScale}，把 MTS 里"按血量上限等比放大"的那部分抵消掉。
     */
    private static double cbramts$damageMultiplier() {
        double healthScale = CbraMtsConfig.vehicleHealthScale > 0 ? CbraMtsConfig.vehicleHealthScale : 1.0D;
        return CbraMtsConfig.vehicleDestructionDamageScale / healthScale;
    }
}

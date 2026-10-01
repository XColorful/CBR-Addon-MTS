package xiao.cbra.mts.mixin;

import minecrafttransportsimulator.baseclasses.ComputedVariable;
import minecrafttransportsimulator.baseclasses.Damage;
import minecrafttransportsimulator.entities.components.AEntityE_Interactable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import xiao.cbra.mts.CbraMts;

/**
 * 临时诊断：记录每一次施加到 MTS 实体上的伤害，用来定位撞击掉血的真实来源。
 */
@Mixin(AEntityE_Interactable.class)
public abstract class AEntityE_InteractableMixin {

    @Shadow
    public ComputedVariable damageVar;

    @Inject(method = "attack(Lminecrafttransportsimulator/baseclasses/Damage;)V", at = @At("HEAD"), remap = false)
    private void cbramts$logAttack(Damage damage, CallbackInfo callbackInfo) {
        CbraMts.LOGGER.info("[cbramts-debug] attack on {}@{} amount={} current={} explosion={}",
                this.getClass().getSimpleName(), Integer.toHexString(System.identityHashCode(this)),
                damage.amount, damageVar.currentValue, damage.isExplosion);
    }
}

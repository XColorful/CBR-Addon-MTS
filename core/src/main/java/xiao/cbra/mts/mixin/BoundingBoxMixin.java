package xiao.cbra.mts.mixin;

import minecrafttransportsimulator.baseclasses.BoundingBox;
import minecrafttransportsimulator.jsondefs.JSONCollisionBox;
import minecrafttransportsimulator.jsondefs.JSONCollisionGroup;
import minecrafttransportsimulator.jsondefs.JSONCollisionGroup.CollisionType;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import xiao.cbra.mts.config.CbraMtsConfig;

/**
 * 让所有碰撞盒都带上 {@code ATTACK} 类型。
 * <p>
 * MTS 只在外部伤害路径里处理标注了 ATTACK 的盒，没标的盒会被子弹命中却不掉血。
 * 盒的 collisionTypes 与所属 {@code JSONCollisionGroup.collisionTypes} 是同一个集合（JSON 构造函数直接传引用），
 * 所以在这里往组里补一次就够了，载具本体与全部部件都覆盖。
 */
@Mixin(BoundingBox.class)
public abstract class BoundingBoxMixin {

    @Inject(method = "<init>(Lminecrafttransportsimulator/jsondefs/JSONCollisionBox;Lminecrafttransportsimulator/jsondefs/JSONCollisionGroup;)V",
            at = @At("RETURN"), remap = false)
    private void cbramts$addAttackType(JSONCollisionBox definition, JSONCollisionGroup groupDef, CallbackInfo callbackInfo) {
        if (CbraMtsConfig.allPartsAttackable && groupDef.collisionTypes != null) {
            groupDef.collisionTypes.add(CollisionType.ATTACK);
        }
    }
}

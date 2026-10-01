package xiao.cbra.mts.mixin;

import net.minecraft.world.entity.EntityType;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.gen.Accessor;

/**
 * 暴露 {@link EntityType#clientTrackingRange()} 背后的字段，用于在实体类型注册后改写载具的可见范围。
 */
@Mixin(EntityType.class)
public interface EntityTypeAccessor {

    @Mutable
    @Accessor("clientTrackingRange")
    void cbramts$setClientTrackingRange(int range);
}

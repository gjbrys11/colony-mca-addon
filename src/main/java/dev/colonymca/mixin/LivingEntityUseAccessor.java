package dev.colonymca.mixin;

import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(LivingEntity.class)
public interface LivingEntityUseAccessor {
    @Accessor("useItemRemaining") void colonyMca$setUseItemRemaining(int ticks);
}

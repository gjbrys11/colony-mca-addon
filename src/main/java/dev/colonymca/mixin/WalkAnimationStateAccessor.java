package dev.colonymca.mixin;

import net.minecraft.world.entity.WalkAnimationState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(WalkAnimationState.class)
public interface WalkAnimationStateAccessor {
    @Accessor("speedOld") float colonyMca$oldSpeed();
    @Accessor("speedOld") void colonyMca$oldSpeed(float value);
    @Accessor("speed") void colonyMca$speed(float value);
    @Accessor("position") void colonyMca$position(float value);
}

package dev.colonymca.bridge;

import dev.colonymca.mixin.WalkAnimationStateAccessor;
import net.minecraft.world.entity.WalkAnimationState;

public final class WalkAnimationSync {
    private WalkAnimationSync() {}

    public static void copy(WalkAnimationState source, WalkAnimationState target) {
        var from = (WalkAnimationStateAccessor) source;
        var to = (WalkAnimationStateAccessor) target;
        to.colonyMca$oldSpeed(from.colonyMca$oldSpeed());
        to.colonyMca$speed(source.speed());
        to.colonyMca$position(source.position());
    }
}

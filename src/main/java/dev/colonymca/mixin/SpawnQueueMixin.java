package dev.colonymca.mixin;

import net.conczin.mca.server.SpawnQueue;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.npc.Villager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Stops vanilla villagers being consumed by MCA's conversion queue. */
@Mixin(value = SpawnQueue.class, remap = false)
public abstract class SpawnQueueMixin {
    @Inject(method = "addVillager", at = @At("HEAD"), cancellable = true)
    private void colonyMca$preserveVanillaVillagers(Entity entity, CallbackInfoReturnable<Boolean> cir) {
        cir.setReturnValue(false);
    }

    @Inject(method = "convert", at = @At("HEAD"), cancellable = true)
    private void colonyMca$disableForcedConversion(Villager villager, CallbackInfo ci) {
        ci.cancel();
    }
}

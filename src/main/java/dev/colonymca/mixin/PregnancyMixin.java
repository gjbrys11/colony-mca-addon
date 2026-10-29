package dev.colonymca.mixin;

import dev.colonymca.bridge.ActorInteractions;
import dev.colonymca.bridge.BirthBridge;
import net.conczin.mca.entity.VillagerEntityMCA;
import net.conczin.mca.entity.ai.Pregnancy;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = Pregnancy.class, remap = false)
public abstract class PregnancyMixin {
    @Shadow @Final private VillagerEntityMCA mother;

    @Inject(method = "tryStartGestation", at = @At("HEAD"), cancellable = true)
    private void colonyMca$cakeBirth(CallbackInfoReturnable<Boolean> cir) {
        var handler = ActorInteractions.get(mother);
        if (handler != null) cir.setReturnValue(BirthBridge.npcBirth(handler.citizen()));
    }
}

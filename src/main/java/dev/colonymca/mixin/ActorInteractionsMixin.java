package dev.colonymca.mixin;

import dev.colonymca.bridge.ActorInteractions;
import net.conczin.mca.entity.VillagerEntityMCA;
import net.conczin.mca.entity.interaction.VillagerCommandHandler;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = VillagerEntityMCA.class, remap = false)
public abstract class ActorInteractionsMixin {
    @Inject(method = "getInteractions()Lnet/conczin/mca/entity/interaction/VillagerCommandHandler;", at = @At("HEAD"), cancellable = true)
    private void colonyMca$routeDialogueActions(CallbackInfoReturnable<VillagerCommandHandler> cir) {
        var handler = ActorInteractions.get((VillagerEntityMCA) (Object) this);
        if (handler != null) cir.setReturnValue(handler);
    }
}

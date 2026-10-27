package dev.colonymca.mixin;

import dev.colonymca.client.McaSidebarModule;
import net.conczin.mca.network.ClientHandlerImpl;
import net.conczin.mca.network.s2c.InteractionDialogueResponse;
import net.conczin.mca.network.s2c.InteractionDialogueQuestionResponse;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = ClientHandlerImpl.class, remap = false)
public abstract class SidebarDialogueMixin {
    @Inject(method = "handleDialogueResponse", at = @At("HEAD"), cancellable = true)
    private void colonyMca$embeddedDialogue(InteractionDialogueResponse response, CallbackInfo ci) {
        var sidebar = McaSidebarModule.current();
        if (sidebar != null) { sidebar.dialogue(response.question(), response.answers()); ci.cancel(); }
    }

    @Inject(method = "handleDialogueQuestionResponse", at = @At("HEAD"), cancellable = true)
    private void colonyMca$embeddedPhrase(InteractionDialogueQuestionResponse response, CallbackInfo ci) {
        var sidebar = McaSidebarModule.current();
        if (sidebar != null) { sidebar.phrase(response.questionText(), response.silent()); ci.cancel(); }
    }
}

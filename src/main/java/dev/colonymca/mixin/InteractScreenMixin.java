package dev.colonymca.mixin;

import dev.colonymca.client.ColonyMcaClient;
import net.conczin.mca.client.gui.InteractScreen;
import net.conczin.mca.client.gui.MCAButton;
import net.conczin.mca.entity.VillagerLike;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = InteractScreen.class, remap = false)
public abstract class InteractScreenMixin {
    @Shadow @Final private VillagerLike<?> villager;

    @Inject(method = "buttonPressed", at = @At("HEAD"), cancellable = true)
    private void colonyMca$openColonyControls(MCAButton button, CallbackInfo ci) {
        if (!button.identifier().equals("gui.button.colony_controls")) return;
        var world = Minecraft.getInstance().level;
        if (world != null && world.getEntity(villager.asEntity().getId())
                instanceof com.minecolonies.core.entity.citizen.EntityCitizen citizen) {
            net.conczin.mca.network.Network.sendToServer(
                    new net.conczin.mca.network.c2s.InteractionCloseRequest(citizen.getUUID()));
            ColonyMcaClient.openColony(citizen);
        }
        ci.cancel();
    }
}

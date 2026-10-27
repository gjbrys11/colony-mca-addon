package dev.colonymca.mixin;

import dev.colonymca.bridge.CitizenMcaActor;
import net.conczin.mca.network.c2s.InteractionCloseRequest;
import net.conczin.mca.network.c2s.InteractionDialogueInitMessage;
import net.conczin.mca.network.c2s.InteractionDialogueMessage;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;
import java.util.UUID;

/** MCA dialogue packets require its concrete class. Resolve only these requests to the citizen's delegate. */
@Mixin(value = {InteractionDialogueInitMessage.class, InteractionDialogueMessage.class, InteractionCloseRequest.class}, remap = false)
public abstract class DialoguePacketMixin {
    @Redirect(method = "handleServer", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/server/level/ServerLevel;getEntity(Ljava/util/UUID;)Lnet/minecraft/world/entity/Entity;"))
    private Entity colonyMca$resolveActor(ServerLevel world, UUID id, ServerPlayer player) {
        Entity entity = world.getEntity(id);
        if (entity instanceof CitizenMcaActor bridge) {
            if (!entity.isAlive() || player.distanceToSqr(entity) > 64) return null;
            return bridge.colonyMca$actor();
        }
        return entity;
    }
}

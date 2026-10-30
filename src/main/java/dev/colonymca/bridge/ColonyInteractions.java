package dev.colonymca.bridge;

import com.minecolonies.core.entity.citizen.EntityCitizen;
import net.conczin.mca.entity.VillagerEntityMCA;
import net.conczin.mca.entity.ai.Chore;
import net.conczin.mca.entity.interaction.VillagerCommandHandler;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

public final class ColonyInteractions extends VillagerCommandHandler {
    private final EntityCitizen citizen;

    public ColonyInteractions(EntityCitizen citizen, VillagerEntityMCA actor) {
        super(actor);
        this.citizen = citizen;
    }

    public EntityCitizen citizen() { return citizen; }

    @Override public boolean handle(ServerPlayer player, String command) {
        if (player.level() != citizen.level() || player.distanceToSqr(citizen) > 64
                || !citizen.isAlive() || citizen.getCitizenData() == null) return false;
        if (!citizen.getCitizenData().getColony().getPermissions().hasPermission(player,
                com.minecolonies.api.colony.permissions.Action.ACCESS_HUTS)) return false;
        // These systems belong to MineColonies. Never let a proxy change jobs or create a second inventory.
        String action = command.split("\\.", 2)[0];
        if (action.equals("procreate")) {
            boolean born = BirthBridge.playerBirth(citizen, player);
            player.displayClientMessage(Component.translatable(born ? "colony_mca.birth_success" : "colony_mca.birth_unavailable"), true);
            return true;
        }
        var movement = net.conczin.mca.entity.ai.MoveState.byCommand(action);
        if (movement.isPresent()) {
            MovementBridge.command(citizen, player, movement.get());
            return true;
        }
        if (Chore.byCommand(action).isPresent() || java.util.Set.of("sethome", "setworkplace", "inventory",
                "hire_short", "hire_long", "execute", "pardon", "pick_up", "ridehorse", "stay_in_village",
                "gohome", "profession", "armor", "stopworking", "infected").contains(action)) {
            player.displayClientMessage(Component.translatable("colony_mca.managed_by_colony"), true);
            return false;
        }
        if ((action.equals("divorceConfirm") || action.equals("divorcePapers"))
                && !entity.getRelationships().isMarriedTo(player.getUUID())) return false;
        if (action.equals("adopt") && (!citizen.isBaby()
                || !entity.getRelationships().getFamilyEntry().getParents().allMatch(n -> n.isDeceased()))) return false;
        if (action.equals("trade")) {
            if (citizen.isBaby() || !entity.canTradeWithProfession()) return false;
            // Packet commands bypass vanilla mobInteract's isTrading guard. Keep its single-customer rule.
            if (entity.getTradingPlayer() != null) {
                if(entity.getTradingPlayer()!=player) player.displayClientMessage(Component.translatable("colony_mca.trade_busy"),true);
                return false;
            }
        }
        boolean close = super.handle(player, command);
        FamilyBridge.synchronize(citizen);
        ((CitizenMcaActor) citizen).colonyMca$flush();
        return close;
    }
}

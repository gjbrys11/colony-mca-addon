package dev.colonymca.bridge;

import com.minecolonies.core.entity.citizen.EntityCitizen;
import net.conczin.mca.entity.VillagerEntityMCA;
import net.conczin.mca.entity.ZombieVillagerEntityMCA;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.tick.EntityTickEvent;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;

public final class CitizenEvents {
    private CitizenEvents() {}

    public static void join(EntityJoinLevelEvent event) {
        // MCA actors live solely as data delegates of citizens, never as independently spawned NPCs.
        if (!event.getLevel().isClientSide && (event.getEntity() instanceof VillagerEntityMCA
                || event.getEntity() instanceof ZombieVillagerEntityMCA)) event.setCanceled(true);
    }

    public static void tick(EntityTickEvent.Post event) {
        if (!(event.getEntity() instanceof EntityCitizen citizen) || citizen.level().isClientSide
                || citizen.getCitizenData() == null || !citizen.isAlive()) return;
        MovementBridge.tick(citizen);
        if (citizen.tickCount % 20 == 0) {
            CitizenMcaActor bridge = (CitizenMcaActor) citizen;
            bridge.colonyMca$actor();
            TradeBridge.tick(citizen, bridge.colonyMca$actor());
            if (citizen.tickCount % 200 == 0) FamilyBridge.synchronize(citizen);
            bridge.colonyMca$flush();
        }
    }

    public static void death(LivingDeathEvent event) {
        if (!(event.getEntity() instanceof EntityCitizen citizen)
                || !(citizen.level() instanceof net.minecraft.server.level.ServerLevel world)
                || citizen.getCitizenData() == null) return;
        ((CitizenMcaActor) citizen).colonyMca$actor();
        ((CitizenMcaActor) citizen).colonyMca$flush();
        net.conczin.mca.server.world.data.FamilyTree.get(world).getOrEmpty(citizen.getUUID())
                .ifPresent(node -> node.setDeceased(true));
        if (event.getSource().getEntity() instanceof net.minecraft.world.entity.LivingEntity killer
                && event.getSource().getDirectEntity() == killer
                && killer.getMainHandItem().getItem() instanceof net.conczin.mca.item.ScytheItem) {
            net.conczin.mca.item.ScytheItem.setSoul(killer.getMainHandItem(), true);
        }
    }

    public static void interact(PlayerInteractEvent.EntityInteractSpecific event) {
        if (!(event.getTarget() instanceof EntityCitizen citizen) || event.getHand() != InteractionHand.MAIN_HAND) return;
        event.setCanceled(true);
        event.setCancellationResult(InteractionResult.SUCCESS);
        if (citizen.level().isClientSide) dev.colonymca.client.ColonyMcaClient.openColony(citizen);
    }
}

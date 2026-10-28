package dev.colonymca.bridge;

import com.minecolonies.api.colony.permissions.Action;
import com.minecolonies.core.colony.jobs.AbstractJobGuard;
import com.minecolonies.core.entity.citizen.EntityCitizen;
import net.conczin.mca.entity.ai.MoveState;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;

public final class MovementBridge {
    private MovementBridge() {}

    public static void command(EntityCitizen citizen, ServerPlayer player, MoveState state) {
        var data = citizen.getCitizenData();
        if (!data.getColony().getPermissions().hasPermission(player, Action.MANAGE_HUTS)) return;
        var bridge = (CitizenMcaData) data;
        var control = bridge.colonyMca$getControl();
        if (state == MoveState.MOVE) {
            if (control.contains("previousPaused")) data.setPaused(control.getBoolean("previousPaused"));
            control.remove("movement");
            control.remove("leader");
            control.remove("previousPaused");
            bridge.colonyMca$setControl(control);
        } else {
            if (!control.contains("previousPaused")) control.putBoolean("previousPaused", data.isPaused());
            control.putString("movement", state.name());
            control.putUUID("leader", player.getUUID());
            bridge.colonyMca$setControl(control);
            if (!(data.getJob() instanceof AbstractJobGuard)) data.setPaused(true);
        }
        ((CitizenMcaActor) citizen).colonyMca$actor().getVillagerBrain().setMoveState(state, player);
        ((CitizenMcaActor) citizen).colonyMca$flush();
        data.markDirty(0);
    }

    public static void tick(EntityCitizen citizen) {
        var data = citizen.getCitizenData();
        if (!(data instanceof CitizenMcaData bridge)) return;
        var control = bridge.colonyMca$getControl();
        if (!control.contains("movement")) return;
        if (control.getString("movement").equals("FOLLOW") && (!control.hasUUID("leader")
                || citizen.level().getPlayerByUUID(control.getUUID("leader")) == null)) {
            if (control.contains("previousPaused")) data.setPaused(control.getBoolean("previousPaused"));
            control.remove("movement"); control.remove("leader"); control.remove("previousPaused");
            bridge.colonyMca$setControl(control);
            citizen.getNavigation().stop();
            ((CitizenMcaActor) citizen).colonyMca$actor().getVillagerBrain().setMoveState(MoveState.MOVE, null);
            ((CitizenMcaActor) citizen).colonyMca$flush();
            data.markDirty(0);
            return;
        }
        if ((citizen.getTarget() != null && citizen.getTarget().isAlive()) || (citizen.getLastHurtByMob() != null
                && citizen.tickCount - citizen.getLastHurtByMobTimestamp() < 100)) {
            data.setPaused(control.getBoolean("previousPaused"));
            return; // Colony combat takes priority over player movement commands.
        }
        if (!(data.getJob() instanceof AbstractJobGuard)) data.setPaused(true);
        if (control.getString("movement").equals("STAY")) {
            citizen.getNavigation().stop();
        } else if (control.hasUUID("leader") && citizen.tickCount % 10 == 0) {
            var leader = citizen.level().getPlayerByUUID(control.getUUID("leader"));
            if (leader == null || leader.isSpectator() || citizen.distanceToSqr(leader) <= 9) {
                citizen.getNavigation().stop();
            } else citizen.getNavigation().moveTo(leader, 1.0);
        }
    }
}

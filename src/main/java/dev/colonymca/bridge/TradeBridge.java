package dev.colonymca.bridge;

import com.minecolonies.api.colony.ICitizenData;
import com.minecolonies.core.entity.citizen.EntityCitizen;
import net.conczin.mca.entity.VillagerEntityMCA;
import net.minecraft.world.entity.npc.VillagerProfession;
import java.util.List;
import java.util.UUID;

public final class TradeBridge {
    private static final List<VillagerProfession> CAREERS = List.of(VillagerProfession.ARMORER,
            VillagerProfession.BUTCHER, VillagerProfession.CARTOGRAPHER, VillagerProfession.CLERIC,
            VillagerProfession.FARMER, VillagerProfession.FISHERMAN, VillagerProfession.FLETCHER,
            VillagerProfession.LEATHERWORKER, VillagerProfession.LIBRARIAN, VillagerProfession.MASON,
            VillagerProfession.SHEPHERD, VillagerProfession.TOOLSMITH, VillagerProfession.WEAPONSMITH);

    private TradeBridge() {}

    /** Advance only vanilla's delayed career update; never run MCA or vanilla world AI. */
    public static void advanceCareer(VillagerEntityMCA actor) {
        var customer = actor.getTradingPlayer();
        if (customer != null && (!customer.isAlive() || customer.isRemoved() || customer.level() != actor.level()
                || customer.distanceToSqr(actor) > 64 || !canTradeWith(actor,customer))) {
            actor.setTradingPlayer(null);
            if (customer instanceof net.minecraft.server.level.ServerPlayer player) player.closeContainer();
        }
        var access = (dev.colonymca.mixin.VillagerTradeAccessor) actor;
        int level = actor.getVillagerData().getLevel();
        if (access.colonyMca$getTradeTimer() <= 0 && net.minecraft.world.entity.npc.VillagerData.canLevelUp(level)
                && actor.getVillagerXp() >= net.minecraft.world.entity.npc.VillagerData.getMaxXpPerLevel(level)) {
            access.colonyMca$setTradeTimer(40);
            access.colonyMca$setPendingLevel(true);
        }
        if (actor.isTrading() || access.colonyMca$getTradeTimer() <= 0) return;
        int remaining = Math.max(0, access.colonyMca$getTradeTimer() - 20);
        access.colonyMca$setTradeTimer(remaining);
        if (remaining == 0 && access.colonyMca$hasPendingLevel()) {
            access.colonyMca$increaseCareer();
            access.colonyMca$setPendingLevel(false);
        }
    }

    public static boolean canTradeWith(VillagerEntityMCA actor, net.minecraft.world.entity.player.Player customer) {
        if(!(actor.level() instanceof net.minecraft.server.level.ServerLevel world))return true;
        var actual=world.getEntity(actor.getUUID());
        if(actual==actor)return true; // Ordinary MCA merchant retains its own vanilla validity rules.
        if(!(actual instanceof EntityCitizen citizen) || !citizen.isAlive() || customer.level()!=world
                || customer.distanceToSqr(citizen)>64)return false;
        var data=citizen.getCitizenData();
        return data!=null && data.getColony().getPermissions().hasPermission(customer,
                com.minecolonies.api.colony.permissions.Action.ACCESS_HUTS);
    }

    public static VillagerProfession initialProfession(ICitizenData data, UUID identity) {
        String job = data == null || data.getJob() == null ? ""
                : data.getJob().getJobRegistryEntry().getKey().getPath();
        if (job.contains("fisher")) return VillagerProfession.FISHERMAN;
        if (job.contains("farmer") || job.contains("cook") || job.contains("baker")) return VillagerProfession.FARMER;
        if (job.contains("shepherd")) return VillagerProfession.SHEPHERD;
        if (job.contains("herder") || job.contains("butcher")) return VillagerProfession.BUTCHER;
        if (job.contains("forester") || job.contains("fletcher")) return VillagerProfession.FLETCHER;
        if (job.contains("research") || job.contains("teacher") || job.contains("librar")) return VillagerProfession.LIBRARIAN;
        if (job.contains("stone") || job.contains("builder")) return VillagerProfession.MASON;
        if (job.contains("guard") || job.contains("smelt")) return VillagerProfession.ARMORER;
        if (job.contains("miner") || job.contains("smith")) return VillagerProfession.TOOLSMITH;
        return CAREERS.get(Math.floorMod(identity.hashCode(), CAREERS.size()));
    }

    /** Colony work buildings replace vanilla trading workstations. Do not change careers after initialization. */
    public static void tick(EntityCitizen citizen, VillagerEntityMCA actor) {
        var data = citizen.getCitizenData();
        if (data.isChild() || data.getWorkBuilding() == null || citizen.tickCount % 200 != 0) return;
        var bridge = (CitizenMcaData) data;
        var control = bridge.colonyMca$getControl();
        long period = citizen.level().getGameTime() / 12000L;
        if (!control.contains("tradeRestockPeriod") || control.getLong("tradeRestockPeriod") != period) {
            actor.restock();
            control.putLong("tradeRestockPeriod", period);
            bridge.colonyMca$setControl(control);
        }
    }
}

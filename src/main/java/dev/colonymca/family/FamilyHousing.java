package dev.colonymca.family;

import com.minecolonies.api.colony.ICitizenData;
import dev.colonymca.IntegrationConfig;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;
import java.util.ArrayList;

/** Observes assignments. Never allocates, moves, or reserves housing. */
public final class FamilyHousing {
    private FamilyHousing() {}

    public static double factor(ICitizenData citizen) {
        var manager = citizen.getColony().getCitizenManager();
        Set<ICitizenData> family = new HashSet<>();
        if (citizen.getPartner() != null) family.add(citizen.getPartner());
        for (int id : citizen.getChildren()) family.add(manager.getCivilian(id));
        for (int id : citizen.getSiblings()) family.add(manager.getCivilian(id));
        // MineColonies stores parent names, not parent IDs. Only accept unique matches.
        for (String name : new String[]{citizen.getParents().getA(), citizen.getParents().getB()}) {
            if (name == null || name.isBlank()) continue;
            var matches = manager.getCitizens().stream().filter(c -> name.equals(c.getName())).toList();
            if (matches.size() == 1) family.add(matches.getFirst());
        }
        family.remove(null);
        family.remove(citizen);
        var home = citizen.getHomeBuilding();
        var homes = new ArrayList<Object>();
        for (var member : family) homes.add(member.getHomeBuilding() == null ? null : member.getHomeBuilding().getPosition());
        if (citizen.getColony().getWorld() instanceof net.minecraft.server.level.ServerLevel world) {
            var tree = net.conczin.mca.server.world.data.FamilyTree.get(world);
            tree.getOrEmpty(citizen.getUUID()).ifPresent(entry -> {
                var player = world.getServer().getPlayerList().getPlayer(entry.partner());
                if (player != null) {
                    boolean sameHome = home != null && player.getRespawnPosition() != null
                            && player.getRespawnDimension().equals(world.dimension())
                            && home.isInBuilding(player.getRespawnPosition());
                    homes.add(sameHome ? home.getPosition() : null);
                }
            });
        }
        return switch (FamilyHousingRule.evaluate(home == null ? null : home.getPosition(), homes)) {
            case NO_KNOWN_FAMILY -> 1.0;
            case TOGETHER -> IntegrationConfig.FAMILY_TOGETHER.get();
            case APART -> IntegrationConfig.FAMILY_APART.get();
        };
    }
}

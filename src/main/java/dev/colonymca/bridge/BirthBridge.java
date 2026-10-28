package dev.colonymca.bridge;

import com.minecolonies.api.colony.ICitizenData;
import com.minecolonies.api.colony.IColony;
import com.minecolonies.core.colony.buildings.modules.LivingBuildingModule;
import com.minecolonies.core.colony.buildings.workerbuildings.BuildingTownHall;
import net.conczin.mca.Config;
import net.conczin.mca.entity.ai.Traits;
import net.conczin.mca.entity.ai.relationship.Gender;
import net.conczin.mca.server.world.data.FamilyTree;
import net.conczin.mca.server.world.data.FamilyTreeNode;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Colony population, housing, skill inheritance and growth; MCA family identities. */
public final class BirthBridge {
    private BirthBridge() {}

    public static boolean playerBirth(com.minecolonies.core.entity.citizen.EntityCitizen parent, ServerPlayer player) {
        var actor = ((CitizenMcaActor) parent).colonyMca$actor();
        if (parent.isBaby() || !actor.getRelationships().isMarriedTo(player.getUUID())
                || actor.getVillagerBrain().getMemoriesForPlayer(player).getHearts() < 100
                || actor.getTraits().hasTrait(Traits.INFERTILE)) return false;
        return create(parent.getCitizenData().getColony(), parent.getCitizenData(), null,
                FamilyTree.get(player.serverLevel()).getOrCreate(player));
    }

    public static void automatic(IColony colony) {
        if (!(colony.getWorld() instanceof ServerLevel)) return;
        List<ICitizenData> adults = new ArrayList<>(colony.getCitizenManager().getCitizens().stream()
                .filter(c -> !c.isChild() && c.getEntity().filter(e -> e.isAlive()).isPresent()).toList());
        Collections.shuffle(adults);
        for (var first : adults) {
            var actor = ((CitizenMcaActor) first.getEntity().orElseThrow()).colonyMca$actor();
            if (actor.getTraits().hasTrait(Traits.INFERTILE)) continue;
            var node = actor.getRelationships().getFamilyEntry();
            if (node.getRelationshipState().isMarried()) {
                var second = adults.stream().filter(c -> c.getUUID().equals(node.partner())).findFirst().orElse(null);
                if (second != null && create(colony, first, second, null)) return;
                continue;
            }
            if (!node.partner().equals(net.minecraft.Util.NIL_UUID)) continue;
            for (var second : adults) {
                if (first == second || first.isRelatedTo(second) || second.isRelatedTo(first)) continue;
                var other = ((CitizenMcaActor) second.getEntity().orElseThrow()).colonyMca$actor();
                if (!other.getRelationships().getFamilyEntry().partner().equals(net.minecraft.Util.NIL_UUID)
                        || node.isRelative(second.getUUID()) || other.getRelationships().getFamilyEntry().isRelative(first.getUUID())
                        || other.getTraits().hasTrait(Traits.INFERTILE)
                        || !actor.canBeAttractedTo(other) || !other.canBeAttractedTo(actor)) continue;
                if (!hasRoom(colony)) return;
                actor.getRelationships().marry(other);
                first.setPartner(second.getId()); second.setPartner(first.getId());
                if (create(colony, first, second, null)) return;
            }
        }
    }

    private static boolean hasRoom(IColony colony) {
        return colony.getSettings().getSetting(BuildingTownHall.MOVE_IN).getValue()
                && colony.getCitizenManager().getCurrentCitizenCount() < colony.getCitizenManager().getMaxCitizens()
                && colony.getServerBuildingManager().getHouseWithSpareBed() != null;
    }

    private static boolean create(IColony colony, ICitizenData first, ICitizenData second, FamilyTreeNode player) {
        if (!(colony.getWorld() instanceof ServerLevel world) || !hasRoom(colony)) return false;
        var firstBridge = (CitizenMcaData) first;
        var control = firstBridge.colonyMca$getControl();
        long now = world.getGameTime();
        if (!ready(control, now)) return false;
        if (second != null) {
            if (!ready(((CitizenMcaData) second).colonyMca$getControl(), now)) return false;
            if (second.getEntity().isEmpty() || ((CitizenMcaActor) second.getEntity().get())
                    .colonyMca$actor().getTraits().hasTrait(Traits.INFERTILE)) return false;
        }
        var home = colony.getServerBuildingManager().getHouseWithSpareBed();
        if (home == null) return false;
        var module = home.getFirstModuleOccurance(LivingBuildingModule.class);
        if (module == null) return false;
        String secondName = second != null ? second.getName() : player.getName();
        var child = colony.getCitizenManager().createAndRegisterCivilianData();
        child.setIsChild(true);
        child.getCitizenSkillHandler().init(colony, first, second, first.getRandom());
        child.setParents(first.getName(), secondName);
        child.generateName(first.getRandom(), first.getName(), secondName, colony.getCitizenNameFile());
        if (!module.assignCitizen(child)) {
            colony.getCitizenManager().removeCivilian(child);
            return false;
        }
        List<Integer> siblings = new ArrayList<>(first.getChildren());
        if (second != null) siblings.addAll(second.getChildren());
        child.addSiblings(siblings.toArray(Integer[]::new));
        for (int id : siblings) {
            var sibling = colony.getCitizenManager().getCivilian(id);
            if (sibling != null) sibling.addSiblings(child.getId());
        }
        first.addChildren(child.getId());
        if (second != null) second.addChildren(child.getId());
        child.setSuffix(first.getTextureSuffix());
        var tree = FamilyTree.get(world);
        var childNode = tree.getOrCreate(child.getUUID(), child.getName(), child.isFemale() ? Gender.FEMALE : Gender.MALE);
        childNode.assignParent(tree.getOrCreate(first.getUUID(), first.getName(), first.isFemale() ? Gender.FEMALE : Gender.MALE));
        childNode.assignParent(second != null
                ? tree.getOrCreate(second.getUUID(), second.getName(), second.isFemale() ? Gender.FEMALE : Gender.MALE) : player);
        var firstActor = first.getEntity().filter(e -> e instanceof CitizenMcaActor)
                .map(e -> ((CitizenMcaActor) e).colonyMca$actor()).orElse(null);
        net.conczin.mca.entity.VillagerLike<?> secondActor = second == null
                ? net.conczin.mca.entity.VillagerLike.toVillager(world.getServer().getPlayerList().getPlayer(player.id()))
                : second.getEntity().filter(e -> e instanceof CitizenMcaActor)
                        .map(e -> ((CitizenMcaActor) e).colonyMca$actor()).orElse(null);
        if (firstActor != null && secondActor != null) {
            var childActor = (child.isFemale() ? net.conczin.mca.registry.EntitiesMCA.FEMALE_VILLAGER
                    : net.conczin.mca.registry.EntitiesMCA.MALE_VILLAGER).create(world);
            if (childActor != null) {
                childActor.setPos(home.getPosition().getX(), home.getPosition().getY(), home.getPosition().getZ());
                childActor.setUUID(child.getUUID());
                childActor.setCustomName(net.minecraft.network.chat.Component.literal(child.getName()));
                childActor.setVillagerData(childActor.getVillagerData()
                        .setProfession(TradeBridge.initialProfession(child, child.getUUID())));
                childActor.getGenetics().combine(firstActor.getGenetics(), secondActor.getGenetics());
                childActor.getGenetics().setGender(child.isFemale() ? Gender.FEMALE : Gender.MALE);
                childActor.getTraits().inherit(firstActor.getTraits());
                childActor.getTraits().inherit(secondActor.getTraits());
                childActor.initialize(net.minecraft.world.entity.MobSpawnType.BREEDING);
                var profile = new net.minecraft.nbt.CompoundTag();
                childActor.addAdditionalSaveData(profile);
                ((CitizenMcaData) child).colonyMca$setData(profile);
            }
        }
        colony.getCitizenManager().spawnOrCreateCitizen(child, world, home.getPosition());
        control.putLong("lastBirth", now);
        firstBridge.colonyMca$setControl(control);
        if (second != null) {
            var secondBridge = (CitizenMcaData) second;
            var secondControl = secondBridge.colonyMca$getControl();
            secondControl.putLong("lastBirth", now);
            secondBridge.colonyMca$setControl(secondControl);
        }
        colony.getEventDescriptionManager().addEventDescription(
                new com.minecolonies.core.colony.eventhooks.citizenEvents.CitizenBornEvent(home.getPosition(), child.getName()));
        colony.getStatisticsManager().increment(com.minecolonies.api.util.constant.StatisticsConstants.BIRTH, colony.getDay());
        colony.markDirty();
        return true;
    }

    private static boolean ready(net.minecraft.nbt.CompoundTag control, long now) {
        return !control.contains("lastBirth") || now - control.getLong("lastBirth") >= Config.getInstance().procreationCooldown;
    }
}

package dev.colonymca.smoke;

import com.minecolonies.api.colony.ICitizenData;
import com.minecolonies.core.entity.citizen.EntityCitizen;
import dev.colonymca.ColonyMca;
import dev.colonymca.bridge.*;
import net.conczin.mca.entity.ai.Traits;
import net.conczin.mca.network.c2s.InteractionVillagerMessage;
import net.conczin.mca.server.world.data.FamilyTree;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;

/** Real native managers/entities; controlled prerequisites, no substitute gameplay implementation. */
@EventBusSubscriber(modid = ColonyMca.ID, value = Dist.CLIENT)
public final class ColonyAcceptance {
    private static boolean done;
    private static final Path RESULT = Path.of("../build/gameplay-preview/colony.txt");
    private static final List<String> checks = new ArrayList<>();

    @SubscribeEvent public static void tick(ServerTickEvent.Post event) {
        if (!Boolean.getBoolean("colony_mca.colonyVerify") || !GameplayPreview.ready() || done) return;
        done = true;
        try {
            Files.deleteIfExists(RESULT);
            var world = event.getServer().overworld();
            var player = event.getServer().getPlayerList().getPlayers().getFirst();
            var worker = (EntityCitizen) world.getEntity(GameplayPreview.citizenIdentity());
            var colony = worker.getCitizenData().getColony();
            var home = GameplayPreview.building(colony, new BlockPos(121,80,122), com.minecolonies.api.blocks.ModBlocks.blockHutHome);
            home.setBuildingLevel(5); home.setBlueprintPath("fundamentals/residence5.blueprint");
            colony.getCitizenManager().calculateMaxCitizens();
            // Exclude existing adults from this deterministic pairing scenario.
            for (var data : colony.getCitizenManager().getCitizens()) data.getEntity().ifPresent(e ->
                    ((CitizenMcaActor)e).colonyMca$actor().getTraits().addTrait(Traits.INFERTILE));
            var first = adult(colony, "Acceptance Parent A");
            var second = adult(colony, "Acceptance Parent B");
            var ids = new HashSet<>(colony.getCitizenManager().getCitizens().stream().map(ICitizenData::getUUID).toList());
            ((com.minecolonies.core.colony.managers.ReproductionManager)colony.getReproductionManager()).trySpawnChild();
            var a = ((CitizenMcaActor)first).colonyMca$actor();
            var b = ((CitizenMcaActor)second).colonyMca$actor();
            check(a.getRelationships().isMarriedTo(second.getUUID()) && b.getRelationships().isMarriedTo(first.getUUID()),
                    "Native colony reproduction creates reciprocal MCA NPC marriage");
            var child = colony.getCitizenManager().getCitizens().stream().filter(c -> !ids.contains(c.getUUID())).findFirst().orElseThrow();
            var node = FamilyTree.get(world).getOrEmpty(child.getUUID()).orElseThrow();
            check(new HashSet<>(List.of(node.father(), node.mother())).equals(new HashSet<>(List.of(first.getUUID(),second.getUUID())))
                    && child.getHomeBuilding() != null && child.isChild(), "Automatic NPC child retains both UUID parents and real colony housing");
            clearBirth(first); clearBirth(second);
            player.teleportTo(first.getX(), first.getY(), first.getZ()-2);
            player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.CAKE));
            int before = colony.getCitizenManager().getCurrentCitizenCount();
            new InteractionVillagerMessage("gift", first.getUUID()).handleServer(player);
            check(player.getMainHandItem().isEmpty() && colony.getCitizenManager().getCurrentCitizenCount() == before+1,
                    "Native MCA cake gift consumes cake and creates a colony child for married NPCs");
            player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.CAKE));
            new InteractionVillagerMessage("gift", first.getUUID()).handleServer(player);
            check(player.getMainHandItem().getCount()==1 && colony.getCitizenManager().getCurrentCitizenCount()==before+1,
                    "Cake birth respects cooldown without consuming rejected gift");
            player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.RED_DYE));
            new InteractionVillagerMessage("gift", first.getUUID()).handleServer(player);
            check(player.getMainHandItem().isEmpty() && a.getHairDye() != 0,
                    "Native MCA dye gift updates actual citizen appearance");
            growAndEmploy(child, worker);
            var younger=colony.getCitizenManager().getCitizens().stream().filter(c->c.isChild() && !ids.contains(c.getUUID())).findFirst().orElseThrow();
            var youngerEntity=(EntityCitizen)younger.getEntity().orElseThrow();
            player.teleportTo(youngerEntity.getX(),youngerEntity.getY(),youngerEntity.getZ()-2);
            player.setItemInHand(InteractionHand.MAIN_HAND,new ItemStack(Items.GOLDEN_APPLE));
            new InteractionVillagerMessage("gift",younger.getUUID()).handleServer(player);
            check(player.getMainHandItem().isEmpty() && !younger.isChild() && !youngerEntity.isBaby(),
                    "Native MCA golden apple gift advances actual colony child growth rather than proxy-only age");
            check(world.getEntitiesOfClass(net.conczin.mca.entity.VillagerEntityMCA.class, first.getBoundingBox().inflate(40)).isEmpty(),
                    "Marriage, gifts, birth and growth spawn no duplicate MCA world villagers");
            Files.writeString(RESULT, "PASS: " + checks.size() + " real colony integration checks\n" + String.join("\n",checks));
            ColonyMca.LOGGER.info("PASS: {} real colony integration checks", checks.size());
        } catch (Throwable error) {
            ColonyMca.LOGGER.error("Real colony acceptance failed",error);
            try { Files.writeString(RESULT,"FAIL: " + error + "\n" + String.join("\n",checks)); } catch(Exception ignored) {}
        }
    }

    static EntityCitizen adult(com.minecolonies.api.colony.IColony colony, String name) {
        var data=colony.getCitizenManager().createAndRegisterCivilianData();
        data.setName(name); data.setIsChild(false); data.setPaused(true);
        colony.getCitizenManager().spawnOrCreateCitizen(data,colony.getWorld(),new BlockPos(126,80,126));
        var citizen=(EntityCitizen)data.getEntity().orElseThrow();
        var actor=((CitizenMcaActor)citizen).colonyMca$actor();
        actor.getTraits().removeTrait(Traits.INFERTILE); actor.getTraits().addTrait(Traits.BISEXUAL);
        ((CitizenMcaActor)citizen).colonyMca$flush();
        return citizen;
    }

    private static void clearBirth(EntityCitizen citizen) {
        var state=(CitizenMcaData)citizen.getCitizenData(); var control=state.colonyMca$getControl();
        control.remove("lastBirth"); state.colonyMca$setControl(control);
    }

    private static void growAndEmploy(ICitizenData child, EntityCitizen worker) throws Exception {
        var entity=(EntityCitizen)child.getEntity().orElseThrow();
        var ai=new com.minecolonies.core.entity.ai.minimal.EntityAICitizenChild(entity);
        var active=ai.getClass().getDeclaredField("aiActiveTime"); active.setAccessible(true); active.setInt(ai,1000000);
        var grow=ai.getClass().getDeclaredMethod("tryGrowUp"); grow.setAccessible(true); grow.invoke(ai);
        check(!entity.isBaby() && !child.isChild() && ((CitizenMcaActor)entity).colonyMca$actor().getAgeState()
                == net.conczin.mca.entity.ai.relationship.AgeState.ADULT,"Native colony child growth updates both native citizen and MCA adult model");
        var building=worker.getCitizenData().getWorkBuilding();
        var module=building.getFirstModuleOccurance(com.minecolonies.core.colony.buildings.modules.WorkerBuildingModule.class);
        module.removeCitizen(worker.getCitizenData());
        check(module.assignCitizen(child) && child.getJob() instanceof com.minecolonies.core.colony.jobs.JobBuilder,
                "Grown colony child can take real builder employment");
    }

    private static void check(boolean ok,String message) throws Exception {
        if(!ok) throw new AssertionError(message);
        checks.add(message); ColonyMca.LOGGER.info("Colony acceptance: {}",message);
        Files.writeString(RESULT,"RUNNING\n"+String.join("\n",checks));
    }
}

package dev.colonymca.smoke;

import com.minecolonies.api.colony.IColonyManager;
import com.minecolonies.api.colony.ICitizenData;
import com.minecolonies.api.tileentities.AbstractTileEntityGrave;
import com.minecolonies.core.entity.citizen.EntityCitizen;
import dev.colonymca.ColonyMca;
import dev.colonymca.bridge.CitizenMcaActor;
import dev.colonymca.bridge.CitizenMcaData;
import net.conczin.mca.entity.ai.Traits;
import net.conczin.mca.registry.ItemsMCA;
import net.conczin.mca.server.world.data.FamilyTree;
import net.conczin.mca.server.world.data.PlayerSaveData;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.inventory.MerchantMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** Real integrated-server acceptance. All expected changes go through upstream handlers/managers. */
@EventBusSubscriber(modid = ColonyMca.ID, value = Dist.CLIENT)
public final class LifecycleAcceptance {
    private static int stage;
    private static int wait;
    private static final List<String> checks = new ArrayList<>();
    private static final Path RESULT = Path.of("../build/gameplay-preview/lifecycle.txt");

    @SubscribeEvent public static void tick(ServerTickEvent.Post event) {
        if (!Boolean.getBoolean("colony_mca.extendedVerify") || !GameplayPreview.ready() || stage >= 3) return;
        try {
            var world = event.getServer().overworld();
            var citizen = (EntityCitizen) world.getEntity(GameplayPreview.citizenIdentity());
            if (citizen == null) throw new AssertionError("Real worker not loaded");
            var player = event.getServer().getPlayerList().getPlayers().getFirst();
            player.teleportTo(citizen.getX(), citizen.getY(), citizen.getZ() - 2);
            if (stage == 0) {
                Files.deleteIfExists(RESULT);
                trade(citizen, player);
                stage = 1;
            } else if (stage == 1 && ++wait >= 60) {
                var actor = ((CitizenMcaActor) citizen).colonyMca$actor();
                check(actor.getVillagerData().getLevel() >= 2, "Actual trade XP advances merchant level after closing menu");
                marriageAndBirth(citizen, player);
                deathAndResurrection(citizen, player);
                stage = 3;
                Files.writeString(RESULT, "PASS: " + checks.size() + " real lifecycle checks\n" + String.join("\n", checks));
                ColonyMca.LOGGER.info("PASS: {} real lifecycle checks", checks.size());
                event.getServer().saveEverything(false, true, true);
                var restored = (EntityCitizen) world.getEntity(GameplayPreview.citizenIdentity());
                net.minecraft.client.Minecraft.getInstance().execute(() -> {
                    net.minecraft.client.Minecraft.getInstance().player.displayClientMessage(
                            net.minecraft.network.chat.Component.literal("真实验收完成：交易、婚育与两种墓碑复活通过。"), false);
                    if (restored != null) dev.colonymca.client.ColonyMcaClient.openColony(restored);
                });
            }
        } catch (Throwable error) {
            stage = 3;
            ColonyMca.LOGGER.error("Real lifecycle acceptance failed", error);
            try { Files.writeString(RESULT, "FAIL: " + error + "\n" + String.join("\n", checks)); }
            catch (Exception ignored) {}
        }
    }

    private static void trade(EntityCitizen citizen, ServerPlayer player) throws Exception {
        citizen.getCitizenData().setPaused(true); citizen.getNavigation().stop();
        var actor = ((CitizenMcaActor) citizen).colonyMca$actor();
        actor.setVillagerData(actor.getVillagerData().setLevel(1)); actor.setVillagerXp(0);
        actor.getOffers().forEach(net.minecraft.world.item.trading.MerchantOffer::resetUses);
        new net.conczin.mca.network.c2s.InteractionVillagerMessage("trade", citizen.getUUID()).handleServer(player);
        check(player.containerMenu instanceof MerchantMenu && actor.getTradingPlayer() == player,
                "Native MCA trade command opens real MerchantMenu for the player");
        var menu = (MerchantMenu) player.containerMenu;
        var offer = actor.getOffers().getFirst();
        int beforeUses = offer.getUses();
        int beforeXp = actor.getVillagerXp();
        for (int i = 0; i < 12 && actor.getVillagerXp() < 10 && !offer.isOutOfStock(); i++) {
            menu.setSelectionHint(0);
            menu.getSlot(0).set(offer.getCostA().copy());
            menu.getSlot(1).set(offer.getCostB().copy());
            check(!menu.getSlot(2).getItem().isEmpty(), "Vanilla merchant computes trade output");
            menu.clicked(2, 0, net.minecraft.world.inventory.ClickType.QUICK_MOVE, player);
        }
        check(offer.getUses() > beforeUses && actor.getVillagerXp() > beforeXp,
                "Real merchant result slot consumes costs and awards native trade XP");
        player.closeContainer();
        ((CitizenMcaActor) citizen).colonyMca$flush();
    }

    private static void marriageAndBirth(EntityCitizen citizen, ServerPlayer player) throws Exception {
        var data = citizen.getCitizenData();
        var colony = data.getColony();
        var extraHome = GameplayPreview.building(colony, new BlockPos(136, 80, 123), com.minecolonies.api.blocks.ModBlocks.blockHutHome);
        extraHome.setBuildingLevel(5); extraHome.setBlueprintPath("fundamentals/residence5.blueprint");
        colony.getCitizenManager().calculateMaxCitizens();
        // Repeated real-world acceptance runs can legitimately fill the earlier homes.
        for(var pos:List.of(new BlockPos(116,80,116),new BlockPos(140,80,140),new BlockPos(116,80,140),new BlockPos(140,80,116))) {
            if(colony.getCitizenManager().getCurrentCitizenCount()<colony.getCitizenManager().getMaxCitizens()
                    && colony.getServerBuildingManager().getHouseWithSpareBed()!=null)break;
            var spare=GameplayPreview.building(colony,pos,com.minecolonies.api.blocks.ModBlocks.blockHutHome);
            spare.setBuildingLevel(5);spare.setBlueprintPath("fundamentals/residence5.blueprint");
            colony.getCitizenManager().calculateMaxCitizens();
        }
        ColonyMca.LOGGER.info("Birth acceptance preconditions: population={}, maximum={}, spareHome={}",
                colony.getCitizenManager().getCurrentCitizenCount(),colony.getCitizenManager().getMaxCitizens(),
                colony.getServerBuildingManager().getHouseWithSpareBed()!=null);
        var actor = ((CitizenMcaActor) citizen).colonyMca$actor();
        PlayerSaveData.get(player).endRelationShip(net.conczin.mca.entity.ai.relationship.RelationshipState.SINGLE);
        actor.getRelationships().endRelationShip(net.conczin.mca.entity.ai.relationship.RelationshipState.SINGLE);
        var birthState=(CitizenMcaData)data; var birthControl=birthState.colonyMca$getControl();
        birthControl.remove("lastBirth"); birthState.colonyMca$setControl(birthControl);
        actor.getTraits().addTrait(Traits.BISEXUAL); actor.getTraits().removeTrait(Traits.INFERTILE);
        actor.getVillagerBrain().getMemoriesForPlayer(player).setHearts(200);
        check(!PlayerSaveData.get(player).isMarried(), "Test player starts unmarried");
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(ItemsMCA.ENGAGEMENT_RING));
        new net.conczin.mca.network.c2s.InteractionVillagerMessage("gift", citizen.getUUID()).handleServer(player);
        check(actor.getRelationships().isEngaged() && player.getMainHandItem().isEmpty(),
                "Native engagement-ring gift creates engagement and consumes ring");
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(ItemsMCA.WEDDING_RING));
        new net.conczin.mca.network.c2s.InteractionVillagerMessage("gift", citizen.getUUID()).handleServer(player);
        ColonyMca.LOGGER.info("Wedding acceptance state: actor={}, player={}, hand={}, hearts={}, handler={}",
                actor.getRelationships().getRelationshipState(), PlayerSaveData.get(player).getRelationshipState(),
                player.getMainHandItem(), actor.getVillagerBrain().getMemoriesForPlayer(player).getHearts(), actor.getInteractions().getClass().getName());
        ColonyMca.LOGGER.info("Wedding range={} alive={} sameWorld={} access={}", citizen.distanceToSqr(player), citizen.isAlive(), citizen.level() == player.level(),
                data.getColony().getPermissions().hasPermission(player, com.minecolonies.api.colony.permissions.Action.ACCESS_HUTS));
        check(actor.getRelationships().isMarriedTo(player.getUUID()) && PlayerSaveData.get(player).isMarried()
                && player.getMainHandItem().isEmpty(), "Native wedding-ring gift marries both sides and consumes ring");
        var ids = new java.util.HashSet<>(colony.getCitizenManager().getCitizens().stream().map(ICitizenData::getUUID).toList());
        check(dev.colonymca.bridge.BirthBridge.playerBirth(citizen, player), "Player marriage births child through colony birth bridge");
        var child = colony.getCitizenManager().getCitizens().stream().filter(c -> !ids.contains(c.getUUID())).findFirst().orElseThrow();
        check(child.isChild() && child.getHomeBuilding() != null && child.getEntity().isPresent(),
                "New child occupies native population and real housing, and spawns in server world");
        var node = FamilyTree.get(player.serverLevel()).getOrEmpty(child.getUUID()).orElseThrow();
        var parents = new java.util.HashSet<>(List.of(node.father(), node.mother()));
        check(parents.contains(player.getUUID()) && parents.contains(citizen.getUUID()), "MCA child family tree retains both actual parents");
        check(!((CitizenMcaData) child).colonyMca$getData().isEmpty(), "Child inherits saved MCA genetic profile");
        int count = colony.getCitizenManager().getCurrentCitizenCount();
        check(!dev.colonymca.bridge.BirthBridge.playerBirth(citizen, player)
                && colony.getCitizenManager().getCurrentCitizenCount() == count, "Birth cooldown blocks duplicate child without leaking population");
    }

    private static void deathAndResurrection(EntityCitizen citizen, ServerPlayer player) throws Exception {
        var world = player.serverLevel();
        var colony = citizen.getCitizenData().getColony();
        var identity = citizen.getUUID();
        net.neoforged.neoforge.items.ItemHandlerHelper.insertItemStacked(citizen.getCitizenData().getInventory(), new ItemStack(Items.DIAMOND, 7), false);
        int diamonds = count(citizen.getCitizenData().getInventory(), Items.DIAMOND);
        var scythe = new ItemStack(ItemsMCA.SCYTHE);
        player.setItemInHand(InteractionHand.MAIN_HAND, scythe);
        citizen.setHealth(0.01f); citizen.invulnerableTime = 0; // Native rules permit a one-point hit without HURT_CITIZEN.
        boolean hurt = citizen.hurt(world.damageSources().playerAttack(player), 1);
        ColonyMca.LOGGER.info("Death acceptance hurt={} health={} alive={} invulnerable={} permission={} raided={} soul={}",
                hurt, citizen.getHealth(), citizen.isAlive(), citizen.isInvulnerable(),
                colony.getPermissions().hasPermission(player, com.minecolonies.api.colony.permissions.Action.HURT_CITIZEN),
                colony.getRaiderManager().isRaided(), net.conczin.mca.item.ScytheItem.hasSoul(scythe));
        check(!citizen.isAlive() && net.conczin.mca.item.ScytheItem.hasSoul(scythe), "Real citizen death charges native MCA scythe");
        check(FamilyTree.get(world).getOrEmpty(identity).orElseThrow().isDeceased(), "Dead citizen marked deceased in MCA family tree");
        check(PlayerSaveData.get(player).getRelationshipState() == net.conczin.mca.entity.ai.relationship.RelationshipState.WIDOW,
                "Native MCA player spouse becomes widowed on colony citizen death");
        var grave = grave(world, colony, identity);
        check(grave != null && count(grave.getInventory(), Items.DIAMOND) == diamonds,
                "Native colony grave stores actual citizen items");
        var staff = new ItemStack(ItemsMCA.STAFF_OF_LIFE);
        player.setGameMode(net.minecraft.world.level.GameType.SURVIVAL);
        player.setItemInHand(InteractionHand.MAIN_HAND, staff);
        BlockPos gravePos = grave.getBlockPos();
        useGrave(player, gravePos);
        var revived = (EntityCitizen) world.getEntity(identity);
        check(revived != null && revived.isAlive(), "Staff resurrects native colony citizen with same MCA UUID");
        check(staff.getDamageValue() == 1 && count(revived.getCitizenData().getInventory(), Items.DIAMOND) == diamonds,
                "Staff consumes one use and restores actual items exactly once");
        int population = colony.getCitizenManager().getCurrentCitizenCount();
        useGrave(player, gravePos);
        check(colony.getCitizenManager().getCurrentCitizenCount() == population && staff.getDamageValue() == 1,
                "Consumed grave cannot resurrect twice or consume a second staff use");
        player.setItemInHand(InteractionHand.MAIN_HAND, scythe);
        revived.setHealth(0.01f); revived.invulnerableTime = 0;
        revived.hurt(world.damageSources().playerAttack(player), 1);
        var secondGrave = grave(world, colony, identity);
        check(secondGrave != null, "Resurrected citizen can die into a second native grave");
        useGrave(player, secondGrave.getBlockPos());
        var again = (EntityCitizen) world.getEntity(identity);
        check(again != null && again.isAlive() && !net.conczin.mca.item.ScytheItem.hasSoul(scythe),
                "Charged native scythe resurrects once and consumes its soul");
        check(count(again.getCitizenData().getInventory(), Items.DIAMOND) == diamonds,
                "Items remain conserved through two death/resurrection cycles");
        player.setGameMode(net.minecraft.world.level.GameType.CREATIVE);
    }

    private static AbstractTileEntityGrave grave(ServerLevel world, com.minecolonies.api.colony.IColony colony, UUID identity) {
        for (var pos : colony.getGraveManager().getGraves().keySet()) {
            if (world.getBlockEntity(pos) instanceof AbstractTileEntityGrave grave && grave.getGraveData() != null) {
                var saved = grave.getGraveData().getCitizenDataNBT();
                if (saved != null && saved.hasUUID("colony_mca_identity") && saved.getUUID("colony_mca_identity").equals(identity)) return grave;
            }
        }
        return null;
    }

    private static void useGrave(ServerPlayer player, BlockPos pos) {
        var hit = new net.minecraft.world.phys.BlockHitResult(net.minecraft.world.phys.Vec3.atCenterOf(pos), net.minecraft.core.Direction.UP, pos, false);
        var event = new net.neoforged.neoforge.event.entity.player.PlayerInteractEvent.RightClickBlock(player, InteractionHand.MAIN_HAND, pos, hit);
        net.neoforged.neoforge.common.NeoForge.EVENT_BUS.post(event);
    }

    private static int count(net.neoforged.neoforge.items.IItemHandler inventory, net.minecraft.world.item.Item item) {
        int count = 0;
        for (int i = 0; i < inventory.getSlots(); i++) if (inventory.getStackInSlot(i).is(item)) count += inventory.getStackInSlot(i).getCount();
        return count;
    }

    private static void check(boolean ok, String message) throws Exception {
        if (!ok) throw new AssertionError(message);
        checks.add(message);
        ColonyMca.LOGGER.info("Lifecycle acceptance: {}", message);
        Files.writeString(RESULT, "RUNNING\n" + String.join("\n", checks));
    }
}

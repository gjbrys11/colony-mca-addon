package dev.colonymca.smoke;

import com.minecolonies.api.colony.IColonyManager;
import com.minecolonies.api.blocks.ModBlocks;
import com.minecolonies.api.tileentities.AbstractTileEntityColonyBuilding;
import com.minecolonies.core.colony.buildings.modules.LivingBuildingModule;
import com.minecolonies.core.colony.buildings.modules.WorkerBuildingModule;
import dev.colonymca.ColonyMca;
import dev.colonymca.bridge.CitizenMcaActor;
import dev.colonymca.client.ColonyMcaClient;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;

/** Interactive fixture with a real integrated-server colony, buildings, job and network identity. */
@EventBusSubscriber(modid = ColonyMca.ID, value = Dist.CLIENT)
public final class GameplayPreview {
    public static boolean verified() { return phase == 7; }
    public static boolean ready() { return citizenId != null; }
    public static UUID citizenIdentity() { return citizenId; }
    public static int colonyIdentity() { return colonyId; }
    private static int ticks;
    private static boolean starting;
    private static boolean opened;
    private static volatile UUID citizenId;
    private static volatile int colonyId;
    private static volatile String failure;
    private static volatile int phase;
    private static int phaseTicks;
    private static net.minecraft.world.phys.Vec3 movementStart;
    private static boolean answerSent;
    private static boolean introductionAnswered;
    private static boolean greetingAnswered;
    private static String beforeChat = "";
    private static int dialogueTicks;
    private static final BlockPos CENTER = new BlockPos(128, 80, 128);

    @SubscribeEvent public static void tick(ClientTickEvent.Post event) {
        if (!Boolean.getBoolean("colony_mca.uiPreview")) return;
        var mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null || mc.getSingleplayerServer() == null) return;
        if (!starting && ++ticks > 40) {
            starting = true;
            UUID player = mc.player.getUUID();
            mc.getSingleplayerServer().execute(() -> {
                try { prepare(mc.getSingleplayerServer().getPlayerList().getPlayer(player)); }
                catch (Throwable error) {
                    ColonyMca.LOGGER.error("Real gameplay preview failed", error);
                    failure = error.toString();
                }
            });
        }
        if (failure != null) {
            mc.player.displayClientMessage(Component.literal("真实试玩检查失败：" + failure), false);
            failure = null;
        }
        if (!opened && citizenId != null && ticks++ % 20 == 0) {
            var colony = IColonyManager.getInstance().getColonyView(colonyId, mc.level.dimension());
            if (colony == null) return;
            for (var entity : mc.level.entitiesForRendering()) {
                if (citizenId.equals(entity.getUUID()) && entity instanceof com.minecolonies.core.entity.citizen.EntityCitizen citizen) {
                    opened = true;
                    ColonyMcaClient.openColony(citizen);
                    mc.player.displayClientMessage(Component.literal("真实试玩环境：建筑工、工作建筑、住宅和背包均在服务器上。可测试对话、赠礼、跟随及殖民地原生任务。"), false);
                    break;
                }
            }
        }
        if (opened && Boolean.getBoolean("colony_mca.gameplayVerify") && !Boolean.getBoolean("colony_mca.extendedVerify")) {
            try {
                var module = dev.colonymca.client.McaSidebarModule.current();
                if (phase == 0 && module != null) {
                    phase = 1;
                    module.startDialogue();
                } else if (phase == 1 && mc.screen instanceof com.ldtteam.blockui.BOScreen screen) {
                    var answer = screen.getWindow().findPaneByID("colony_mca_dialogue_answer_chat");
                    var phrase = screen.getWindow().findPaneOfTypeByID("colony_mca_dialogue_phrase", com.ldtteam.blockui.controls.Text.class);
                    if (++dialogueTicks % 40 == 0) {
                        ColonyMca.LOGGER.info("Real dialogue state: sent={}, chatAvailable={}, phrase={}, before={}", answerSent,
                                answer != null, phrase == null ? "<missing>" : phrase.getText().getString(), beforeChat);
                    }
                    var introduction = screen.getWindow().findPaneByID("colony_mca_dialogue_answer_exploring");
                    var greeting = screen.getWindow().findPaneByID("colony_mca_dialogue_answer_kind");
                    if (!greetingAnswered && greeting instanceof com.ldtteam.blockui.controls.Button button) {
                        greetingAnswered = true;
                        button.handleClick(0, 0);
                    }
                    if (!introductionAnswered && introduction instanceof com.ldtteam.blockui.controls.Button button) {
                        introductionAnswered = true;
                        button.handleClick(0, 0);
                    }
                    if (!answerSent && answer instanceof com.ldtteam.blockui.controls.Button button) {
                        beforeChat = phrase == null ? "" : phrase.getText().getString();
                        ColonyMca.LOGGER.info("Selecting real chat answer; previous phrase={}", beforeChat);
                        answerSent = true;
                        button.handleClick(0, 0); // Sends the actual UI answer packet to the real server citizen.
                    }
                    boolean received = answerSent && phrase != null && !phrase.getText().getString().equals(beforeChat) && !phrase.getText().getString().equals(
                            Component.translatable("colony_mca.sidebar.waiting").getString());
                    if (received) {
                        ColonyMca.LOGGER.info("Gameplay network check: received real MCA dialogue response");
                        phase = 2;
                        mc.getSingleplayerServer().execute(() -> {
                            var player = mc.getSingleplayerServer().getPlayerList().getPlayer(mc.player.getUUID());
                            var citizen = player.serverLevel().getEntity(citizenId);
                            player.teleportTo(citizen.getX(), citizen.getY(), citizen.getZ() - 3);
                            player.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND,
                                    new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.DIAMOND, 16));
                            send("gift");
                        });
                    }
                }
            } catch (Throwable error) { fail(error); }
        }
    }

    @SubscribeEvent public static void serverTick(net.neoforged.neoforge.event.tick.ServerTickEvent.Post event) {
        if (Boolean.getBoolean("colony_mca.extendedVerify") || !Boolean.getBoolean("colony_mca.gameplayVerify") || citizenId == null || phase == 0 || phase >= 7) return;
        try {
            if (++phaseTicks > 600) throw new AssertionError("Gameplay verification timed out at phase " + phase);
            var world = event.getServer().overworld();
            var citizen = (com.minecolonies.core.entity.citizen.EntityCitizen) world.getEntity(citizenId);
            if (citizen == null) return;
            var player = event.getServer().getPlayerList().getPlayers().getFirst();
            var data = citizen.getCitizenData();
            var control = ((dev.colonymca.bridge.CitizenMcaData) data).colonyMca$getControl();
            if (phase == 2) {
                int diamonds = 0;
                for (int slot = 0; slot < data.getInventory().getSlots(); slot++) {
                    var stack = data.getInventory().getStackInSlot(slot);
                    if (stack.is(net.minecraft.world.item.Items.DIAMOND)) diamonds += stack.getCount();
                }
                if (diamonds > 0 && player.getMainHandItem().getCount() < 16) {
                    ColonyMca.LOGGER.info("Gameplay network check: gift consumed from player and stored in actual colony inventory");
                    phase = 3; phaseTicks = 0; send("follow");
                }
            } else if (phase == 3 && control.getString("movement").equals("FOLLOW") && data.isPaused()) {
                movementStart = citizen.position();
                player.teleportTo(citizen.getX(), citizen.getY(), citizen.getZ() - 8);
                phase = 4; phaseTicks = 0;
            } else if (phase == 4 && citizen.position().distanceToSqr(movementStart) >= 1) {
                ColonyMca.LOGGER.info("Gameplay network check: actual server citizen followed player, travelled {} blocks",
                        citizen.position().distanceTo(movementStart));
                phase = 5; phaseTicks = 0; send("stay");
            } else if (phase == 5 && control.getString("movement").equals("STAY") && data.isPaused()) {
                phase = 6; phaseTicks = 0; send("move");
            } else if (phase == 6 && !control.contains("movement") && !data.isPaused()) {
                if (!data.getJob().getJobRegistryEntry().getKey().equals(com.minecolonies.api.colony.jobs.ModJobs.BUILDER_ID))
                    throw new AssertionError("Social commands replaced colony builder job");
                if (data.getWorkBuilding() == null || data.getWorkBuilding().getFirstModuleOccurance(WorkerBuildingModule.class) == null)
                    throw new AssertionError("Native work building was lost");
                phase = 7;
                String result = "PASS: real network dialogue, gift consumption and colony inventory, follow navigation, stay, work resume and native builder job preserved";
                Files.writeString(Path.of("../build/gameplay-preview/result.txt"), result);
                ColonyMca.LOGGER.info(result);
                Minecraft.getInstance().execute(() -> {
                    Minecraft.getInstance().player.displayClientMessage(Component.literal("真实功能检查通过：对话、赠礼、跟随移动、停留及恢复建筑工工作。"), false);
                    net.neoforged.neoforge.network.PacketDistributor.sendToServer(
                            new dev.colonymca.network.CitizenPanelRequest(citizen.getId()));
                });
            }
        } catch (Throwable error) { fail(error); }
    }

    private static void send(String command) {
        Minecraft.getInstance().execute(() -> net.conczin.mca.network.Network.sendToServer(
                new net.conczin.mca.network.c2s.InteractionVillagerMessage(command, citizenId)));
    }

    private static void fail(Throwable error) {
        phase = 8;
        ColonyMca.LOGGER.error("Real gameplay verification failed", error);
        try { Files.createDirectories(Path.of("../build/gameplay-preview")); Files.writeString(Path.of("../build/gameplay-preview/result.txt"), "FAIL: " + error); }
        catch (Exception ignored) {}
        failure = error.toString();
    }

    private static void prepare(ServerPlayer player) throws Exception {
        if (player == null) throw new IllegalStateException("Integrated-server player not found");
        Files.deleteIfExists(Path.of("../build/gameplay-preview/result.txt"));
        var world = player.serverLevel();
        player.teleportTo(world,128.5,80,124.5,0,0);
        for(int cx=7;cx<=8;cx++)for(int cz=7;cz<=8;cz++) {
            if(!com.minecolonies.api.util.WorldUtil.isEntityChunkLoaded(world,cx,cz)) {
                world.getChunk(cx,cz);starting=false;ticks=0;return;
            }
        }
        player.setGameMode(net.minecraft.world.level.GameType.CREATIVE);
        player.setHealth(player.getMaxHealth());
        world.setDayTime(6000);
        world.getGameRules().getRule(net.minecraft.world.level.GameRules.RULE_DAYLIGHT).set(false, world.getServer());
        for (int x = -12; x <= 12; x++) for (int z = -12; z <= 12; z++) {
            var ground = CENTER.offset(x, -1, z);
            world.setBlockAndUpdate(ground, Blocks.SMOOTH_STONE.defaultBlockState());
            for (int y = 0; y < 5; y++) {
                var pos = CENTER.offset(x, y, z);
                if (!(world.getBlockEntity(pos) instanceof AbstractTileEntityColonyBuilding))
                    world.setBlockAndUpdate(pos, Blocks.AIR.defaultBlockState());
            }
        }
        player.teleportTo(world, 128.5, 80, 124.5, 0, 0);
        var manager = IColonyManager.getInstance();
        var colony = manager.getColonyByPosFromWorld(world, CENTER);
        if (colony == null) colony = manager.createColony(world, CENTER, player, "Colony MCA 真实试玩", "Medieval Oak");
        if (colony == null) throw new IllegalStateException("Native colony creation returned null");
        colony.getPermissions().setOwner(player);
        colony.getPackageManager().addImportantColonyPlayer(player);
        colony.getPackageManager().addCloseSubscriber(player);
        var town = building(colony, CENTER, ModBlocks.blockHutTownHall);
        var home = building(colony, CENTER.offset(-5, 0, 3), ModBlocks.blockHutHome);
        var builder = building(colony, CENTER.offset(5, 0, 3), ModBlocks.blockHutBuilder);
        town.setBuildingLevel(1); home.setBuildingLevel(5); builder.setBuildingLevel(1);
        town.setBlueprintPath("fundamentals/townhall1.blueprint");
        home.setBlueprintPath("fundamentals/residence5.blueprint");
        builder.setBlueprintPath("fundamentals/builder1.blueprint");
        colony.getCitizenManager().calculateMaxCitizens();
        var data = colony.getCitizenManager().getCitizens().stream()
                .filter(c -> "Taylor 真实建筑工".equals(c.getName())).findFirst().orElse(null);
        if (data == null) data = colony.getCitizenManager().createAndRegisterCivilianData();
        data.setName("Taylor 真实建筑工"); data.setIsChild(false); data.setPaused(false);
        var living = home.getFirstModuleOccurance(LivingBuildingModule.class);
        if (data.getHomeBuilding() == null && !living.assignCitizen(data)) throw new IllegalStateException("Home assignment failed");
        var work = builder.getFirstModuleOccurance(WorkerBuildingModule.class);
        if (data.getWorkBuilding() == null && !work.assignCitizen(data)) throw new IllegalStateException("Builder assignment failed");
        if(world.getEntity(data.getUUID())==null)
            colony.getCitizenManager().spawnOrCreateCivilian(data,world,java.util.List.of(CENTER.offset(0,0,1)),true);
        if (!(world.getEntity(data.getUUID()) instanceof com.minecolonies.core.entity.citizen.EntityCitizen citizen))
            throw new IllegalStateException("Native citizen did not spawn");
        citizen.setPos(128.5, 80, 127.5);
        ((CitizenMcaActor) citizen).colonyMca$actor();
        if (Boolean.getBoolean("colony_mca.gameplayVerify")) {
            var actor=((CitizenMcaActor)citizen).colonyMca$actor();
            while(!actor.getRelationships().getGiftSaturation().toNbt().isEmpty()) actor.getRelationships().getGiftSaturation().pop();
            actor.getVillagerBrain().getMemoriesForPlayer(player).setInteractionFatigue(0);
        }
        dev.colonymca.bridge.MovementBridge.command(citizen, player, net.conczin.mca.entity.ai.MoveState.MOVE);
        data.setPaused(false);
        ((CitizenMcaActor) citizen).colonyMca$flush();
        player.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND,
                new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.DIAMOND, 16));
        colony.markDirty(); colony.getPackageManager().updateSubscribers();
        colonyId = colony.getID(); citizenId = citizen.getUUID();
        Files.createDirectories(Path.of("../build/gameplay-preview"));
        Files.writeString(Path.of("../build/gameplay-preview/setup.txt"), "REAL colony=" + colonyId
                + " citizen=" + data.getId() + " uuid=" + citizenId + " job=" + data.getJob().getJobRegistryEntry().getKey());
        ColonyMca.LOGGER.info("Real gameplay preview ready: colony {}, citizen {}, job {}", colonyId, citizenId,
                data.getJob().getJobRegistryEntry().getKey());
    }

    static com.minecolonies.api.colony.buildings.IBuilding building(com.minecolonies.api.colony.IColony colony, BlockPos pos, Block block) {
        var old = colony.getServerBuildingManager().getBuilding(pos);
        var world = colony.getWorld();
        if(!(world.getBlockEntity(pos) instanceof AbstractTileEntityColonyBuilding))world.setBlockAndUpdate(pos, block.defaultBlockState());
        if (!(world.getBlockEntity(pos) instanceof AbstractTileEntityColonyBuilding tile))
            throw new IllegalStateException("Missing native building block entity at " + pos);
        if(old!=null){tile.setColony(colony);tile.setBuilding(old);return old;}
        var result = colony.getServerBuildingManager().addNewBuilding(tile, world);
        if (result == null) throw new IllegalStateException("Could not register building at " + pos);
        return result;
    }
}

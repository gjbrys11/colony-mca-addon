package dev.colonymca.smoke;

import com.minecolonies.api.colony.ICitizenDataView;
import com.minecolonies.api.colony.IColonyView;
import com.minecolonies.api.entity.ModEntities;
import com.minecolonies.core.client.gui.citizen.MainWindowCitizen;
import com.minecolonies.core.entity.citizen.citizenhandlers.CitizenHappinessHandler;
import com.minecolonies.core.entity.citizen.citizenhandlers.CitizenSkillHandler;
import dev.colonymca.ColonyMca;
import dev.colonymca.bridge.CitizenMcaActor;
import dev.colonymca.client.McaSidebarModule;
import dev.colonymca.network.CitizenPanelSnapshot;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import java.lang.reflect.Proxy;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

/** Visual fixture in a separate source set; never included in the mod jar. */
@EventBusSubscriber(modid = ColonyMca.ID, value = Dist.CLIENT)
public final class ClientUiSmoke {
    private static int ticks;
    private static boolean opened;
    private static int captureAt;
    private static int stage;
    private static ICitizenDataView fixture;

    @SubscribeEvent public static void tick(ClientTickEvent.Post event) {
        var mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null) return;
        if (!opened && mc.getSingleplayerServer() != null) {
            var uuid = mc.player.getUUID();
            mc.getSingleplayerServer().execute(() -> {
                var player = mc.getSingleplayerServer().getPlayerList().getPlayer(uuid);
                if (player != null) {
                    if (player.isDeadOrDying()) player = mc.getSingleplayerServer().getPlayerList().respawn(player, false,
                            net.minecraft.world.entity.Entity.RemovalReason.KILLED);
                    player.setGameMode(net.minecraft.world.level.GameType.CREATIVE);
                    player.setHealth(player.getMaxHealth());
                }
            });
        }
        if (opened && Boolean.getBoolean("colony_mca.uiPreview")) return;
        try {
            if (!opened && ++ticks > 180) {
                opened = true;
                openFixture(mc);
                captureAt = ticks + 35;
            } else if (opened && ++ticks == captureAt) {
                Files.createDirectories(Path.of("../build/ui-smoke"));
                try (var screenshot = Screenshot.takeScreenshot(mc.getMainRenderTarget())) {
                    screenshot.writeToFile(Path.of("../build/ui-smoke/" + switch (stage) {
                        case 0 -> "unified-main.png";
                        case 1 -> "unified-social.png";
                        case 2 -> "unified-dialogue.png";
                        case 3 -> "unified-happiness.png";
                        default -> "debug-chinese.png";
                    }));
                }
                var screen = (com.ldtteam.blockui.BOScreen) mc.screen;
                if (stage < 4 && (screen.getWindow().getWidth() != 384 || screen.getWindow().findPaneByID("mainIcon").getX() != 179))
                    throw new AssertionError("Native colony navigation moved or sidebar width incorrect");
                if (stage == 0) {
                    var overlay = new com.ldtteam.blockui.views.View();
                    overlay.setID("click_regression_overlay"); overlay.setSize(170, 20);
                    screen.getWindow().addChild(overlay);
                    overlay.setPosition(0, screen.getWindow().getHeight() - 51);
                    for (int attempt = 0; attempt < 3; attempt++) {
                        clickThroughWindow("colony_mca_social_tab");
                        if (screen.getWindow().findPaneByID("colony_mca_action_talk") == null
                                || screen.getWindow().findPaneByID("colony_mca_value_profession") != null)
                            throw new AssertionError("Social tab did not replace attribute content");
                        clickThroughWindow("colony_mca_attributes_tab");
                        if (screen.getWindow().findPaneByID("colony_mca_value_profession") == null
                                || screen.getWindow().findPaneByID("colony_mca_action_talk") != null)
                            throw new AssertionError("Attributes tab did not replace social content");
                    }
                    clickThroughWindow("colony_mca_social_tab");
                    screen.getWindow().removeChild(overlay);
                    stage = 1; captureAt = ticks + 25;
                } else if (stage == 1) {
                    McaSidebarModule.current().startDialogue();
                    net.conczin.mca.ClientProxy.getNetworkHandler().handleDialogueQuestionResponse(
                            new net.conczin.mca.network.s2c.InteractionDialogueQuestionResponse(Component.literal("你好，今天过得怎么样？"), true));
                    net.conczin.mca.ClientProxy.getNetworkHandler().handleDialogueResponse(
                            new net.conczin.mca.network.s2c.InteractionDialogueResponse("main", List.of("chat", "joke", "story")));
                    stage = 2; captureAt = ticks + 25;
                } else if (stage == 2) {
                    clickThroughWindow("happinessIcon");
                    if (!( ((com.ldtteam.blockui.BOScreen) mc.screen).getWindow() instanceof com.minecolonies.core.client.gui.citizen.HappinessWindowCitizen))
                        throw new AssertionError("Clicking happiness navigation did not open its page");
                    stage = 3; captureAt = ticks + 25;
                } else if (stage == 3) {
                    clickThroughWindow("mainIcon");
                    if (!(((com.ldtteam.blockui.BOScreen) mc.screen).getWindow() instanceof MainWindowCitizen))
                        throw new AssertionError("Clicking main navigation did not return to attributes");
                    new com.minecolonies.core.debug.gui.DebugWindowCitizen(fixture).open();
                    stage = 4; captureAt = ticks + 25;
                } else {
                    if (!screen.getWindow().findPaneOfTypeByID("aihistory", com.ldtteam.blockui.controls.Button.class)
                            .getText().getString().equals("查询行为历史")) throw new AssertionError("Debug label not translated");
                    Files.writeString(Path.of("../build/ui-smoke/result.txt"), "PASS: five screens rendered; screen-coordinate clicks switch social and attributes repeatedly under overlays; native navigation edge clicks open the correct pages");
                    mc.stop();
                }
            }
        } catch (Throwable failure) {
            ColonyMca.LOGGER.error("Client UI fixture failed", failure);
            try { Files.createDirectories(Path.of("../build/ui-smoke")); Files.writeString(Path.of("../build/ui-smoke/result.txt"), "FAIL: " + failure); }
            catch (Exception ignored) {}
            mc.stop();
        }
    }

    private static void clickThroughWindow(String id) {
        var screen = (com.ldtteam.blockui.BOScreen) Minecraft.getInstance().screen;
        var window = screen.getWindow();
        var button = window.findPaneByID(id);
        double x = id.endsWith("Icon") ? button.getWidth() - 2.0 : button.getWidth() / 2.0;
        double y = button.getHeight() / 2.0;
        for (com.ldtteam.blockui.Pane pane = button; pane != null; pane = pane.getParent()) {
            x += pane.getX(); y += pane.getY();
        }
        var hit = window.findPaneForClick(x, y);
        ColonyMca.LOGGER.info("UI click {} at {},{} hits {} ({})", id, x, y,
                hit == null ? "null" : hit.getID(), hit == null ? "null" : hit.getClass().getSimpleName());
        double scale = screen.getRenderScale();
        double guiScale = screen.getVanillaGuiScale();
        var display = Minecraft.getInstance().getWindow();
        double originX = Math.floor((display.getWidth() - window.getWidth() * scale) / 2.0);
        double originY = Math.floor((display.getHeight() - window.getHeight() * scale) / 2.0);
        double mouseX = (originX + x * scale) / guiScale;
        double mouseY = (originY + y * scale) / guiScale;
        if (!screen.mouseClicked(mouseX, mouseY, 0)) throw new AssertionError("Click was not handled: " + id);
        screen.mouseReleased(mouseX, mouseY, 0);
    }

    private static void openFixture(Minecraft mc) throws Exception {
        var entity = ModEntities.CITIZEN.create(mc.level);
        entity.setId(-12345); entity.setPos(mc.player.position().add(3, 0, 3)); entity.setCustomName(Component.literal("Taylor · Builder"));
        entity.getEntityData().set(com.minecolonies.api.entity.citizen.AbstractEntityCitizen.DATA_JOB,
                com.minecolonies.api.colony.jobs.ModJobs.BUILDER_ID.toString());
        mc.level.addEntity(entity);
        var actor = ((CitizenMcaActor) entity).colonyMca$actor();
        actor.setCustomName(Component.literal("Taylor · Builder"));
        actor.setVillagerData(actor.getVillagerData().setProfession(net.minecraft.world.entity.npc.VillagerProfession.FARMER));
        actor.initialize(net.minecraft.world.entity.MobSpawnType.COMMAND);
        actor.getTraits().addTrait(net.conczin.mca.entity.ai.Traits.TOUGH);
        var skills = new CitizenSkillHandler(); skills.init(10);
        var happiness = new CitizenHappinessHandler();
        happiness.addModifier(new com.minecolonies.api.entity.citizen.happiness.StaticHappinessModifier(
                "colony_mca.family_housing", 1.0, new com.minecolonies.api.entity.citizen.happiness.StaticHappinessSupplier(0.9)));
        var buildingType = IColonyView.class.getMethod("getClientBuildingManager").getReturnType();
        var buildings = stub(buildingType, (method, args) -> null);
        var permissions = stub(com.minecolonies.api.colony.permissions.IPermissions.class, (method, args) -> true);
        var colony = (IColonyView) stub(IColonyView.class, (method, args) -> switch (method.getName()) {
            case "getID" -> 9876;
            case "getClientBuildingManager" -> buildings;
            case "getPermissions" -> permissions;
            case "getDimension" -> mc.level.dimension();
            default -> null;
        });
        var citizen = (ICitizenDataView) stub(ICitizenDataView.class, (method, args) -> switch (method.getName()) {
            case "getId" -> 1;
            case "getEntityId" -> entity.getId();
            case "getColonyId" -> 9876;
            case "getName" -> "Taylor · Builder";
            case "getColony" -> colony;
            case "getHealth", "getMaxHealth", "getSaturation" -> 20.0;
            case "getHappiness" -> 8.0;
            case "getCitizenSkillHandler" -> skills;
            case "getHappinessHandler" -> happiness;
            case "getJob" -> "com.minecolonies.coremod.job.builder";
            case "getJobComponent" -> Component.translatable("com.minecolonies.coremod.job.builder");
            case "getPosition", "getStatusPosition", "getWorkBuilding", "getHomeBuilding" -> BlockPos.ZERO;
            case "getParents" -> new com.minecolonies.api.util.Tuple<>("", "");
            default -> null;
        });
        var summary = new CompoundTag(); summary.putInt("hearts", 85); summary.putString("marriage", "SINGLE");
        fixture = citizen;
        McaSidebarModule.cache(new CitizenPanelSnapshot(9876, 1, mc.level.dimension().location(), new byte[0], summary, true));
        new MainWindowCitizen(citizen).open();
    }

    private interface Answers { Object answer(java.lang.reflect.Method method, Object[] args); }
    private static Object stub(Class<?> type, Answers answers) {
        return Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[]{type}, (proxy, method, args) -> {
            Object result = answers.answer(method, args);
            if (result != null) return result;
            var returns = method.getReturnType();
            if (returns == boolean.class) return false;
            if (returns == int.class || returns == Integer.class) return 0;
            if (returns == double.class) return 0.0;
            if (returns == float.class) return 0.0F;
            if (returns == long.class) return 0L;
            if (List.class.isAssignableFrom(returns)) return List.of();
            if (Optional.class.isAssignableFrom(returns)) return Optional.empty();
            return null;
        });
    }
}

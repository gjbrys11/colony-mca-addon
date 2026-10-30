package dev.colonymca.smoke;

import dev.colonymca.ColonyMca;
import dev.colonymca.client.RemoteTerminalClient;
import dev.colonymca.client.RemoteTerminalScreen;
import dev.colonymca.network.TerminalRequest;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.Button;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import java.nio.file.*;

/** Real C2S requests and native building operation while the player is outside colony subscription range. */
@EventBusSubscriber(modid=ColonyMca.ID,value=Dist.CLIENT)
public final class TerminalAcceptance {
    private static volatile int stage;
    private static int ticks,priority,colonyId;
    private static BlockPos target;
    private static Vec3 originalPosition;
    private static String originalName;
    private static final Path RESULT=Path.of("../build/gameplay-preview/terminal.txt");
    @SubscribeEvent public static void server(ServerTickEvent.Post event) {
        if(!Boolean.getBoolean("colony_mca.terminalVerify") || !GameplayPreview.ready() || stage>=6)return;
        if(Boolean.getBoolean("colony_mca.workVerify") && !WorkAcceptance.passed())return;
        try {
            var server=event.getServer();var player=server.getPlayerList().getPlayers().getFirst();
            var citizen=(com.minecolonies.core.entity.citizen.EntityCitizen)server.overworld().getEntity(GameplayPreview.citizenIdentity());
            if(stage==0 && citizen==null)return;
            var colony=stage==0?citizen.getCitizenData().getColony():com.minecolonies.api.colony.IColonyManager.getInstance().getColonyByWorld(colonyId,server.overworld());
            var hut=stage==0?citizen.getCitizenData().getWorkBuilding():colony.getServerBuildingManager().getBuilding(target);
            if(stage==0) {
                Files.deleteIfExists(RESULT);colonyId=colony.getID();target=hut.getID();priority=hut.getPickUpPriority();
                originalName=hut.getCustomName();originalPosition=player.position();
                hut.setCustomBuildingName("远程验收工坊");
                player.teleportTo(target.getX()+640,150,target.getZ()+640);
                var guest=net.neoforged.neoforge.common.util.FakePlayerFactory.get(server.overworld(),
                        new com.mojang.authlib.GameProfile(java.util.UUID.randomUUID(),"TerminalGuest"));
                var denied=new TerminalRequest(-1,BlockPos.ZERO).snapshot(guest);
                if(!denied.getList("buildings",10).isEmpty() || new TerminalRequest(colonyId,target).snapshot(guest)!=null)
                    throw new AssertionError("Unauthorized terminal reveals buildings");
                stage=1;ticks=0;
            } else if(stage==1 && ++ticks>100) {
                if(player.distanceToSqr(Vec3.atCenterOf(target))<640*640)throw new AssertionError("Player not remote");
                Minecraft.getInstance().execute(()->{Minecraft.getInstance().setScreen(null);RemoteTerminalClient.KEY.setDown(false);net.minecraft.client.KeyMapping.click(RemoteTerminalClient.KEY.getKey());});
                stage=2;ticks=0;
            } else if(stage==4 && hut.getPickUpPriority()!=priority) {
                hut.alterPickUpPriority(priority-hut.getPickUpPriority());hut.setCustomBuildingName(originalName);
                player.teleportTo(originalPosition.x,originalPosition.y,originalPosition.z);
                Files.writeString(RESULT,"PASS: N key requests authorized remote building list; real coordinate click opens authoritative native building window 640+ blocks away; native priority packet changes actual hut; unauthorized list/open rejected");
                stage=6;
            }
            if(stage>1 && ++ticks>1600)throw new AssertionError("Terminal timeout stage="+stage);
        }catch(Throwable failure){fail(failure);}
    }
    @SubscribeEvent public static void client(ClientTickEvent.Post event) {
        if(!Boolean.getBoolean("colony_mca.terminalVerify"))return;
        var mc=Minecraft.getInstance();
        mc.options.pauseOnLostFocus=false; // Test JVM only; keep integrated server ticking during background automation.
        try {
            if(stage==2 && mc.screen instanceof RemoteTerminalScreen screen) {
                for(var child:java.util.List.copyOf(screen.children()))if(child instanceof net.minecraft.client.gui.components.EditBox search && !search.getValue().equals("远程验收工坊"))search.setValue("远程验收工坊");
                for(var child:screen.children())if(child instanceof Button button && button.getMessage().getString().contains("远程验收工坊")) {
                    stage=3;screen.mouseClicked(button.getX()+5,button.getY()+5,0);break;
                }
            } else if(stage==3 && mc.screen instanceof com.ldtteam.blockui.BOScreen screen) {
                if(!(screen.getWindow() instanceof com.minecolonies.core.client.gui.AbstractBuildingWindow<?>))return;
                var field=com.minecolonies.core.client.gui.AbstractBuildingWindow.class.getDeclaredField("buildingView");field.setAccessible(true);
                var view=(com.minecolonies.api.colony.buildings.views.IBuildingView)field.get(screen.getWindow());
                if(!view.getID().equals(target) || !view.getCustomName().equals("远程验收工坊"))throw new AssertionError("Native window has stale or wrong building data");
                new com.minecolonies.core.network.messages.server.colony.building.ChangeDeliveryPriorityMessage(view,priority<5).sendToServer();stage=4;
            }
        }catch(Throwable failure){fail(failure);}
    }
    private static void fail(Throwable failure){stage=6;ColonyMca.LOGGER.error("Remote terminal acceptance failed",failure);try{Files.writeString(RESULT,"FAIL: "+failure);}catch(Exception ignored){}}
}

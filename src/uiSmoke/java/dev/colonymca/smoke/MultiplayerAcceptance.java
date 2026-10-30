package dev.colonymca.smoke;

import dev.colonymca.ColonyMca;
import dev.colonymca.bridge.*;
import com.minecolonies.core.entity.citizen.EntityCitizen;
import net.conczin.mca.network.Network;
import net.conczin.mca.network.c2s.InteractionVillagerMessage;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.inventory.MerchantMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.portal.DimensionTransition;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** Two actual NeoForge client connections. The only listening socket is bound to loopback. */
@EventBusSubscriber(modid=ColonyMca.ID,value=Dist.CLIENT)
public final class MultiplayerAcceptance {
    private static int stage,ticks,sequence,lastPeerCommand,peerWaiting;
    private static UUID citizenId;
    private static UUID pendingPanel;
    private static int panelTicks;
    private static boolean peerPrepared;
    private static final List<String> checks=new ArrayList<>();
    private static Path file(String name){return Path.of(System.getProperty("colony_mca.testRoot"),name);}
    private static boolean role(String name){return System.getProperty("colony_mca.peerRole","").equals(name);}

    @SubscribeEvent public static void clientTick(ClientTickEvent.Post event) {
        if(!role("client"))return;
        var mc=Minecraft.getInstance();
        if(mc.player==null || mc.level==null) {
            if(++peerWaiting%100==0)ColonyMca.LOGGER.info("Peer connection screen: {}",mc.screen==null?"none":mc.screen.getClass().getName());
            return;
        }
        if(pendingPanel!=null) {
            try {
                net.minecraft.world.entity.Entity subject=null;
                for(var entity:mc.level.entitiesForRendering())if(entity.getUUID().equals(pendingPanel)){subject=entity;break;}
                if(subject!=null && mc.screen instanceof com.ldtteam.blockui.BOScreen screen
                        && screen.getWindow() instanceof com.minecolonies.core.client.gui.citizen.MainWindowCitizen
                        && dev.colonymca.client.McaSidebarModule.current()!=null) {
                    var field=com.minecolonies.core.client.gui.citizen.AbstractWindowCitizen.class.getDeclaredField("citizen");field.setAccessible(true);
                    var view=(com.minecolonies.api.colony.ICitizenDataView)field.get(screen.getWindow());
                    var profession=screen.getWindow().findPaneOfTypeByID("colony_mca_value_profession",com.ldtteam.blockui.controls.Text.class);
                    var skill=screen.getWindow().findPaneOfTypeByID("mana",com.ldtteam.blockui.controls.Text.class);
                    if(view.getEntityId()==subject.getId() && view.getName().equals("Taylor 真实建筑工") && view.getJob().toLowerCase(java.util.Locale.ROOT).contains("builder")
                            && profession!=null && !profession.getText().getString().isBlank() && skill!=null && !skill.getText().getString().isBlank()) {
                        Files.writeString(file("peer-panel.txt"),"PASS: real peer unified colony panel contains actual server name, builder job, skill values and MCA sidebar");pendingPanel=null;
                    }
                }
                if(pendingPanel!=null && subject!=null && panelTicks++%100==0)
                    net.neoforged.neoforge.network.PacketDistributor.sendToServer(new dev.colonymca.network.CitizenPanelRequest(subject.getId()));
            }catch(Exception error){ColonyMca.LOGGER.error("Peer panel validation failed",error);}
        }
        if(!Files.exists(file("peer-command.txt")))return;
        try {
            var parts=Files.readString(file("peer-command.txt")).trim().split("\\|");
            if(parts.length!=3)return;int command=Integer.parseInt(parts[0]);if(command<=lastPeerCommand)return;
            if(parts[1].equals("quit"))mc.stop();
            else if(parts[1].equals("panel")){pendingPanel=UUID.fromString(parts[2]);panelTicks=0;}
            else Network.sendToServer(new InteractionVillagerMessage(parts[1],UUID.fromString(parts[2])));
            lastPeerCommand=command;Files.writeString(file("peer-ack.txt"),Integer.toString(command));
        }catch(Exception error){ColonyMca.LOGGER.error("Peer client command failed",error);}
    }

    @SubscribeEvent public static void serverTick(ServerTickEvent.Post event) {
        if(!role("host") || !GameplayPreview.ready() || stage>=12)return;
        try {
            var server=event.getServer();var world=server.overworld();
            var owner=server.getPlayerList().getPlayers().stream().filter(p->!p.getGameProfile().getName().equals("AcceptancePeer")).findFirst().orElseThrow();
            var peer=server.getPlayerList().getPlayerByName("AcceptancePeer");
            var citizen=(EntityCitizen)world.getEntity(GameplayPreview.citizenIdentity());
            var data=citizen.getCitizenData();var actor=((CitizenMcaActor)citizen).colonyMca$actor();var permissions=data.getColony().getPermissions();
            var control=((CitizenMcaData)data).colonyMca$getControl();
            if(stage==0) {
                Files.createDirectories(file(".").normalize());Files.deleteIfExists(file("peer-command.txt"));Files.deleteIfExists(file("peer-ack.txt"));Files.deleteIfExists(file("peer-panel.txt"));Files.deleteIfExists(file("multiplayer.txt"));
                citizenId=citizen.getUUID();data.setPaused(true);citizen.getNavigation().stop();
                server.setUsesAuthentication(false);server.getConnection().startTcpServerListener(java.net.InetAddress.getByAddress(new byte[]{127,0,0,1}),25579);
                var published=server.getClass().getDeclaredField("publishedPort");published.setAccessible(true);published.setInt(server,25579);
                ColonyMca.LOGGER.info("Acceptance host listening on explicit IPv4 loopback 127.0.0.1:25579");
                Files.writeString(file("peer-ready.txt"),"READY: loopback-only NeoForge server 127.0.0.1:25579");next(1);
            } else if(stage==1 && peer!=null) {
                if(!peerPrepared) {
                permissions.addPlayer(peer.getGameProfile(),permissions.getRankOfficer());
                permissions.setPlayerRank(peer.getUUID(),permissions.getRankOfficer(),world);
                owner.teleportTo(citizen.getX(),citizen.getY(),citizen.getZ()-2);peer.teleportTo(citizen.getX(),citizen.getY(),citizen.getZ()+2);
                peer.setGameMode(net.minecraft.world.level.GameType.CREATIVE);
                check(server.getPlayerList().getPlayerCount()>=2,"Two real NeoForge clients connected with separate player identities");
                peerPrepared=true;peerCommand("panel");
                }
                if(Files.exists(file("peer-panel.txt")) && Files.readString(file("peer-panel.txt")).startsWith("PASS:")) {
                    check(true,"Real peer unified panel displays authoritative citizen name, builder job, native skill values and MCA sidebar");
                    ownerCommand("trade");next(2);
                }
            } else if(stage==2 && owner.containerMenu instanceof MerchantMenu && actor.getTradingPlayer()==owner) {
                peerCommand("trade");next(3);
            } else if(stage==3 && acknowledged() && ticks>25) {
                check(owner.containerMenu instanceof MerchantMenu && actor.getTradingPlayer()==owner && !(peer.containerMenu instanceof MerchantMenu),
                        "Second real client cannot steal an active merchant session or close its owner's menu");
                ownerCommand("trade");next(4);
            } else if(stage==4 && ticks>25) {
                check(owner.containerMenu instanceof MerchantMenu && actor.getTradingPlayer()==owner,"Repeated trade request from current customer does not invalidate merchant menu");
                Minecraft.getInstance().execute(()->Minecraft.getInstance().player.closeContainer());next(5);
            } else if(stage==5 && actor.getTradingPlayer()==null && !(owner.containerMenu instanceof MerchantMenu)) {
                peerCommand("trade");next(6);
            } else if(stage==6 && peer.containerMenu instanceof MerchantMenu && actor.getTradingPlayer()==peer) {
                check(true,"Second real client can trade after first customer closes its native menu");
                permissions.setPlayerRank(peer.getUUID(),permissions.getRankNeutral(),world);next(7);
            } else if(stage==7 && ticks>30) {
                check(!(peer.containerMenu instanceof MerchantMenu) && actor.getTradingPlayer()==null,"Revoking colony access closes the real peer's active trade session");
                peer.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND,new ItemStack(Items.DIAMOND));peerCommand("gift");next(8);
            } else if(stage==8 && acknowledged() && ticks>25) {
                check(peer.getMainHandItem().getCount()==1,"Unauthorized real peer gift packet cannot consume held item");
                permissions.setPlayerRank(peer.getUUID(),permissions.getRankOfficer(),world);
                data.setPaused(false);peer.teleportTo(citizen.getX(),citizen.getY(),citizen.getZ()+2);peerCommand("follow");next(9);
            } else if(stage==9 && control.getString("movement").equals("FOLLOW") && control.getUUID("leader").equals(peer.getUUID())) {
                check(data.isPaused(),"Real peer follow command pauses native work and records requesting player's UUID");
                var nether=server.getLevel(net.minecraft.world.level.Level.NETHER);nether.getChunk(0,0);
                for(var pos:BlockPos.betweenClosed(new BlockPos(6,139,6),new BlockPos(10,139,10)))nether.setBlockAndUpdate(pos,net.minecraft.world.level.block.Blocks.SMOOTH_STONE.defaultBlockState());
                peer.changeDimension(new DimensionTransition(nether,new Vec3(8.5,140,8.5),Vec3.ZERO,0,0,DimensionTransition.DO_NOTHING));next(10);
            } else if(stage==10 && peer.serverLevel()!=world && !control.contains("movement")) {
                check(!data.isPaused() && data.getJob()!=null,"Peer changing dimension releases follow and restores original native work state");
                peer.changeDimension(new DimensionTransition(world,citizen.position().add(0,0,2),Vec3.ZERO,0,0,DimensionTransition.DO_NOTHING));
                peerCommand("quit");next(11);
            } else if(stage==11 && ticks>30) {
                Files.writeString(file("multiplayer.txt"),"PASS: "+checks.size()+" actual two-client network checks\n"+String.join("\n",checks));
                stage=12;ColonyMca.LOGGER.info("Real two-client multiplayer acceptance passed");
            }
            if(++ticks>(stage==1?6000:1000))throw new AssertionError("Two-client test timed out stage="+stage+" peer="+(peer!=null));
        } catch(Throwable error) {
            stage=12;ColonyMca.LOGGER.error("Real multiplayer acceptance failed",error);
            try{Files.writeString(file("multiplayer.txt"),"FAIL: "+error+"\n"+String.join("\n",checks));}catch(Exception ignored){}
        }
    }
    private static void next(int value){stage=value;ticks=0;}
    private static void ownerCommand(String action){Minecraft.getInstance().execute(()->Network.sendToServer(new InteractionVillagerMessage(action,citizenId)));}
    private static void peerCommand(String action)throws Exception{sequence++;Files.writeString(file("peer-command.txt"),sequence+"|"+action+"|"+citizenId);}
    private static boolean acknowledged()throws Exception{return Files.exists(file("peer-ack.txt")) && Files.readString(file("peer-ack.txt")).trim().equals(Integer.toString(sequence));}
    private static void check(boolean value,String message)throws Exception{if(!value)throw new AssertionError(message);checks.add(message);Files.writeString(file("multiplayer.txt"),"RUNNING\n"+String.join("\n",checks));}
}

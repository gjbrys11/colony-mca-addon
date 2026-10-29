package dev.colonymca.smoke;

import com.minecolonies.api.IMinecoloniesAPI;
import com.minecolonies.core.entity.citizen.EntityCitizen;
import dev.colonymca.ColonyMca;
import dev.colonymca.bridge.*;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.ChunkPos;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/** Actually unloads a server chunk and returns; no entity cloning or synthetic NBT replacement. */
@EventBusSubscriber(modid=ColonyMca.ID,value=Dist.CLIENT)
public final class ChunkAcceptance {
    private static int stage,ticks;
    private static EntityCitizen original;
    private static UUID uuid;
    private static String skin;
    private static BlockPos origin;
    private static com.minecolonies.api.colony.IColony colony;
    private static boolean priorForce;
    private static Set<Long> priorTickets=Set.of();
    private static final Path RESULT=Path.of("../build/gameplay-preview/chunk.txt");
    @SubscribeEvent public static void tick(ServerTickEvent.Post event) {
        if(!Boolean.getBoolean("colony_mca.chunkVerify") || !GameplayPreview.ready() || stage>=3)return;
        try {
            var world=event.getServer().overworld();var player=event.getServer().getPlayerList().getPlayers().getFirst();
            if(stage==0) {
                Files.deleteIfExists(RESULT);
                original=(EntityCitizen)world.getEntity(GameplayPreview.citizenIdentity());uuid=original.getUUID();origin=original.blockPosition();colony=original.getCitizenData().getColony();
                var actor=((CitizenMcaActor)original).colonyMca$actor();skin=actor.getSkin();
                actor.getVillagerBrain().getMemoriesForPlayer(player).setHearts(167);
                MovementBridge.command(original,player,net.conczin.mca.entity.ai.MoveState.STAY);
                ((CitizenMcaActor)original).colonyMca$flush();
                priorForce=IMinecoloniesAPI.getInstance().getConfig().getServer().forceLoadColony.get();
                IMinecoloniesAPI.getInstance().getConfig().getServer().forceLoadColony.set(false);
                priorTickets=new HashSet<>(colony.getTicketedChunks());
                for(long packed:priorTickets) {
                    var pos=new ChunkPos(packed);
                    world.getChunkSource().removeRegionTicket(com.minecolonies.api.util.constant.ColonyConstants.KEEP_LOADED_TYPE,pos,2,pos,true);
                }
                colony.getTicketedChunks().clear();
                world.getChunk(64,64);
                for(var pos:BlockPos.betweenClosed(new BlockPos(1022,79,1022),new BlockPos(1026,79,1026)))world.setBlockAndUpdate(pos,net.minecraft.world.level.block.Blocks.SMOOTH_STONE.defaultBlockState());
                player.teleportTo(1024.5,80,1024.5);
                stage=1;Files.writeString(RESULT,"RUNNING: player moved away; waiting for actual server chunk unload");
            } else if(stage==1) {
                if(world.getEntity(uuid)==null && !world.getChunkSource().hasChunk(origin.getX()>>4,origin.getZ()>>4)) {
                    stage=2;ticks=0;player.teleportTo(origin.getX()+.5,origin.getY(),origin.getZ()-2);
                    world.getChunk(origin);
                    Files.writeString(RESULT,"RUNNING: actual server chunk unloaded; returned to reload");
                }
            } else if(stage==2 && world.getEntity(uuid) instanceof EntityCitizen loaded && loaded.getCitizenData()!=null) {
                if(loaded==original || !original.isRemoved())throw new AssertionError("Old server entity was retained rather than unloaded");
                var actor=((CitizenMcaActor)loaded).colonyMca$actor();var data=loaded.getCitizenData();
                var control=((CitizenMcaData)data).colonyMca$getControl();
                if(!actor.getSkin().equals(skin) || actor.getVillagerBrain().getMemoriesForPlayer(player).getHearts()!=167
                        || !data.isPaused() || !control.getString("movement").equals("STAY")
                        || !data.getJob().getJobRegistryEntry().getKey().equals(com.minecolonies.api.colony.jobs.ModJobs.BUILDER_ID))
                    throw new AssertionError("Chunk reload lost profile, hearts, stay, pause, or colony job");
                MovementBridge.command(loaded,player,net.conczin.mca.entity.ai.MoveState.MOVE);
                if(data.isPaused())throw new AssertionError("Reloaded citizen did not restore prior work pause state");
                restore(world);
                Files.writeString(RESULT,"PASS: real server chunk unloaded and loaded a new citizen entity with same UUID, skin, hearts, native builder job and stored stay/pause state; free movement restored work state");
                stage=3;ColonyMca.LOGGER.info("Actual chunk unload/reload acceptance passed");
            }
            if(++ticks>1200)throw new AssertionError("Chunk lifecycle timed out in stage "+stage+" removed="+(original!=null&&original.isRemoved()));
        }catch(Throwable error) {
            stage=3;ColonyMca.LOGGER.error("Actual chunk reload acceptance failed",error);
            try{restore(event.getServer().overworld());Files.writeString(RESULT,"FAIL: "+error);}catch(Exception ignored){}
        }
    }
    private static void restore(net.minecraft.server.level.ServerLevel world) {
        IMinecoloniesAPI.getInstance().getConfig().getServer().forceLoadColony.set(priorForce);
        for(long packed:priorTickets){var pos=new ChunkPos(packed);world.getChunkSource().addRegionTicket(com.minecolonies.api.util.constant.ColonyConstants.KEEP_LOADED_TYPE,pos,2,pos,true);}
        if(colony!=null)colony.getTicketedChunks().addAll(priorTickets);
    }
}

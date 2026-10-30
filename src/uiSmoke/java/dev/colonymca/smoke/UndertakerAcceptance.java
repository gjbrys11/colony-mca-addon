package dev.colonymca.smoke;

import dev.colonymca.ColonyMca;
import dev.colonymca.bridge.CitizenMcaActor;
import com.minecolonies.core.entity.citizen.EntityCitizen;
import com.minecolonies.core.colony.buildings.modules.GraveyardManagementModule;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
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

/** Lets the existing undertaker AI navigate, collect items, dig and bury without calling its actions. */
@EventBusSubscriber(modid=ColonyMca.ID,value=Dist.CLIENT)
public final class UndertakerAcceptance {
    private static boolean done,seeded;
    private static int ticks;
    private static UUID identity;
    private static String name,skin;
    private static EntityCitizen undertaker;
    private static com.minecolonies.api.colony.buildings.IBuilding hut;
    private static BlockPos originalGrave;
    private static final BlockPos PLOT=new BlockPos(136,80,137);
    private static final List<BlockPos> reserved=new ArrayList<>();
    private static final Path RESULT=Path.of("../build/gameplay-preview/undertaker.txt");
    @SubscribeEvent public static void tick(ServerTickEvent.Post event) {
        if(!Boolean.getBoolean("colony_mca.funeralVerify") || !GameplayPreview.ready() || done)return;
        try {
            var world=event.getServer().overworld();var player=event.getServer().getPlayerList().getPlayers().getFirst();
            var worker=(EntityCitizen)world.getEntity(GameplayPreview.citizenIdentity());var colony=worker.getCitizenData().getColony();
            if(undertaker==null) {
                Files.deleteIfExists(RESULT);
                hut=GameplayPreview.building(colony,new BlockPos(118,80,138),com.minecolonies.api.blocks.ModBlocks.blockHutGraveyard);
                hut.setBuildingLevel(1);hut.setBlueprintPath("mystic/graveyard1.blueprint");
                var module=hut.getFirstModuleOccurance(GraveyardManagementModule.class);
                module.getGravePositions().clear();module.getGravePositions().add(new com.minecolonies.api.util.Tuple<>(PLOT,Direction.NORTH));
                world.setBlockAndUpdate(PLOT,net.minecraft.world.level.block.Blocks.AIR.defaultBlockState());
                var data=colony.getCitizenManager().createAndRegisterCivilianData();data.setName("Acceptance full-cycle undertaker");data.setIsChild(false);data.setPaused(false);data.setSaturation(20);data.getCitizenSkillHandler().init(20);
                if(!hut.getFirstModuleOccurance(com.minecolonies.core.colony.buildings.modules.WorkerBuildingModule.class).assignCitizen(data))throw new AssertionError("Native undertaker job assignment failed");
                colony.getCitizenManager().spawnOrCreateCitizen(data,world,new BlockPos(119,80,138));undertaker=(EntityCitizen)data.getEntity().orElseThrow();
                net.neoforged.neoforge.items.ItemHandlerHelper.insertItemStacked(data.getInventory(),new ItemStack(Items.WOODEN_SHOVEL),false);
                net.neoforged.neoforge.items.ItemHandlerHelper.insertItemStacked(data.getInventory(),new ItemStack(Items.BREAD,32),false);
                var victim=ColonyAcceptance.adult(colony,"Acceptance full-cycle victim");victim.setPos(128.5,80,120.5);
                identity=victim.getUUID();name=victim.getCitizenData().getName();skin=((CitizenMcaActor)victim).colonyMca$actor().getSkin();
                net.neoforged.neoforge.items.ItemHandlerHelper.insertItemStacked(victim.getCitizenData().getInventory(),new ItemStack(Items.DIAMOND,6),false);
                victim.setHealth(.01f);victim.invulnerableTime=0;victim.hurt(world.damageSources().playerAttack(player),1);
                if(victim.isAlive())throw new AssertionError("Real citizen death failed");
                for(var pos:List.copyOf(colony.getGraveManager().getGraves().keySet())) {
                    if(world.getBlockEntity(pos) instanceof com.minecolonies.api.tileentities.AbstractTileEntityGrave grave && grave.getGraveData()!=null
                            && grave.getGraveData().getCitizenDataNBT()!=null && grave.getGraveData().getCitizenDataNBT().hasUUID("colony_mca_identity")
                            && grave.getGraveData().getCitizenDataNBT().getUUID("colony_mca_identity").equals(identity)) originalGrave=pos;
                    else if(colony.getGraveManager().reserveGrave(pos))reserved.add(pos);
                }
                if(originalGrave==null)throw new AssertionError("Original native grave not found");
                Files.writeString(RESULT,"RUNNING: actual native undertaker AI collecting, digging and burying");
            }
            ticks++;undertaker.getCitizenData().setSaturation(20);
            var ai=undertaker.getCitizenData().getJob().getWorkerAI();
            if(ai!=null && !seeded) {
                // Controlled RNG makes the native resurrection attempt fail, so the burial branch is covered.
                var field=ai.getClass().getDeclaredField("random");field.setAccessible(true);((java.util.Random)field.get(ai)).setSeed(1);seeded=true;
            }
            var module=hut.getFirstModuleOccurance(GraveyardManagementModule.class);
            if(module.hasRestingCitizen(java.util.Set.of(name)) && world.getBlockEntity(PLOT) instanceof com.minecolonies.api.tileentities.AbstractTileEntityNamedGrave) {
                if(world.getBlockEntity(originalGrave) instanceof com.minecolonies.api.tileentities.AbstractTileEntityGrave
                        || count(undertaker.getCitizenData().getInventory())+count(hut.getTileEntity().getInventory())!=6)throw new AssertionError("Native digging or collected-item conservation failed");
                var staff=new ItemStack(net.conczin.mca.registry.ItemsMCA.STAFF_OF_LIFE);player.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND,staff);
                player.teleportTo(PLOT.getX(),PLOT.getY(),PLOT.getZ()-2);player.setGameMode(net.minecraft.world.level.GameType.SURVIVAL);
                net.neoforged.neoforge.common.NeoForge.EVENT_BUS.post(new net.neoforged.neoforge.event.entity.player.PlayerInteractEvent.RightClickBlock(player,net.minecraft.world.InteractionHand.MAIN_HAND,PLOT,
                        new net.minecraft.world.phys.BlockHitResult(net.minecraft.world.phys.Vec3.atCenterOf(PLOT),Direction.UP,PLOT,false)));
                var revived=(EntityCitizen)world.getEntity(identity);
                if(revived==null || !revived.isAlive() || !((CitizenMcaActor)revived).colonyMca$actor().getSkin().equals(skin)
                        || count(revived.getCitizenData().getInventory())!=0 || staff.getDamageValue()!=1
                        || count(undertaker.getCitizenData().getInventory())+count(hut.getTileEntity().getInventory())!=6)throw new AssertionError("Full-cycle named grave revival failed or cloned items");
                for(var pos:reserved)colony.getGraveManager().unReserveGrave(pos);
                player.setGameMode(net.minecraft.world.level.GameType.CREATIVE);
                Files.writeString(RESULT,"PASS: existing undertaker AI navigated to real grave, collected 6 diamonds, dug original grave and buried citizen at registered plot; life staff revived same UUID/skin without duplicating collected items");
                done=true;ColonyMca.LOGGER.info("Full native undertaker work cycle acceptance passed");
            } else if(ticks%200==0) Files.writeString(RESULT,"RUNNING ticks="+ticks+" workerState="+undertaker.getCitizenAI().getState()+" jobState="+(ai==null?"pending":ai.getState())+
                    " pos="+undertaker.blockPosition()+" collected="+(count(undertaker.getCitizenData().getInventory())+count(hut.getTileEntity().getInventory())));
            if(ticks>6000)throw new AssertionError("Native undertaker cycle exceeded 5 minutes: "+Files.readString(RESULT));
        }catch(Throwable error){done=true;ColonyMca.LOGGER.error("Full undertaker cycle failed",error);try{if(hut!=null)for(var pos:reserved)hut.getColony().getGraveManager().unReserveGrave(pos);Files.writeString(RESULT,"FAIL: "+error);}catch(Exception ignored){}}
    }
    private static int count(net.neoforged.neoforge.items.IItemHandler inventory){int n=0;for(int i=0;i<inventory.getSlots();i++)if(inventory.getStackInSlot(i).is(Items.DIAMOND))n+=inventory.getStackInSlot(i).getCount();return n;}
}

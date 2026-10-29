package dev.colonymca.smoke;

import dev.colonymca.ColonyMca;
import dev.colonymca.bridge.CitizenMcaActor;
import com.minecolonies.api.colony.ICitizenData;
import com.minecolonies.core.colony.buildings.modules.*;
import com.minecolonies.core.colony.jobs.JobDeliveryman;
import com.minecolonies.core.entity.citizen.EntityCitizen;
import net.minecraft.core.BlockPos;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.items.IItemHandler;
import java.nio.file.Files;
import java.nio.file.Path;

/** Real warehouse queue and native courier AI, including MCA gift while a task is active. */
@EventBusSubscriber(modid=ColonyMca.ID,value=Dist.CLIENT)
public final class LogisticsAcceptance {
    private static int ticks;
    private static boolean done,gifted;
    private static ICitizenData courier;
    private static IItemHandler source,target;
    private static final Path RESULT=Path.of("../build/gameplay-preview/logistics.txt");
    @SubscribeEvent public static void tick(ServerTickEvent.Post event) {
        if(!Boolean.getBoolean("colony_mca.logisticsVerify") || !GameplayPreview.ready() || done) return;
        try {
            var world=event.getServer().overworld();
            var worker=(EntityCitizen)world.getEntity(GameplayPreview.citizenIdentity());
            var colony=worker.getCitizenData().getColony();
            var player=event.getServer().getPlayerList().getPlayers().getFirst();
            if(courier==null) {
                Files.deleteIfExists(RESULT);
                var warehouse=GameplayPreview.building(colony,new BlockPos(119,80,119),com.minecolonies.api.blocks.ModBlocks.blockHutWareHouse);
                var hut=GameplayPreview.building(colony,new BlockPos(119,80,136),com.minecolonies.api.blocks.ModBlocks.blockHutDeliveryman);
                warehouse.setBuildingLevel(1); hut.setBuildingLevel(1);
                warehouse.setBlueprintPath("craftsmanship/storage/warehouse1.blueprint"); hut.setBlueprintPath("craftsmanship/storage/deliveryman1.blueprint");
                courier=colony.getCitizenManager().createAndRegisterCivilianData(); courier.setName("Acceptance courier"); courier.setIsChild(false); courier.setPaused(false); courier.setSaturation(20);
                if(!hut.getFirstModuleOccurance(WorkerBuildingModule.class).assignCitizen(courier)
                        || !warehouse.getFirstModuleOccurance(CourierAssignmentModule.class).assignCitizen(courier)) throw new AssertionError("Native courier assignment failed");
                colony.getCitizenManager().spawnOrCreateCitizen(courier,world,new BlockPos(120,80,133));
                var destination=worker.getCitizenData().getHomeBuilding();
                source=warehouse.getTileEntity().getInventory(); target=destination.getTileEntity().getInventory();
                net.neoforged.neoforge.items.ItemHandlerHelper.insertItemStacked(source,new ItemStack(Items.EMERALD,5),false);
                if(count(source,Items.EMERALD)!=5 || count(target,Items.EMERALD)!=0)throw new AssertionError("Use clean native inventories for courier fixture");
                var delivery=new com.minecolonies.api.colony.requestsystem.requestable.deliveryman.Delivery(
                        new com.minecolonies.core.colony.requestsystem.locations.StaticLocation(warehouse.getPosition(),world.dimension()),
                        new com.minecolonies.core.colony.requestsystem.locations.StaticLocation(destination.getPosition(),world.dimension()),new ItemStack(Items.EMERALD,5),100);
                var token=destination.createRequest(delivery,false);
                Files.writeString(RESULT,"RUNNING: native delivery request="+token);
            }
            ticks++; courier.setSaturation(20);
            var entity=(EntityCitizen)courier.getEntity().orElseThrow(); var job=(JobDeliveryman)courier.getJob();
            if(!gifted && job.getCurrentTask()!=null) {
                player.teleportTo(entity.getX(),entity.getY(),entity.getZ()-2);
                player.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND,new ItemStack(Items.DIAMOND));
                new net.conczin.mca.network.c2s.InteractionVillagerMessage("gift",entity.getUUID()).handleServer(player);
                if(!player.getMainHandItem().isEmpty() || count(courier.getInventory(),Items.DIAMOND)!=1)throw new AssertionError("Native gift failed during active delivery");
                gifted=true;
            }
            if(count(target,Items.EMERALD)==5) {
                if(count(source,Items.EMERALD)!=0 || count(courier.getInventory(),Items.EMERALD)!=0 || !gifted
                        || ((CitizenMcaActor)entity).colonyMca$actor().getSkin().isBlank())throw new AssertionError("Delivery/gift conservation or model failed");
                Files.writeString(RESULT,"PASS: real warehouse request and native courier AI moved all 5 emeralds to destination; source and courier hold zero; MCA gift succeeded during active task; native courier job and MCA appearance retained");
                ColonyMca.LOGGER.info("Real courier delivery acceptance passed"); done=true;
            } else if(ticks%200==0) Files.writeString(RESULT,"RUNNING ticks="+ticks+" source="+count(source,Items.EMERALD)+" target="+count(target,Items.EMERALD)+
                    " courier="+count(courier.getInventory(),Items.EMERALD)+" state="+job.getWorkerAI().getState()+" pos="+entity.blockPosition()+" task="+job.getCurrentTask()+" gifted="+gifted);
            if(ticks>3600)throw new AssertionError("Courier did not complete within 3 minutes: "+Files.readString(RESULT));
        } catch(Throwable error) {
            done=true; ColonyMca.LOGGER.error("Real courier acceptance failed",error);
            try{Files.writeString(RESULT,"FAIL: "+error);}catch(Exception ignored){}
        }
    }
    private static int count(IItemHandler inventory,net.minecraft.world.item.Item item){int count=0;for(int i=0;i<inventory.getSlots();i++)if(inventory.getStackInSlot(i).is(item))count+=inventory.getStackInSlot(i).getCount();return count;}
}

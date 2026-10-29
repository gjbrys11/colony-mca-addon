package dev.colonymca.smoke;

import com.minecolonies.core.entity.citizen.EntityCitizen;
import com.minecolonies.core.colony.buildings.AbstractBuildingStructureBuilder;
import com.minecolonies.core.colony.workorders.WorkOrderDecoration;
import com.minecolonies.api.colony.workorders.WorkOrderType;
import dev.colonymca.ColonyMca;
import dev.colonymca.bridge.CitizenMcaActor;
import net.minecraft.core.BlockPos;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import java.nio.file.Files;
import java.nio.file.Path;

/** Native builder AI completion with supplied materials; no direct block placement by this test. */
@EventBusSubscriber(modid=ColonyMca.ID,value=Dist.CLIENT)
public final class WorkAcceptance {
    private static int ticks, orderId;
    private static boolean done, started;
    private static final Path RESULT=Path.of("../build/gameplay-preview/work.txt");
    private static final BlockPos SITE=new BlockPos(137,80,119);
    @SubscribeEvent public static void tick(ServerTickEvent.Post event) {
        if(!Boolean.getBoolean("colony_mca.workVerify") || !GameplayPreview.ready() || done) return;
        try {
            var world=event.getServer().overworld();
            var citizen=(EntityCitizen)world.getEntity(GameplayPreview.citizenIdentity());
            var data=citizen.getCitizenData(); var colony=data.getColony();
            var building=(AbstractBuildingStructureBuilder)data.getWorkBuilding();
            if(!started) {
                started=true; Files.deleteIfExists(RESULT);
                for(var old:java.util.List.copyOf(colony.getWorkManager().getWorkOrders().values()))
                    colony.getWorkManager().removeWorkOrder(old.getID());
                building.setWorkOrder(null);
                var order=WorkOrderDecoration.create(WorkOrderType.BUILD,"Medieval Oak","decorations/misc/pillory.blueprint",
                        "Acceptance pillory",SITE,com.ldtteam.structurize.api.RotationMirror.NONE,0);
                colony.getWorkManager().addWorkOrder(order,false); order.setClaimedBy(building.getID()); building.setWorkOrder(order);
                orderId=order.getID(); data.setPaused(false); data.setSaturation(20);
                net.neoforged.neoforge.items.ItemHandlerHelper.insertItemStacked(data.getInventory(),new ItemStack(Items.BREAD,64),false);
                for(var item:java.util.List.of(Items.WOODEN_AXE,Items.WOODEN_PICKAXE,Items.WOODEN_SHOVEL,Items.SHEARS))
                    net.neoforged.neoforge.items.ItemHandlerHelper.insertItemStacked(data.getInventory(),new ItemStack(item),false);
                Files.writeString(RESULT,"RUNNING: native builder order="+orderId);
            }
            if(++ticks%20==0) {
                data.setSaturation(20);
                for(var request:java.util.List.copyOf(building.getOpenRequests(data.getId()))) {
                    if(request.getRequest() instanceof com.minecolonies.api.colony.requestsystem.requestable.IDeliverable deliverable
                            && !request.getDisplayStacks().isEmpty()) {
                        var stack=request.getDisplayStacks().getFirst().copy();
                        stack.setCount(Math.min(stack.getMaxStackSize(),deliverable.getCount()));
                        var remainder=net.neoforged.neoforge.items.ItemHandlerHelper.insertItemStacked(data.getInventory(),stack,false);
                        if(remainder.isEmpty()) colony.getRequestManager().updateRequestState(request.getId(),
                                com.minecolonies.api.colony.requestsystem.request.RequestState.RESOLVED);
                    }
                }
                int placed=0;
                for(var pos:BlockPos.betweenClosed(SITE.offset(-5,0,-5),SITE.offset(5,5,5)))
                    if(!world.isEmptyBlock(pos) && !(world.getBlockEntity(pos) instanceof com.minecolonies.api.tileentities.AbstractTileEntityColonyBuilding)) placed++;
                boolean exists=colony.getWorkManager().getWorkOrders().containsKey(orderId);
                if(!exists && placed>0) {
                    if(!(data.getJob() instanceof com.minecolonies.core.colony.jobs.JobBuilder)
                            || ((CitizenMcaActor)citizen).colonyMca$actor().getSkin().isBlank()) throw new AssertionError("Builder/model lost");
                    Files.writeString(RESULT,"PASS: native builder AI completed official Medieval Oak pillory blueprint; "+placed+
                            " real non-air blocks at site; original builder job and MCA appearance retained; requested materials supplied by fixture");
                    ColonyMca.LOGGER.info("Native construction acceptance passed: {} blocks",placed); done=true;
                } else if(ticks%200==0) {
                    Files.writeString(RESULT,"RUNNING ticks="+ticks+" blocks="+placed+" order="+exists+" state="+
                            data.getJob().getWorkerAI().getState()+" citizenState="+citizen.getCitizenAI().getState()+" pos="+citizen.blockPosition()+
                            " requests="+building.getOpenRequests(data.getId()).size());
                }
                if(ticks>6000) throw new AssertionError("Builder did not complete within 5 minutes: "+Files.readString(RESULT));
            }
        } catch(Throwable error) {
            done=true; ColonyMca.LOGGER.error("Native construction acceptance failed",error);
            try{Files.writeString(RESULT,"FAIL: "+error);}catch(Exception ignored){}
        }
    }
}

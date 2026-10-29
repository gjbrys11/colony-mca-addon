package dev.colonymca.smoke;

import com.minecolonies.core.entity.citizen.EntityCitizen;
import dev.colonymca.ColonyMca;
import dev.colonymca.bridge.*;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import java.nio.file.Files;
import java.nio.file.Path;

/** Native knight combat while an MCA stay command is active. */
@EventBusSubscriber(modid=ColonyMca.ID,value=Dist.CLIENT)
public final class DefenseAcceptance {
    private static EntityCitizen guard;
    private static net.minecraft.world.entity.monster.Zombie enemy;
    private static int ticks, stage;
    private static boolean done, sawBowUse;
    private static final Path RESULT=Path.of("../build/gameplay-preview/defense.txt");
    @SubscribeEvent public static void tick(ServerTickEvent.Post event) {
        if(!Boolean.getBoolean("colony_mca.defenseVerify") || !GameplayPreview.ready() || done)return;
        try {
            var world=event.getServer().overworld(); var player=event.getServer().getPlayerList().getPlayers().getFirst();
            if(guard==null) {
                if(stage==0) Files.deleteIfExists(RESULT);
                event.getServer().setDifficulty(net.minecraft.world.Difficulty.NORMAL,true);
                var worker=(EntityCitizen)world.getEntity(GameplayPreview.citizenIdentity()); var colony=worker.getCitizenData().getColony();
                var tower=GameplayPreview.building(colony,new BlockPos(136,80,stage==0?136:121),com.minecolonies.api.blocks.ModBlocks.blockHutGuardTower);
                tower.setBuildingLevel(1); tower.setBlueprintPath("military/guardtower1.blueprint");
                var data=colony.getCitizenManager().createAndRegisterCivilianData(); data.setName(stage==0?"Acceptance knight":"Acceptance archer");data.setIsChild(false);data.setPaused(false);data.setSaturation(20);
                var module=tower.getModulesByType(com.minecolonies.core.colony.buildings.modules.GuardBuildingModule.class).stream()
                        .filter(m->m.getJobEntry().getKey().equals(stage==0?com.minecolonies.api.colony.jobs.ModJobs.KNIGHT_ID:com.minecolonies.api.colony.jobs.ModJobs.ARCHER_ID)).findFirst().orElseThrow();
                if(!module.assignCitizen(data))throw new AssertionError("Native knight assignment failed");
                colony.getCitizenManager().spawnOrCreateCitizen(data,world,new BlockPos(131,80,stage==0?134:120));
                guard=(EntityCitizen)data.getEntity().orElseThrow();
                net.neoforged.neoforge.items.ItemHandlerHelper.insertItemStacked(data.getInventory(),new ItemStack(stage==0?Items.WOODEN_SWORD:Items.BOW),false);
                if(stage==1)net.neoforged.neoforge.items.ItemHandlerHelper.insertItemStacked(data.getInventory(),new ItemStack(Items.ARROW,64),false);
                net.neoforged.neoforge.items.ItemHandlerHelper.insertItemStacked(data.getInventory(),new ItemStack(Items.BREAD,32),false);
                MovementBridge.command(guard,player,net.conczin.mca.entity.ai.MoveState.STAY);
                enemy=net.minecraft.world.entity.EntityType.ZOMBIE.create(world); enemy.setPos(stage==0?133:138,80,stage==0?134:120); enemy.setNoAi(true);
                enemy.setItemSlot(EquipmentSlot.HEAD,new ItemStack(Items.DIAMOND_HELMET));enemy.setHealth(6);
                world.addFreshEntity(enemy);
                guard.getThreatTable().addThreat(enemy,100);guard.setTarget(enemy);
                Files.writeString(RESULT,"RUNNING: native knight versus zombie with MCA stay active");
            }
            ticks++;guard.getCitizenData().setSaturation(20);
            if(stage==1 && guard.isUsingItem())sawBowUse=true;
            if(!enemy.isAlive()) {
                if(!guard.getCitizenData().getJob().getJobRegistryEntry().getKey().equals(stage==0?com.minecolonies.api.colony.jobs.ModJobs.KNIGHT_ID:com.minecolonies.api.colony.jobs.ModJobs.ARCHER_ID)
                        || ((CitizenMcaActor)guard).colonyMca$actor().getSkin().isBlank()
                        || !((CitizenMcaData)guard.getCitizenData()).colonyMca$getControl().getString("movement").equals("STAY"))
                    throw new AssertionError("Combat lost native job, MCA model, or movement command");
                guard.setTarget(null);
                if(stage==0){stage=1;guard=null;ticks=0;return;}
                if(!sawBowUse)throw new AssertionError("Archer never entered actual bow-use state");
                Files.writeString(RESULT,"PASS: native knight and archer AI each killed a zombie with MCA stay active; actual bow use observed; native guard jobs, MCA appearances and stored stay commands retained");
                ColonyMca.LOGGER.info("Native knight and archer combat acceptance passed");done=true;
            } else if(ticks%200==0)Files.writeString(RESULT,"RUNNING ticks="+ticks+" enemyHealth="+enemy.getHealth()+" weapon="+guard.getMainHandItem()+
                    " state="+guard.getCitizenData().getJob().getWorkerAI().getState()+" citizenState="+guard.getCitizenAI().getState()+" pos="+guard.blockPosition());
            if(ticks>2400)throw new AssertionError("Knight did not defeat target within 2 minutes: "+Files.readString(RESULT));
        } catch(Throwable error) {
            done=true;ColonyMca.LOGGER.error("Native knight combat acceptance failed",error);
            try{Files.writeString(RESULT,"FAIL: "+error);}catch(Exception ignored){}
        }
    }
}

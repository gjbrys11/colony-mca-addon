package dev.colonymca.smoke;

import dev.colonymca.ColonyMca;
import dev.colonymca.bridge.CitizenMcaActor;
import com.minecolonies.core.entity.citizen.EntityCitizen;
import com.minecolonies.api.tileentities.AbstractTileEntityGrave;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.InteractionHand;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import java.nio.file.Files;
import java.nio.file.Path;

/** Native burial handoff with real grave inventory transfer, block reload and revival event. */
@EventBusSubscriber(modid=ColonyMca.ID,value=Dist.CLIENT)
public final class BurialAcceptance {
    private static boolean done;
    private static final Path RESULT=Path.of("../build/gameplay-preview/burial.txt");
    @SubscribeEvent public static void tick(ServerTickEvent.Post event) {
        if(!Boolean.getBoolean("colony_mca.burialVerify") || !GameplayPreview.ready() || done)return;
        done=true;
        try {
            Files.deleteIfExists(RESULT);
            var world=event.getServer().overworld();var player=event.getServer().getPlayerList().getPlayers().getFirst();
            var worker=(EntityCitizen)world.getEntity(GameplayPreview.citizenIdentity());var colony=worker.getCitizenData().getColony();
            var graveyard=GameplayPreview.building(colony,new BlockPos(119,80,136),com.minecolonies.api.blocks.ModBlocks.blockHutGraveyard);
            graveyard.setBuildingLevel(1);graveyard.setBlueprintPath("mystic/graveyard1.blueprint");
            var undertakerData=colony.getCitizenManager().createAndRegisterCivilianData();undertakerData.setName("Acceptance undertaker");undertakerData.setIsChild(false);
            if(!graveyard.getFirstModuleOccurance(com.minecolonies.core.colony.buildings.modules.WorkerBuildingModule.class).assignCitizen(undertakerData))throw new AssertionError("Undertaker assignment failed");
            colony.getCitizenManager().spawnOrCreateCitizen(undertakerData,world,new BlockPos(120,80,134));
            var undertaker=(EntityCitizen)undertakerData.getEntity().orElseThrow();undertakerData.setPaused(true);
            var dead=ColonyAcceptance.adult(colony,"Acceptance buried citizen");
            var uuid=dead.getUUID();var skin=((CitizenMcaActor)dead).colonyMca$actor().getSkin();
            net.neoforged.neoforge.items.ItemHandlerHelper.insertItemStacked(dead.getCitizenData().getInventory(),new ItemStack(Items.DIAMOND,7),false);
            dead.setHealth(.01f);dead.invulnerableTime=0;dead.hurt(world.damageSources().playerAttack(player),1);
            if(dead.isAlive())throw new AssertionError("Real death failed");
            AbstractTileEntityGrave grave=null;
            for(var pos:colony.getGraveManager().getGraves().keySet())if(world.getBlockEntity(pos) instanceof AbstractTileEntityGrave candidate
                    && candidate.getGraveData()!=null && candidate.getGraveData().getCitizenDataNBT()!=null
                    && candidate.getGraveData().getCitizenDataNBT().hasUUID("colony_mca_identity")
                    && candidate.getGraveData().getCitizenDataNBT().getUUID("colony_mca_identity").equals(uuid)){grave=candidate;break;}
            if(grave==null)throw new AssertionError("Native temporary grave missing");
            var graveData=(com.minecolonies.api.colony.GraveData)grave.getGraveData();
            com.minecolonies.api.util.InventoryUtils.transferAllItemHandler(grave.getInventory(),undertakerData.getInventory());
            if(count(undertakerData.getInventory())!=7)throw new AssertionError("Undertaker did not receive original diamonds");
            var module=graveyard.getFirstModuleOccurance(com.minecolonies.core.colony.buildings.modules.GraveyardManagementModule.class);
            module.setLastGraveData(graveData);var oldPos=grave.getBlockPos();world.setBlockAndUpdate(oldPos,net.minecraft.world.level.block.Blocks.AIR.defaultBlockState());
            colony.getGraveManager().removeGrave(oldPos);
            var burialPos=new BlockPos(122,80,136);
            if(!module.buryCitizenHere(new com.minecolonies.api.util.Tuple<>(burialPos,Direction.NORTH),undertaker))throw new AssertionError("Native named-grave burial failed");
            module.setLastGraveData(null);
            var tile=world.getBlockEntity(burialPos);var saved=tile.saveWithFullMetadata(world.registryAccess());var state=tile.getBlockState();
            world.setBlockAndUpdate(burialPos,net.minecraft.world.level.block.Blocks.AIR.defaultBlockState());world.setBlockAndUpdate(burialPos,state);
            world.getBlockEntity(burialPos).loadWithComponents(saved,world.registryAccess());
            var staff=new ItemStack(net.conczin.mca.registry.ItemsMCA.STAFF_OF_LIFE);player.setGameMode(net.minecraft.world.level.GameType.SURVIVAL);
            player.setItemInHand(InteractionHand.MAIN_HAND,staff);player.teleportTo(122,80,134);
            use(player,burialPos);
            var revived=(EntityCitizen)world.getEntity(uuid);
            if(revived==null || !revived.isAlive())throw new AssertionError("Staff cannot revive native named grave after burial and block reload");
            if(!((CitizenMcaActor)revived).colonyMca$actor().getSkin().equals(skin) || count(revived.getCitizenData().getInventory())!=0
                    || count(undertakerData.getInventory())!=7 || staff.getDamageValue()!=1)throw new AssertionError("Burial revival lost profile or cloned previously collected items");
            int population=colony.getCitizenManager().getCurrentCitizenCount();use(player,burialPos);
            if(colony.getCitizenManager().getCurrentCitizenCount()!=population || staff.getDamageValue()!=1)throw new AssertionError("Named grave revived twice");
            if(module.hasRestingCitizen(java.util.Set.of(revived.getCitizenData().getName())))throw new AssertionError("Revived citizen still marked buried in native graveyard");
            var scythe=new ItemStack(net.conczin.mca.registry.ItemsMCA.SCYTHE);player.setItemInHand(InteractionHand.MAIN_HAND,scythe);
            revived.setHealth(.01f);revived.invulnerableTime=0;revived.hurt(world.damageSources().playerAttack(player),1);
            AbstractTileEntityGrave secondGrave=null;
            for(var pos:colony.getGraveManager().getGraves().keySet())if(world.getBlockEntity(pos) instanceof AbstractTileEntityGrave candidate
                    && candidate.getGraveData()!=null && candidate.getGraveData().getCitizenDataNBT()!=null
                    && candidate.getGraveData().getCitizenDataNBT().hasUUID("colony_mca_identity")
                    && candidate.getGraveData().getCitizenDataNBT().getUUID("colony_mca_identity").equals(uuid)){secondGrave=candidate;break;}
            if(secondGrave==null || !net.conczin.mca.item.ScytheItem.hasSoul(scythe))throw new AssertionError("Second native death or soul charge failed");
            com.minecolonies.api.util.InventoryUtils.transferAllItemHandler(secondGrave.getInventory(),undertakerData.getInventory());
            module.setLastGraveData((com.minecolonies.api.colony.GraveData)secondGrave.getGraveData());
            var secondPos=secondGrave.getBlockPos();world.setBlockAndUpdate(secondPos,net.minecraft.world.level.block.Blocks.AIR.defaultBlockState());colony.getGraveManager().removeGrave(secondPos);
            if(!module.buryCitizenHere(new com.minecolonies.api.util.Tuple<>(burialPos,Direction.NORTH),undertaker))throw new AssertionError("Revived citizen cannot be buried again in same native graveyard");
            module.setLastGraveData(null);use(player,burialPos);
            var again=(EntityCitizen)world.getEntity(uuid);
            if(again==null || !again.isAlive() || net.conczin.mca.item.ScytheItem.hasSoul(scythe)
                    || count(again.getCitizenData().getInventory())!=0 || count(undertakerData.getInventory())!=7
                    || module.hasRestingCitizen(java.util.Set.of(again.getCitizenData().getName())))throw new AssertionError("Charged scythe named-grave revival duplicated items or retained burial record");
            again.setHealth(.01f);again.invulnerableTime=0;again.hurt(world.damageSources().playerAttack(player),1);
            AbstractTileEntityGrave nativeGrave=null;
            for(var pos:colony.getGraveManager().getGraves().keySet())if(world.getBlockEntity(pos) instanceof AbstractTileEntityGrave candidate
                    && candidate.getGraveData()!=null && candidate.getGraveData().getCitizenDataNBT()!=null
                    && candidate.getGraveData().getCitizenDataNBT().hasUUID("colony_mca_identity")
                    && candidate.getGraveData().getCitizenDataNBT().getUUID("colony_mca_identity").equals(uuid)){nativeGrave=candidate;break;}
            if(nativeGrave==null)throw new AssertionError("Native resurrection fixture grave missing");
            var direct=colony.getCitizenManager().resurrectCivilianData(nativeGrave.getGraveData().getCitizenDataNBT().copy(),true,world,nativeGrave.getBlockPos().above());
            if(direct==null || net.conczin.mca.server.world.data.FamilyTree.get(world).getOrEmpty(uuid).orElseThrow().isDeceased())
                throw new AssertionError("Native colony resurrection leaves MCA family member deceased");
            colony.getGraveManager().removeGrave(nativeGrave.getBlockPos());world.setBlockAndUpdate(nativeGrave.getBlockPos(),net.minecraft.world.level.block.Blocks.AIR.defaultBlockState());
            player.setGameMode(net.minecraft.world.level.GameType.CREATIVE);
            Files.writeString(RESULT,"PASS: native named-grave burial and block NBT reload; staff and charged scythe revived same UUID without cloning 7 collected diamonds; repeated burial allowed and native burial records cleared; native colony resurrection manager immediately clears MCA deceased state");
            ColonyMca.LOGGER.info("Native burial handoff acceptance passed");
        }catch(Throwable error){ColonyMca.LOGGER.error("Native burial acceptance failed",error);try{Files.writeString(RESULT,"FAIL: "+error);}catch(Exception ignored){}}
    }
    private static int count(net.neoforged.neoforge.items.IItemHandler inventory){int result=0;for(int i=0;i<inventory.getSlots();i++)if(inventory.getStackInSlot(i).is(Items.DIAMOND))result+=inventory.getStackInSlot(i).getCount();return result;}
    private static void use(net.minecraft.server.level.ServerPlayer player,BlockPos pos){net.neoforged.neoforge.common.NeoForge.EVENT_BUS.post(new net.neoforged.neoforge.event.entity.player.PlayerInteractEvent.RightClickBlock(player,InteractionHand.MAIN_HAND,pos,new net.minecraft.world.phys.BlockHitResult(net.minecraft.world.phys.Vec3.atCenterOf(pos),Direction.UP,pos,false)));}
}

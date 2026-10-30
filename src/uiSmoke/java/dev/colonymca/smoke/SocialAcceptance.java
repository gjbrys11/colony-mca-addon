package dev.colonymca.smoke;

import dev.colonymca.ColonyMca;
import dev.colonymca.bridge.*;
import com.minecolonies.core.entity.citizen.EntityCitizen;
import net.conczin.mca.network.c2s.InteractionVillagerMessage;
import net.conczin.mca.server.world.data.FamilyTree;
import net.conczin.mca.server.world.data.PlayerSaveData;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
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

/** Native divorce/adoption and server-side access, capacity, overflow and trade boundaries. */
@EventBusSubscriber(modid=ColonyMca.ID,value=Dist.CLIENT)
public final class SocialAcceptance {
    private static boolean done;
    private static final List<String> checks=new ArrayList<>();
    private static final Path RESULT=Path.of("../build/gameplay-preview/social.txt");
    @SubscribeEvent public static void tick(ServerTickEvent.Post event) {
        if(!Boolean.getBoolean("colony_mca.socialVerify") || !GameplayPreview.ready() || done)return;
        done=true;
        try {
            Files.deleteIfExists(RESULT);
            var world=event.getServer().overworld();var player=event.getServer().getPlayerList().getPlayers().getFirst();
            var worker=(EntityCitizen)world.getEntity(GameplayPreview.citizenIdentity());var data=worker.getCitizenData();var colony=data.getColony();
            var actor=((CitizenMcaActor)worker).colonyMca$actor(); player.teleportTo(worker.getX(),worker.getY(),worker.getZ()-2);
            // Preconditions intentionally use native relationship setup. Actions below use native messages.
            actor.getRelationships().marry(player);PlayerSaveData.get(player).marry(actor);
            actor.getVillagerBrain().getMemoriesForPlayer(player).setHearts(200);
            var papers=net.conczin.mca.registry.ItemsMCA.DIVORCE_PAPERS.getDefaultInstance();player.getInventory().add(papers);
            send(player,worker,"divorceConfirm");
            check(!actor.getRelationships().isMarried() && !PlayerSaveData.get(player).isMarried()
                    && actor.getVillagerBrain().getMemoriesForPlayer(player).getHearts()==180,
                    "Native divorce clears both marriage records and applies native paper penalty");
            var orphanData=colony.getCitizenManager().createAndRegisterCivilianData();orphanData.setName("Acceptance orphan");orphanData.setIsChild(true);
            colony.getCitizenManager().spawnOrCreateCitizen(orphanData,world,worker.blockPosition());
            var orphan=(EntityCitizen)orphanData.getEntity().orElseThrow();var childActor=((CitizenMcaActor)orphan).colonyMca$actor();
            var tree=FamilyTree.get(world);var father=tree.getOrCreate(UUID.randomUUID(),"Deceased acceptance father",net.conczin.mca.entity.ai.relationship.Gender.MALE);
            var mother=tree.getOrCreate(UUID.randomUUID(),"Deceased acceptance mother",net.conczin.mca.entity.ai.relationship.Gender.FEMALE);
            father.setDeceased(true);mother.setDeceased(false);childActor.getRelationships().getFamilyEntry().replaceParents(father,java.util.Optional.of(mother));
            send(player,orphan,"adopt");
            check(childActor.getRelationships().getFamilyEntry().streamParents().noneMatch(player.getUUID()::equals),"Adoption rejects child with a living parent");
            mother.setDeceased(true);send(player,orphan,"adopt");
            check(childActor.getRelationships().getFamilyEntry().streamParents().anyMatch(player.getUUID()::equals)
                    && (orphanData.getParents().getA().equals(player.getName().getString()) || orphanData.getParents().getB().equals(player.getName().getString())),
                    "Native orphan adoption updates MCA tree and colony parent names");
            player.setItemInHand(InteractionHand.MAIN_HAND,new ItemStack(Items.DIAMOND));
            player.teleportTo(worker.getX()+30,worker.getY(),worker.getZ());send(player,worker,"gift");
            check(player.getMainHandItem().getCount()==1,"Out-of-range native gift packet cannot consume items");
            player.teleportTo(worker.getX(),worker.getY(),worker.getZ()-2);
            var visitor=net.neoforged.neoforge.common.util.FakePlayerFactory.get(world,new com.mojang.authlib.GameProfile(UUID.randomUUID(),"AcceptanceVisitor"));
            visitor.setPos(worker.position());visitor.setItemInHand(InteractionHand.MAIN_HAND,new ItemStack(Items.DIAMOND));
            var control=((CitizenMcaData)data).colonyMca$getControl();send(visitor,worker,"gift");send(visitor,worker,"follow");
            check(visitor.getMainHandItem().getCount()==1 && ((CitizenMcaData)data).colonyMca$getControl().equals(control),
                    "Unauthorized server-side visitor cannot gift or change movement (FakePlayer permission check)");
            var inventory=data.getInventory();var backup=new ArrayList<ItemStack>();
            for(int i=0;i<inventory.getSlots();i++){backup.add(inventory.getStackInSlot(i).copy());inventory.setStackInSlot(i,new ItemStack(Items.COBBLESTONE,64));}
            var existing=world.getEntitiesOfClass(net.minecraft.world.entity.item.ItemEntity.class,worker.getBoundingBox().inflate(4)).stream().map(net.minecraft.world.entity.Entity::getUUID).toList();
            while(!actor.getRelationships().getGiftSaturation().toNbt().isEmpty()) actor.getRelationships().getGiftSaturation().pop();
            player.setItemInHand(InteractionHand.MAIN_HAND,new ItemStack(Items.DIAMOND));send(player,worker,"gift");
            int dropped=world.getEntitiesOfClass(net.minecraft.world.entity.item.ItemEntity.class,worker.getBoundingBox().inflate(4)).stream()
                    .filter(e->!existing.contains(e.getUUID()) && e.getItem().is(Items.DIAMOND)).mapToInt(e->e.getItem().getCount()).sum();
            boolean giftConserved=player.getMainHandItem().isEmpty() && dropped==1;
            for(int i=0;i<backup.size();i++)inventory.setStackInSlot(i,backup.get(i));
            check(giftConserved,"Full colony inventory drops accepted native MCA gift exactly once");
            send(player,worker,"trade");check(player.containerMenu instanceof net.minecraft.world.inventory.MerchantMenu,"Native trade opens before range test");
            player.teleportTo(worker.getX()+30,worker.getY(),worker.getZ());TradeBridge.advanceCareer(actor);
            check(!(player.containerMenu instanceof net.minecraft.world.inventory.MerchantMenu) && !actor.isTrading(),"Leaving trade range closes native menu and clears merchant customer");
            player.teleportTo(worker.getX(),worker.getY(),worker.getZ()-2);
            var offer=actor.getOffers().getFirst();offer.increaseUses();
            var savedControl=((CitizenMcaData)data).colonyMca$getControl();savedControl.remove("tradeRestockPeriod");((CitizenMcaData)data).colonyMca$setControl(savedControl);
            int oldTick=worker.tickCount;worker.tickCount=200;TradeBridge.tick(worker,actor);worker.tickCount=oldTick;
            check(offer.getUses()==0,"Colony work building triggers native offer restock");
            var transientTrader=ColonyAcceptance.adult(colony,"Acceptance merchant lifetime");
            player.teleportTo(transientTrader.getX(),transientTrader.getY(),transientTrader.getZ()-2);
            send(player,transientTrader,"trade");
            check(player.containerMenu instanceof net.minecraft.world.inventory.MerchantMenu,"Native merchant menu opens for lifecycle validity test");
            var menu=player.containerMenu;transientTrader.setHealth(.01f);transientTrader.invulnerableTime=0;
            transientTrader.hurt(world.damageSources().playerAttack(player),1);
            check(!transientTrader.isAlive() && !menu.stillValid(player),"Native merchant menu becomes invalid immediately when actual colony citizen dies");
            player.closeContainer();
            for(var pos:java.util.List.copyOf(colony.getGraveManager().getGraves().keySet())) {
                if(world.getBlockEntity(pos) instanceof com.minecolonies.api.tileentities.AbstractTileEntityGrave grave && grave.getGraveData()!=null
                        && grave.getGraveData().getCitizenDataNBT()!=null && grave.getGraveData().getCitizenDataNBT().hasUUID("colony_mca_identity")
                        && grave.getGraveData().getCitizenDataNBT().getUUID("colony_mca_identity").equals(transientTrader.getUUID())) {
                    player.setItemInHand(InteractionHand.MAIN_HAND,new ItemStack(net.conczin.mca.registry.ItemsMCA.STAFF_OF_LIFE));
                    net.neoforged.neoforge.common.NeoForge.EVENT_BUS.post(new net.neoforged.neoforge.event.entity.player.PlayerInteractEvent.RightClickBlock(player,InteractionHand.MAIN_HAND,pos,
                            new net.minecraft.world.phys.BlockHitResult(net.minecraft.world.phys.Vec3.atCenterOf(pos),net.minecraft.core.Direction.UP,pos,false)));
                    break;
                }
            }
            Files.writeString(RESULT,"PASS: "+checks.size()+" real social/boundary checks\n"+String.join("\n",checks));
        } catch(Throwable error) {
            ColonyMca.LOGGER.error("Real social boundary acceptance failed",error);
            try{Files.writeString(RESULT,"FAIL: "+error+"\n"+String.join("\n",checks));}catch(Exception ignored){}
        }
    }
    private static void send(ServerPlayer player,EntityCitizen citizen,String action){new InteractionVillagerMessage(action,citizen.getUUID()).handleServer(player);}
    private static void check(boolean ok,String message)throws Exception{if(!ok)throw new AssertionError(message);checks.add(message);Files.writeString(RESULT,"RUNNING\n"+String.join("\n",checks));}
}

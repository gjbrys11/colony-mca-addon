package dev.colonymca.smoke;

import dev.colonymca.ColonyMca;
import dev.colonymca.bridge.*;
import com.minecolonies.core.entity.citizen.EntityCitizen;
import net.minecraft.core.BlockPos;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;
import java.util.UUID;

/** Two separate JVM runs verify actual disk persistence, rather than an in-memory NBT clone. */
@EventBusSubscriber(modid=ColonyMca.ID,value=Dist.CLIENT)
public final class PersistenceAcceptance {
    private static boolean done;
    private static final Path BASE=Path.of("../build/gameplay-preview");
    @SubscribeEvent public static void tick(ServerTickEvent.Post event) {
        String mode=System.getProperty("colony_mca.persistenceVerify","");
        if(mode.isEmpty() || !GameplayPreview.ready() || done) return;
        done=true;
        try {
            var world=event.getServer().overworld();
            var player=event.getServer().getPlayerList().getPlayers().getFirst();
            var worker=(EntityCitizen)world.getEntity(GameplayPreview.citizenIdentity());
            var colony=worker.getCitizenData().getColony();
            Properties p=new Properties();
            if(mode.equals("write")) {
                var data=colony.getCitizenManager().createAndRegisterCivilianData();
                data.setName("Persistence acceptance citizen"); data.setIsChild(false); data.setPaused(true);
                colony.getCitizenManager().spawnOrCreateCitizen(data,world,new BlockPos(126,80,128));
                var citizen=(EntityCitizen)data.getEntity().orElseThrow();
                var actor=((CitizenMcaActor)citizen).colonyMca$actor();
                actor.getVillagerBrain().getMemoriesForPlayer(player).setHearts(173);
                actor.getTraits().addTrait(net.conczin.mca.entity.ai.Traits.BISEXUAL);
                actor.setHairDye(0xffa03467);
                actor.setVillagerData(actor.getVillagerData().setLevel(2)); actor.setVillagerXp(13);
                actor.getOffers().getFirst().increaseUses(); actor.getOffers().getFirst().increaseUses();
                actor.getRelationships().marry(player); net.conczin.mca.server.world.data.PlayerSaveData.get(player).marry(actor);
                var control=((CitizenMcaData)data).colonyMca$getControl(); control.putLong("lastBirth",world.getGameTime());
                ((CitizenMcaData)data).colonyMca$setControl(control);
                net.neoforged.neoforge.items.ItemHandlerHelper.insertItemStacked(data.getInventory(),new ItemStack(Items.EMERALD,11),false);
                ((CitizenMcaActor)citizen).colonyMca$flush();
                p.setProperty("uuid",data.getUUID().toString()); p.setProperty("skin",actor.getSkin());
                p.setProperty("colony",Integer.toString(colony.getID())); p.setProperty("lastBirth",Long.toString(control.getLong("lastBirth")));
                try(var out=Files.newOutputStream(BASE.resolve("persistence.properties"))){p.store(out,"Real server restart expectations");}
                event.getServer().saveEverything(false,true,true);
                Files.writeString(BASE.resolve("persistence.txt"),"SAVED: waiting for separate JVM read verification");
            } else if(mode.equals("read")) {
                try(var in=Files.newInputStream(BASE.resolve("persistence.properties"))){p.load(in);}
                UUID uuid=UUID.fromString(p.getProperty("uuid"));
                var data=colony.getCitizenManager().getCitizens().stream().filter(c->c.getUUID().equals(uuid)).findFirst().orElseThrow();
                colony.getCitizenManager().spawnOrCreateCitizen(data,world,new BlockPos(126,80,128));
                var citizen=(EntityCitizen)data.getEntity().orElseThrow();
                var actor=((CitizenMcaActor)citizen).colonyMca$actor();
                require(actor.getSkin().equals(p.getProperty("skin")),"skin");
                require(actor.getHairDye()==0xffa03467 && actor.getTraits().hasTrait(net.conczin.mca.entity.ai.Traits.BISEXUAL),"appearance and traits");
                require(actor.getVillagerBrain().getMemoriesForPlayer(player).getHearts()==173,"hearts");
                require(actor.getVillagerData().getLevel()==2 && actor.getVillagerXp()==13 && actor.getOffers().getFirst().getUses()==2,"trade progress and offers");
                require(actor.getRelationships().isMarriedTo(player.getUUID()) && net.conczin.mca.server.world.data.PlayerSaveData.get(player).isMarried(),"both marriage records");
                require(((CitizenMcaData)data).colonyMca$getControl().getLong("lastBirth")==Long.parseLong(p.getProperty("lastBirth")),"birth cooldown");
                int count=0; for(int i=0;i<data.getInventory().getSlots();i++) if(data.getInventory().getStackInSlot(i).is(Items.EMERALD)) count+=data.getInventory().getStackInSlot(i).getCount();
                require(count==11 && data.isPaused(),"native inventory and paused state");
                require(world.getEntity(uuid)==citizen,"single actual entity");
                Files.writeString(BASE.resolve("persistence.txt"),"PASS: separate JVM restart retained citizen UUID, MCA skin/dye/traits/hearts, trade level/XP/offer uses, reciprocal player marriage, birth cooldown, colony inventory and pause state; no duplicate entity");
                ColonyMca.LOGGER.info("Real disk restart acceptance passed");
            } else throw new IllegalArgumentException(mode);
        } catch(Throwable error) {
            ColonyMca.LOGGER.error("Real disk restart acceptance failed",error);
            try{Files.writeString(BASE.resolve("persistence.txt"),"FAIL: "+error);}catch(Exception ignored){}
        }
        net.minecraft.client.Minecraft.getInstance().execute(()->net.minecraft.client.Minecraft.getInstance().stop());
    }
    private static void require(boolean ok,String field){if(!ok)throw new AssertionError("Restart lost "+field);}
}

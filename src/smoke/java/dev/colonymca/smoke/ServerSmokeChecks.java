package dev.colonymca.smoke;

import com.minecolonies.api.colony.IColony;
import com.minecolonies.api.entity.ModEntities;
import com.minecolonies.core.colony.CitizenData;
import dev.colonymca.ColonyMca;
import dev.colonymca.bridge.CitizenMcaActor;
import dev.colonymca.bridge.CitizenMcaData;
import net.conczin.mca.Config;
import net.conczin.mca.entity.VillagerLike;
import net.conczin.mca.server.SpawnQueue;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.EntityType;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import java.lang.reflect.Proxy;
import java.nio.file.Files;
import java.nio.file.Path;

/** Development-only checks. This source set is excluded from the distributed jar. */
@EventBusSubscriber(modid = ColonyMca.ID)
public final class ServerSmokeChecks {
    @SubscribeEvent public static void started(ServerStartedEvent event) {
        event.getServer().execute(() -> {
            String result;
            try {
                var world = event.getServer().overworld();
                check(!Config.getInstance().overwriteOriginalVillagers, "vanilla conversion disabled");
                check(!Config.getInstance().enableInfection, "infection disabled");
                var vanilla = EntityType.VILLAGER.create(world);
                check(!SpawnQueue.getInstance().addVillager(vanilla), "vanilla villager not queued");
                SpawnQueue.getInstance().convert(vanilla);
                SpawnQueue.getInstance().tick();
                check(!vanilla.isRemoved(), "forced conversion preserves original villager");
                var citizen = ModEntities.CITIZEN.create(world);
                check(citizen instanceof CitizenMcaActor, "citizen actor mixin applied");
                check(citizen instanceof VillagerLike<?>, "MCA villager interface applied");
                var actor = ((CitizenMcaActor) citizen).colonyMca$actor();
                check(actor.getUUID().equals(citizen.getUUID()), "same identity");
                check(actor.getId() == citizen.getId(), "same network entity ID");
                check(world.getEntity(actor.getUUID()) == null, "no duplicate world NPC");
                check(!actor.getSkin().isBlank(), "MCA skin initialized");
                check(actor.getInfectionProgress() == 0, "actor uninfected");
                var sourceWalk = new net.minecraft.world.entity.WalkAnimationState();
                var targetWalk = new net.minecraft.world.entity.WalkAnimationState();
                sourceWalk.update(0.4F, 0.3F);
                sourceWalk.update(0.1F, 0.3F);
                for (int frame = 0; frame < 240; frame++) dev.colonymca.bridge.WalkAnimationSync.copy(sourceWalk, targetWalk);
                check(targetWalk.position() == sourceWalk.position(), "240 render passes do not advance walking phase");
                check(targetWalk.speed(0.25F) == sourceWalk.speed(0.25F), "previous and current walking speeds interpolate identically");
                check(targetWalk.position(0.75F) == sourceWalk.position(0.75F), "partial tick walking phase matches colony");
                var builder = com.minecolonies.api.IMinecoloniesAPI.getInstance().getJobRegistry()
                        .get(com.minecolonies.api.colony.jobs.ModJobs.BUILDER_ID);
                citizen.getEntityData().set(com.minecolonies.api.entity.citizen.AbstractEntityCitizen.DATA_JOB,
                        com.minecolonies.api.colony.jobs.ModJobs.BUILDER_ID.toString());
                check(((VillagerLike<?>) citizen).getProfessionText().getContents()
                        instanceof net.minecraft.network.chat.contents.TranslatableContents label
                        && label.getKey().equals(builder.getTranslationKey().toLowerCase(java.util.Locale.ROOT)), "builder label uses colony job");
                citizen.getEntityData().set(com.minecolonies.api.entity.citizen.AbstractEntityCitizen.DATA_JOB, "");
                check(((VillagerLike<?>) citizen).getProfessionText().getContents()
                        instanceof net.minecraft.network.chat.contents.TranslatableContents label
                        && label.getKey().equals(com.minecolonies.api.util.constant.TranslationConstants.COM_MINECOLONIES_COREMOD_GUI_TOWNHALL_CITIZEN_UNEMPLOYED), "unemployed only when colony has no job");
                check(actor.getInteractions() instanceof dev.colonymca.bridge.ColonyInteractions, "dialogue commands use colony adapter");
                var profile = net.conczin.mca.network.c2s.GetVillagerRequest.getVillagerData(citizen);
                check(profile.getString("Skin").equals(actor.getSkin()), "editor receives MCA profile");
                Class.forName("net.conczin.mca.network.c2s.InteractionDialogueInitMessage");
                Class.forName("net.conczin.mca.network.c2s.InteractionDialogueMessage");
                Class.forName("net.conczin.mca.network.c2s.InteractionCloseRequest");
                var managerType = com.minecolonies.api.colony.managers.interfaces.ICitizenManager.class;
                var manager = Proxy.newProxyInstance(managerType.getClassLoader(), new Class[]{managerType},
                        (proxy, method, args) -> method.getName().equals("getCitizens") ? java.util.List.of() : null);
                var effectsType = com.minecolonies.api.research.IResearchEffectManager.class;
                var effects = Proxy.newProxyInstance(effectsType.getClassLoader(), new Class[]{effectsType},
                        (proxy, method, args) -> method.getReturnType() == double.class ? 0.0 : null);
                var researchType = com.minecolonies.api.research.IResearchManager.class;
                var research = Proxy.newProxyInstance(researchType.getClassLoader(), new Class[]{researchType},
                        (proxy, method, args) -> method.getName().equals("getResearchEffects") ? effects : null);
                var colony = (IColony) Proxy.newProxyInstance(IColony.class.getClassLoader(), new Class[]{IColony.class},
                        (proxy, method, args) -> {
                            if (method.getName().equals("getID")) return 900001;
                            if (method.getName().equals("getWorld")) return world;
                            if (method.getName().equals("getDimension")) return world.dimension();
                            if (method.getName().equals("getCitizenManager")) return manager;
                            if (method.getName().equals("getResearchManager")) return research;
                            return null;
                        });
                var data = new CitizenData(1, colony);
                var bridge = (CitizenMcaData) data;
                check(data.getUUID().equals(data.getUUID()), "stable citizen identity");
                var tag = new CompoundTag(); tag.putString("test", "original");
                bridge.colonyMca$setData(tag); tag.putString("test", "mutated");
                check(bridge.colonyMca$getData().getString("test").equals("original"), "defensive state copy");
                var control = new CompoundTag(); control.putLong("lastBirth", 123);
                bridge.colonyMca$setControl(control); control.putLong("lastBirth", 456);
                check(bridge.colonyMca$getControl().getLong("lastBirth") == 123, "defensive control copy");
                data.setName("Smoke Citizen");
                data.setSuffix("_w");
                data.getCitizenSkillHandler().init(5);
                var saved = data.serializeNBT(world.registryAccess());
                check(saved.getCompound("colony_mca").getString("test").equals("original"), "MCA profile saved in citizen NBT");
                var restored = new CitizenData(2, colony);
                restored.deserializeNBT(world.registryAccess(), saved);
                check(restored.getUUID().equals(data.getUUID()), "identity survives restore and colony ID allocation");
                check(((CitizenMcaData) restored).colonyMca$getControl().getLong("lastBirth") == 123, "birth cooldown survives restore");
                check(((CitizenMcaData) restored).colonyMca$getData().getString("test").equals("original"), "MCA profile survives restore");
                var networkBuffer = new net.minecraft.network.RegistryFriendlyByteBuf(io.netty.buffer.Unpooled.buffer(), world.registryAccess());
                try {
                    var socialData = new CompoundTag(); socialData.putInt("hearts", 55); socialData.putString("spouse", "Test Partner");
                    var snapshot = new dev.colonymca.network.CitizenPanelSnapshot(123, 7, world.dimension().location(), new byte[]{1, 2, 3}, socialData, false);
                    dev.colonymca.network.CitizenPanelSnapshot.STREAM_CODEC.encode(networkBuffer, snapshot);
                    var decoded = dev.colonymca.network.CitizenPanelSnapshot.STREAM_CODEC.decode(networkBuffer);
                    check(decoded.colonyId() == 123 && decoded.citizenId() == 7 && decoded.dimension().equals(world.dimension().location())
                            && java.util.Arrays.equals(decoded.citizenData(), snapshot.citizenData()), "panel snapshot codec retains citizen identity and data");
                    check(decoded.mcaData().getInt("hearts") == 55 && decoded.mcaData().getString("spouse").equals("Test Partner")
                            && !decoded.openPanel(), "social summary refresh does not reopen the colony panel");
                    var request = new dev.colonymca.network.CitizenPanelRequest(81, false);
                    dev.colonymca.network.CitizenPanelRequest.STREAM_CODEC.encode(networkBuffer, request);
                    var decodedRequest = dev.colonymca.network.CitizenPanelRequest.STREAM_CODEC.decode(networkBuffer);
                    check(decodedRequest.entityId() == 81 && !decodedRequest.openPanel()
                            && new dev.colonymca.network.CitizenPanelRequest(81).openPanel(), "initial open and passive refresh are distinct");
                } finally { networkBuffer.release(); }
                result = "PASS: 28 server integration checks";
                ColonyMca.LOGGER.info(result);
            } catch (Throwable failure) {
                result = "FAIL: " + failure;
                ColonyMca.LOGGER.error("Addon integration smoke checks failed", failure);
            }
            try { Files.writeString(Path.of("../build/smoke-result.txt"), result); }
            catch (Exception failure) { ColonyMca.LOGGER.error("Cannot write smoke result", failure); }
            event.getServer().halt(false);
        });
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}

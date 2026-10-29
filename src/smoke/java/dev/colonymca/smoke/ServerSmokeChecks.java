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
    private static int checks;
    @SubscribeEvent public static void started(ServerStartedEvent event) {
        event.getServer().execute(() -> {
            String result;
            try {
                checks = 0;
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
                verifySocialAndTrade(actor);
                verifyMovementAndInventory(world);
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
                var letter = new net.conczin.mca.server.world.data.PlayerSaveData.Letter("Acceptance letter",
                        java.util.List.of(net.minecraft.network.chat.Component.literal("悼念信正文保存检查")));
                var encode = letter.getClass().getDeclaredMethod("toTag", net.minecraft.core.HolderLookup.Provider.class);
                encode.setAccessible(true);
                var encoded = (CompoundTag) encode.invoke(letter, world.registryAccess());
                var loadedLetter = new net.conczin.mca.server.world.data.PlayerSaveData.Letter(encoded, world.registryAccess());
                check(loadedLetter.pages().size() == 1 && loadedLetter.pages().getFirst().getString().equals("悼念信正文保存检查"),
                        "native MCA letter pages survive serialization and reload");
                result = "PASS: " + checks + " server integration checks";
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
        checks++;
    }

    private static void verifySocialAndTrade(net.conczin.mca.entity.VillagerEntityMCA actor) {
        int fatiguePeriod = Config.getInstance().interactionFatigueCooldown;
        int giftPeriod = Config.getInstance().giftDesaturationReset;
        try {
            Config.getInstance().interactionFatigueCooldown = 10;
            Config.getInstance().giftDesaturationReset = 20;
            var id = java.util.UUID.randomUUID();
            var memory = new net.conczin.mca.entity.ai.Memories(actor.getVillagerBrain(), 0, id);
            memory.setHearts(80); memory.setInteractionFatigue(5);
            var gift = new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.EMERALD);
            actor.getRelationships().getGiftSaturation().add(gift);
            actor.getRelationships().getGiftSaturation().add(gift);
            var clocks = new CompoundTag();
            dev.colonymca.bridge.SocialLifecycle.tick(actor, clocks, 0);
            dev.colonymca.bridge.SocialLifecycle.tick(actor, clocks, 9);
            check(actor.getVillagerBrain().getMemories().get(id).getInteractionFatigue() == 5, "fatigue waits for configured cooldown");
            dev.colonymca.bridge.SocialLifecycle.tick(actor, clocks, 10);
            check(actor.getVillagerBrain().getMemories().get(id).getInteractionFatigue() == 4, "fatigue recovers without ticking proxy AI");
            check(actor.getRelationships().getGiftSaturation().get(gift) == 2, "gift cooldown independent of social fatigue");
            dev.colonymca.bridge.SocialLifecycle.tick(actor, clocks, 20);
            check(actor.getRelationships().getGiftSaturation().get(gift) == 1, "repeated gift penalty expires");
            var savedClocks = clocks.copy();
            dev.colonymca.bridge.SocialLifecycle.tick(actor, savedClocks, 1000);
            check(actor.getVillagerBrain().getMemories().get(id).getInteractionFatigue() == 0, "elapsed time catches up and clamps fatigue to zero");
            check(actor.getRelationships().getGiftSaturation().get(gift) == 0, "elapsed recovery clears gift queue safely");
            check(actor.getVillagerBrain().getMemories().get(id).getHearts() == 80, "clock recovery preserves earned affection");
            memory = actor.getVillagerBrain().getMemories().get(id); memory.setInteractionFatigue(2);
            dev.colonymca.bridge.SocialLifecycle.tick(actor, savedClocks, 1000);
            check(actor.getVillagerBrain().getMemories().get(id).getInteractionFatigue() == 2, "same timestamp cannot recover twice");
            dev.colonymca.bridge.SocialLifecycle.tick(actor, savedClocks, 1);
            check(actor.getVillagerBrain().getMemories().get(id).getInteractionFatigue() == 2, "time rollback resets clocks without draining social state");
            actor.setVillagerData(actor.getVillagerData().setProfession(net.minecraft.world.entity.npc.VillagerProfession.FARMER).setLevel(1));
            actor.setVillagerXp(net.minecraft.world.entity.npc.VillagerData.getMaxXpPerLevel(1));
            dev.colonymca.bridge.TradeBridge.advanceCareer(actor);
            check(actor.getVillagerData().getLevel() == 1, "trade advancement retains vanilla delay");
            dev.colonymca.bridge.TradeBridge.advanceCareer(actor);
            check(actor.getVillagerData().getLevel() == 2, "merchant gains level without competing world AI");
            check(!actor.getOffers().isEmpty(), "career advancement provides real trade offers");
            dev.colonymca.bridge.TradeBridge.advanceCareer(actor);
            check(actor.getVillagerData().getLevel() == 2, "trade level does not advance twice for same XP");
            var profile = new CompoundTag(); actor.addAdditionalSaveData(profile);
            var reloaded = net.conczin.mca.registry.EntitiesMCA.MALE_VILLAGER.create(actor.level());
            reloaded.readAdditionalSaveData(profile);
            check(reloaded.getVillagerData().getLevel() == 2 && reloaded.getVillagerXp() == actor.getVillagerXp(), "merchant level and XP persist after reload");
            reloaded.setVillagerXp(net.minecraft.world.entity.npc.VillagerData.getMaxXpPerLevel(2));
            var pending = new CompoundTag(); reloaded.addAdditionalSaveData(pending);
            var recovered = net.conczin.mca.registry.EntitiesMCA.MALE_VILLAGER.create(actor.level());
            recovered.readAdditionalSaveData(pending);
            dev.colonymca.bridge.TradeBridge.advanceCareer(recovered);
            dev.colonymca.bridge.TradeBridge.advanceCareer(recovered);
            check(recovered.getVillagerData().getLevel() == 3, "pending trade advancement recovers after entity reload");
            check(((net.minecraft.server.level.ServerLevel) actor.level()).getEntity(recovered.getUUID()) == null, "social and trade tests never spawn a second citizen");
        } finally {
            Config.getInstance().interactionFatigueCooldown = fatiguePeriod;
            Config.getInstance().giftDesaturationReset = giftPeriod;
        }
    }

    private static void verifyMovementAndInventory(net.minecraft.server.level.ServerLevel world) {
        var citizen = (com.minecolonies.core.entity.citizen.EntityCitizen) ModEntities.CITIZEN.create(world);
        var control = new java.util.concurrent.atomic.AtomicReference<>(new CompoundTag());
        var profile = new java.util.concurrent.atomic.AtomicReference<>(new CompoundTag());
        var paused = new java.util.concurrent.atomic.AtomicBoolean();
        var allowed = new java.util.concurrent.atomic.AtomicBoolean(true);
        var inventory = new com.minecolonies.api.inventory.InventoryCitizen("Integration regression", false);
        var children = new java.util.HashSet<Integer>();
        var siblings = new java.util.HashSet<Integer>();
        var parents = new java.util.concurrent.atomic.AtomicReference<>(new com.minecolonies.api.util.Tuple<>("", ""));
        var members = new java.util.ArrayList<com.minecolonies.api.colony.ICitizenData>();
        var managerType = com.minecolonies.api.colony.managers.interfaces.ICitizenManager.class;
        var manager = Proxy.newProxyInstance(managerType.getClassLoader(), new Class[]{managerType},
                (proxy, method, args) -> method.getName().equals("getCitizens") ? members : null);
        var permissionsType = com.minecolonies.api.colony.permissions.IPermissions.class;
        var permissions = Proxy.newProxyInstance(permissionsType.getClassLoader(), new Class[]{permissionsType},
                (proxy, method, args) -> method.getReturnType() == boolean.class ? allowed.get() : null);
        var colony = (IColony) Proxy.newProxyInstance(IColony.class.getClassLoader(), new Class[]{IColony.class},
                (proxy, method, args) -> switch (method.getName()) {
                    case "getID" -> 900002;
                    case "getWorld" -> world;
                    case "getPermissions" -> permissions;
                    case "getCitizenManager" -> manager;
                    default -> null;
                });
        var data = (com.minecolonies.api.colony.ICitizenData) Proxy.newProxyInstance(
                CitizenMcaData.class.getClassLoader(), new Class[]{com.minecolonies.api.colony.ICitizenData.class, CitizenMcaData.class},
                (proxy, method, args) -> switch (method.getName()) {
                    case "getId" -> 17;
                    case "getName" -> "Integration Worker";
                    case "getUUID" -> citizen.getUUID();
                    case "getColony" -> colony;
                    case "getInventory" -> inventory;
                    case "getChildren" -> new java.util.ArrayList<>(children);
                    case "getSiblings" -> new java.util.ArrayList<>(siblings);
                    case "getParents" -> parents.get();
                    case "setParents" -> { parents.set(new com.minecolonies.api.util.Tuple<>((String) args[0], (String) args[1])); yield null; }
                    case "addChildren" -> { children.addAll(java.util.Arrays.asList((Integer[]) args[0])); yield null; }
                    case "addSiblings" -> { siblings.addAll(java.util.Arrays.asList((Integer[]) args[0])); yield null; }
                    case "onDeath" -> { children.remove((Integer) args[0]); siblings.remove((Integer) args[0]); yield null; }
                    case "isPaused" -> paused.get();
                    case "setPaused" -> { paused.set((boolean) args[0]); yield null; }
                    case "colonyMca$getData" -> profile.get().copy();
                    case "colonyMca$setData" -> { profile.set(((CompoundTag) args[0]).copy()); yield null; }
                    case "colonyMca$getControl" -> control.get().copy();
                    case "colonyMca$setControl" -> { control.set(((CompoundTag) args[0]).copy()); yield null; }
                    default -> method.getReturnType() == boolean.class ? false : null;
                });
        citizen.setCivilianData(data);
        var player = new net.minecraft.server.level.ServerPlayer(world.getServer(), world,
                new com.mojang.authlib.GameProfile(java.util.UUID.randomUUID(), "IntegrationTester"),
                net.minecraft.server.level.ClientInformation.createDefault());
        dev.colonymca.bridge.MovementBridge.command(citizen, player, net.conczin.mca.entity.ai.MoveState.FOLLOW);
        check(paused.get() && control.get().getString("movement").equals("FOLLOW"), "follow pauses native work and records leader");
        check(control.get().getUUID("leader").equals(player.getUUID()), "follow uses requesting player identity");
        control.get().putLong("lastBirth", 1234);
        dev.colonymca.bridge.MovementBridge.command(citizen, player, net.conczin.mca.entity.ai.MoveState.STAY);
        check(paused.get() && !control.get().getBoolean("previousPaused"), "switching follow to stay preserves original work state");
        dev.colonymca.bridge.MovementBridge.tick(citizen);
        check(paused.get() && control.get().getString("movement").equals("STAY"), "stay remains paused with no leader online");
        dev.colonymca.bridge.MovementBridge.command(citizen, player, net.conczin.mca.entity.ai.MoveState.MOVE);
        check(!paused.get() && !control.get().contains("movement"), "release resumes native work");
        check(control.get().getLong("lastBirth") == 1234, "release preserves birth and other control data");
        paused.set(true);
        dev.colonymca.bridge.MovementBridge.command(citizen, player, net.conczin.mca.entity.ai.MoveState.FOLLOW);
        dev.colonymca.bridge.MovementBridge.tick(citizen);
        check(paused.get() && !control.get().contains("movement"), "offline leader restores originally paused work state");
        paused.set(false);
        dev.colonymca.bridge.MovementBridge.command(citizen, player, net.conczin.mca.entity.ai.MoveState.FOLLOW);
        dev.colonymca.bridge.MovementBridge.tick(citizen);
        check(!paused.get() && !control.get().contains("leader"), "offline or other-dimension leader releases citizen");
        allowed.set(false);
        dev.colonymca.bridge.MovementBridge.command(citizen, player, net.conczin.mca.entity.ai.MoveState.STAY);
        check(!paused.get() && !control.get().contains("movement"), "unauthorized movement cannot pause colony worker");
        var actor = ((CitizenMcaActor) citizen).colonyMca$actor();
        actor.getInventory().addItem(new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.DIAMOND, 3));
        ((CitizenMcaActor) citizen).colonyMca$flush();
        int diamonds = 0;
        for (int slot = 0; slot < inventory.getSlots(); slot++) {
            var stack = inventory.getStackInSlot(slot);
            if (stack.is(net.minecraft.world.item.Items.DIAMOND)) diamonds += stack.getCount();
        }
        check(diamonds == 3 && actor.getInventory().isEmpty(), "MCA gifts transfer to native colony inventory");
        ((CitizenMcaActor) citizen).colonyMca$flush();
        int after = 0;
        for (int slot = 0; slot < inventory.getSlots(); slot++) if (inventory.getStackInSlot(slot).is(net.minecraft.world.item.Items.DIAMOND)) after += inventory.getStackInSlot(slot).getCount();
        check(after == diamonds, "repeated profile flush cannot duplicate gifts");
        check(!profile.get().isEmpty(), "social profile persists alongside colony inventory");
        members.add(data);
        var relative = new CitizenData(18, colony); relative.setName("Relative"); members.add(relative);
        var tree = net.conczin.mca.server.world.data.FamilyTree.get(world);
        var entry = dev.colonymca.bridge.FamilyBridge.node(tree, data);
        dev.colonymca.bridge.FamilyBridge.node(tree, relative).assignParent(entry);
        dev.colonymca.bridge.FamilyBridge.synchronize(citizen);
        check(children.contains(18), "MCA child UUID is reflected in native colony family IDs");
        members.remove(relative);
        var replacement = new CitizenData(18, colony); replacement.setName("Unrelated replacement"); members.add(replacement);
        dev.colonymca.bridge.FamilyBridge.synchronize(citizen);
        check(!children.contains(18), "reused colony numeric ID cannot inherit deceased citizen family relationship");
        var parent = tree.getOrCreate(java.util.UUID.randomUUID(), "Renamed Parent", net.conczin.mca.entity.ai.relationship.Gender.MALE);
        entry.assignParent(parent);
        dev.colonymca.bridge.FamilyBridge.synchronize(citizen);
        check(parents.get().getA().equals("Renamed Parent"), "parent names refresh from authoritative MCA family tree");
    }
}

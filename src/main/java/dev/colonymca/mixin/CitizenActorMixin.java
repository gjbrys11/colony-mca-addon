package dev.colonymca.mixin;

import com.minecolonies.core.entity.citizen.EntityCitizen;
import dev.colonymca.bridge.CitizenMcaActor;
import dev.colonymca.bridge.CitizenMcaData;
import dev.colonymca.bridge.ColonyInteractions;
import net.conczin.mca.entity.VillagerEntityMCA;
import net.conczin.mca.entity.VillagerLike;
import net.conczin.mca.entity.ai.BreedableRelationship;
import net.conczin.mca.entity.ai.Genetics;
import net.conczin.mca.entity.ai.Traits;
import net.conczin.mca.entity.ai.brain.VillagerBrain;
import net.conczin.mca.entity.ai.relationship.AgeState;
import net.conczin.mca.entity.ai.relationship.CompassionateEntity;
import net.conczin.mca.entity.ai.relationship.Gender;
import net.conczin.mca.entity.interaction.EntityCommandHandler;
import net.conczin.mca.registry.EntitiesMCA;
import net.conczin.mca.util.network.datasync.CDataManager;
import net.conczin.mca.util.network.datasync.CParameter;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.npc.VillagerData;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** MCA logic delegate is never spawned or ticked; MineColonies remains the only world entity and AI. */
@Mixin(value = EntityCitizen.class, remap = false)
public abstract class CitizenActorMixin implements CitizenMcaActor, VillagerLike<VillagerEntityMCA>,
        CompassionateEntity<BreedableRelationship> {
    @Unique private static final EntityDataAccessor<CompoundTag> colonyMca$SYNC =
            SynchedEntityData.defineId(EntityCitizen.class, EntityDataSerializers.COMPOUND_TAG);
    @Unique private VillagerEntityMCA colonyMca$actor;
    @Unique private com.minecolonies.api.colony.ICitizenData colonyMca$boundData;
    @Unique private ColonyInteractions colonyMca$interactions;
    @Unique private CompoundTag colonyMca$lastClientData;

    @Inject(method = "defineSynchedData", at = @At("TAIL"))
    private void colonyMca$defineData(SynchedEntityData.Builder builder, CallbackInfo ci) {
        builder.define(colonyMca$SYNC, new CompoundTag());
    }

    @Override public VillagerEntityMCA colonyMca$actor() {
        var citizen = (EntityCitizen) (Object) this;
        // Chunk loading may expose the world entity before its colony data has been linked.
        // Discard any provisional delegate when authoritative data first becomes available.
        if (!citizen.level().isClientSide && citizen.getCitizenData() != null
                && colonyMca$boundData != citizen.getCitizenData()) {
            colonyMca$actor = null;
            colonyMca$boundData = citizen.getCitizenData();
        }
        if (colonyMca$actor == null) {
            colonyMca$actor = (citizen.isFemale() ? EntitiesMCA.FEMALE_VILLAGER : EntitiesMCA.MALE_VILLAGER)
                    .create(citizen.level());
            if (colonyMca$actor == null) throw new IllegalStateException("MCA villager factory returned null");
            colonyMca$actor.setPos(citizen.position());
            if (!citizen.level().isClientSide) {
                CompoundTag saved = citizen.getCitizenData() instanceof CitizenMcaData data
                        ? data.colonyMca$getData() : new CompoundTag();
                if (saved.isEmpty()) {
                    colonyMca$actor.setVillagerData(colonyMca$actor.getVillagerData()
                            .setProfession(dev.colonymca.bridge.TradeBridge.initialProfession(citizen.getCitizenData(), citizen.getUUID())));
                    colonyMca$actor.initialize(MobSpawnType.COMMAND);
                }
                else colonyMca$actor.readAdditionalSaveData(saved);
            }
            colonyMca$interactions = new ColonyInteractions(citizen, colonyMca$actor);
        }
        if (citizen.level().isClientSide) {
            CompoundTag incoming = citizen.getEntityData().get(colonyMca$SYNC);
            if (!incoming.equals(colonyMca$lastClientData)) {
                colonyMca$actor.readAdditionalSaveDataForEditor(incoming);
                colonyMca$lastClientData = incoming.copy();
            }
        }
        colonyMca$actor.setUUID(citizen.getUUID());
        colonyMca$actor.setId(citizen.getId());
        dev.colonymca.bridge.ActorInteractions.register(colonyMca$actor, colonyMca$interactions);
        colonyMca$actor.setCustomName(citizen.getCustomName());
        colonyMca$actor.setPos(citizen.position());
        colonyMca$actor.getGenetics().setGender(citizen.isFemale() ? Gender.FEMALE : Gender.MALE);
        colonyMca$actor.setAge(citizen.isBaby() ? -AgeState.getMaxAge() / 2 : 0);
        colonyMca$actor.setAgeState(citizen.isBaby() ? AgeState.CHILD : AgeState.ADULT);
        colonyMca$actor.setInfectionProgress(0);
        return colonyMca$actor;
    }

    @Override public void colonyMca$flush() {
        var citizen = (EntityCitizen) (Object) this;
        if (colonyMca$actor == null || citizen.level().isClientSide) return;
        if (citizen.getCitizenData() != null && colonyMca$boundData != citizen.getCitizenData()) colonyMca$actor();
        if (citizen.getCitizenData() != null) {
            var inventory = colonyMca$actor.getInventory();
            for (int slot = 0; slot < inventory.getContainerSize(); slot++) {
                var stack = inventory.getItem(slot);
                if (!stack.isEmpty()) {
                    var remainder = com.minecolonies.api.util.InventoryUtils.addItemStackToItemHandlerWithResult(
                            citizen.getCitizenData().getInventory(), stack.copy());
                    inventory.setItem(slot, net.minecraft.world.item.ItemStack.EMPTY);
                    if (!remainder.isEmpty()) citizen.spawnAtLocation(remainder);
                }
            }
        }
        CompoundTag snapshot = new CompoundTag();
        colonyMca$actor.addAdditionalSaveData(snapshot);
        if (citizen.getCitizenData() instanceof CitizenMcaData data) data.colonyMca$setData(snapshot);
        citizen.getEntityData().set(colonyMca$SYNC, snapshot);
    }

    @Override public Mob asEntity() { return colonyMca$actor(); }
    @Override public CDataManager<VillagerEntityMCA> getTypeDataManager() { return colonyMca$actor().getTypeDataManager(); }
    @Override public <P, T> P getTrackedValue(CParameter<P, T> key) { return colonyMca$actor().getTrackedValue(key); }
    @Override public <P, T> void setTrackedValue(CParameter<P, T> key, P value) { colonyMca$actor().setTrackedValue(key, value); }
    @Override public Genetics getGenetics() { return colonyMca$actor().getGenetics(); }
    @Override public Traits getTraits() { return colonyMca$actor().getTraits(); }
    @Override public VillagerBrain<?> getVillagerBrain() { return colonyMca$actor().getVillagerBrain(); }
    @Override public EntityCommandHandler<?> getInteractions() { colonyMca$actor(); return colonyMca$interactions; }
    @Override public BreedableRelationship getRelationships() { return colonyMca$actor().getRelationships(); }
    @Override public VillagerData getVillagerData() { return colonyMca$actor().getVillagerData(); }
    @Override public void setVillagerData(VillagerData data) { colonyMca$actor().setVillagerData(data); }
    @Override public float getInfectionProgress() { return 0; }
    @Override public void setInfectionProgress(float progress) {}
    @Override public boolean isBurned() { return false; }
    @Override public net.minecraft.resources.ResourceLocation getProfessionId() { return colonyMca$actor().getProfessionId(); }
    @Override public boolean canTradeWithProfession() { return colonyMca$actor().canTradeWithProfession(); }
    @Override public net.minecraft.network.chat.MutableComponent getProfessionText() {
        var citizen = (EntityCitizen) (Object) this;
        // Employment belongs to MineColonies; MCA's trading career must not label a builder unemployed.
        String jobName = citizen.getEntityData().get(com.minecolonies.api.entity.citizen.AbstractEntityCitizen.DATA_JOB);
        var jobId = jobName.isBlank() ? null : net.minecraft.resources.ResourceLocation.tryParse(jobName);
        if (jobId != null) {
            var registry = com.minecolonies.api.IMinecoloniesAPI.getInstance().getJobRegistry();
            var job = registry.containsKey(jobId) ? registry.get(jobId) : null;
            if (job != null) return net.minecraft.network.chat.Component.translatable(job.getTranslationKey().toLowerCase(java.util.Locale.ROOT));
        }
        if (!citizen.level().isClientSide && citizen.getCitizenData() != null && citizen.getCitizenData().getJob() != null) {
            return net.minecraft.network.chat.Component.translatable(citizen.getCitizenData().getJob()
                    .getJobRegistryEntry().getTranslationKey().toLowerCase(java.util.Locale.ROOT));
        }
        return net.minecraft.network.chat.Component.translatable(com.minecolonies.api.util.constant.TranslationConstants.COM_MINECOLONIES_COREMOD_GUI_TOWNHALL_CITIZEN_UNEMPLOYED);
    }
    @Override public void readAdditionalSaveDataForEditor(CompoundTag tag) {
        colonyMca$actor().readAdditionalSaveDataForEditor(tag);
        var citizen = (EntityCitizen) (Object) this;
        if (!citizen.level().isClientSide && citizen.getCitizenData() != null) {
            var actor = colonyMca$actor;
            if (actor.getCustomName() != null) citizen.getCitizenData().setName(actor.getName().getString());
        }
        colonyMca$flush();
    }
}

package dev.colonymca.mixin;

import com.minecolonies.core.colony.CitizenData;
import dev.colonymca.bridge.CitizenMcaActor;
import dev.colonymca.bridge.CitizenMcaData;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import java.util.UUID;

@Mixin(value = CitizenData.class, remap = false)
public abstract class CitizenDataMixin implements CitizenMcaData {
    @Unique private CompoundTag colonyMca$data = new CompoundTag();
    @Unique private UUID colonyMca$identity;
    @Unique private CompoundTag colonyMca$control = new CompoundTag();
    @Override public CompoundTag colonyMca$getData() { return colonyMca$data.copy(); }
    @Override public void colonyMca$setData(CompoundTag data) { colonyMca$data = data.copy(); }
    @Override public CompoundTag colonyMca$getControl() { return colonyMca$control.copy(); }
    @Override public void colonyMca$setControl(CompoundTag control) { colonyMca$control = control.copy(); }

    @Inject(method = "serializeNBT(Lnet/minecraft/core/HolderLookup$Provider;)Lnet/minecraft/nbt/CompoundTag;", at = @At("RETURN"))
    private void colonyMca$save(HolderLookup.Provider provider, CallbackInfoReturnable<CompoundTag> cir) {
        var data = (CitizenData) (Object) this;
        data.getEntity().filter(e -> e instanceof CitizenMcaActor)
                .ifPresent(e -> ((CitizenMcaActor) e).colonyMca$flush());
        cir.getReturnValue().put("colony_mca", colonyMca$data.copy());
        cir.getReturnValue().putInt("colony_mca_colony", data.getColony().getID());
        if (colonyMca$identity != null) cir.getReturnValue().putUUID("colony_mca_identity", colonyMca$identity);
        cir.getReturnValue().put("colony_mca_control", colonyMca$control.copy());
    }

    @Inject(method = "deserializeNBT(Lnet/minecraft/core/HolderLookup$Provider;Lnet/minecraft/nbt/CompoundTag;)V", at = @At("TAIL"))
    private void colonyMca$load(HolderLookup.Provider provider, CompoundTag tag, CallbackInfo ci) {
        colonyMca$data = tag.getCompound("colony_mca").copy();
        colonyMca$identity = tag.hasUUID("colony_mca_identity") ? tag.getUUID("colony_mca_identity") : null;
        colonyMca$control = tag.getCompound("colony_mca_control").copy();
    }

    @Inject(method = "getUUID", at = @At("HEAD"), cancellable = true)
    private void colonyMca$stableIdentity(CallbackInfoReturnable<UUID> cir) {
        if (colonyMca$identity == null) colonyMca$identity = UUID.randomUUID();
        cir.setReturnValue(colonyMca$identity);
    }
}

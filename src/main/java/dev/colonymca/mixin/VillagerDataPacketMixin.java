package dev.colonymca.mixin;

import dev.colonymca.bridge.CitizenMcaActor;
import net.conczin.mca.network.c2s.GetVillagerRequest;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = GetVillagerRequest.class, remap = false)
public abstract class VillagerDataPacketMixin {
    @Inject(method = "getVillagerData", at = @At("HEAD"), cancellable = true)
    private static void colonyMca$useMcaProfile(Entity entity, CallbackInfoReturnable<CompoundTag> cir) {
        if (entity instanceof CitizenMcaActor bridge) {
            cir.setReturnValue(GetVillagerRequest.getVillagerData(bridge.colonyMca$actor()));
        }
    }
}

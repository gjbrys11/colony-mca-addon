package dev.colonymca.mixin;

import com.minecolonies.core.colony.buildings.modules.AbstractAssignedCitizenModule;
import com.minecolonies.core.colony.buildings.modules.LivingBuildingModule;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Import work/pathing improvements while preserving the colony's original population progression. */
@Mixin(value=LivingBuildingModule.class,remap=false,priority=900)
public abstract class NativeHousingCapacityMixin extends AbstractAssignedCitizenModule {
    @Inject(method="getModuleMax",at=@At("HEAD"),cancellable=true)
    private void colonyMca$nativeHomeCapacity(CallbackInfoReturnable<Integer> cir){cir.setReturnValue(building.getBuildingLevel());}
}

package dev.colonymca.mixin;

import com.minecolonies.api.colony.ICitizenData;
import com.minecolonies.api.colony.IColony;
import com.minecolonies.api.entity.citizen.citizenhandlers.ICitizenHappinessHandler;
import com.minecolonies.api.entity.citizen.happiness.StaticHappinessModifier;
import com.minecolonies.api.entity.citizen.happiness.StaticHappinessSupplier;
import com.minecolonies.core.entity.citizen.citizenhandlers.CitizenHappinessHandler;
import dev.colonymca.IntegrationConfig;
import dev.colonymca.family.FamilyHousing;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = CitizenHappinessHandler.class, remap = false)
public abstract class CitizenHappinessMixin {
    @Unique private static final String colonyMca$ID = "colony_mca.family_housing";

    @Inject(method = "getHappiness", at = @At("HEAD"))
    private void colonyMca$includeFamilyHousing(IColony colony, ICitizenData citizen,
                                               CallbackInfoReturnable<Double> cir) {
        if (citizen == null || colony.getWorld() == null || colony.getWorld().isClientSide) return;
        var handler = (ICitizenHappinessHandler) this;
        double factor = FamilyHousing.factor(citizen);
        double weight = IntegrationConfig.FAMILY_WEIGHT.get();
        var previous = handler.getModifier(colonyMca$ID);
        if (previous == null || Double.compare(previous.getFactor(citizen), factor) != 0
                || Double.compare(previous.getWeight(), weight) != 0) {
            handler.addModifier(new StaticHappinessModifier(colonyMca$ID, weight,
                    new StaticHappinessSupplier(factor)));
        }
    }
}

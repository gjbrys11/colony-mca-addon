package dev.colonymca.mixin;

import com.minecolonies.api.colony.ICitizenDataView;
import com.minecolonies.core.client.gui.AbstractWindowSkeleton;
import com.minecolonies.core.client.gui.citizen.AbstractWindowCitizen;
import dev.colonymca.client.McaSidebarModule;
import net.minecraft.resources.ResourceLocation;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = AbstractWindowCitizen.class, remap = false)
public abstract class CitizenWindowMixin {
    @Inject(method = "<init>", at = @At("TAIL"))
    private void colonyMca$attachSocialSidebar(ICitizenDataView citizen, ResourceLocation ui, CallbackInfo ci) {
        ((AbstractWindowSkeleton) (Object) this).registerModule(McaSidebarModule::new, citizen);
    }
}

package dev.colonymca.mixin;

import com.ldtteam.blockui.Pane;
import com.ldtteam.blockui.views.View;
import com.minecolonies.core.client.gui.citizen.AbstractWindowCitizen;
import dev.colonymca.client.McaSidebarModule;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = View.class, remap = false)
public abstract class CitizenWindowLayoutMixin {
    @Shadow protected int padding;

    @Inject(method = "click", at = @At("HEAD"), cancellable = true)
    private void colonyMca$routeNavigationBeforeDecorations(double x, double y, CallbackInfoReturnable<Boolean> cir) {
        if ((Object) this instanceof AbstractWindowCitizen window
                && McaSidebarModule.clickNavigation(window, x - window.getX() - padding, y - window.getY() - padding)) {
            cir.setReturnValue(true);
        }
    }

    @Inject(method = "postAddChild", at = @At("TAIL"))
    private void colonyMca$keepDynamicColonyContentAligned(Pane child, CallbackInfo ci) {
        if ((Object) this instanceof AbstractWindowCitizen window
                && window.findPaneByID(McaSidebarModule.PANEL_ID) != null
                && !McaSidebarModule.PANEL_ID.equals(child.getID())) {
            child.moveBy(McaSidebarModule.WIDTH, 0);
        }
    }
}

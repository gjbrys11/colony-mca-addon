package dev.colonymca.mixin;

import com.minecolonies.core.colony.Colony;
import com.minecolonies.core.colony.managers.ReproductionManager;
import dev.colonymca.bridge.BirthBridge;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = ReproductionManager.class, remap = false)
public abstract class ReproductionMixin {
    @Shadow @Final private Colony colony;

    @Inject(method = "trySpawnChild", at = @At("HEAD"), cancellable = true)
    private void colonyMca$unifiedParents(CallbackInfo ci) {
        BirthBridge.automatic(colony);
        ci.cancel();
    }
}

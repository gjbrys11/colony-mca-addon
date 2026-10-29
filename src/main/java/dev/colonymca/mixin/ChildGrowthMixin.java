package dev.colonymca.mixin;

import com.minecolonies.core.entity.ai.minimal.EntityAICitizenChild;
import com.minecolonies.core.entity.citizen.EntityCitizen;
import dev.colonymca.bridge.ChildGrowth;
import dev.colonymca.bridge.ChildGrowthBridge;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value=EntityAICitizenChild.class,remap=false)
public abstract class ChildGrowthMixin implements ChildGrowth {
    @Shadow private int aiActiveTime;
    @Shadow protected abstract boolean tryGrowUp();

    @Inject(method="<init>",at=@At("TAIL"))
    private void colonyMca$rememberActualAi(EntityCitizen citizen,CallbackInfo ci) {
        ChildGrowthBridge.register(citizen,this);
    }
    @Override public void colonyMca$addGrowth(int ticks) {
        aiActiveTime=(int)Math.min(Integer.MAX_VALUE-2000L,(long)aiActiveTime+ticks);
        tryGrowUp(); // Native colony research, events, model invalidation and adult flags.
    }
}

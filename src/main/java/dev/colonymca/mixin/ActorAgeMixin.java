package dev.colonymca.mixin;

import dev.colonymca.bridge.ActorInteractions;
import dev.colonymca.bridge.ChildGrowthBridge;
import net.conczin.mca.entity.VillagerEntityMCA;
import net.minecraft.world.entity.AgeableMob;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(AgeableMob.class)
public abstract class ActorAgeMixin {
    @Inject(method="ageUp(IZ)V",at=@At("HEAD"),cancellable=true)
    private void colonyMca$nativeGrowth(int seconds,boolean forced,CallbackInfo ci) {
        if((Object)this instanceof VillagerEntityMCA actor) {
            var handler=ActorInteractions.get(actor);
            if(handler!=null) {
                ChildGrowthBridge.advance(handler.citizen(),seconds);
                ci.cancel();
            }
        }
    }
}

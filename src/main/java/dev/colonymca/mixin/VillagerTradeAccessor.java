package dev.colonymca.mixin;

import net.minecraft.world.entity.npc.Villager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(Villager.class)
public interface VillagerTradeAccessor {
    @Accessor("updateMerchantTimer") int colonyMca$getTradeTimer();
    @Accessor("updateMerchantTimer") void colonyMca$setTradeTimer(int ticks);
    @Accessor("increaseProfessionLevelOnUpdate") boolean colonyMca$hasPendingLevel();
    @Accessor("increaseProfessionLevelOnUpdate") void colonyMca$setPendingLevel(boolean pending);
    @Invoker("increaseMerchantCareer") void colonyMca$increaseCareer();
}

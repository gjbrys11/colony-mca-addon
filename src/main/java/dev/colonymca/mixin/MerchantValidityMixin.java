package dev.colonymca.mixin;

import dev.colonymca.bridge.TradeBridge;
import net.conczin.mca.entity.VillagerEntityMCA;
import net.minecraft.world.inventory.MerchantMenu;
import net.minecraft.world.item.trading.Merchant;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Keep vanilla transaction handling, but validate the actual colony citizen behind its data delegate. */
@Mixin(MerchantMenu.class)
public abstract class MerchantValidityMixin {
    @Shadow @Final private Merchant trader;
    @Inject(method="stillValid",at=@At("HEAD"),cancellable=true)
    private void colonyMca$actualMerchant(Player player,CallbackInfoReturnable<Boolean> cir) {
        if(trader instanceof VillagerEntityMCA actor && !TradeBridge.canTradeWith(actor,player))cir.setReturnValue(false);
    }
}

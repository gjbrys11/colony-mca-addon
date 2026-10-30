package dev.colonymca.mixin;

import com.minecolonies.api.util.Utils;
import com.minecolonies.core.colony.crafting.LootTableAnalyzer;
import dev.colonymca.bridge.LootRegistryBridge;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/** Keep the native colony packet format and cached stacks; repair only outgoing holder ownership. */
@Mixin(LootTableAnalyzer.LootDrop.class)
public abstract class LootDropRegistryMixin {
    @Redirect(method="serialize", at=@At(value="INVOKE", target="Lcom/minecolonies/api/util/Utils;serializeCodecMess(Lnet/minecraft/network/RegistryFriendlyByteBuf;Lnet/minecraft/world/item/ItemStack;)V"))
    private void colonyMca$networkRegistry(RegistryFriendlyByteBuf buffer, ItemStack stack) {
        Utils.serializeCodecMess(buffer, LootRegistryBridge.forNetwork(stack, buffer));
    }
}

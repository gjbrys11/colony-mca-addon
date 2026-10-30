package dev.colonymca.bridge;

import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.ItemEnchantments;

/** The integrated client can replace the colony's shared loot cache with client registry holders. */
public final class LootRegistryBridge {
    private LootRegistryBridge() {}

    public static ItemStack forNetwork(ItemStack source, RegistryFriendlyByteBuf buffer) {
        ItemStack copy = source.copy();
        rebind(copy, DataComponents.ENCHANTMENTS, buffer);
        rebind(copy, DataComponents.STORED_ENCHANTMENTS, buffer);
        return copy;
    }

    private static void rebind(ItemStack stack, DataComponentType<ItemEnchantments> type,
                               RegistryFriendlyByteBuf buffer) {
        ItemEnchantments original = stack.get(type);
        if (original == null || original.isEmpty()) return;
        var registry = buffer.registryAccess().registryOrThrow(Registries.ENCHANTMENT);
        var rebound = new ItemEnchantments.Mutable(original);
        rebound.removeIf(holder -> true);
        for (var entry : original.entrySet()) {
            var key = entry.getKey().unwrapKey().orElseThrow();
            rebound.set(registry.getHolderOrThrow(key), entry.getIntValue());
        }
        stack.set(type, rebound.toImmutable());
    }
}

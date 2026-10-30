package dev.colonymca.smoke;

import com.minecolonies.core.colony.crafting.LootTableAnalyzer.LootDrop;
import com.mojang.serialization.Lifecycle;
import net.minecraft.core.MappedRegistry;
import net.minecraft.core.RegistrationInfo;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.item.enchantment.ItemEnchantments;

final class LootRegistryChecks {
    static int run(ServerLevel world) {
        var key = Enchantments.UNBREAKING;
        var real = world.registryAccess().registryOrThrow(Registries.ENCHANTMENT).getHolderOrThrow(key);
        var foreign = new MappedRegistry<Enchantment>(Registries.ENCHANTMENT, Lifecycle.stable());
        var ops = world.registryAccess().createSerializationContext(net.minecraft.nbt.NbtOps.INSTANCE);
        var encoded = Enchantment.DIRECT_CODEC.encodeStart(ops, real.value()).getOrThrow();
        var cloned = Enchantment.DIRECT_CODEC.parse(ops, encoded).getOrThrow();
        var other = foreign.register(key, cloned, RegistrationInfo.BUILT_IN);
        foreign.freeze();
        var levels = new ItemEnchantments.Mutable(ItemEnchantments.EMPTY.withTooltip(false));
        levels.set(other, 3);
        var book = new ItemStack(Items.ENCHANTED_BOOK, 2);
        book.set(DataComponents.STORED_ENCHANTMENTS, levels.toImmutable());
        var buffer = new RegistryFriendlyByteBuf(io.netty.buffer.Unpooled.buffer(), world.registryAccess());
        try {
            boolean rejected = false;
            try { ItemStack.STREAM_CODEC.encode(buffer, book); }
            catch (IllegalArgumentException expected) { rejected = true; }
            require(rejected, "foreign registry must reproduce native encoding failure");
            buffer.clear();
            new LootDrop(java.util.List.of(book), 0.25F, 2F, true).serialize(buffer);
            var decoded = LootDrop.deserialize(buffer);
            require(decoded.getItemStacks().size() == 1, "book must not become empty loot drop");
            var result = decoded.getItemStacks().getFirst();
            require(result.getCount() == 2 && result.get(DataComponents.STORED_ENCHANTMENTS).getLevel(real) == 3,
                    "count and enchantment level preserved");
            var expected = new ItemEnchantments.Mutable(ItemEnchantments.EMPTY.withTooltip(false));
            expected.set(real, 3);
            require(result.get(DataComponents.STORED_ENCHANTMENTS).equals(expected.toImmutable()), "hidden tooltip preserved");
            require(book.get(DataComponents.STORED_ENCHANTMENTS).getLevel(other) == 3, "shared cached stack unchanged");
            require(decoded.getProbability() == 0.25F && decoded.getQuality() == 2F && decoded.getConditional(), "loot metadata preserved");
            buffer.clear();
            var sword = new ItemStack(Items.DIAMOND_SWORD);
            sword.set(DataComponents.ENCHANTMENTS, levels.toImmutable());
            new LootDrop(java.util.List.of(sword), 1, 0, false).serialize(buffer);
            require(LootDrop.deserialize(buffer).getItemStacks().getFirst().get(DataComponents.ENCHANTMENTS).getLevel(real) == 3,
                    "equipped enchantments also rebound");
            buffer.clear();
            var plain = new ItemStack(Items.DIAMOND, 5);
            new LootDrop(java.util.List.of(plain), 1, 0, false).serialize(buffer);
            require(ItemStack.matches(plain, LootDrop.deserialize(buffer).getItemStacks().getFirst()), "ordinary item unchanged");
            return 8;
        } finally { buffer.release(); }
    }
    private static void require(boolean value, String message) {
        if (!value) throw new AssertionError(message);
    }
}

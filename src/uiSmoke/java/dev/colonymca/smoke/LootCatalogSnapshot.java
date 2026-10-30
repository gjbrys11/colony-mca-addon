package dev.colonymca.smoke;

import com.minecolonies.core.colony.crafting.CustomRecipeManager;
import com.minecolonies.core.colony.crafting.LootTableAnalyzer.LootDrop;
import net.minecraft.core.component.DataComponents;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.storage.loot.LootTable;
import java.util.*;

/** Test-only canonical snapshot of every enchanted book in the native synchronized catalog. */
final class LootCatalogSnapshot {
    @SuppressWarnings("unchecked") static String books() throws Exception {
        var field = CustomRecipeManager.class.getDeclaredField("lootTables");
        field.setAccessible(true);
        var tables = (Map<ResourceKey<LootTable>, List<LootDrop>>) field.get(CustomRecipeManager.getInstance());
        List<String> books = new ArrayList<>();
        for (var table : tables.entrySet()) for (var drop : table.getValue()) for (var stack : drop.getItemStacks()) {
            if (!stack.is(Items.ENCHANTED_BOOK)) continue;
            var enchants = stack.get(DataComponents.STORED_ENCHANTMENTS);
            if (enchants == null || enchants.isEmpty()) continue;
            List<String> levels = new ArrayList<>();
            for (var entry : enchants.entrySet()) levels.add(entry.getKey().unwrapKey().orElseThrow().location() + "=" + entry.getIntValue());
            Collections.sort(levels);
            books.add(table.getKey().location()+"|"+stack.getCount()+"|"+drop.getProbability()+"|"+drop.getQuality()+"|"+drop.getConditional()+"|"+String.join(",", levels));
        }
        Collections.sort(books);
        return String.join("\n", books);
    }
}

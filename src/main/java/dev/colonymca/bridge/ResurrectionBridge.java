package dev.colonymca.bridge;

import com.minecolonies.api.IMinecoloniesAPI;
import com.minecolonies.api.colony.permissions.Action;
import com.minecolonies.api.tileentities.AbstractTileEntityGrave;
import net.conczin.mca.item.ScytheItem;
import net.conczin.mca.item.StaffOfLifeItem;
import net.conczin.mca.server.world.data.FamilyTree;
import net.minecraft.nbt.ListTag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.items.ItemHandlerHelper;

import static com.minecolonies.api.util.constant.NbtTagConstants.TAG_INVENTORY;
import static com.minecolonies.api.util.constant.NbtTagConstants.TAG_ARMOR_INVENTORY;

public final class ResurrectionBridge {
    private ResurrectionBridge() {}

    public static void use(PlayerInteractEvent.RightClickBlock event) {
        var stack = event.getItemStack();
        boolean staff = stack.getItem() instanceof StaffOfLifeItem;
        boolean scythe = stack.getItem() instanceof ScytheItem && ScytheItem.hasSoul(stack);
        if ((!staff && !scythe) || !(event.getLevel().getBlockEntity(event.getPos()) instanceof AbstractTileEntityGrave grave)) return;
        event.setCanceled(true);
        event.setCancellationResult(InteractionResult.SUCCESS);
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        var graveData = grave.getGraveData();
        if (graveData == null || graveData.getCitizenDataNBT() == null) return;
        var saved = graveData.getCitizenDataNBT().copy();
        if (!saved.hasUUID("colony_mca_identity")) return;
        var colony = IMinecoloniesAPI.getInstance().getColonyManager()
                .getColonyByWorld(saved.getInt("colony_mca_colony"), player.level());
        if (colony == null || !colony.getPermissions().hasPermission(player, Action.MANAGE_HUTS)) return;
        var identity = saved.getUUID("colony_mca_identity");
        if (colony.getCitizenManager().getCitizens().stream().anyMatch(c -> identity.equals(c.getUUID()))) return;
        // Inventory belongs to the physical grave. Never clone inventory data from a historical snapshot.
        saved.put(TAG_INVENTORY, new ListTag());
        saved.put(TAG_ARMOR_INVENTORY, new ListTag());
        var revived = colony.getCitizenManager().resurrectCivilianData(saved, true, player.level(), event.getPos().above());
        if (revived == null) return;
        graveData.setCitizenDataNBT(null); // Consume this resurrection before touching any items.
        grave.setGraveData(graveData);
        for (int slot = 0; slot < grave.getInventory().getSlots(); slot++) {
            var item = grave.getInventory().extractItem(slot, Integer.MAX_VALUE, false);
            var remainder = ItemHandlerHelper.insertItemStacked(revived.getInventory(), item, false);
            if (!remainder.isEmpty()) player.drop(remainder, false);
        }
        FamilyTree.get(player.serverLevel()).getOrEmpty(identity).ifPresent(node -> node.setDeceased(false));
        colony.getGraveManager().removeGrave(event.getPos());
        player.level().setBlockAndUpdate(event.getPos(), Blocks.AIR.defaultBlockState());
        if (staff) stack.hurtAndBreak(1, player, event.getHand() == net.minecraft.world.InteractionHand.MAIN_HAND
                ? EquipmentSlot.MAINHAND : EquipmentSlot.OFFHAND);
        else ScytheItem.setSoul(stack, false);
        colony.markDirty();
    }
}

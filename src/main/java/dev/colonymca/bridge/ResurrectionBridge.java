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
    private static final String BURIED_CITIZEN = "colony_mca_buried_citizen";
    private static final String BURIAL_BUILDING = "colony_mca_burial_building";
    private ResurrectionBridge() {}

    /** Also covers the native undertaker and other callers of the colony resurrection manager. */
    public static void restored(com.minecolonies.api.eventbus.events.colony.citizens.CitizenAddedModEvent event) {
        if(event.getSource()==com.minecolonies.api.eventbus.events.colony.citizens.CitizenAddedModEvent.CitizenAddedSource.RESURRECTED
                && event.getCitizen() instanceof com.minecolonies.api.colony.ICitizenData data
                && event.getColony().getWorld() instanceof net.minecraft.server.level.ServerLevel world)
            FamilyTree.get(world).getOrEmpty(data.getUUID()).ifPresent(node->node.setDeceased(false));
    }

    /** Native undertakers have already collected the physical items before creating this memorial. */
    public static void buried(com.minecolonies.api.eventbus.events.colony.citizens.CitizenBuriedModEvent event) {
        var saved=event.getSavedCitizenNbt();
        if(!saved.hasUUID("colony_mca_identity") || !(event.getColony().getWorld().getBlockEntity(event.getGravePosition())
                instanceof com.minecolonies.api.tileentities.AbstractTileEntityNamedGrave tile)) return;
        saved.put(TAG_INVENTORY,new ListTag()); saved.put(TAG_ARMOR_INVENTORY,new ListTag());
        tile.getPersistentData().put(BURIED_CITIZEN,saved);
        if(event.getUndertaker() instanceof com.minecolonies.api.colony.ICitizenData data && data.getWorkBuilding()!=null)
            tile.getPersistentData().putLong(BURIAL_BUILDING,data.getWorkBuilding().getPosition().asLong());
        tile.setChanged();
    }

    public static void use(PlayerInteractEvent.RightClickBlock event) {
        var stack = event.getItemStack();
        boolean staff = stack.getItem() instanceof StaffOfLifeItem;
        boolean scythe = stack.getItem() instanceof ScytheItem && ScytheItem.hasSoul(stack);
        if (!staff && !scythe) return;
        var tile=event.getLevel().getBlockEntity(event.getPos());
        var grave=tile instanceof AbstractTileEntityGrave physical?physical:null;
        if(grave==null && !(tile instanceof com.minecolonies.api.tileentities.AbstractTileEntityNamedGrave))return;
        event.setCanceled(true);
        event.setCancellationResult(InteractionResult.SUCCESS);
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        var graveData = grave==null?null:grave.getGraveData();
        var saved=grave==null?tile.getPersistentData().getCompound(BURIED_CITIZEN).copy()
                :graveData==null || graveData.getCitizenDataNBT()==null?null:graveData.getCitizenDataNBT().copy();
        if(saved==null)return;
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
        if(graveData!=null) {
            graveData.setCitizenDataNBT(null); // Consume this resurrection before touching any items.
            grave.setGraveData(graveData);
        } else {
            tile.getPersistentData().remove(BURIED_CITIZEN); tile.setChanged();
            if(tile.getPersistentData().contains(BURIAL_BUILDING)) {
                var building=colony.getServerBuildingManager().getBuilding(net.minecraft.core.BlockPos.of(tile.getPersistentData().getLong(BURIAL_BUILDING)));
                if(building!=null) {
                    var module=building.getFirstModuleOccurance(com.minecolonies.core.colony.buildings.modules.GraveyardManagementModule.class);
                    if(module!=null && ((dev.colonymca.mixin.GraveyardRestingAccessor)module).colonyMca$restingCitizens().remove(revived.getName())) module.markDirty();
                }
            }
        }
        for (int slot = 0; grave!=null && slot < grave.getInventory().getSlots(); slot++) {
            var item = grave.getInventory().extractItem(slot, Integer.MAX_VALUE, false);
            var remainder = ItemHandlerHelper.insertItemStacked(revived.getInventory(), item, false);
            if (!remainder.isEmpty()) player.drop(remainder, false);
        }
        FamilyTree.get(player.serverLevel()).getOrEmpty(identity).ifPresent(node -> node.setDeceased(false));
        colony.getCitizenManager().updateCitizenMourn(revived,false);
        colony.getGraveManager().removeGrave(event.getPos());
        player.level().setBlockAndUpdate(event.getPos(), Blocks.AIR.defaultBlockState());
        if (staff) stack.hurtAndBreak(1, player, event.getHand() == net.minecraft.world.InteractionHand.MAIN_HAND
                ? EquipmentSlot.MAINHAND : EquipmentSlot.OFFHAND);
        else ScytheItem.setSoul(stack, false);
        colony.markDirty();
    }
}

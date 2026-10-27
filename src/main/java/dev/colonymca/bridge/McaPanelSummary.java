package dev.colonymca.bridge;

import com.minecolonies.core.entity.citizen.EntityCitizen;
import net.conczin.mca.server.world.data.FamilyTreeNode;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.server.level.ServerPlayer;

/** Player-specific hearts and authoritative family names; no client access to server FamilyTree. */
public final class McaPanelSummary {
    private McaPanelSummary() {}

    public static CompoundTag create(EntityCitizen citizen, ServerPlayer player) {
        var actor = ((CitizenMcaActor) citizen).colonyMca$actor();
        var summary = new CompoundTag();
        summary.putInt("hearts", actor.getVillagerBrain().getMemoriesForPlayer(player).getHearts());
        var relationship = actor.getRelationships();
        var tree = relationship.getFamilyTree();
        var entry = relationship.getFamilyEntry();
        summary.putString("marriage", relationship.getRelationshipState().name());
        summary.putBoolean("marriedToViewer", relationship.isMarriedTo(player.getUUID()));
        summary.putString("spouse", tree.getOrEmpty(entry.partner()).map(FamilyTreeNode::getName).orElse(""));
        summary.putString("father", tree.getOrEmpty(entry.father()).map(FamilyTreeNode::getName).orElse(""));
        summary.putString("mother", tree.getOrEmpty(entry.mother()).map(FamilyTreeNode::getName).orElse(""));
        var children = new ListTag();
        entry.streamChildren().forEach(id -> tree.getOrEmpty(id).ifPresent(child -> children.add(StringTag.valueOf(child.getName()))));
        summary.put("children", children);
        return summary;
    }
}

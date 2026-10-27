package dev.colonymca.bridge;

import com.minecolonies.api.colony.ICitizenData;
import com.minecolonies.core.entity.citizen.EntityCitizen;
import net.conczin.mca.entity.ai.relationship.Gender;
import net.conczin.mca.server.world.data.FamilyTree;
import net.conczin.mca.server.world.data.FamilyTreeNode;
import net.minecraft.Util;
import net.minecraft.server.level.ServerLevel;

public final class FamilyBridge {
    private FamilyBridge() {}

    public static FamilyTreeNode node(FamilyTree tree, ICitizenData citizen) {
        return tree.getOrCreate(citizen.getUUID(), citizen.getName(), citizen.isFemale() ? Gender.FEMALE : Gender.MALE);
    }

    public static void synchronize(EntityCitizen citizen) {
        if (!(citizen.level() instanceof ServerLevel world) || citizen.getCitizenData() == null) return;
        var data = citizen.getCitizenData();
        var tree = FamilyTree.get(world);
        var entry = node(tree, data);
        var state = (CitizenMcaData) data;
        var control = state.colonyMca$getControl();
        if (!control.getBoolean("familyImported")) {
            var partner = data.getPartner();
            if (entry.partner().equals(Util.NIL_UUID) && partner != null) {
                var partnerEntry = node(tree, partner);
                if (partnerEntry.partner().equals(Util.NIL_UUID) || partnerEntry.partner().equals(data.getUUID())) {
                    entry.updatePartner(partnerEntry); partnerEntry.updatePartner(entry);
                }
            }
            for (String parentName : new String[]{data.getParents().getA(), data.getParents().getB()}) {
                if (parentName == null || parentName.isBlank()) continue;
                var matches = data.getColony().getCitizenManager().getCitizens().stream()
                        .filter(c -> c != data && parentName.equals(c.getName())).toList();
                if (matches.size() == 1) entry.assignParent(node(tree, matches.getFirst()));
            }
            control.putBoolean("familyImported", true);
            state.colonyMca$setControl(control);
        }
        entry.setName(data.getName());
        entry.setDeceased(false);
        var members = data.getColony().getCitizenManager().getCitizens();
        int partnerId = members.stream().filter(c -> c.getUUID().equals(entry.partner()))
                .mapToInt(ICitizenData::getId).findFirst().orElse(0);
        if ((data.getPartner() == null ? 0 : data.getPartner().getId()) != partnerId) data.setPartner(partnerId);
        for (var member : members) {
            if (entry.children().contains(member.getUUID()) && !data.getChildren().contains(member.getId())) data.addChildren(member.getId());
            if (entry.siblings().contains(member.getUUID()) && !data.getSiblings().contains(member.getId())) data.addSiblings(member.getId());
        }
    }
}

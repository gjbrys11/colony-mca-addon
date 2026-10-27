package dev.colonymca.bridge;

import net.conczin.mca.entity.VillagerEntityMCA;

public interface CitizenMcaActor {
    VillagerEntityMCA colonyMca$actor();
    void colonyMca$flush();
}

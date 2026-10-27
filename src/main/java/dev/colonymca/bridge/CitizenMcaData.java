package dev.colonymca.bridge;

import net.minecraft.nbt.CompoundTag;

/** Attached to CitizenData, so MCA state follows colony saves, graves and resurrection. */
public interface CitizenMcaData {
    CompoundTag colonyMca$getData();
    void colonyMca$setData(CompoundTag data);
    CompoundTag colonyMca$getControl();
    void colonyMca$setControl(CompoundTag control);
}

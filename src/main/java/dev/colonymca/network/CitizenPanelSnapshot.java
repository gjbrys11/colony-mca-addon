package dev.colonymca.network;

import dev.colonymca.ColonyMca;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record CitizenPanelSnapshot(int colonyId, int citizenId, ResourceLocation dimension, byte[] citizenData,
                                   net.minecraft.nbt.CompoundTag mcaData, boolean openPanel) implements CustomPacketPayload {
    public CitizenPanelSnapshot(int colonyId, int citizenId, ResourceLocation dimension, byte[] citizenData) {
        this(colonyId, citizenId, dimension, citizenData, new net.minecraft.nbt.CompoundTag(), true);
    }
    public static final Type<CitizenPanelSnapshot> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(ColonyMca.ID, "citizen_panel_snapshot"));
    public static final StreamCodec<RegistryFriendlyByteBuf, CitizenPanelSnapshot> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, CitizenPanelSnapshot::colonyId,
            ByteBufCodecs.VAR_INT, CitizenPanelSnapshot::citizenId,
            ResourceLocation.STREAM_CODEC, CitizenPanelSnapshot::dimension,
            ByteBufCodecs.BYTE_ARRAY, CitizenPanelSnapshot::citizenData,
            ByteBufCodecs.COMPOUND_TAG, CitizenPanelSnapshot::mcaData,
            ByteBufCodecs.BOOL, CitizenPanelSnapshot::openPanel, CitizenPanelSnapshot::new);
    @Override public Type<CitizenPanelSnapshot> type() { return TYPE; }
}

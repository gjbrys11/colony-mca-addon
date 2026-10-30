package dev.colonymca.network;

import dev.colonymca.ColonyMca;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record TerminalSnapshot(CompoundTag data) implements CustomPacketPayload {
    public static final Type<TerminalSnapshot> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(ColonyMca.ID,"terminal_snapshot"));
    public static final StreamCodec<RegistryFriendlyByteBuf,TerminalSnapshot> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.COMPOUND_TAG,TerminalSnapshot::data,TerminalSnapshot::new);
    @Override public Type<TerminalSnapshot> type(){return TYPE;}
}

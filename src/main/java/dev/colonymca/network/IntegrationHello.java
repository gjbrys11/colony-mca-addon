package dev.colonymca.network;

import dev.colonymca.ColonyMca;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/** Mandatory channel declaration makes an absent or incompatible client addon detectable. */
public record IntegrationHello() implements CustomPacketPayload {
    public static final Type<IntegrationHello> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(ColonyMca.ID, "hello"));
    public static final StreamCodec<RegistryFriendlyByteBuf, IntegrationHello> STREAM_CODEC = StreamCodec.unit(new IntegrationHello());
    @Override public Type<IntegrationHello> type() { return TYPE; }
}

package dev.colonymca.network;

import com.minecolonies.api.colony.permissions.Action;
import com.minecolonies.core.entity.citizen.EntityCitizen;
import dev.colonymca.ColonyMca;
import io.netty.buffer.Unpooled;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;

public record CitizenPanelRequest(int entityId, boolean openPanel) implements CustomPacketPayload {
    public CitizenPanelRequest(int entityId) { this(entityId, true); }
    public static final Type<CitizenPanelRequest> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(ColonyMca.ID, "citizen_panel_request"));
    public static final StreamCodec<RegistryFriendlyByteBuf, CitizenPanelRequest> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, CitizenPanelRequest::entityId,
            ByteBufCodecs.BOOL, CitizenPanelRequest::openPanel, CitizenPanelRequest::new);
    @Override public Type<CitizenPanelRequest> type() { return TYPE; }

    public void handle(ServerPlayer player) {
        if (!(player.serverLevel().getEntity(entityId) instanceof EntityCitizen citizen)
                || !citizen.isAlive() || player.distanceToSqr(citizen) > 64 || citizen.getCitizenData() == null) return;
        var data = citizen.getCitizenData();
        if (!data.getColony().getPermissions().hasPermission(player, Action.ACCESS_HUTS)) return;
        citizen.getCitizenData().setInteractedRecently(player.getUUID());
        var buffer = new RegistryFriendlyByteBuf(Unpooled.buffer(), player.registryAccess());
        try {
            data.serializeViewNetworkData(buffer);
            byte[] snapshot = new byte[buffer.readableBytes()];
            buffer.getBytes(buffer.readerIndex(), snapshot);
            PacketDistributor.sendToPlayer(player, new CitizenPanelSnapshot(data.getColony().getID(), data.getId(),
                    player.level().dimension().location(), snapshot,
                    dev.colonymca.bridge.McaPanelSummary.create(citizen, player), openPanel));
        } finally { buffer.release(); }
    }
}

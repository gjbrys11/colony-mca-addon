package dev.colonymca.network;

import com.minecolonies.api.colony.IColonyManager;
import com.minecolonies.api.colony.permissions.Action;
import com.minecolonies.core.colony.Colony;
import com.minecolonies.core.colony.ColonyView;
import dev.colonymca.ColonyMca;
import io.netty.buffer.Unpooled;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;

/** Remote access only authorizes/synchronizes native views; native packets still perform all operations. */
public record TerminalRequest(int colonyId, BlockPos building) implements CustomPacketPayload {
    public static final Type<TerminalRequest> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(ColonyMca.ID,"terminal_request"));
    public static final StreamCodec<RegistryFriendlyByteBuf, TerminalRequest> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, TerminalRequest::colonyId, BlockPos.STREAM_CODEC, TerminalRequest::building, TerminalRequest::new);
    @Override public Type<TerminalRequest> type() { return TYPE; }

    public void handle(ServerPlayer player) {
        var result=snapshot(player);
        if(result==null)return;
        PacketDistributor.sendToPlayer(player,new TerminalSnapshot(result));
        if(colonyId>=0) {
            var actual=(Colony)IColonyManager.getInstance().getColonyByWorld(colonyId,player.level());
            // Initialize the colony first, then let native packets fill related buildings and work orders.
            actual.getServerBuildingManager().sendPackets(java.util.Set.of(),java.util.Set.of(player));
            new com.minecolonies.core.network.messages.client.colony.ColonyViewWorkOrderMessage(actual,
                    new java.util.ArrayList<>(actual.getWorkManager().getWorkOrders().values())).sendToPlayer(player);
        }
    }

    public CompoundTag snapshot(ServerPlayer player) {
        var manager = IColonyManager.getInstance();
        var result = new CompoundTag();
        result.putString("dimension",player.level().dimension().location().toString());
        if (colonyId < 0) {
            var entries = new ListTag();
            for (var colony : manager.getColonies(player.level())) {
                if (!colony.getPermissions().hasPermission(player,Action.ACCESS_HUTS)) continue;
                for (var hut : colony.getServerBuildingManager().getBuildings().values()) {
                    if (entries.size() >= 4096) break;
                    var entry = new CompoundTag();
                    entry.putInt("colony",colony.getID()); entry.putString("colonyName",colony.getName());
                    entry.putString("name",hut.getBuildingDisplayName()); entry.putInt("level",hut.getBuildingLevel());
                    entry.putLong("pos",hut.getID().asLong()); entries.add(entry);
                }
            }
            result.put("buildings",entries);
        } else {
            var colony = manager.getColonyByWorld(colonyId,player.level());
            if (!(colony instanceof Colony actual) || !colony.getPermissions().hasPermission(player,Action.ACCESS_HUTS)
                    || colony.getServerBuildingManager().getBuilding(building)==null) {
                player.displayClientMessage(net.minecraft.network.chat.Component.translatable("colony_mca.terminal.denied"),true);
                return null;
            }
            result.putByteArray("colonyData",encode(player,b->ColonyView.serializeNetworkData(actual,b,true)));
            result.putByteArray("permissions",encode(player,b->actual.getPermissions().serializeViewNetworkData(b,actual.getPermissions().getRank(player))));
            result.putByteArray("buildingData",encode(player,b->actual.getServerBuildingManager().getBuilding(building).serializeToView(b,true)));
            var citizens=new ListTag();
            for(var citizen:actual.getCitizenManager().getCitizens()) {
                var entry=new CompoundTag();entry.putInt("id",citizen.getId());
                entry.putByteArray("data",encode(player,citizen::serializeViewNetworkData));citizens.add(entry);
            }
            result.put("citizens",citizens);
            var research=new CompoundTag();actual.getResearchManager().writeToNBT(player.registryAccess(),research);result.put("research",research);
            result.putInt("openColony",colonyId);result.putLong("openPos",building.asLong());
        }
        return result;
    }

    private static byte[] encode(ServerPlayer player,java.util.function.Consumer<RegistryFriendlyByteBuf> writer) {
        var buffer=new RegistryFriendlyByteBuf(Unpooled.buffer(),player.registryAccess());
        try {writer.accept(buffer);byte[] bytes=new byte[buffer.readableBytes()];buffer.getBytes(buffer.readerIndex(),bytes);return bytes;}
        finally {buffer.release();}
    }
}

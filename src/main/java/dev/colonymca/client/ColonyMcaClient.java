package dev.colonymca.client;

import com.minecolonies.api.entity.ModEntities;
import dev.colonymca.ColonyMca;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;

@EventBusSubscriber(modid = ColonyMca.ID, value = Dist.CLIENT)
public final class ColonyMcaClient {
    private ColonyMcaClient() {}

    public static void openColony(com.minecolonies.core.entity.citizen.EntityCitizen citizen) {
        net.neoforged.neoforge.network.PacketDistributor.sendToServer(new dev.colonymca.network.CitizenPanelRequest(citizen.getId()));
    }

    public static void acceptCitizenPanel(dev.colonymca.network.CitizenPanelSnapshot snapshot) {
        var player = net.minecraft.client.Minecraft.getInstance().player;
        if (player == null || !player.level().dimension().location().equals(snapshot.dimension())) return;
        var colony = com.minecolonies.api.colony.IColonyManager.getInstance()
                .getColonyView(snapshot.colonyId(), player.level().dimension());
        if (colony != null && colony.getPermissions().hasPermission(player, com.minecolonies.api.colony.permissions.Action.ACCESS_HUTS)) {
            var buffer = new net.minecraft.network.RegistryFriendlyByteBuf(io.netty.buffer.Unpooled.wrappedBuffer(snapshot.citizenData()), player.registryAccess());
            try { colony.handleColonyViewCitizensMessage(snapshot.citizenId(), buffer); }
            finally { buffer.release(); }
            var freshData = colony.getCitizen(snapshot.citizenId());
            McaSidebarModule.cache(snapshot);
            if (snapshot.openPanel() && freshData != null) new com.minecolonies.core.client.gui.citizen.MainWindowCitizen(freshData).open();
        } else if (colony == null) {
            player.displayClientMessage(net.minecraft.network.chat.Component.translatable("colony_mca.colony_not_ready"), true);
        }
    }

    @SubscribeEvent
    public static void registerRenderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerEntityRenderer(ModEntities.CITIZEN, CitizenMcaRenderer::new);
    }
}

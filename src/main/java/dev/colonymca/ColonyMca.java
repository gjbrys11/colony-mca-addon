package dev.colonymca;

import com.mojang.logging.LogUtils;
import net.conczin.mca.Config;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.server.ServerAboutToStartEvent;
import org.slf4j.Logger;
import dev.colonymca.bridge.CitizenEvents;
import dev.colonymca.bridge.ResurrectionBridge;

@Mod(ColonyMca.ID)
public final class ColonyMca {
    public static final String ID = "colony_mca";
    public static final Logger LOGGER = LogUtils.getLogger();

    public ColonyMca(IEventBus modBus, ModContainer container) {
        modBus.addListener(this::registerNetwork);
        modBus.addListener((net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent event) -> event.enqueueWork(() -> {
                var colonyBus=com.minecolonies.api.IMinecoloniesAPI.getInstance().getEventBus();
                colonyBus.subscribe(
                        com.minecolonies.api.eventbus.events.colony.citizens.CitizenBuriedModEvent.class,
                        ResurrectionBridge::buried);
                colonyBus.subscribe(com.minecolonies.api.eventbus.events.colony.citizens.CitizenAddedModEvent.class,
                        ResurrectionBridge::restored);
        }));
        container.registerConfig(ModConfig.Type.COMMON, IntegrationConfig.SPEC);
        NeoForge.EVENT_BUS.addListener(this::beforeServerStart);
        NeoForge.EVENT_BUS.addListener(CitizenEvents::join);
        NeoForge.EVENT_BUS.addListener(CitizenEvents::tick);
        NeoForge.EVENT_BUS.addListener(CitizenEvents::interact);
        NeoForge.EVENT_BUS.addListener(net.neoforged.bus.api.EventPriority.LOWEST, CitizenEvents::death);
        NeoForge.EVENT_BUS.addListener(ResurrectionBridge::use);
    }

    private void registerNetwork(net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent event) {
        var registrar = event.registrar("4");
        registrar.playToServer(dev.colonymca.network.TerminalRequest.TYPE,dev.colonymca.network.TerminalRequest.STREAM_CODEC,
                (payload,context)->context.enqueueWork(()->payload.handle((net.minecraft.server.level.ServerPlayer)context.player())));
        registrar.playToClient(dev.colonymca.network.TerminalSnapshot.TYPE,dev.colonymca.network.TerminalSnapshot.STREAM_CODEC,
                (payload,context)->context.enqueueWork(()->dev.colonymca.client.RemoteTerminalClient.accept(payload.data())));
        registrar.playToClient(dev.colonymca.network.IntegrationHello.TYPE,
                dev.colonymca.network.IntegrationHello.STREAM_CODEC, (payload, context) -> {});
        registrar.playToServer(dev.colonymca.network.CitizenPanelRequest.TYPE,
                dev.colonymca.network.CitizenPanelRequest.STREAM_CODEC, (payload, context) ->
                        context.enqueueWork(() -> payload.handle((net.minecraft.server.level.ServerPlayer) context.player())));
        registrar.playToClient(dev.colonymca.network.CitizenPanelSnapshot.TYPE,
                dev.colonymca.network.CitizenPanelSnapshot.STREAM_CODEC, (payload, context) ->
                        context.enqueueWork(() -> dev.colonymca.client.ColonyMcaClient.acceptCitizenPanel(payload)));
    }

    private void beforeServerStart(ServerAboutToStartEvent event) {
        // Runtime policy only: do not rewrite the user's MCA config file.
        Config config = Config.getInstance();
        config.overwriteOriginalVillagers = false;
        config.overwriteOriginalZombieVillagers = false;
        config.overwriteAllZombiesWithZombieVillagers = false;
        config.enableInfection = false;
        config.innSpawnsAdventurers = false;
        config.innSpawnsCultists = false;
        config.innSpawnsWanderingTraders = false;
        LOGGER.info("Colony MCA: colony-only conversion policy enabled; MCA infection disabled");
    }
}

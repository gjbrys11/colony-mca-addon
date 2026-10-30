package dev.colonymca.client;

import dev.colonymca.ColonyMca;
import dev.colonymca.network.TerminalRequest;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.network.PacketDistributor;

@EventBusSubscriber(modid=ColonyMca.ID,value=Dist.CLIENT)
public final class RemoteTerminalClient {
    public static final KeyMapping KEY=new KeyMapping("colony_mca.terminal.key",org.lwjgl.glfw.GLFW.GLFW_KEY_N,"key.categories.colony_mca");
    private static CompoundTag pending;
    private static int waitTicks;
    public static void request(){PacketDistributor.sendToServer(new TerminalRequest(-1,BlockPos.ZERO));}
    public static void accept(CompoundTag data) {
        var mc=Minecraft.getInstance();
        if(mc.level==null || !mc.level.dimension().location().toString().equals(data.getString("dimension")))return;
        if(data.contains("openColony")){
            var manager=com.minecolonies.api.colony.IColonyManager.getInstance();
            int id=data.getInt("openColony");var dim=mc.level.dimension();
            decode(data.getByteArray("colonyData"),b->manager.handleColonyViewMessage(id,b,true,dim));
            decode(data.getByteArray("permissions"),b->manager.handlePermissionsViewMessage(id,b,dim));
            var colony=manager.getColonyView(id,dim);
            if(colony==null)return;
            colony.handleColonyViewResearchManagerUpdate(mc.player.registryAccess(),data.getCompound("research"));
            for(var tag:data.getList("citizens",10)){
                var citizen=(CompoundTag)tag;
                decode(citizen.getByteArray("data"),b->colony.handleColonyViewCitizensMessage(citizen.getInt("id"),b));
            }
            decode(data.getByteArray("buildingData"),b->manager.handleColonyBuildingViewMessage(id,BlockPos.of(data.getLong("openPos")),b,dim));
            pending=data;waitTicks=0;
        }
        else mc.setScreen(new RemoteTerminalScreen(data));
    }
    private static void decode(byte[] bytes,java.util.function.Consumer<net.minecraft.network.RegistryFriendlyByteBuf> reader) {
        var buffer=new net.minecraft.network.RegistryFriendlyByteBuf(io.netty.buffer.Unpooled.wrappedBuffer(bytes),Minecraft.getInstance().player.registryAccess());
        try{reader.accept(buffer);}finally{buffer.release();}
    }
    @SubscribeEvent public static void tick(ClientTickEvent.Post event) {
        var mc=Minecraft.getInstance();
        if(mc.player==null){pending=null;return;}
        while(KEY.consumeClick())if(mc.screen==null)request();
        if(pending==null)return;
        if(!mc.level.dimension().location().toString().equals(pending.getString("dimension")) || ++waitTicks>100){pending=null;return;}
        var colony=com.minecolonies.api.colony.IColonyManager.getInstance().getColonyView(pending.getInt("openColony"),mc.level.dimension());
        if(colony==null || !colony.getPermissions().hasPermission(mc.player,com.minecolonies.api.colony.permissions.Action.ACCESS_HUTS))return;
        var building=colony.getClientBuildingManager().getBuilding(BlockPos.of(pending.getLong("openPos")));
        if(building!=null){pending=null;building.openGui(false);}
    }
    @EventBusSubscriber(modid=ColonyMca.ID,value=Dist.CLIENT,bus=EventBusSubscriber.Bus.MOD)
    public static final class Registration {
        @SubscribeEvent public static void keys(RegisterKeyMappingsEvent event){event.register(KEY);}
    }
}

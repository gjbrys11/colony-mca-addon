package dev.colonymca.client;

import dev.colonymca.network.TerminalRequest;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.network.PacketDistributor;
import java.util.*;

/** A searchable entry point to the original colony building windows. */
public final class RemoteTerminalScreen extends Screen {
    private final List<CompoundTag> buildings;
    private String query="";
    private int page;
    private int pages=1;
    public RemoteTerminalScreen(CompoundTag data) {
        super(Component.translatable("colony_mca.terminal.title"));
        buildings=new ArrayList<>();
        for(var tag:data.getList("buildings",10))buildings.add((CompoundTag)tag);
        buildings.sort(Comparator.comparing((CompoundTag t)->t.getString("colonyName"))
                .thenComparing(t->Component.translatable(t.getString("name")).getString()).thenComparingLong(t->t.getLong("pos")));
    }
    @Override protected void init() {
        int x=width/2-180;
        var search=new EditBox(font,x,36,360,20,Component.translatable("colony_mca.terminal.search"));
        search.setHint(Component.translatable("colony_mca.terminal.search"));search.setValue(query);
        search.setResponder(value->{if(value.equals(query))return;query=value;page=0;rebuildWidgets();});addRenderableWidget(search);
        var filtered=buildings.stream().filter(t->(t.getString("colonyName")+" "+Component.translatable(t.getString("name")).getString())
                .toLowerCase(Locale.ROOT).contains(query.toLowerCase(Locale.ROOT))).toList();
        int rows=Math.max(1,Math.min(10,(height-124)/24));pages=Math.max(1,(filtered.size()+rows-1)/rows);page=Math.min(page,pages-1);
        for(int i=page*rows;i<Math.min(filtered.size(),(page+1)*rows);i++) {
            var t=filtered.get(i);var pos=BlockPos.of(t.getLong("pos"));
            var label=Component.literal(t.getString("colonyName")+" · ").append(Component.translatable(t.getString("name")))
                    .append(" Lv."+t.getInt("level")+"  ["+pos.getX()+", "+pos.getY()+", "+pos.getZ()+"]");
            addRenderableWidget(Button.builder(label,b->PacketDistributor.sendToServer(new TerminalRequest(t.getInt("colony"),pos)))
                    .bounds(x,64+(i-page*rows)*24,360,20).build());
        }
        addRenderableWidget(Button.builder(Component.literal("◀"),b->{page=Math.max(0,page-1);rebuildWidgets();}).bounds(x,height-38,45,20).build());
        addRenderableWidget(Button.builder(Component.literal("▶"),b->{page=Math.min(pages-1,page+1);rebuildWidgets();}).bounds(x+50,height-38,45,20).build());
        addRenderableWidget(Button.builder(Component.translatable("colony_mca.terminal.refresh"),b->RemoteTerminalClient.request()).bounds(x+215,height-38,70,20).build());
        addRenderableWidget(Button.builder(Component.translatable("gui.done"),b->onClose()).bounds(x+290,height-38,70,20).build());
        if(!query.isEmpty()){setInitialFocus(search);search.moveCursorToEnd(false);}
    }
    @Override public void render(GuiGraphics graphics,int mouseX,int mouseY,float partialTick) {
        graphics.fill(0,0,width,height,0xE0101820);
        super.render(graphics,mouseX,mouseY,partialTick);
        graphics.drawCenteredString(font,title,width/2,16,0xFFFFFF);
        graphics.drawCenteredString(font,(page+1)+" / "+pages,width/2,height-32,0xFFFFFF);
        if(buildings.isEmpty())graphics.drawCenteredString(font,Component.translatable("colony_mca.terminal.empty"),width/2,80,0xFFFFFF);
    }
    @Override public boolean isPauseScreen(){return false;}
}

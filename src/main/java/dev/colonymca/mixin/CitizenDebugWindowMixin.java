package dev.colonymca.mixin;

import com.ldtteam.blockui.controls.Text;
import com.ldtteam.blockui.controls.Button;
import com.minecolonies.core.debug.gui.DebugWindowCitizen;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Localizes upstream's literal debug labels without changing the original mod. */
@Mixin(value = DebugWindowCitizen.class, remap = false)
public abstract class CitizenDebugWindowMixin {
    @Inject(method = {"<init>", "onUpdate"}, at = @At("TAIL"))
    private void colonyMca$translateDebugPage(CallbackInfo ci) {
        var window = (DebugWindowCitizen) (Object) this;
        for (String id : new String[]{"citizenid", "colonyid", "output"}) {
            var text = window.findPaneOfTypeByID(id, Text.class);
            if (text != null && text.getText() != null) {
                String original = text.getText().getString();
                String translated = original.replace("Citizen ID:", "居民编号：")
                        .replace("Colony ID:", "殖民地编号：")
                        .replace("Enabled Citizen AI History!", "已启用居民行为历史记录！")
                        .replace("Receiving pathfinding data", "正在接收寻路数据")
                        .replace("Citizen AI: ", "居民行为：")
                        .replace("Job AI: ", "工作行为：");
                if (!original.equals(translated)) text.setText(Component.literal(translated));
                if (!id.equals("output")) text.setSize(180, text.getHeight());
            }
        }
        window.findPaneOfTypeByID("aihistory", Button.class).setText(Component.literal("查询行为历史"));
        var path = window.findPaneOfTypeByID("pathfinding", Button.class);
        String label = path.getText().getString();
        if (label.equals("disable Pathfinding tracking")) path.setText(Component.literal("关闭寻路追踪"));
        else if (label.equals("enable Pathfinding tracking")) path.setText(Component.literal("开启寻路追踪"));
    }
}

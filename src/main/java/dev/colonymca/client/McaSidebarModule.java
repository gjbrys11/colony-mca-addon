package dev.colonymca.client;

import com.ldtteam.blockui.BOScreen;
import com.ldtteam.blockui.Alignment;
import com.ldtteam.blockui.Pane;
import com.ldtteam.blockui.controls.ButtonImage;
import com.ldtteam.blockui.controls.Image;
import com.ldtteam.blockui.controls.Text;
import com.ldtteam.blockui.views.ScrollingView;
import com.ldtteam.blockui.views.View;
import com.minecolonies.api.colony.ICitizenDataView;
import com.minecolonies.core.client.gui.AbstractWindowSkeleton;
import com.minecolonies.core.client.gui.modules.IWindowModule;
import dev.colonymca.network.CitizenPanelRequest;
import dev.colonymca.network.CitizenPanelSnapshot;
import net.conczin.mca.entity.VillagerLike;
import net.conczin.mca.network.Network;
import net.conczin.mca.network.c2s.InteractionDialogueInitMessage;
import net.conczin.mca.network.c2s.InteractionDialogueMessage;
import net.conczin.mca.network.c2s.InteractionVillagerMessage;
import net.conczin.mca.resources.data.dialogue.Question;
import net.minecraft.client.Minecraft;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.PacketDistributor;
import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** Native colony window module. The sidebar stays present across all citizen tabs. */
public final class McaSidebarModule implements IWindowModule {
    public static final String PANEL_ID = "colony_mca_sidebar";
    public static final int WIDTH = 174;
    private static final ResourceLocation PAPER = ResourceLocation.fromNamespaceAndPath("minecolonies", "textures/gui/citizen/colonist_paper.png");
    private static final ResourceLocation BUTTON = ResourceLocation.fromNamespaceAndPath("minecolonies", "textures/gui/builderhut/builder_button_medium_large.png");
    private static final Map<String, CompoundTag> SUMMARIES = new LinkedHashMap<>(16, 0.75F, true) {
        @Override protected boolean removeEldestEntry(Map.Entry<String, CompoundTag> entry) { return size() > 256; }
    };
    private static WeakReference<McaSidebarModule> active = new WeakReference<>(null);
    private final AbstractWindowSkeleton window;
    private final ICitizenDataView citizen;
    private ScrollingView scroll;
    private boolean social;
    private boolean talking;
    private String question = "root";
    private List<String> answers = List.of();
    private Component phrase = Component.empty();
    private int ticks;
    private int y;
    private final List<Pane> traitTooltips = new ArrayList<>();

    /** Navigation tabs stay clickable even where parchment or a transient tooltip overlaps them. */
    public static boolean clickNavigation(AbstractWindowSkeleton window, double x, double y) {
        var panel = window.findPaneByID(PANEL_ID);
        if (panel != null) {
            for (String id : List.of("colony_mca_attributes_tab", "colony_mca_social_tab")) {
                var tab = window.findPaneByID(id);
                double localX = x - panel.getX(), localY = y - panel.getY();
                if (tab != null && tab.canHandleClick(localX, localY)) return tab.click(localX, localY);
            }
        }
        for (String page : List.of("main", "request", "inventory", "happiness", "family", "job", "debug")) {
            for (String suffix : List.of("Icon", "Tab")) {
                var tab = window.findPaneByID(page + suffix);
                if (tab != null && tab.canHandleClick(x, y)) return tab.click(x, y);
            }
        }
        return false;
    }

    public McaSidebarModule(AbstractWindowSkeleton window, ICitizenDataView citizen) {
        this.window = window;
        this.citizen = citizen;
    }

    public static void cache(CitizenPanelSnapshot snapshot) {
        SUMMARIES.put(key(snapshot.colonyId(), snapshot.citizenId(), snapshot.dimension()), snapshot.mcaData().copy());
    }

    private static String key(int colony, int citizen, ResourceLocation dimension) { return dimension + ":" + colony + ":" + citizen; }

    public static McaSidebarModule current() {
        var module = active.get();
        return module != null && Minecraft.getInstance().screen instanceof BOScreen screen
                && screen.getWindow() == module.window ? module : null;
    }

    private VillagerLike<?> villager() {
        var world = Minecraft.getInstance().level;
        return world != null && world.getEntity(citizen.getEntityId()) instanceof VillagerLike<?> actor ? actor : null;
    }

    private CompoundTag summary() {
        var world = Minecraft.getInstance().level;
        return world == null ? new CompoundTag() : SUMMARIES.getOrDefault(
                key(citizen.getColonyId(), citizen.getId(), world.dimension().location()), new CompoundTag());
    }

    @Override public void onOpened() {
        active = new WeakReference<>(this);
        if (window.findPaneByID(PANEL_ID) == null) {
            int height = Math.max(window.getHeight(), 244);
            window.setSize(window.getWidth() + WIDTH, height);
            for (Pane child : new ArrayList<>(window.getChildren())) child.moveBy(WIDTH, 0);
            var panel = new View();
            panel.setID(PANEL_ID); panel.setSize(WIDTH - 4, height); panel.setPosition(0, 0);
            var paper = new Image();
            paper.setImage(PAPER, false); paper.setSize(WIDTH - 4, height); panel.addChild(paper);
            var title = new Text(); title.setText(Component.translatable("colony_mca.sidebar.title"));
            title.setTextAlignment(Alignment.MIDDLE);
            title.setColors(0x332818); title.setPosition(12, 14); title.setSize(146, 14); panel.addChild(title);
            scroll = new ScrollingView();
            scroll.setPosition(10, 33); scroll.setSize(150, height - 92); panel.addChild(scroll);
            var attributesTab = button(Component.translatable("colony_mca.sidebar.attributes"), 10, height - 51, 72, () -> {
                social = false; talking = false; scroll.setScrollY(0); render();
            });
            attributesTab.setID("colony_mca_attributes_tab"); panel.addChild(attributesTab);
            var socialTab = button(Component.translatable("colony_mca.sidebar.social"), 87, height - 51, 72, () -> {
                social = true; talking = false; scroll.setScrollY(0); render();
            });
            socialTab.setID("colony_mca_social_tab"); panel.addChild(socialTab);
            var hint = new Text(); hint.setText(Component.translatable("colony_mca.sidebar.hint"));
            hint.setTextAlignment(Alignment.MIDDLE);
            hint.setColors(0x665842); hint.setPosition(12, height - 25); hint.setSize(146, 13);
            hint.setText(Component.translatable("colony_mca.sidebar.hint"));
            panel.addChild(hint);
            window.addChild(panel);
        }
        render();
    }

    @Override public void onUpdate() {
        centerColonySkills();
        if (++ticks % 20 == 0 && !social) render();
        if (ticks % 100 == 0) refresh();
    }

    private void centerColonySkills() {
        if (!(window instanceof com.minecolonies.core.client.gui.citizen.MainWindowCitizen)) return;
        for (var skill : com.minecolonies.api.entity.citizen.Skill.values()) {
            String id = skill.name().toLowerCase(Locale.ROOT);
            var value = window.findPaneOfTypeByID(id, Text.class);
            if (value == null || !(value.getParent() instanceof View row)) continue;
            for (Pane parent = row.getParent(); parent != null && parent != window; parent = parent.getParent()) {
                if (parent instanceof ScrollingView skills) { skills.setPosition(WIDTH + 63, skills.getY()); break; }
            }
            var label = row.getChildren().stream().filter(pane -> pane instanceof Text && pane != value)
                    .map(pane -> (Text) pane).findFirst().orElse(null);
            var icon = row.getChildren().stream().filter(pane -> pane instanceof Image).findFirst().orElse(null);
            if (label == null || icon == null) continue;
            int labelWidth = Minecraft.getInstance().font.width(label.getText());
            int valueWidth = Math.max(9, Minecraft.getInstance().font.width(value.getText()));
            int left = (row.getWidth() - labelWidth - valueWidth - 17) / 2;
            label.setPosition(left, label.getY()); label.setSize(labelWidth, label.getHeight());
            label.setTextAlignment(Alignment.MIDDLE);
            icon.setPosition(left + labelWidth + 4, icon.getY());
            value.setPosition(left + labelWidth + 17, value.getY()); value.setSize(valueWidth, value.getHeight());
            value.setTextAlignment(Alignment.MIDDLE);
            var buttons = window.findPaneByID(id + "_bts");
            if (buttons != null) buttons.setPosition(icon.getX() - 12, buttons.getY());
        }
    }

    @Override public void onClosed() { if (active.get() == this) active.clear(); }

    private void refresh() {
        var world = Minecraft.getInstance().level;
        if (world != null && world.getEntity(citizen.getEntityId()) != null)
            PacketDistributor.sendToServer(new CitizenPanelRequest(citizen.getEntityId(), false));
    }

    private void render() {
        if (scroll == null) return;
        for (Pane tooltip : traitTooltips) window.removeChild(tooltip);
        traitTooltips.clear();
        double previousScroll = scroll.getScrollY();
        scroll.getContainer().getChildren().clear(); y = 0;
        if (social) renderSocial(); else renderAttributes();
        scroll.getContainer().setContentHeight(y + 6);
        scroll.setScrollY(previousScroll);
    }

    private void renderAttributes() {
        var villager = villager();
        if (villager == null) { line(Component.translatable("colony_mca.colony_not_ready")); return; }
        var data = summary();
        value("profession", villager.getProfessionText());
        value("hearts", Component.literal(Integer.toString(data.getInt("hearts"))));
        value("mood", villager.getVillagerBrain().getMood().getText());
        value("personality", villager.getVillagerBrain().getPersonality().getName());
        value("gender", Component.translatable("gui.villager_editor." + villager.getGenetics().getGender().name().toLowerCase(Locale.ROOT)));
        value("age", villager.getAgeState().getName());
        value("marriage", data.getString("marriage").isBlank() ? name("")
                : Component.translatable("colony_mca.marriage." + data.getString("marriage").toLowerCase(Locale.ROOT)));
        value("spouse", name(data.getString("spouse")));
        value("father", name(data.getString("father")));
        value("mother", name(data.getString("mother")));
        var children = data.getList("children", 8);
        value("children", children.isEmpty() ? Component.translatable("colony_mca.sidebar.none")
                : Component.literal(String.join(", ", children.stream().map(tag -> tag.getAsString()).toList())));
        line(Component.translatable("colony_mca.sidebar.traits"));
        var traits = villager.getTraits().getTraits();
        if (traits.isEmpty()) line(Component.translatable("colony_mca.sidebar.none"));
        for (var trait : traits) {
            var text = line(trait.getName());
            traitTooltips.add(com.ldtteam.blockui.PaneBuilders.singleLineTooltip(trait.getDescription().copy(), text));
        }
        line(Component.translatable("colony_mca.sidebar.genetics"));
        for (var gene : villager.getGenetics()) line(Component.translatable(gene.getType().getTranslationKey())
                .append(": " + Math.round(gene.get() * 100) + "%"));
    }

    private void renderSocial() {
        var villager = villager();
        if (villager == null) { line(Component.translatable("colony_mca.colony_not_ready")); return; }
        if (talking) {
            if (!phrase.getString().isBlank()) line(phrase).setID("colony_mca_dialogue_phrase");
            for (String answer : answers) action(Component.translatable(Question.getTranslationKey(question, answer)), () -> {
                Network.sendToServer(new InteractionDialogueMessage(villager.asEntity().getUUID(), question, answer));
                refresh();
            }).setID("colony_mca_dialogue_answer_" + answer);
            action(Component.translatable("colony_mca.sidebar.back"), () -> { talking = false; render(); });
            return;
        }
        action(Component.translatable("gui.button.talk"), this::startDialogue).setID("colony_mca_action_talk");
        action(Component.translatable("colony_mca.sidebar.gift_held"), () -> command("gift"));
        if (villager.canTradeWithProfession() && !villager.asEntity().isBaby())
            action(Component.translatable("gui.button.trade"), () -> command("trade"));
        if (summary().getBoolean("marriedToViewer") && !villager.asEntity().isBaby())
            action(Component.translatable("colony_mca.sidebar.procreate"), () -> command("procreate"));
        action(Component.translatable("gui.button.follow"), () -> command("follow"));
        action(Component.translatable("gui.button.stay"), () -> command("stay"));
        action(Component.translatable("gui.button.move"), () -> command("move"));
    }

    public void startDialogue() {
        var villager = villager();
        if (villager == null) return;
        social = true; talking = true; answers = List.of(); phrase = Component.translatable("colony_mca.sidebar.waiting"); render();
        Network.sendToServer(new InteractionDialogueInitMessage(villager.asEntity().getUUID()));
    }

    private void command(String command) {
        var villager = villager();
        if (villager == null) return;
        Network.sendToServer(new InteractionVillagerMessage(command, villager.asEntity().getUUID()));
        if (!command.equals("trade")) refresh();
    }

    public void dialogue(String question, List<String> answers) {
        if (!talking) return;
        this.question = question; this.answers = List.copyOf(answers); render();
    }

    public void phrase(Component text, boolean silent) {
        if (!talking) return;
        var villager = villager(); var player = Minecraft.getInstance().player;
        phrase = villager != null && player != null
                ? (silent ? villager.transformMessage(text) : villager.sendChatMessage(text, player)) : text;
        render(); refresh();
    }

    /** MCA sends final answers (e.g. chat.success) as ordinary chat packets instead of dialogue packets. */
    public void chatMessage(java.util.UUID speaker, Component text) {
        var actor = villager();
        if (!talking || actor == null || !speaker.equals(actor.asEntity().getUUID())) return;
        phrase = text.copy();
        render(); refresh(); // Do not resend chat here: handleVillagerMessage already displayed it.
    }

    private static Component name(String text) { return text.isBlank() ? Component.translatable("colony_mca.sidebar.none") : Component.literal(text); }
    private void value(String key, Component value) { line(Component.translatable("colony_mca.sidebar." + key).append(": ").append(value)).setID("colony_mca_value_" + key); }

    private Text line(Component component) {
        var text = new Text();
        int height = Math.max(13, Minecraft.getInstance().font.split(component, 138).size() * 10 + 3);
        text.setPosition(2, y); text.setSize(138, height); text.setColors(0x332818); text.setTextWrap(true); text.setText(component);
        text.setTextAlignment(Alignment.MIDDLE);
        scroll.getContainer().addChild(text); y += height + 2;
        return text;
    }

    private ButtonImage action(Component text, Runnable action) {
        var button = button(text, 2, y, 138, action);
        scroll.getContainer().addChild(button); y += 24;
        return button;
    }

    private static ButtonImage button(Component text, int x, int y, int width, Runnable action) {
        var button = new ButtonImage();
        button.setImage(BUTTON); button.setPosition(x, y); button.setSize(width, 20);
        button.setTextRenderBox(width - 6, 18); button.setText(text); button.setColors(0x332818);
        button.setTextAlignment(Alignment.MIDDLE);
        button.setHandler(clicked -> action.run());
        return button;
    }
}

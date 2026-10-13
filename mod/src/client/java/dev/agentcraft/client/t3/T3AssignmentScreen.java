package dev.agentcraft.client.t3;

import java.util.List;
import java.util.Locale;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/** Select an existing conversation without changing its T3 workspace or executing work. */
final class T3AssignmentScreen extends T3MenuScreen {
    private final T3CraftClient mod;
    private final String cast;
    private final Screen parent;
    private String query = "";
    private int page;
    T3AssignmentScreen(T3CraftClient mod, String cast, Screen parent) {
        super(Component.literal("Assign chat")); this.mod = mod; this.cast = cast; this.parent = parent;
    }
    private List<T3State.ThreadRow> rows() {
        String search = query.toLowerCase(Locale.ROOT);
        return mod.state().snapshot().threads().stream().filter(r -> (r.title() + " " + r.projectTitle() + " " + mod.machineLabel(r.ownerKey())).toLowerCase(Locale.ROOT).contains(search)).toList();
    }
    @Override protected void init() {
        int x = Math.max(20, (width - 560) / 2), w = width - 2 * x, count = Math.max(1, (height - 135) / 34);
        var search = addRenderableWidget(new EditBox(font, x, 46, w, 20, Component.literal("Search chats")));
        search.setValue(query); search.setResponder(value -> { query = value; page = 0; rebuildWidgets(); }); placeholder(search, "Search chat, project or machine");
        var rows = rows(); page = Math.min(page, Math.max(0, (rows.size() - 1) / count));
        for (int i = page * count; i < Math.min(rows.size(), (page + 1) * count); i++) {
            var row = rows.get(i);
            var button = menuAction(row.title(), row.projectTitle() + " · " + mod.machineLabel(row.ownerKey()), x, 76 + (i - page * count) * 34, w, b -> {
                var current = mod.state().snapshot().threads().stream().filter(r -> r.id().equals(row.id()) && r.ownerKey().equals(row.ownerKey())).findFirst().orElse(null);
                if (current != null && mod.canPin() && mod.assignDesk(cast, current)) mod.openAgent(cast);
            }); button.active = mod.canPin();
        }
        var previous = addRenderableWidget(Button.builder(Component.literal("Previous"), b -> { page--; rebuildWidgets(); }).bounds(x, height - 34, 70, 20).build()); previous.active = page > 0;
        var next = addRenderableWidget(Button.builder(Component.literal("Next"), b -> { page++; rebuildWidgets(); }).bounds(x + 78, height - 34, 60, 20).build()); next.active = (page + 1) * count < rows.size();
        addRenderableWidget(Button.builder(Component.literal("Cancel"), b -> onClose()).bounds(width - x - 70, height - 34, 70, 20).build());
        setInitialFocus(search);
    }
    @Override public void onClose() { minecraft.gui.setScreen(parent); }
    @Override public void extractRenderState(GuiGraphicsExtractor g, int mx, int my, float delta) {
        int x = Math.max(20, (width - 560) / 2); paper(g, x - 12, 8, width - x + 12, height - 8);
        g.text(font, "Assign an existing chat to " + mod.npcName(cast), x, 18, INK, false);
        if (rows().isEmpty()) g.text(font, "No matching loaded chats.", x, 80, MUTED, false);
        super.extractRenderState(g, mx, my, delta);
    }
}

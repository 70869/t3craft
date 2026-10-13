package dev.agentcraft.client.t3;

import java.util.List;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.network.chat.Component;

/** A bounded work inbox; all actions resolve the current owning chat before opening. */
final class T3WorkScreen extends T3MenuScreen {
    private final T3CraftClient mod;
    private T3Workflow.View view = T3Workflow.View.WAITING;
    private String query = "";
    private EditBox search;
    private boolean searchChanged;
    private int page;
    private List<T3State.ThreadRow> shown = List.of();
    private List<String> signature = List.of();
    T3WorkScreen(T3CraftClient mod) { super(Component.literal("Your work")); this.mod = mod; }
    private int left() { return Math.max(16, (width - 580) / 2); }
    private int count() { return Math.max(1, (height - 144) / 44); }
    private List<T3State.ThreadRow> rows() {
        var desks = view == T3Workflow.View.DESKS ? mod.studioBindings().values().stream().map(T3State.ThreadRow::id).collect(java.util.stream.Collectors.toSet()) : java.util.Set.<String>of();
        String needle = query.strip().toLowerCase(java.util.Locale.ROOT);
        return mod.state().snapshot().threads().stream().filter(r -> T3Workflow.matches(view, r, desks.contains(r.id())))
            .filter(r -> needle.isEmpty() || (r.title() + " " + r.projectTitle() + " " + mod.machineLabel(r.ownerKey())).toLowerCase(java.util.Locale.ROOT).contains(needle))
            .sorted(T3Workflow.order(view)).toList();
    }
    @Override protected void init() {
        int x = left(), w = width - 2 * x;
        boolean searching = search != null && search.isFocused();
        search = new EditBox(font, x, 54, w - 126, 20, search, Component.literal("Search work"));
        search.setMaxLength(200); search.setValue(query); placeholder(search, "Search chat, project or machine");
        search.setResponder(value -> { query = value; page = 0; searchChanged = true; }); addRenderableWidget(search);
        if (searching) setInitialFocus(search);
        addRenderableWidget(Button.builder(Component.literal(view.label + " >"), b -> {
            view = T3Workflow.View.values()[(view.ordinal() + 1) % T3Workflow.View.values().length]; page = 0; rebuildWidgets();
        }).bounds(x + w - 120, 54, 120, 20).build());
        shown = rows(); page = Math.min(page, Math.max(0, (shown.size() - 1) / count())); signature = signatures(shown);
        for (int i = page * count(); i < Math.min(shown.size(), (page + 1) * count()); i++) {
            var row = shown.get(i); int y = 82 + (i - page * count()) * 44;
            var action = mod.nextAction(row);
            menuAction(row.title(), row.projectTitle() + " · " + mod.machineLabel(row.ownerKey()) + " · " + (mod.state().online(row.id()) ? StudioProjection.label(row.status()) : "Offline"), x, y, w - 126,
                b -> { mod.focus(row.id()); mod.openPanel(); });
            addRenderableWidget(Button.builder(Component.literal(T3Workflow.label(action, row.status())), b -> mod.openNextAction(row.id())).bounds(x + w - 120, y + 7, 120, 20).build());
        }
        Button prev = addRenderableWidget(Button.builder(Component.literal("Previous"), b -> { page--; rebuildWidgets(); }).bounds(x, height - 56, 72, 18).build()); prev.active = page > 0;
        Button next = addRenderableWidget(Button.builder(Component.literal("Next"), b -> { page++; rebuildWidgets(); }).bounds(x + 78, height - 56, 56, 18).build()); next.active = (page + 1) * count() < shown.size();
        addRenderableWidget(Button.builder(Component.literal("Back"), b -> onClose()).bounds(width - x - 64, height - 56, 64, 18).build());
        navigation(mod, x, height - 30, w, "Your work");
    }
    private List<String> signatures(List<T3State.ThreadRow> rows) {
        return rows.stream().map(r -> r.id() + ":" + r.title() + ":" + r.status() + ":" + mod.nextAction(r)).toList();
    }
    @Override public void tick() {
        if (searchChanged || !signature.equals(signatures(rows()))) {
            boolean searching = search != null && search.isFocused();
            searchChanged = false; rebuildWidgets();
            // Screen rebuilding clears focus before init; restore it after the new field exists.
            if (searching) { setFocused(search); search.setFocused(true); }
        }
    }
    @Override public boolean mouseScrolled(double x, double y, double h, double v) {
        int next = Math.max(0, Math.min(page - (int) Math.signum(v), Math.max(0, (shown.size() - 1) / count())));
        if (next != page) { page = next; rebuildWidgets(); } return true;
    }
    @Override public void extractRenderState(GuiGraphicsExtractor g, int mx, int my, float delta) {
        int x = left(), w = width - 2 * x; paper(g, x - 10, 10, width - x + 10, height - 8);
        g.text(font, "Your work", x, 22, INK, false);
        long waiting = mod.state().snapshot().threads().stream().filter(r -> r.status() == T3State.Status.NEEDS_YOU && mod.state().online(r.id())).count();
        g.text(font, T3Hud.ellipsize(font, waiting + " waiting for you · " + shown.size() + " in " + view.label.toLowerCase(java.util.Locale.ROOT), w), x, 38, MUTED, false);
        if (shown.isEmpty()) g.text(font, T3Hud.ellipsize(font, mod.state().snapshot().threads().isEmpty() ? "Connecting… check Connections if no chats appear." : "Nothing here. Choose All chats or clear your search.", w), x, 92, MUTED, false);
        g.text(font, (page + 1) + " / " + Math.max(1, (shown.size() + count() - 1) / count()), x + 148, height - 50, MUTED, false);
        super.extractRenderState(g, mx, my, delta);
    }
}

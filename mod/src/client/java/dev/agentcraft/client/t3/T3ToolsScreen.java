package dev.agentcraft.client.t3;

import java.util.List;
import java.util.function.Supplier;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/** Searchable direct destinations, with bounded pages on very small screens. */
final class T3ToolsScreen extends T3MenuScreen {
    private final T3CraftClient mod;
    private final T3State.ThreadRow row;
    private String query = "";
    private net.minecraft.client.gui.components.EditBox search;
    private boolean searchChanged;
    private int toolPage;
    private record Tool(String label, String description, Supplier<Screen> screen) {}
    T3ToolsScreen(T3CraftClient mod, T3State.ThreadRow row) { super(Component.literal("Workspace tools")); this.mod = mod; this.row = row; }
    private List<Tool> tools() {
        return List.of(
            new Tool("Chat settings", "Name & chat mode", () -> new T3SettingsScreen(mod, row)),
            new Tool("History", "Saved chat timeline", () -> new T3HistoryScreen(mod, row.id())),
            new Tool("Archived chats", "Restore a chat", () -> new T3ArchiveScreen(mod, row)),
            new Tool("Activity", "Run receipts", () -> new T3ActivityScreen(mod, row.id())),
            new Tool("Run queue", "Queued messages", () -> new T3QueueScreen(mod, row)),
            new Tool("Model options", "Reasoning & provider", () -> new T3OptionsScreen(mod, row)),
            new Tool("Delegated tasks", "Child task results", () -> new T3TasksScreen(mod, row)),
            new Tool("Schedules", "Recurring work", () -> new T3SchedulesScreen(mod, row)),
            new Tool("Review changes", "Checkpoint files", () -> { mod.openReview(row.id()); return minecraft.gui.screen(); }),
            new Tool("Worktrees & handoff", "Change checkout", () -> new T3WorkspaceScreen(mod, row, true)),
            new Tool("Terminal", "Workspace shell", () -> new T3TerminalScreen(mod, row)),
            new Tool("Git & pull requests", "Branches & reviews", () -> new T3GitScreen(mod, row)));
    }
    private int left() { return Math.max(20, (width - 600) / 2); }
    @Override protected void init() {
        int x = left(), available = width - 2 * x;
        search = addRenderableWidget(new net.minecraft.client.gui.components.EditBox(font, x, 56, available, 20, Component.literal("Find a tool")));
        search.setMaxLength(100); search.setValue(query); placeholder(search, "Find a tool…  Ctrl+F");
        search.setResponder(value -> { query = value; toolPage = 0; searchChanged = true; });
        var matching = tools().stream().filter(t -> matches(t)).toList();
        int columns = available >= 430 ? 3 : available >= 280 ? 2 : 1;
        int spacing = 38;
        if (height - 122 < ((matching.size() + columns - 1) / columns) * spacing) spacing = 36;
        int rows = Math.max(1, (height - 122) / spacing);
        if (matching.size() > rows * columns) rows = Math.max(1, (height - 146) / spacing);
        int pageSize = rows * columns;
        int pages = Math.max(1, (matching.size() + pageSize - 1) / pageSize); toolPage = Math.min(toolPage, pages - 1);
        int colWidth = (available - (columns - 1) * 8) / columns;
        int first = toolPage * pageSize, end = Math.min(matching.size(), first + pageSize);
        for (int i = first; i < end; i++) {
            Tool tool = matching.get(i); int position = i - first;
            var button = menuAction(tool.label(), tool.description(), x + position % columns * (colWidth + 8), 84 + position / columns * spacing, colWidth, b -> {
                Screen target = tool.screen().get(); if (target != this) minecraft.gui.setScreen(target);
            });
            button.setTooltip(net.minecraft.client.gui.components.Tooltip.create(Component.literal(tool.description())));
        }
        if (pages > 1) {
            addRenderableWidget(Button.builder(Component.literal("<"), b -> { toolPage = Math.floorMod(toolPage - 1, pages); rebuildWidgets(); }).bounds(x, height - 58, 32, 18).build());
            addRenderableWidget(Button.builder(Component.literal((toolPage + 1) + "/" + pages + " >"), b -> { toolPage = (toolPage + 1) % pages; rebuildWidgets(); }).bounds(x + 38, height - 58, 68, 18).build());
        }
        addRenderableWidget(Button.builder(Component.literal("Back"), b -> onClose()).bounds(width - x - 64, 18, 64, 20).build());
        navigation(mod, x, height - 34, available, "Workspace");
    }
    private boolean matches(Tool tool) { return (tool.label() + " " + tool.description()).toLowerCase(java.util.Locale.ROOT).contains(query.toLowerCase(java.util.Locale.ROOT)); }
    @Override public boolean keyPressed(net.minecraft.client.input.KeyEvent event) {
        if (event.hasControlDown() && event.key() == com.mojang.blaze3d.platform.InputConstants.KEY_F) {
            setFocused(search); search.setFocused(true); return true;
        }
        if (search.isFocused() && event.isEscape() && !query.isEmpty()) { search.setValue(""); return true; }
        if (search.isFocused() && event.isConfirmation()) {
            var matches = tools().stream().filter(this::matches).toList();
            if (matches.size() == 1) { Screen target = matches.getFirst().screen().get(); if (target != this) minecraft.gui.setScreen(target); }
            return true;
        }
        return super.keyPressed(event);
    }
    @Override public void tick() {
        if (searchChanged) { boolean focused = search.isFocused(); searchChanged = false; rebuildWidgets(); if (focused) { setFocused(search); search.setFocused(true); } }
    }
    @Override public void extractRenderState(GuiGraphicsExtractor g, int mx, int my, float delta) {
        int x = left(), available = width - 2 * x;
        paper(g, x - 12, 10, width - x + 12, height - 8);
        g.text(font, "Workspace tools", x, 22, INK, false);
        g.text(font, T3Hud.ellipsize(font, row.title() + " · " + mod.machineLabel(row.ownerKey()), available), x, 40, MUTED, false);
        if (tools().stream().noneMatch(this::matches)) g.text(font, "No matching tools — try a name or purpose", x, 90, MUTED, false);
        super.extractRenderState(g, mx, my, delta);
    }
}

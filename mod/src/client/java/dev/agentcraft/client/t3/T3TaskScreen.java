package dev.agentcraft.client.t3;

import com.google.gson.JsonObject;
import dev.agentcraft.client.hud.UiBits;
import java.util.ArrayList;
import java.util.List;
import java.util.function.BooleanSupplier;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.network.chat.Component;

/** NPC controls stay bound to the conversation shown when opened. */
final class T3TaskScreen extends T3MenuScreen {
    private final T3CraftClient mod;
    private final String threadId, cast;
    private final List<Control> controls = new ArrayList<>();
    private int group, scroll, actionPage, x, w, contentBottom;
    private boolean closed, scheduleLoaded;
    private int scheduleSerial;
    private long scheduleAttempt;
    private String schedules = "Open Runs to load schedules from this NPC's machine.";
    private record Control(Button button, BooleanSupplier enabled) {}
    T3TaskScreen(T3CraftClient mod, String threadId, String cast) {
        super(Component.literal("Agent desk")); this.mod = mod; this.threadId = threadId; this.cast = cast;
    }
    private T3State.ThreadRow row() { return mod.state().snapshot().threads().stream().filter(r -> r.id().equals(threadId)).findFirst().orElse(null); }
    private boolean bound() {
        if (row() == null) return false;
        if (cast == null) return true;
        var current = dev.agentcraft.client.foreman.T3Projection.agentThread(cast);
        return current != null && current.id().equals(threadId) && current.ownerKey().equals(row().ownerKey());
    }
    private boolean online() { return bound() && mod.state().online(threadId); }
    private boolean working() { return online() && row().status() == T3State.Status.WORKING; }
    private void action(String label, String hint, Runnable run, BooleanSupplier enabled) {
        int n = controls.size(), cols = w >= 280 ? 2 : 1, count = cols * 3, bw = (w - (cols - 1) * 8) / cols;
        var button = Button.builder(Component.literal(label), b -> { if (enabled.getAsBoolean()) run.run(); })
            .bounds(x + n % cols * (bw + 8), height - 110 + (n % count) / cols * 24, bw, 20).build();
        button.setTooltip(Tooltip.create(Component.literal(hint))); controls.add(new Control(button, enabled));
        if (n / count == actionPage) addRenderableWidget(button);
    }
    private void compose(boolean steer) {
        var row = row(); if (row == null || !online()) return;
        mod.openComposer(row.id(), steer);
    }
    @Override protected void init() {
        closed = false; controls.clear(); w = Math.min(560, width - 32); x = (width - w) / 2;
        String[] tabs = {"Chat", "Runs", "Workspace", "Desk"}; int tw = (w - 18) / 4;
        for (int i = 0; i < tabs.length; i++) {
            int selected = i;
            var tab = addRenderableWidget(Button.builder(Component.literal(tabs[i]), b -> { group = selected; scroll = actionPage = 0; rebuildWidgets(); })
                .bounds(x + i * (tw + 6), 58, tw, 20).build()); tab.active = group != i;
        }
        switch (group) {
            case 0 -> {
                var current = row();
                if (current != null) action(T3Workflow.label(mod.nextAction(current), current.status()), T3Workflow.hint(mod.nextAction(current), current.status()), () -> mod.openNextAction(threadId), this::bound);
                action("Reply", "Compose a reply; active runs queue it", () -> compose(false), this::online);
                action("Steer run", "Compose steering for this active run", () -> compose(true), this::working);
                action("Saved history", "Read this chat's saved timeline", () -> mod.openHistory(threadId), this::bound);
                action("Stop run", "Interrupt this NPC's active T3 run", () -> mod.interrupt(threadId), this::working);
                action("Your work", "Waiting requests, active runs and recent chats", mod::openWork, () -> true);
            }
            case 1 -> {
                action("Run queue", "Edit, reorder, cancel, or steer queued messages", () -> minecraft.gui.setScreen(new T3QueueScreen(mod, row())), this::online);
                action("Delegated tasks", "Parent/child tasks, progress, results and cancellation", () -> minecraft.gui.setScreen(new T3TasksScreen(mod, row())), this::online);
                action("Schedules", "Upcoming runs and schedule controls for this project", () -> minecraft.gui.setScreen(new T3SchedulesScreen(mod, row())), this::online);
                action("Activity", "Read this NPC's actual T3 receipts", () -> minecraft.gui.setScreen(new T3ActivityScreen(mod, threadId)), this::bound);
                action("Parent chat", "Open a delegated task's parent conversation", () -> { mod.focus(parentId()); mod.openPanel(); }, () -> online() && parentId() != null && mod.state().online(parentId()));
                action("Tools", "All native T3 controls for this conversation", () -> minecraft.gui.setScreen(new T3ToolsScreen(mod, row())), this::bound);
                loadSchedules();
            }
            case 2 -> {
                action("Review changes", "Open this chat's checkpoint/diff review", () -> mod.openReview(threadId), this::online);
                action("Worktrees & handoff", "Inspect or change the actual bound checkout", () -> minecraft.gui.setScreen(new T3WorkspaceScreen(mod, row(), true)), this::online);
                action("Terminal", "Open the owning workspace's shell", () -> minecraft.gui.setScreen(new T3TerminalScreen(mod, row())), this::online);
                action("Git & pull requests", "Workspace Git and PR controls", () -> minecraft.gui.setScreen(new T3GitScreen(mod, row())), this::online);
                action("Model options", "This machine's model and option catalog", () -> minecraft.gui.setScreen(new T3OptionsScreen(mod, row())), this::online);
                action("Chat settings", "Conversation modes and metadata", () -> minecraft.gui.setScreen(new T3SettingsScreen(mod, row())), this::online);
            }
            default -> {
                action("Reassign chat", "Choose an existing chat for this NPC", () -> minecraft.gui.setScreen(new T3AssignmentScreen(mod, cast, this)), () -> cast != null && mod.canPin());
                action(mod.pinned(row()) ? "Unpin desk" : "Pin desk", "Change this chat's saved desk pin", () -> { mod.togglePin(row()); rebuildWidgets(); }, () -> bound() && mod.canPin());
                action("Rename", "Change the resident's local name", () -> minecraft.gui.setScreen(new T3NpcNameScreen(mod, cast, this)), () -> cast != null && mod.canPin());
                action("Character look", "Change appearance without changing the linked chat", () -> minecraft.gui.setScreen(new T3NpcLookScreen(mod, cast, this)), () -> cast != null && mod.canPin());
            }
        }
        if (w < 280 && controls.size() > 3) addRenderableWidget(Button.builder(Component.literal(actionPage == 0 ? "More actions >" : "< Actions"), b -> { actionPage = 1 - actionPage; rebuildWidgets(); }).bounds(x, height - 36, 110, 20).build());
        contentBottom = height - 122;
        addRenderableWidget(Button.builder(Component.literal("Done"), b -> onClose()).bounds(x + w - 70, height - 36, 70, 20).build()); tick();
    }
    private String parentId() {
        var row = row(); if (row == null) return null; var lineage = T3Protocol.object(row.raw(), "lineage");
        return lineage != null && "subagent".equals(T3Protocol.string(lineage, "relationshipToParent")) ? T3Protocol.string(lineage, "parentThreadId") : null;
    }
    private void loadSchedules() {
        if (scheduleLoaded || !online() || System.currentTimeMillis() - scheduleAttempt < 10000) return;
        scheduleAttempt = System.currentTimeMillis(); int serial = ++scheduleSerial;
        scheduleLoaded = true; schedules = "Loading schedules…";
        var row = row(); var api = mod.state().apiFor(threadId);
        mod.state().run(() -> {
            if (api == null || api != mod.state().apiFor(threadId) || !mod.state().online(threadId)) throw new java.io.IOException("Reconnect this NPC's machine to load schedules.");
            var result = api.rpc("scheduledTasks.list", new JsonObject()); var lines = new ArrayList<String>(); int projectCount = 0;
            for (var value : T3Protocol.array(result, "tasks")) {
                if (!value.isJsonObject()) continue; var task = value.getAsJsonObject();
                if (!java.util.Objects.equals(T3Protocol.string(row.raw(), "projectId"), T3Protocol.string(task, "projectId"))) continue;
                projectCount++; if (!threadId.equals(T3Protocol.string(task, "threadId"))) continue;
                if (lines.size() < 8) lines.add(T3Protocol.string(task, "title") + " · " + (task.has("enabled") && task.get("enabled").getAsBoolean() ? "next " + T3Protocol.string(task, "nextRunAt") : "disabled"));
            }
            String text = "Project schedules: " + projectCount + "\n" + (lines.isEmpty() ? "No schedules bound to this chat." : String.join("\n", lines));
            minecraft.execute(() -> { if (!closed && serial == scheduleSerial) schedules = text; });
        }, e -> minecraft.execute(() -> { if (!closed && serial == scheduleSerial) { schedules = "Schedules unavailable: " + e.getMessage(); scheduleLoaded = false; } }));
    }
    @Override public void removed() { closed = true; scheduleSerial++; scheduleLoaded = false; scheduleAttempt = 0; }
    @Override public void tick() {
        controls.forEach(c -> c.button.active = c.enabled.getAsBoolean());
        if (group == 0 && row() != null && !controls.isEmpty()) {
            var next = mod.nextAction(row()); var button = controls.getFirst().button();
            button.setMessage(Component.literal(T3Workflow.label(next, row().status()))); button.setTooltip(Tooltip.create(Component.literal(T3Workflow.hint(next, row().status()))));
        }
        if (group == 1) loadSchedules();
    }
    @Override public boolean mouseScrolled(double mx, double my, double horizontal, double vertical) { scroll = Math.max(0, scroll - (int)(vertical * 3)); return true; }
    @Override public void extractRenderState(GuiGraphicsExtractor g, int mx, int my, float delta) {
        paper(g, x - 12, 8, x + w + 12, height - 8); UiBits.framedPortrait(g, cast, x, 17, 1);
        g.text(font, T3Hud.ellipsize(font, cast == null ? "T3 conversation" : mod.npcName(cast), w - 36), x + 30, 18, INK, false);
        var row = row(); String status = row == null ? "No chat attached" : !bound() ? "Desk assignment changed; reopen this NPC" : !mod.state().online(threadId) ? "Machine offline" : StudioProjection.label(row.status());
        g.text(font, T3Hud.ellipsize(font, status, w - 36), x + 30, 32, MUTED, false);
        String text = row == null ? "Reassign chat in Desk to attach an existing conversation." : row.projectTitle() + " · " + row.title() + "\n" + T3Workflow.hint(mod.nextAction(row), row.status()) + "\n" + StudioProjection.location(row)
            + "\nMachine: " + mod.machineLabel(row.ownerKey()) + "\nModel: " + model(row)
            + "\nActivity: " + (row.status() == T3State.Status.WORKING ? StudioProjection.workActivity(row, mod.state().studioDetail(threadId)).label() : StudioProjection.label(row.status()));
        if (group == 1 && row != null) text += "\n\n" + StudioProjection.delegationSummary(row, mod.state().studioDetail(threadId)) + "\n\n" + schedules;
        if (group == 3) text += "\n\nUnpin allows automatic staffing to choose this desk again. Reassign chooses an existing chat; it does not create one.";
        var lines = font.split(Component.literal(text), w); int visible = Math.max(1, (contentBottom - 86) / 11); scroll = Math.min(scroll, Math.max(0, lines.size() - visible));
        g.enableScissor(x, 86, x + w, Math.max(86, contentBottom));
        for (int i = scroll, y = 86; i < lines.size() && y + 9 <= contentBottom; i++, y += 11) g.text(font, lines.get(i), x, y, MUTED, false);
        g.disableScissor(); super.extractRenderState(g, mx, my, delta);
    }
    private String model(T3State.ThreadRow row) { var selection = T3Protocol.object(row.raw(), "modelSelection"); String value = selection == null ? null : T3Protocol.string(selection, "model"); return value == null ? "T3 default" : value; }
    JsonObject inspectFixture() {
        var out = new JsonObject(); out.addProperty("threadId", threadId); out.addProperty("cast", cast);
        out.addProperty("owner", row() == null ? null : row().ownerKey()); out.addProperty("bound", bound());
        out.addProperty("group", group); out.addProperty("schedules", schedules);
        if (row() != null) out.addProperty("delegation", StudioProjection.delegationSummary(row(), mod.state().studioDetail(threadId)));
        return out;
    }
}

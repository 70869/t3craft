package dev.agentcraft.client.t3;

import com.google.gson.JsonObject;
import com.mojang.blaze3d.platform.InputConstants;
import java.io.IOException;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;

/** One T3-owned shell per chat; hiding detaches without ending it. */
final class T3TerminalScreen extends T3MenuScreen {
	private static final String TERMINAL = "t3craft";
	private final T3CraftClient mod;
	private final T3State.ThreadRow row;
	private final T3Api api;
	private final String draftKey;
	private final T3TerminalText output = new T3TerminalText();
	private volatile T3Socket socket;
	private volatile String subscription;
	private volatile boolean closed;
	private volatile int generation;
	private EditBox input;
	private Button send, interrupt, complete, end, recovery;
	private String message = "Opening T3 terminal…", status = "connecting";
	private boolean sending, connecting, focusInput;
	private int scroll;
	private long renderedRevision = -1;
	private int renderedWidth;
	private List<FormattedCharSequence> renderedLines = List.of();

	T3TerminalScreen(T3CraftClient mod, T3State.ThreadRow row) {
		super(Component.literal("Terminal")); this.mod = mod; this.row = row;
		api = mod.state().apiFor(row.id()); draftKey = T3Drafts.key(row.ownerKey(), row.id(), false);
	}
	private T3Drafts.Entry draft() { return mod.draftEntry(draftKey); }
	private boolean owned() { return api != null && api == mod.state().apiFor(row.id()) && mod.state().online(row.id()); }
	private boolean running() { return !closed && owned() && !connecting && socket != null && socket.isOpen() && status.equals("running"); }
	private int columns() { return Math.max(1, Math.min(1000, (width - 40) / 6)); }
	private int rows() { return Math.max(1, Math.min(500, (height - 138) / (font.lineHeight + 2))); }
	@Override protected void init() {
		closed = false;
		input = addRenderableWidget(new EditBox(font, 20, height - 61, width - 40, 20, Component.literal("Terminal input")));
		input.setMaxLength(65535); input.setValue(draft().terminalInput);
		input.setResponder(value -> { var entry = draft(); entry.terminalInput = value; entry.terminalRevision++; mod.draftChanged(); });
		placeholder(input, "Type a command · Enter to send"); setInitialFocus(input);
		int buttonWidth = Math.min(90, (width - 64) / 5), gap = buttonWidth + 6;
		send = addRenderableWidget(Button.builder(Component.literal("Send"), b -> sendInput("\r")).bounds(20, height - 32, buttonWidth, 20).build());
		interrupt = addRenderableWidget(Button.builder(Component.literal("Ctrl+C"), b -> write("\u0003", () -> {})).bounds(20 + gap, height - 32, buttonWidth, 20).build());
		complete = addRenderableWidget(Button.builder(Component.literal("Tab"), b -> sendInput("\t")).bounds(20 + 2 * gap, height - 32, buttonWidth, 20).build());
		end = addRenderableWidget(Button.builder(Component.literal("End shell"), b -> endShell()).bounds(20 + 3 * gap, height - 32, buttonWidth, 20).build());
		end.setTooltip(Tooltip.create(Component.literal("Stops this chat's shell. Back keeps it running.")));
		addRenderableWidget(Button.builder(Component.literal("Back"), b -> onClose()).bounds(20 + 4 * gap, height - 32, buttonWidth, 20).build());
		recovery = addRenderableWidget(Button.builder(Component.literal("Reconnect"), b -> attach(status.equals("exited") || status.equals("closed"))).bounds(width - 130, 16, 110, 18).build());
		if (socket == null || !socket.isOpen()) attach(false); else resize();
		tick();
	}
	private JsonObject location() throws Exception {
		if (!owned()) throw new IOException("Reconnect the owning machine first.");
		api.requireProtocol2();
		JsonObject thread = api.thread(row.id(), 4);
		JsonObject params = T3FormScreen.values("threadId", row.id(), "terminalId", TERMINAL, "cwd", T3WorkspaceScreen.root(api, thread));
		String worktree = T3FormScreen.text(thread, "worktreePath"); if (!worktree.isBlank()) params.addProperty("worktreePath", worktree);
		var model = T3Protocol.object(thread, "modelSelection");
		String provider = model == null ? "" : T3FormScreen.text(model, "instanceId"); if (!provider.isBlank()) params.addProperty("providerInstanceId", provider);
		return params;
	}
	private void detach() {
		T3Socket current = socket; socket = null;
		if (current != null) { current.interrupt(subscription); current.close(); }
		subscription = null;
	}
	private void attach(boolean restart) {
		focusInput = true;
		int attempt = ++generation; detach(); connecting = true; message = "Connecting to this chat's checkout…";
		int cols = columns(), rows = rows();
		mod.state().run(() -> {
			T3Socket opened = null;
			try {
				JsonObject params = location(); params.addProperty("cols", cols); params.addProperty("rows", rows);
				opened = api.openSocket(reason -> Minecraft.getInstance().execute(() -> {
					if (!closed && attempt == generation) { connecting = false; status = "disconnected"; message = "Disconnected · input kept. Reconnect, then check the shell before retrying."; }
				}));
				var snapshot = opened.call(restart ? "terminal.restart" : "terminal.open", params, 20);
				if (closed || attempt != generation) { opened.close(); return; }
				T3Socket current = opened; socket = current;
				Minecraft.getInstance().execute(() -> { if (!closed && attempt == generation) showSnapshot(snapshot); });
				String attached = current.stream("terminal.attach", T3FormScreen.values("threadId", row.id(), "terminalId", TERMINAL), new T3Socket.StreamHandler() {
					@Override public void onValue(JsonObject value) { Minecraft.getInstance().execute(() -> {
						if (closed || attempt != generation) return;
						switch (T3FormScreen.text(value, "type")) {
							case "snapshot", "started", "restarted" -> showSnapshot(T3Protocol.object(value, "snapshot"));
							case "output" -> output.append(T3FormScreen.text(value, "data"));
							case "exited" -> { status = "exited"; message = "Shell exited · " + T3FormScreen.text(value, "exitCode"); }
							case "closed" -> { status = "closed"; message = "Shell closed"; }
							case "error" -> { status = "error"; message = T3FormScreen.text(value, "message"); }
							case "cleared" -> output.clear(); default -> {}
						}
					}); }
					@Override public void onEnd(JsonObject exit) { Minecraft.getInstance().execute(() -> {
						if (!closed && attempt == generation && !status.equals("closed") && !status.equals("exited")) {
							detach(); connecting = false; status = "disconnected"; message = "Stream ended · input kept. Use Reconnect.";
						}
					}); }
				});
				if (closed || attempt != generation) { current.interrupt(attached); current.close(); if (socket == current) socket = null; }
				else subscription = attached;
			} catch (Exception e) { if (opened != null) opened.close(); throw e; }
		}, e -> Minecraft.getInstance().execute(() -> {
			if (!closed && attempt == generation) { detach(); connecting = false; status = "disconnected"; message = e.getMessage(); }
		}));
	}
	private void showSnapshot(JsonObject snapshot) {
		connecting = false;
		if (snapshot == null) { status = "error"; message = "Terminal snapshot unavailable. Reconnect."; return; }
		output.clear(); output.append(T3FormScreen.text(snapshot, "history")); status = T3FormScreen.text(snapshot, "status");
		message = "T3 terminal · " + T3FormScreen.text(snapshot, "cwd") + " · " + status;
	}
	private void resize() {
		T3Socket current = socket; int attempt = generation, cols = columns(), rows = rows();
		mod.state().run(() -> { if (!closed && attempt == generation && current != null && current.isOpen() && owned()) {
			var params = T3FormScreen.values("threadId", row.id(), "terminalId", TERMINAL); params.addProperty("cols", cols); params.addProperty("rows", rows);
			current.call("terminal.resize", params, 20);
		} }, e -> {});
	}
	private void endShell() { if (!running() || sending) return; sending = true; write("", () -> { status = "closed"; message = "Shell closed"; }); }
	private void sendInput(String suffix) {
		if (sending || !running()) return;
		String data = input.getValue(); long revision = draft().terminalRevision; sending = true;
		write(data + suffix, () -> {
			var entry = draft();
			if (entry.terminalRevision == revision && entry.terminalInput.equals(data)) {
				entry.terminalInput = ""; entry.terminalRevision++; mod.draftChanged();
				if (!closed) input.setValue("");
			}
			scroll = 0;
		});
	}
	private void write(String data, Runnable accepted) {
		focusInput = true;
		T3Socket current = socket; int attempt = generation;
		mod.state().run(() -> {
			if (closed || attempt != generation || current == null || current != socket || !current.isOpen() || !owned()) throw new IOException("Reconnect the owning T3 terminal first.");
			var params = T3FormScreen.values("threadId", row.id(), "terminalId", TERMINAL); if (!data.isEmpty()) params.addProperty("data", data);
			current.call(data.isEmpty() ? "terminal.close" : "terminal.write", params, 20);
			Minecraft.getInstance().execute(() -> { accepted.run(); if (attempt == generation) sending = false; });
		}, e -> Minecraft.getInstance().execute(() -> {
			if (!closed && attempt == generation) { sending = false; status = "disconnected"; message = e.getMessage() + " · Input kept; reconnect and check the shell before retrying."; }
		}));
	}
	@Override public void tick() {
		// An acknowledged write from a previous view can settle after this view opens.
		// The shared draft is authoritative, so never leave accepted text ready to resend.
		if (!input.getValue().equals(draft().terminalInput)) input.setValue(draft().terminalInput);
		if (focusInput) { setFocused(input); input.setFocused(true); focusInput = false; }
		boolean ready = running() && !sending; send.active = interrupt.active = complete.active = end.active = ready;
		recovery.active = !closed && !connecting && !sending && !running();
		recovery.setMessage(Component.literal(status.equals("exited") || status.equals("closed") ? "Restart shell" : "Reconnect"));
	}
	@Override public boolean keyPressed(KeyEvent e) {
		if (e.hasControlDown() && e.key() == InputConstants.KEY_GRAVE) { onClose(); return true; }
		if (e.hasControlDown() && e.key() == InputConstants.KEY_C && (!input.isFocused() || input.getHighlighted().isEmpty())) { if (running()) write("\u0003", () -> {}); return true; }
		if (input.isFocused() && e.key() == InputConstants.KEY_TAB) { sendInput("\t"); return true; }
		if (input.isFocused() && input.getValue().isEmpty() && (e.key() == InputConstants.KEY_UP || e.key() == InputConstants.KEY_DOWN)) { if (running()) write(e.key() == InputConstants.KEY_UP ? "\u001b[A" : "\u001b[B", () -> {}); return true; }
		if (e.isConfirmation()) { sendInput("\r"); return true; }
		return super.keyPressed(e);
	}
	@Override public boolean mouseScrolled(double x, double y, double h, double v) { scroll = Math.max(0, scroll + (int)(v * 3)); return true; }
	@Override public void removed() { closed = true; generation++; sending = connecting = false; detach(); }
	JsonObject inspectFixture() {
		var value = new JsonObject(); value.addProperty("input", input == null ? "" : input.getValue()); value.addProperty("sending", sending);
		value.addProperty("message", message); value.addProperty("running", running()); value.addProperty("focused", input != null && input.isFocused()); value.addProperty("output", output.text()); return value;
	}
	@Override public void extractRenderState(GuiGraphicsExtractor g, int mx, int my, float delta) {
		paper(g, 8, 8, width - 8, height - 8); g.text(font, "Terminal", 20, 18, INK, false);
		g.text(font, T3Hud.ellipsize(font, row.title() + " · " + mod.machineLabel(row.ownerKey()), width - 40), 20, 35, MUTED, false);
		g.text(font, T3Hud.ellipsize(font, message, width - 40), 20, 48, MUTED, false);
		if (renderedRevision != output.revision() || renderedWidth != width) { renderedLines = font.split(Component.literal(output.text()), width - 40); renderedRevision = output.revision(); renderedWidth = width; }
		int visible = rows(); scroll = Math.min(scroll, Math.max(0, renderedLines.size() - visible));
		int start = Math.max(0, renderedLines.size() - visible - scroll), y = 66; g.enableScissor(20, 64, width - 20, height - 72);
		for (int i = start; i < renderedLines.size() && y < height - 72; i++, y += font.lineHeight + 2) g.text(font, renderedLines.get(i), 20, y, INK, false);
		g.disableScissor(); super.extractRenderState(g, mx, my, delta);
	}
}

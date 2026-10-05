package dev.agentcraft.client.t3;

import com.google.gson.JsonObject;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/** Pages the actual saved T3 timeline on demand, with no provider-directory scanning. */
final class T3HistoryScreen extends Screen {
	private final T3CraftClient mod;
	private final String threadId;
	private final List<JsonObject> items = new ArrayList<>();
	private String cursor, error;
	private boolean loading, more = true, closed;
	private int scroll, serial;
	private Button older;
	T3HistoryScreen(T3CraftClient mod, String threadId) { super(Component.literal("T3 chat history")); this.mod = mod; this.threadId = threadId; }
	@Override protected void init() {
		older = addRenderableWidget(Button.builder(Component.literal("Load older"), b -> load()).bounds(16, height - 32, 94, 20).build());
		addRenderableWidget(Button.builder(Component.literal("Back to chat"), b -> { mod.focus(threadId); mod.openPanel(); }).bounds(118, height - 32, 100, 20).build());
		if (items.isEmpty() && !loading) load();
	}
	private void load() {
		var api = mod.state().apiFor(threadId);
		if (api == null || loading || !more) return;
		loading = true; error = null; int request = ++serial;
		mod.state().run(() -> {
			if (!mod.state().online(threadId) || api != mod.state().apiFor(threadId)) throw new java.io.IOException("Reconnect this T3 machine before loading history.");
			var page = api.history(threadId, cursor);
			var fetched = new ArrayList<JsonObject>();
			var values = page.has("projection") ? T3Protocol.array(page.getAsJsonObject("projection"), "turnItems") : T3Protocol.array(page, "items");
			for (var value : values) fetched.add(value.getAsJsonObject());
			if (fetched.isEmpty() && page.has("projection")) for (var value : T3Protocol.array(page.getAsJsonObject("projection"), "messages")) {
				var message = value.getAsJsonObject().deepCopy(); message.addProperty("type", T3Protocol.string(message, "role") + "_message"); fetched.add(message);
			}
			Minecraft.getInstance().execute(() -> {
				if (closed || request != serial || api != mod.state().apiFor(threadId)) return;
				items.addAll(0, fetched); cursor = T3Protocol.string(page, page.has("projection") ? "historyCursor" : "nextCursor");
				more = page.has("hasMoreHistory") && page.get("hasMoreHistory").getAsBoolean() && cursor != null; loading = false; scroll = 0;
			});
		}, e -> Minecraft.getInstance().execute(() -> { if (!closed && request == serial) { error = e.getMessage(); loading = false; } }));
	}
	@Override public void tick() { older.active = more && !loading && mod.state().online(threadId); }
	@Override public boolean isPauseScreen() { return false; }
	@Override public boolean isInGameUi() { return true; }
	@Override public void removed() { closed = true; serial++; }
	@Override public boolean mouseScrolled(double x, double y, double h, double v) { scroll = Math.max(0, scroll - (int)(v * 3)); return true; }
	@Override public void extractRenderState(GuiGraphicsExtractor g, int mx, int my, float delta) {
		g.fill(8, 8, width - 8, height - 8, 0xF0222529);
		g.text(font, "Saved T3 history · " + items.size() + " items" + (more ? " · older history available" : " · beginning reached"), 16, 18, 0xFFFFFFFF, false);
		var lines = new ArrayList<net.minecraft.util.FormattedCharSequence>();
		for (var item : items) {
			String kind = T3Protocol.string(item, "type"), text = T3Protocol.string(item, "text");
			if (text == null) text = T3Protocol.string(item, "markdown");
			if (text == null) text = T3Protocol.string(item, "title");
			if (text != null && !text.isBlank()) lines.addAll(font.split(Component.literal((kind == null ? "" : kind + "\n") + text + "\n"), width - 40));
		}
		int visible = Math.max(1, (height - 100) / (font.lineHeight + 2)); scroll = Math.min(scroll, Math.max(0, lines.size() - visible));
		g.enableScissor(16, 42, width - 16, height - 48); int y = 44;
		for (int i = scroll; i < lines.size() && y < height - 48; i++, y += font.lineHeight + 2) g.text(font, lines.get(i), 20, y, 0xFFD4D4D4, false);
		g.disableScissor();
		if (loading || error != null) g.text(font, loading ? "Loading T3 history…" : error, 16, height - 46, 0xFFFFB02E, false);
		super.extractRenderState(g, mx, my, delta);
	}
}

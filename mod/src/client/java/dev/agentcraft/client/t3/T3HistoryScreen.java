package dev.agentcraft.client.t3;

import com.google.gson.JsonObject;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/** Pages the actual saved T3 timeline on demand, with no provider-directory scanning. */
final class T3HistoryScreen extends T3MenuScreen {
	private final T3CraftClient mod;
	private final String threadId;
	private final List<JsonObject> items = new ArrayList<>();
	private String cursor, error;
	private boolean loading, more = true, closed;
	private int scroll, serial;
	private Button older;
	private T3Api explicitApi;
	private String search = "";
	T3HistoryScreen(T3CraftClient mod, String threadId) { super(Component.literal("T3 chat history")); this.mod = mod; this.threadId = threadId; }
	T3HistoryScreen(T3CraftClient mod, String threadId, T3Api api) { this(mod, threadId); explicitApi = api; }
	@Override protected void init() {
		closed = false;
		EditBox filter = addRenderableWidget(new EditBox(font, 16, 34, width - 32, 20, Component.literal("Search loaded history")));
		placeholder(filter, "Search loaded history"); filter.setValue(search); filter.setResponder(value -> { search = value; scroll = 0; });
		older = addRenderableWidget(Button.builder(Component.literal("Load older"), b -> load()).bounds(16, height - 32, 94, 20).build());
		addRenderableWidget(Button.builder(Component.literal("Back"), b -> onClose()).bounds(118, height - 32, 100, 20).build());
		if (items.isEmpty() && !loading) load();
	}
	private void load() {
		var api = explicitApi != null ? explicitApi : mod.state().apiFor(threadId);
		if (api == null || loading || !more) return;
		loading = true; error = null; int request = ++serial;
		mod.state().run(() -> {
			if (!mod.state().onlineOwner(api.ownerKey()) || api != mod.state().apiForOwner(api.ownerKey()) || (explicitApi == null && api != mod.state().apiFor(threadId))) throw new java.io.IOException("Reconnect this T3 machine before loading history.");
			var page = api.history(threadId, cursor);
			var fetched = new ArrayList<JsonObject>();
			var values = page.has("projection") ? T3Protocol.array(page.getAsJsonObject("projection"), "turnItems") : T3Protocol.array(page, "items");
			for (var value : values) fetched.add(value.getAsJsonObject());
			if (fetched.isEmpty() && page.has("projection")) for (var value : T3Protocol.array(page.getAsJsonObject("projection"), "messages")) {
				var message = value.getAsJsonObject().deepCopy(); message.addProperty("type", T3Protocol.string(message, "role") + "_message"); fetched.add(message);
			}
			Minecraft.getInstance().execute(() -> {
				if (closed || request != serial || (explicitApi == null && api != mod.state().apiFor(threadId))) return;
				items.addAll(0, fetched); cursor = T3Protocol.string(page, page.has("projection") ? "historyCursor" : "nextCursor");
				more = page.has("hasMoreHistory") && page.get("hasMoreHistory").getAsBoolean() && cursor != null; loading = false; scroll = 0;
			});
		}, e -> Minecraft.getInstance().execute(() -> { if (!closed && request == serial) { error = e.getMessage(); loading = false; } }));
	}
	@Override public void tick() { older.active = more && !loading && (explicitApi != null || mod.state().online(threadId)); }
	@Override public boolean isPauseScreen() { return false; }
	@Override public boolean isInGameUi() { return false; }
	@Override public void removed() { closed = true; serial++; }
	@Override public boolean mouseScrolled(double x, double y, double h, double v) { scroll = Math.max(0, scroll - (int)(v * 3)); return true; }
	@Override public void extractRenderState(GuiGraphicsExtractor g, int mx, int my, float delta) {
		paper(g, 8, 8, width - 8, height - 8);
		g.text(font, "Saved T3 history · " + items.size() + " items" + (more ? " · older history available" : " · beginning reached"), 16, 18, INK, false);
		var lines = new ArrayList<net.minecraft.util.FormattedCharSequence>();
		for (var item : items) {
			String kind = T3Protocol.string(item, "type"), text = T3Protocol.string(item, "text");
			if (text == null) text = T3Protocol.string(item, "markdown");
			if (text == null) text = T3Protocol.string(item, "title");
			if (text != null && !text.isBlank() && text.toLowerCase(java.util.Locale.ROOT).contains(search.toLowerCase(java.util.Locale.ROOT))) lines.addAll(font.split(Component.literal((kind == null ? "" : kind + "\n") + text + "\n"), width - 40));
		}
		int visible = Math.max(1, (height - 120) / (font.lineHeight + 2)); scroll = Math.min(scroll, Math.max(0, lines.size() - visible));
		g.enableScissor(16, 60, width - 16, height - 48); int y = 62;
		for (int i = scroll; i < lines.size() && y < height - 48; i++, y += font.lineHeight + 2) g.text(font, lines.get(i), 20, y, INK, false);
		g.disableScissor();
		if (loading || error != null) g.text(font, loading ? "Loading T3 history…" : error, 16, height - 46, ACCENT, false);
		super.extractRenderState(g, mx, my, delta);
	}
}

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

/** Shared bounded forms for T3 workflows; requests retain their original machine/thread binding. */
abstract class T3FormScreen extends T3MenuScreen {
	protected final T3CraftClient mod;
	protected final T3State.ThreadRow row;
	protected final T3Api api;
	protected final List<Field> fields = new ArrayList<>();
	protected final List<Action> actions = new ArrayList<>();
	protected String output = "", message = "", guidance = "";
	protected boolean busy, closed;
	protected boolean requiresThread = true;
	private int contentTop, contentBottom, scroll, serial, fieldPage, firstField, lastField;
	private List<Field> shownFields = List.of();
	protected List<Field> visibleFields() { return fields; }
	protected void fieldChanged(Field field) {}
	protected int outputReserve() { return 110; }
	void refreshWidgets() { rebuildWidgets(); }
	protected record Action(String label, Runnable run) {}
	protected static final class Field {
		final String label; String value; List<String> choices;
		Field(String label, String value) { this(label, value, List.of()); }
		Field(String label, String value, List<String> choices) { this.label = label; this.value = value; this.choices = choices; }
	}
	protected interface Request { String execute() throws Exception; }
	T3FormScreen(String title, T3CraftClient mod, T3State.ThreadRow row) {
		super(Component.literal(title)); this.mod = mod; this.row = row; this.api = row == null ? mod.state().api() : mod.state().apiFor(row.id());
	}
	protected Field field(String label, String value) { Field f = new Field(label, value); fields.add(f); return f; }
	protected Field choice(String label, String value, String... choices) { Field f = new Field(label, value, List.of(choices)); fields.add(f); return f; }
	protected void action(String label, Runnable run) { actions.add(new Action(label, run)); }
	@Override protected void init() {
		closed = false; shownFields = visibleFields(); int columns = width >= 460 ? 2 : 1, fieldWidth = (width - 48) / columns;
		int rows = 1, measureX = 20;
		for (Action a : actions) { int w = Math.min(width - 40, Math.max(70, font.width(a.label) + 16)); if (measureX + w > width - 20) { measureX = 20; rows++; } measureX += w + 6; }
		int pageSize = columns * Math.max(1, (height - 34 - rows * 24 - outputReserve()) / 37);
		int pages = Math.max(1, (shownFields.size() + pageSize - 1) / pageSize); fieldPage = Math.min(fieldPage, pages - 1); firstField = fieldPage * pageSize; lastField = Math.min(shownFields.size(), firstField + pageSize);
		if (pages > 1) {
			addRenderableWidget(Button.builder(Component.literal("Fields <"), b -> { fieldPage = Math.floorMod(fieldPage - 1, pages); rebuildWidgets(); }).bounds(width - 152, 16, 64, 18).build());
			addRenderableWidget(Button.builder(Component.literal((fieldPage + 1) + "/" + pages + " >"), b -> { fieldPage = (fieldPage + 1) % pages; rebuildWidgets(); }).bounds(width - 82, 16, 62, 18).build());
		}
		for (int i = firstField; i < lastField; i++) {
			int position = i - firstField;
			Field f = shownFields.get(i); int x = 20 + (position % columns) * (fieldWidth + 8), y = 60 + (position / columns) * 37;
			if (f.choices.isEmpty()) {
				EditBox box = addRenderableWidget(new EditBox(font, x, y, fieldWidth, 20, Component.literal(f.label)));
				box.setMaxLength(120000); box.setValue(f.value == null ? "" : f.value); box.setResponder(value -> f.value = value);
			} else {
				Button button = Button.builder(Component.literal(f.value), b -> { int at = f.choices.indexOf(f.value); f.value = f.choices.get((at + 1) % f.choices.size()); b.setMessage(Component.literal(f.value)); fieldChanged(f); }).bounds(x, y, fieldWidth, 20).build(); addRenderableWidget(button);
			}
		}
		contentTop = 53 + ((lastField - firstField + columns - 1) / columns) * 37;
		int x = 20, bottom = height - 34 - rows * 24; contentBottom = bottom - 8;
		for (Action a : actions) {
			int w = Math.min(width - 40, Math.max(70, font.width(a.label) + 16));
			if (x + w > width - 20) { x = 20; bottom += 24; }
			addRenderableWidget(Button.builder(Component.literal(a.label), b -> { if (!busy) try { a.run.run(); } catch (Exception e) { message = e.getMessage(); } }).bounds(x, bottom, w, 20).build()); x += w + 6;
		}
		addRenderableWidget(Button.builder(Component.literal("Connections"), b -> mod.openConnections()).bounds(20, height - 30, 94, 20).build());
		addRenderableWidget(Button.builder(Component.literal("Back"), b -> back()).bounds(width - 92, height - 30, 72, 20).build());
	}
	protected void back() { onClose(); }
	protected void request(Request request) { request(request, () -> {}); }
	protected void request(Request request, Runnable done) {
		if (busy) return; busy = true; message = "Working in T3…"; int id = ++serial;
		mod.state().run(() -> {
			if (api == null || !mod.state().onlineOwner(api.ownerKey()) || api != mod.state().apiForOwner(api.ownerKey()) || (row != null && requiresThread && api != mod.state().apiFor(row.id()))) throw new java.io.IOException("Reconnect the owning T3 machine first.");
			String result = request.execute();
			Minecraft.getInstance().execute(() -> { if (id != serial) return; output = result == null ? "" : result; message = "Updated from T3"; busy = false; if (!closed) done.run(); });
		}, e -> Minecraft.getInstance().execute(() -> { if (id != serial) return; message = e.getMessage() == null ? "Action failed. Check Connections, then retry the action." : e.getMessage(); busy = false; }));
	}
	protected static JsonObject values(String... pairs) { JsonObject value = new JsonObject(); for (int i = 0; i < pairs.length; i += 2) value.addProperty(pairs[i], pairs[i + 1]); return value; }
	protected static String text(JsonObject value, String key) { String result = T3Protocol.string(value, key); return result == null ? "" : result; }
	protected static String require(String value, String label) throws java.io.IOException { if (value == null || value.isBlank()) throw new java.io.IOException("Enter " + label + "."); return value.strip(); }
	@Override public void removed() { closed = true; }
	@Override public boolean isPauseScreen() { return false; }
	@Override public boolean isInGameUi() { return false; }
	@Override public boolean mouseScrolled(double x, double y, double h, double v) { scroll = Math.max(0, scroll - (int)(v * 3)); return true; }
	@Override public void extractRenderState(GuiGraphicsExtractor g, int mx, int my, float delta) {
		paper(g, 8, 8, width - 8, height - 8); g.text(font, title, 20, 18, INK, false);
		g.text(font, T3Hud.ellipsize(font, message.isEmpty() ? guidance : message, width - 40), 20, 33, MUTED, false);
		int columns = width >= 460 ? 2 : 1, fieldWidth = (width - 48) / columns;
		for (int i = firstField; i < lastField; i++) { int p = i - firstField; g.text(font, T3Hud.ellipsize(font, shownFields.get(i).label, fieldWidth), 20 + (p % columns) * (fieldWidth + 8), 49 + (p / columns) * 37, MUTED, false); }
		var lines = font.split(Component.literal(output), width - 40);
		int visible = Math.max(1, (contentBottom - contentTop) / (font.lineHeight + 2)); scroll = Math.min(scroll, Math.max(0, lines.size() - visible));
		g.enableScissor(20, contentTop, width - 20, Math.max(contentTop, contentBottom)); int y = contentTop;
		for (int i = scroll; i < lines.size() && y < contentBottom; i++, y += font.lineHeight + 2) g.text(font, lines.get(i), 20, y, INK, false);
		g.disableScissor(); super.extractRenderState(g, mx, my, delta);
	}
}

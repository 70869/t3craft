// Adapted from maxwellyoung/t3craft (MIT), copyright 2026 Maxwell Young.
// See THIRD-PARTY-NOTICES.md and licenses/t3craft-client-MIT.txt.
package dev.agentcraft.client.t3;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.network.chat.Component;
import java.util.List;

/** The focused owner's recent backend activity, including cached activity when offline. */
final class T3ActivityScreen extends T3MenuScreen {
	private final T3CraftClient mod;
	private final String threadId;
	private final String threadTitle;
	private T3State.Focus cached;
	private T3State.Focus rendered;
	private int wrappedWidth;
	private record Line(net.minecraft.util.FormattedCharSequence text, int color) {}
	private List<Line> lines = List.of();
	private int scroll;
	T3ActivityScreen(T3CraftClient mod, String threadId) {
		super(Component.literal("Activity")); this.mod = mod; this.threadId = threadId;
		var row = mod.state().snapshot().focusedRow();
		threadTitle = row != null && threadId.equals(row.id()) ? row.title() : "Thread";
	}
	@Override protected void init() {
		mod.state().setPanelOpen(true);
		addRenderableWidget(Button.builder(Component.literal("Back to chat"), b -> { mod.focus(threadId); mod.openPanel(); })
			.bounds(16, height - 32, 98, 20).build());
	}
	@Override public boolean isPauseScreen() { return false; }
	@Override public boolean isInGameUi() { return false; }
	@Override public void removed() { mod.state().setPanelOpen(false); }
	@Override public boolean mouseScrolled(double x, double y, double h, double v) { scroll = Math.max(0, scroll - (int)(v * 3)); return true; }
	@Override public boolean keyPressed(KeyEvent event) {
		if (event.key() == InputConstants.KEY_DOWN) { scroll += 3; return true; }
		if (event.key() == InputConstants.KEY_UP) { scroll = Math.max(0, scroll - 3); return true; }
		if (event.key() == InputConstants.KEY_HOME) { scroll = 0; return true; }
		return super.keyPressed(event);
	}
	List<T3Activity.Entry> entriesForTest() { return cached == null ? List.of() : cached.activity(); }
	@Override public void extractRenderState(GuiGraphicsExtractor g, int mx, int my, float delta) {
		var snapshot = mod.state().snapshot();
		if (snapshot.focus() != null && threadId.equals(snapshot.focus().threadId())) cached = snapshot.focus();
		paper(g, 8, 8, width - 8, height - 8);
		g.text(font, T3Hud.ellipsize(font, "Activity · " + threadTitle, width - 32), 16, 17, INK, false);
		String state = !mod.state().online(threadId) ? "OFFLINE · cached events" : "Connected · recent events";
		g.text(font, state + " · newest first", 16, 34, MUTED, false);
		if (rendered != cached || wrappedWidth != width) {
			List<Line> content = new java.util.ArrayList<>();
			if (cached == null) append(content, "Loading activity…", MUTED);
			else if (cached.activity().isEmpty()) append(content, "No activity in this recent window. Older events may be outside it.", MUTED);
			else for (var event : cached.activity().reversed()) {
				append(content, event.summary() + (event.status() == null ? "" : " · " + event.status()), ACCENT);
				if (event.at() != null) append(content, event.at(), MUTED);
				if (event.detail() != null) append(content, event.detail(), INK);
				append(content, " ", INK);
			}
			lines = List.copyOf(content); rendered = cached; wrappedWidth = width;
		}
		scroll = Math.min(scroll, Math.max(0, lines.size() - Math.max(1, (height - 96) / (font.lineHeight + 2))));
		g.enableScissor(16, 54, width - 16, height - 42);
		int y = 56;
		for (int i = scroll; i < lines.size() && y < height - 42; i++, y += font.lineHeight + 2) g.text(font, lines.get(i).text(), 20, y, lines.get(i).color(), false);
		g.disableScissor();
		super.extractRenderState(g, mx, my, delta);
	}
	private void append(List<Line> content, String text, int color) {
		for (var line : font.split(Component.literal(text), width - 40)) content.add(new Line(line, color));
	}

}

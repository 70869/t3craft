package dev.agentcraft.client.t3;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/** Human-triggered settings always go to the owning T3 thread. */
final class T3SettingsScreen extends Screen {
	private final T3CraftClient mod;
	private final T3State.ThreadRow row;
	private String message = "";
	private boolean busy, closed;
	T3SettingsScreen(T3CraftClient mod, T3State.ThreadRow row) { super(Component.literal("T3 chat settings")); this.mod = mod; this.row = row; }
	@Override protected void init() {
		var title = addRenderableWidget(new EditBox(font, 20, 45, width - 126, 20, Component.literal("Chat title"))); title.setMaxLength(512); title.setValue(row.title());
		addRenderableWidget(Button.builder(Component.literal("Rename"), b -> set("title", title.getValue())).bounds(width - 98, 45, 78, 20).build());
		int x = 20;
		for (String mode : new String[]{"default", "plan"}) { final String chosen = mode; addRenderableWidget(Button.builder(Component.literal(mode.equals("default") ? "Chat mode" : "Plan mode"), b -> set("interactionMode", chosen)).bounds(x, 82, 96, 20).build()); x += 104; }
		x = 20;
		for (String mode : new String[]{"approval-required", "auto-accept-edits", "full-access"}) { final String chosen = mode; addRenderableWidget(Button.builder(Component.literal(mode.equals("approval-required") ? "Ask permission" : mode.equals("auto-accept-edits") ? "Allow edits" : "Full access"), b -> set("runtimeMode", chosen)).bounds(x, 116, 102, 20).build()); x += 110; }
		addRenderableWidget(Button.builder(Component.literal("Back to chat"), b -> mod.openPanel()).bounds(20, height - 34, 110, 20).build());
	}
	private void set(String field, String value) {
		if (busy || value.isBlank()) return; var api = mod.state().apiFor(row.id()); if (api == null) return;
		busy = true; message = "Saving in T3…";
		mod.state().run(() -> {
			if (!mod.state().online(row.id()) || api != mod.state().apiFor(row.id())) throw new java.io.IOException("Reconnect this machine before changing settings.");
			api.configure(row.id(), field, value);
			Minecraft.getInstance().execute(() -> { if (!closed) { busy = false; message = "Saved in T3 Code"; } });
		}, e -> Minecraft.getInstance().execute(() -> { if (!closed) { busy = false; message = e.getMessage(); } }));
	}
	@Override public boolean isPauseScreen() { return false; }
	@Override public boolean isInGameUi() { return true; }
	@Override public void removed() { closed = true; }
	@Override public void extractRenderState(GuiGraphicsExtractor g, int mx, int my, float delta) {
		g.fill(8, 8, width - 8, height - 8, 0xF0222529); g.text(font, "T3 chat settings", 20, 20, 0xFFFFFFFF, false);
		g.text(font, "Current: " + T3Protocol.string(row.raw(), "interactionMode") + " · " + T3Protocol.string(row.raw(), "runtimeMode"), 20, 153, 0xFFA8A8A8, false);
		g.text(font, message, 20, height - 54, 0xFFD4D4D4, false); super.extractRenderState(g, mx, my, delta);
	}
}

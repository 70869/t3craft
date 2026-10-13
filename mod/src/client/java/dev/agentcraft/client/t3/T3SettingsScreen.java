package dev.agentcraft.client.t3;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.network.chat.Component;

/** Human-triggered settings always go to the owning T3 thread. */
final class T3SettingsScreen extends T3MenuScreen {
	private final T3CraftClient mod;
	private final T3State.ThreadRow row;
	private String message = "";
	private String editedTitle;
	private String interactionMode;
	private String runtimeMode;
	private boolean busy, closed;

	T3SettingsScreen(T3CraftClient mod, T3State.ThreadRow row) {
		super(Component.literal("T3 chat settings"));
		this.mod = mod;
		this.row = row;
		editedTitle = row.title();
		interactionMode = valueOr(T3Protocol.string(row.raw(), "interactionMode"), "default");
		runtimeMode = valueOr(T3Protocol.string(row.raw(), "runtimeMode"), "approval-required");
	}

	private static String valueOr(String value, String fallback) { return value == null || value.isBlank() ? fallback : value; }

	@Override protected void init() {
		closed = false;
		int left = Math.min(20, Math.max(8, (width - 300) / 2));
		int usable = width - left * 2;
		int renameWidth = Math.min(78, Math.max(58, usable / 4));
		var title = addRenderableWidget(new EditBox(font, left, 45, usable - renameWidth - 8, 20, Component.literal("Chat title")));
		title.setMaxLength(512);
		title.setValue(editedTitle); title.setResponder(value -> editedTitle = value);
		addRenderableWidget(Button.builder(Component.literal("Rename"), b -> set("title", title.getValue()))
			.bounds(width - left - renameWidth, 45, renameWidth, 20).build());

		int gap = 6;
		int modeWidth = (usable - gap) / 2;
		String[] modeIds = {"default", "plan"};
		String[] modeLabels = {"Default chat mode", "Plan mode"};
		for (int i = 0; i < modeIds.length; i++) {
			String chosen = modeIds[i];
			Button button = addRenderableWidget(Button.builder(Component.literal((interactionMode.equals(chosen) ? "✓ " : "") + modeLabels[i]), b -> set("interactionMode", chosen))
				.bounds(left + i * (modeWidth + gap), 82, modeWidth, 20).build());
			button.active = !busy && !interactionMode.equals(chosen);
		}

		int runtimeWidth = (usable - 2 * gap) / 3;
		String[] runtimeIds = {"approval-required", "auto-accept-edits", "full-access"};
		String[] runtimeLabels = {"Ask permission", "Allow edits", "Full access"};
		for (int i = 0; i < runtimeIds.length; i++) {
			String chosen = runtimeIds[i];
			Button button = addRenderableWidget(Button.builder(Component.literal((runtimeMode.equals(chosen) ? "✓ " : "") + runtimeLabels[i]), b -> set("runtimeMode", chosen))
				.bounds(left + i * (runtimeWidth + gap), 116, runtimeWidth, 20).build());
			button.active = !busy && !runtimeMode.equals(chosen);
		}

		int footerWidth = (usable - 2 * gap) / 3;
		addRenderableWidget(Button.builder(Component.literal("Back"), b -> onClose())
			.bounds(left, height - 34, footerWidth, 20).build());
		addRenderableWidget(Button.builder(Component.literal("Archive chat"), b -> operate(false))
			.bounds(left + footerWidth + gap, height - 34, footerWidth, 20).build());
		addRenderableWidget(Button.builder(Component.literal("Fork chat"), b -> operate(true))
			.bounds(left + 2 * (footerWidth + gap), height - 34, footerWidth, 20).build());
	}

	private void operate(boolean fork) {
		if (busy) return;
		var api = mod.state().apiFor(row.id());
		if (api == null) { message = "Reconnect this machine first."; return; }
		busy = true;
		mod.state().run(() -> {
			if (!mod.state().online(row.id()) || api != mod.state().apiFor(row.id())) throw new java.io.IOException("Reconnect this machine first.");
			String id = fork ? api.fork(row.id()) : null;
			if (!fork) api.operate("thread.archive", row.id(), new com.google.gson.JsonObject());
			Minecraft.getInstance().execute(() -> { busy = false; if (closed) return; if (id != null) mod.focus(id); mod.openPanel(); });
		}, e -> Minecraft.getInstance().execute(() -> { busy = false; if (!closed) message = e.getMessage(); }));
	}

	private void set(String field, String value) {
		if (busy || value.isBlank()) return;
		var api = mod.state().apiFor(row.id());
		if (api == null) { message = "Reconnect this machine before changing settings."; return; }
		busy = true;
		message = "Saving in T3...";
		rebuildWidgets();
		mod.state().run(() -> {
			if (!mod.state().online(row.id()) || api != mod.state().apiFor(row.id())) throw new java.io.IOException("Reconnect this machine before changing settings.");
			api.configure(row.id(), field, value);
			Minecraft.getInstance().execute(() -> {
				busy = false;
				if (field.equals("interactionMode")) interactionMode = value;
				if (field.equals("runtimeMode")) runtimeMode = value;
				message = "Saved in T3 Code";
				if (!closed) rebuildWidgets();
			});
		}, e -> Minecraft.getInstance().execute(() -> { busy = false; message = e.getMessage(); if (!closed) rebuildWidgets(); }));
	}

	@Override public boolean isPauseScreen() { return false; }
	@Override public boolean isInGameUi() { return false; }
	@Override public void removed() { closed = true; }
	@Override public void extractRenderState(GuiGraphicsExtractor g, int mx, int my, float delta) {
		int left = Math.min(20, Math.max(8, (width - 300) / 2));
		paper(g, 8, 8, width - 8, height - 8);
		g.text(font, "T3 chat settings", left, 20, INK, false);
		g.text(font, "Chat mode: " + interactionMode + "   ·   Access: " + runtimeMode, left, 153, MUTED, false);
		g.text(font, message, left, height - 54, INK, false);
		super.extractRenderState(g, mx, my, delta);
	}
}

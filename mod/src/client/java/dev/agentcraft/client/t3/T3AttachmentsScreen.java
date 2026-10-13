package dev.agentcraft.client.t3;

import java.nio.file.Files;
import java.nio.file.Path;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/** Local attachment staging; upload happens only on Send, to that draft's owning machine. */
final class T3AttachmentsScreen extends T3MenuScreen {
	private final T3CraftClient mod;
	private final Screen parent;
	private final String key;
	private EditBox path;
	private String message = "Paste a file path, or press F2 in the world and attach the latest screenshot.";
	T3AttachmentsScreen(T3CraftClient mod, Screen parent, String key) { super(Component.literal("Attachments")); this.mod = mod; this.parent = parent; this.key = key; }
	@Override protected void init() {
		path = addRenderableWidget(new EditBox(font, 20, 65, width - 140, 20, Component.literal("Local file path"))); path.setMaxLength(4096);
		addRenderableWidget(Button.builder(Component.literal("Add file"), b -> add(path.getValue())).bounds(width - 112, 65, 92, 20).build());
		addRenderableWidget(Button.builder(Component.literal("Latest screenshot"), b -> {
			try (var files = Files.list(minecraft.gameDirectory.toPath().resolve("screenshots"))) {
				var latest = files.filter(Files::isRegularFile).filter(p -> p.getFileName().toString().endsWith(".png"))
					.max(java.util.Comparator.comparingLong(p -> { try { return Files.getLastModifiedTime(p).toMillis(); } catch (Exception e) { return 0L; } }));
				if (latest.isPresent()) add(latest.get().toString()); else message = "No screenshots yet. Press F2 in the world first.";
			} catch (Exception e) { message = "No screenshot folder yet. Press F2 in the world first."; }
		}).bounds(20, 94, 140, 20).build());
		addRenderableWidget(Button.builder(Component.literal("Clear attachments"), b -> { var entry = mod.draftEntry(key); if (!entry.sending) { entry.files.clear(); entry.prepared = null; mod.draftChanged(); message = "Attachments cleared."; } }).bounds(168, 94, 130, 20).build());
		addRenderableWidget(Button.builder(Component.literal("Back to draft"), b -> minecraft.gui.setScreen(parent)).bounds(20, height - 34, 120, 20).build());
	}
	private void add(String value) {
		var entry = mod.draftEntry(key); if (entry.sending) { message = "Wait for this send to finish."; return; }
		try {
			Path file = Path.of(value.strip().replaceAll("^\"|\"$", "")).toAbsolutePath().normalize();
			if (!Files.isRegularFile(file)) throw new IllegalArgumentException("Choose an existing regular file.");
			if (entry.files.size() >= 8) throw new IllegalArgumentException("Attach up to eight files per message.");
			if (!entry.files.contains(file.toString())) entry.files.add(file.toString());
			entry.prepared = null; mod.draftChanged(); mod.flushDrafts(); message = "Attached " + file.getFileName(); path.setValue("");
		} catch (Exception e) { message = e.getMessage(); }
	}
	@Override public boolean isPauseScreen() { return false; }
	@Override public boolean isInGameUi() { return false; }
	@Override public void extractRenderState(GuiGraphicsExtractor g, int mx, int my, float delta) {
		paper(g, 8, 8, width - 8, height - 8); g.text(font, "Attachments", 20, 20, INK, false);
		g.text(font, T3Hud.ellipsize(font, message, width - 40), 20, 43, MUTED, false);
		int y = 129; for (String file : mod.draftEntry(key).files) { g.text(font, T3Hud.ellipsize(font, Path.of(file).getFileName().toString(), width - 40), 20, y, INK, false); y += 18; }
		super.extractRenderState(g, mx, my, delta);
	}
}

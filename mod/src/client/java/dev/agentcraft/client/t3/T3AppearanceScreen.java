package dev.agentcraft.client.t3;

import dev.agentcraft.client.ui.T3Theme;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/** Palette selection and a validated local accent override; never writes T3 server settings. */
final class T3AppearanceScreen extends T3MenuScreen {
	private final T3CraftClient mod;
	private final Screen parent;
	private int page;
	private EditBox accent;
	private Button save;
	private String draft, notice = "Matching T3 Code palettes for screens and studio displays.";
	T3AppearanceScreen(T3CraftClient mod, Screen parent) { super(Component.literal("Appearance")); this.mod = mod; this.parent = parent; draft = mod.accentColor(); }
	private int left() { return Math.max(20, (width - 520) / 2); }
	private int columns() { return width >= 520 ? 2 : 1; }
	private int perPage() { return columns() * Math.max(1, (height - 226) / 40); }
	@Override protected void init() {
		int x = left(), w = width - 2 * x, cols = columns(), cw = (w - (cols - 1) * 8) / cols;
		addRenderableWidget(Button.builder(Component.literal("Mode · " + (mod.darkTheme() ? "Dark" : "Light")), b -> {
			if (mod.saveAppearance(mod.themeId(), !mod.darkTheme(), mod.accentColor())) notice = "Appearance saved."; else notice = "Couldn't save appearance."; rebuildWidgets();
		}).bounds(x, 56, w, 20).build());
		var themes = T3Theme.presets(); int count = perPage(); page = Math.min(page, (themes.size() - 1) / count);
		for (int i = page * count; i < Math.min(themes.size(), (page + 1) * count); i++) {
			var preset = themes.get(i); int n = i - page * count;
			menuAction((preset.id().equals(mod.themeId()) ? "• " : "") + preset.label(), mod.darkTheme() ? "Dark palette" : "Light palette", x + (n % cols) * (cw + 8), 86 + (n / cols) * 40, cw, b -> {
				if (mod.saveAppearance(preset.id(), mod.darkTheme(), mod.accentColor())) notice = preset.label() + " saved."; else notice = "Couldn't save appearance."; rebuildWidgets();
			});
		}
		Button prev = addRenderableWidget(Button.builder(Component.literal("Previous"), b -> { page--; rebuildWidgets(); }).bounds(x, height - 120, 72, 20).build()); prev.active = page > 0;
		Button next = addRenderableWidget(Button.builder(Component.literal("Next"), b -> { page++; rebuildWidgets(); }).bounds(x + 80, height - 120, 60, 20).build()); next.active = (page + 1) * count < themes.size();
		accent = addRenderableWidget(new EditBox(font, x, height - 80, w - 82, 20, Component.literal("Accent hex color")));
		accent.setMaxLength(7); accent.setValue(draft); placeholder(accent, "Theme default, or #RRGGBB");
		save = addRenderableWidget(Button.builder(Component.literal("Save accent"), b -> saveAccent()).bounds(x + w - 76, height - 80, 76, 20).build());
		accent.setResponder(value -> { draft = value; save.active = T3Theme.validAccent(value); }); save.active = T3Theme.validAccent(draft);
		String[] labels = {"Blue", "Violet", "Rose", "Teal", "Gold", "Default"}; String[] values = {"#346BF1", "#8B5CF6", "#DB2777", "#0D9488", "#CA8A04", ""}; int sw = (w - 25) / labels.length;
		for (int i = 0; i < labels.length; i++) { String value = values[i]; addRenderableWidget(Button.builder(Component.literal(labels[i]), b -> { accent.setValue(value); saveAccent(); }).bounds(x + i * (sw + 5), height - 54, sw, 20).build()); }
		addRenderableWidget(Button.builder(Component.literal("Done"), b -> onClose()).bounds(x, height - 28, w, 20).build());
	}
	private void saveAccent() {
		if (!save.active) return;
		notice = mod.saveAppearance(mod.themeId(), mod.darkTheme(), draft) ? "Accent saved across screens and displays." : "Couldn't save accent. Check the config folder.";
	}
	@Override public void onClose() { minecraft.gui.setScreen(parent); }
	@Override public void extractRenderState(GuiGraphicsExtractor g, int mx, int my, float delta) {
		int x = left(); paper(g, x - 12, 10, width - x + 12, height - 8);
		g.text(font, "Appearance · " + T3Theme.label(mod.themeId()), x, 22, INK, false);
		g.text(font, T3Hud.ellipsize(font, notice, width - 2*x), x, 40, MUTED, false);
		g.text(font, T3Theme.validAccent(draft) ? "Accent color · #RRGGBB" : "Use a six-digit hex color, e.g. #346BF1", x, height - 96, T3Theme.validAccent(draft) ? MUTED : ERROR, false);
		super.extractRenderState(g, mx, my, delta);
	}
}

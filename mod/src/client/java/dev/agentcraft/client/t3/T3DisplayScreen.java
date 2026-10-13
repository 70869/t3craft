package dev.agentcraft.client.t3;

import dev.agentcraft.DisplayViews;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/** Local, per-world presentation controls. Selecting a view never starts T3 work. */
final class T3DisplayScreen extends T3MenuScreen {
	private final T3CraftClient mod;
	private final Screen parent;
	private final String key, binding;
	private String notice = "Choose what this display shows. Graphs use current T3 counts.";
	T3DisplayScreen(T3CraftClient mod, Screen parent, String key, String binding) {
		super(Component.literal("Display options")); this.mod = mod; this.parent = parent; this.key = key; this.binding = binding;
	}
	@Override protected void init() {
		int x = Math.max(20, (width - 480) / 2), w = width - 2*x;
		int cols = width >= 420 ? 2 : 1, cw = (w - 8*(cols-1)) / cols;
		for (int i = 0; i < DisplayViews.IDS.size(); i++) {
			String view = DisplayViews.IDS.get(i);
			Button button = menuAction((mod.displayView(key).equals(view) ? "• " : "") + DisplayViews.label(view), view.equals("default") ? "Restore the display's usual content" : view.equals("status") ? "Live chat counts by state" : view.equals("usage") ? "Native usage & quota information" : "Read your paired T3 workspace", x + i%cols*(cw+8), 62 + i/cols*40, cw, b -> {
				notice = mod.setDisplayView(key, view) ? "Display saved for this world." : "Couldn't save the display."; rebuildWidgets();
			}); button.active = mod.canPin();
		}
		addRenderableWidget(Button.builder(Component.literal("Open chat / board"), b -> { if (binding.isEmpty() || binding.equals("feed") || binding.equals("usage")) mod.openBoard(); else mod.openAgent(binding); }).bounds(x, height-58, w, 20).build());
		addRenderableWidget(Button.builder(Component.literal("Done"), b -> onClose()).bounds(x, height-30, w, 20).build());
	}
	@Override public void onClose() { minecraft.gui.setScreen(parent); }
	@Override public void extractRenderState(GuiGraphicsExtractor g, int mx, int my, float delta) {
		int x = Math.max(20, (width-480)/2); paper(g, x-12, 10, width-x+12, height-8);
		g.text(font, key.equals("atrium") ? "Atrium display" : "Monitor display", x, 22, INK, false);
		g.text(font, T3Hud.ellipsize(font, notice, width-2*x), x, 40, MUTED, false);
		super.extractRenderState(g, mx, my, delta);
	}
}

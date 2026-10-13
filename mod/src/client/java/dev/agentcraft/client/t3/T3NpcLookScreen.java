package dev.agentcraft.client.t3;

import dev.agentcraft.Cast;
import dev.agentcraft.NpcLooks;
import dev.agentcraft.client.hud.UiBits;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/** Explicit character selection, with the same portrait and arm model used in the world. */
final class T3NpcLookScreen extends T3MenuScreen {
	private final T3CraftClient mod;
	private final Screen parent;
	private final String cast;
	private int page;
	private String notice = "Choose a look. Your name, desk and T3 chat stay saved.";
	T3NpcLookScreen(T3CraftClient mod, String cast, Screen parent) { super(Component.literal("Character look")); this.mod = mod; this.cast = cast; this.parent = parent; }
	private int left() { return Math.max(20, (width - 500) / 2); }
	private int cols() { return width >= 520 ? 2 : 1; }
	private int count() { return cols() * Math.max(1, (height - 130) / 40); }
	@Override protected void init() {
		int x = left(), w = width - 2*x, cw = (w - (cols() - 1)*8) / cols(), count = count();
		page = Math.min(page, (NpcLooks.IDS.size() - 1) / count);
		for (int i = page*count; i < Math.min(NpcLooks.IDS.size(), (page+1)*count); i++) {
			String look = NpcLooks.IDS.get(i); int n = i-page*count;
			String label = look.equals("default") ? Cast.get(cast).name() + " · Original" : NpcLooks.label(look);
			Button button = menuAction((mod.npcSkin(cast).equals(look) ? "• " : "") + label, mod.npcSkin(cast).equals(look) ? "Selected" : "Apply this character", x + n%cols()*(cw+8) + 30, 62+n/cols()*40, cw-30, b -> {
				notice = mod.setNpcSkin(cast, look) ? "Character saved." : "Couldn't save look. Check the config folder."; rebuildWidgets();
			}); button.active = mod.canPin();
		}
		Button previous = addRenderableWidget(Button.builder(Component.literal("Previous"), b -> { page--; rebuildWidgets(); }).bounds(x, height-60, 72, 20).build()); previous.active = page > 0;
		Button next = addRenderableWidget(Button.builder(Component.literal("Next"), b -> { page++; rebuildWidgets(); }).bounds(x+80, height-60, 60, 20).build()); next.active = (page+1)*count<NpcLooks.IDS.size();
		addRenderableWidget(Button.builder(Component.literal("Done"), b -> onClose()).bounds(x, height-32, w, 20).build());
	}
	@Override public void onClose() { minecraft.gui.setScreen(parent); }
	@Override public void extractRenderState(GuiGraphicsExtractor g, int mx, int my, float delta) {
		int x = left(), cw = (width-2*x-(cols()-1)*8)/cols(); paper(g, x-12, 10, width-x+12, height-8);
		g.text(font, "Character · " + mod.npcName(cast), x, 22, INK, false);
		g.text(font, T3Hud.ellipsize(font, notice, width-2*x), x, 40, MUTED, false);
		for (int i=page*count(); i<Math.min(NpcLooks.IDS.size(), (page+1)*count()); i++) {
			String look=NpcLooks.IDS.get(i); int n=i-page*count();
			String skin=look.equals("default") ? cast : look;
			// Use the original portrait directly for Original, even when this desk wears a community skin.
			g.blit(net.minecraft.client.renderer.RenderPipelines.GUI_TEXTURED, dev.agentcraft.AgentCraft.id("textures/gui/portrait/"+skin+"_framed.png"), x+n%cols()*(cw+8)+2, 69+n/cols()*40, 0, 0, 20, 20, 20, 20, 20, 20);
		}
		super.extractRenderState(g, mx, my, delta);
	}
}

package dev.agentcraft.client.t3;

import dev.agentcraft.client.hud.UiBits;
import dev.agentcraft.client.ui.Kit;
import dev.agentcraft.client.ui.Panels;
import dev.agentcraft.client.ui.TextUtil;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Renderable;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.narration.NarratableEntry;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/** One paper-and-ink presentation; native widgets retain their input, focus and narration. */
abstract class T3MenuScreen extends Screen {
	private Screen previous = this instanceof T3Screen ? null : net.minecraft.client.Minecraft.getInstance().gui.screen();
	@Override public void onClose() { minecraft.gui.setScreen(previous instanceof T3MenuScreen ? previous : null); }
	private final java.util.Map<Button, String> descriptions = new java.util.IdentityHashMap<>();
	private final java.util.Map<EditBox, String> placeholders = new java.util.IdentityHashMap<>();
	static int INK = 0xFF29251F, MUTED = 0xFF71685D, ACCENT = 0xFF9B4936;
	static int GOOD = 0xFF455D47, ERROR = 0xFF9A2F2B, SELECTED = 0xFFE3D8C5, WELL = 0xFFEAE3D6;
	private static boolean dark;
	T3MenuScreen(Component title) { super(title); applyTheme(T3CraftClient.get() != null && T3CraftClient.get().darkTheme()); }
	static boolean darkTheme() { return dark; }
	static void applyTheme(boolean enabled) {
		dark = enabled;
		var client = T3CraftClient.get();
		dev.agentcraft.client.ui.T3Theme.apply(client == null ? "t3-code" : client.themeId(), enabled, client == null ? "" : client.accentColor());
		INK = dev.agentcraft.client.ui.T3Theme.color("text"); MUTED = dev.agentcraft.client.ui.T3Theme.color("textMuted");
		ACCENT = dev.agentcraft.client.ui.T3Theme.color("accentText"); GOOD = dev.agentcraft.client.ui.T3Theme.color("goodText");
		ERROR = dev.agentcraft.client.ui.T3Theme.color("errorText"); SELECTED = dev.agentcraft.client.ui.T3Theme.color("sidebarRowSelected");
		WELL = dev.agentcraft.client.ui.T3Theme.color("codeBackground");
		T3Markdown.TEXT = INK; T3Markdown.HEADING = INK; T3Markdown.MUTED = MUTED; T3Markdown.CODE = ACCENT;
		T3Markdown.LINK = ACCENT;
	}
	@Override public boolean isPauseScreen() { return false; }
	@Override public boolean isInGameUi() { return false; }
	static void paper(GuiGraphicsExtractor g, int x0, int y0, int x1, int y1) {
		Panels.panel(g, x0, y0, x1 - x0, y1 - y0);
	}
    protected void navigation(T3CraftClient mod, int x, int y, int w, String selected) {
        String[] labels = {"Chat", "Your work", "Workspace", "Campus"};
        Runnable[] actions = {mod::openPanel, mod::openWork, mod::openTools, mod::openCampus};
        int bw = (w - 18) / 4;
        for (int i = 0; i < labels.length; i++) {
            int index = i;
            var button = addRenderableWidget(Button.builder(Component.literal(labels[i]), b -> { actions[index].run(); if (minecraft.gui.screen() instanceof T3MenuScreen next && !(next instanceof T3Screen)) next.previous = previous; }).bounds(x + i * (bw + 6), y, bw, 20).build());
            button.active = !labels[i].equals(selected);
            button.setTooltip(net.minecraft.client.gui.components.Tooltip.create(Component.literal(labels[i].equals(selected) ? "You are here" : "Open " + labels[i].toLowerCase(java.util.Locale.ROOT))));
        }
    }
	protected Button menuAction(String label, String description, int x, int y, int w, java.util.function.Consumer<Button> action) {
		Button button = Button.builder(Component.literal(label), action::accept).bounds(x, y, w, 34).build();
		descriptions.put(button, description); return addRenderableWidget(button);
	}
	protected void placeholder(EditBox box, String text) {
		placeholders.put(box, text); box.setHint(Component.empty());
	}
	@Override protected void clearWidgets() { descriptions.clear(); placeholders.clear(); super.clearWidgets(); }
	@Override protected <T extends GuiEventListener & Renderable & NarratableEntry> T addRenderableWidget(T widget) {
		if (widget instanceof Button button) {
			super.addWidget(widget);
			super.addRenderableOnly((g, mx, my, delta) -> {
				if (!button.visible) return;
				boolean hover = button.isMouseOver(mx, my) || button.isFocused();
				boolean primary = primary(button.getMessage().getString());
				dev.agentcraft.client.ui.T3ThemeWidgets.button(g, button.getX(), button.getY(), button.getWidth(), button.getHeight(), primary, hover, !button.active);
				String label = TextUtil.ellipsize(font, button.getMessage().getString(), button.getWidth() - 12);
				String description = descriptions.get(button);
				g.text(font, label, description == null ? button.getX() + (button.getWidth() - font.width(label)) / 2 : button.getX() + 10, description == null ? button.getY() + (button.getHeight() - font.lineHeight) / 2 : button.getY() + 6,
					!button.active ? MUTED : primary ? dev.agentcraft.client.ui.T3Theme.color("accentForeground") : INK, false);
				if (description != null) g.text(font, TextUtil.ellipsize(font, description, button.getWidth() - 20), button.getX() + 10, button.getY() + 20, primary && button.active ? dev.agentcraft.client.ui.T3Theme.color("accentForeground") : MUTED, false);
			});
			return widget;
		}
		// Subclasses such as the private pairing field own their rendering and must never expose text.
		if (widget instanceof EditBox box && widget.getClass() == EditBox.class) {
			super.addWidget(widget);
			super.addRenderableOnly((g, mx, my, delta) -> {
				if (!box.visible) return;
				Panels.sprite(g, box.isFocused() ? Kit.TEXT_FIELD_FOCUSED : Kit.TEXT_FIELD, box.getX(), box.getY(), box.getWidth(), box.getHeight());
				box.setTextColor(INK); box.setTextColorUneditable(MUTED); box.setTextShadow(false);
				int originalWidth = box.getWidth();
				box.setBordered(false); box.setWidth(Math.max(8, originalWidth - 8));
				g.pose().pushMatrix(); g.pose().translate(4, (box.getHeight() - 8) / 2f);
				try { box.extractRenderState(g, mx - 4, my, delta); }
				finally { g.pose().popMatrix(); box.setWidth(originalWidth); box.setBordered(true); }
				String hint = placeholders.get(box);
				if (hint != null && box.getValue().isEmpty() && !box.isFocused()) {
					g.text(font, TextUtil.ellipsize(font, hint, originalWidth - 12), box.getX() + 4, box.getY() + (box.getHeight() - 8) / 2, MUTED, false);
				}
			});
			return widget;
		}
		return super.addRenderableWidget(widget);
	}
	private static boolean primary(String label) {
		return label.equals("Chat") || label.equals("Open request") || label.equals("Answer request") || label.equals("Review changes") || label.equals("Reply") || label.equals("Visit")
			|| label.startsWith("Send") || label.equals("Save") || label.startsWith("Save ") || label.equals("Pair machine") || label.equals("Yes (Y)");
	}
}

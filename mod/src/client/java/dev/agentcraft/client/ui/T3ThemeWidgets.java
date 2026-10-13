package dev.agentcraft.client.ui;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.resources.Identifier;

/** Shared surfaces for native widgets and retained studio screens. */
public final class T3ThemeWidgets {
	private T3ThemeWidgets() {}
	public static void button(GuiGraphicsExtractor g, int x, int y, int w, int h, boolean primary, boolean hover, boolean disabled) {
		String role = disabled ? "surface" : primary ? hover ? "messageActionHover" : "accent" : hover ? "toolbarControlHover" : "toolbarControl";
		g.fill(x, y, x + w, y + h, T3Theme.color(role));
	}
	public static String surfaceRole(String name) {
		if (name.startsWith("kit/")) name = name.substring(4);
		return switch (name) {
			case "panel_paper", "frame_brass" -> "canvas";
			case "panel_inset", "text_field", "text_field_focused" -> "codeBackground";
			case "header", "tab_active" -> "accentSurface";
			case "tab_inactive", "keycap", "pill" -> "toolbarControl";
			case "divider", "scroll_track", "progress_track" -> "border";
			case "scroll_thumb", "scroll_thumb_hover", "scroll_grip" -> "textMuted";
			case "card_todo", "card_doing", "card_done", "card_review", "card_blocked" -> "surfaceRaised";
			default -> null;
		};
	}
	public static boolean sprite(GuiGraphicsExtractor g, Identifier id, int x, int y, int w, int h) {
		if (!id.getNamespace().equals("agentcraft")) return false;
		String name = id.getPath();
		if (name.startsWith("kit/button")) {
			button(g, x, y, w, h, name.contains("primary"), name.contains("hover") || name.contains("pressed"), name.contains("disabled")); return true;
		}
		String role = surfaceRole(name); if (role == null) return false;
		g.fill(x, y, x + w, y + h, T3Theme.color(role));
		if (name.startsWith("kit/text_field")) g.outline(x, y, w, h, T3Theme.color(name.endsWith("focused") ? "focus" : "input"));
		return true;
	}
}

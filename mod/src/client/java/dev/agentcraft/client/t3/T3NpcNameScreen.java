package dev.agentcraft.client.t3;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.network.chat.Component;

/** Local character identity belongs to this world, independently of its linked T3 chat. */
final class T3NpcNameScreen extends Screen {
	private final T3CraftClient mod;
	private final String cast;
	private final Screen parent;
	private EditBox name;
	private Button save;
	private String draft, message = "";
	private int left, top, panelWidth;

	T3NpcNameScreen(T3CraftClient mod, String cast, Screen parent) {
		super(Component.literal("Rename NPC"));
		this.mod = mod; this.cast = cast; this.parent = parent;
		draft = mod.npcName(cast);
	}
	@Override protected void init() {
		panelWidth = Math.min(320, width - 40); left = (width - panelWidth) / 2; top = Math.max(20, (height - 164) / 2);
		name = addRenderableWidget(new EditBox(font, left, top + 46, panelWidth, 20, Component.literal("NPC name")));
		name.setMaxLength(OfficeRoster.MAX_NPC_NAME_LENGTH); name.setValue(draft);
		save = addRenderableWidget(Button.builder(Component.literal("Save"), b -> saveName()).bounds(left, top + 124, 70, 20).build());
		addRenderableWidget(Button.builder(Component.literal("Reset name"), b -> name.setValue(dev.agentcraft.Cast.get(cast).name())).bounds(left + 78, top + 124, 94, 20).build());
		addRenderableWidget(Button.builder(Component.literal("Cancel"), b -> onClose()).bounds(left + panelWidth - 70, top + 124, 70, 20).build());
		name.setResponder(value -> { draft = value; message = ""; updateSave(); });
		updateSave();
	}
	@Override protected void setInitialFocus() { setInitialFocus(name); }
	private void updateSave() { save.active = mod.canPin() && OfficeRoster.npcNameError(name.getValue()) == null; }
	private void saveName() {
		if (!save.active) return;
		String error = mod.renameNpc(cast, name.getValue());
		if (error == null) onClose(); else message = error;
	}
	@Override public boolean keyPressed(KeyEvent event) {
		if (name.isFocused() && event.isConfirmation()) { saveName(); return true; }
		return super.keyPressed(event);
	}
	@Override public void onClose() { minecraft.gui.setScreen(parent); }
	@Override public boolean isPauseScreen() { return false; }
	@Override public boolean isInGameUi() { return true; }
	@Override public void extractRenderState(GuiGraphicsExtractor g, int mx, int my, float delta) {
		g.fill(left - 12, top - 12, left + panelWidth + 12, top + 156, 0xF0222529);
		g.text(font, "Rename NPC", left, top, 0xFFFFFFFF, false);
		g.text(font, "Saved for this world and dimension", left, top + 20, 0xFFA8A8A8, false);
		String hint = message.isEmpty() ? OfficeRoster.npcNameError(name.getValue()) : message;
		if (hint == null) hint = "Up to 32 characters";
		int y = top + 78;
		for (var line : font.split(Component.literal(hint), panelWidth)) {
			g.text(font, line, left, y, message.isEmpty() ? 0xFFA8A8A8 : 0xFFFF9772, false);
			y += font.lineHeight + 2;
		}
		super.extractRenderState(g, mx, my, delta);
	}
}

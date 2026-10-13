package dev.agentcraft.client.t3;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.MultiLineEditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.network.chat.Component;

/** Expanded composer. Enter inserts a line; sending remains explicit and acknowledgement based. */
final class T3DraftEditorScreen extends T3MenuScreen {
	private final T3CraftClient mod; private final T3State.ThreadRow row; private final String key; private final boolean fresh;
	private MultiLineEditBox editor; private Button send;
	T3DraftEditorScreen(T3CraftClient mod, T3State.ThreadRow row, String key, boolean fresh) { super(Component.literal("Compose message")); this.mod = mod; this.row = row; this.key = key; this.fresh = fresh; }
	@Override protected void init() {
		editor = addRenderableWidget(MultiLineEditBox.builder().setX(20).setY(50).setShowBackground(true).build(font, width - 40, height - 100, Component.literal("Message")));
		editor.setCharacterLimit(120000); editor.setValue(mod.draft(key)); editor.setValueListener(value -> mod.saveDraft(key, value)); setInitialFocus(editor);
		send = addRenderableWidget(Button.builder(Component.literal("Send (Ctrl+Enter)"), b -> submit()).bounds(20, height - 34, 140, 20).build());
		addRenderableWidget(Button.builder(Component.literal("Save & back"), b -> { mod.flushDrafts(); mod.openPanel(); }).bounds(width - 130, height - 34, 110, 20).build());
	}
	private boolean steering() { return !fresh && mod.draftEntry(key).delivery.equals("steer"); }
	private void submit() { if (mod.draftEntry(key).sending || row == null || !mod.state().online(row.id())) return; mod.saveDraft(key, editor.getValue()); mod.sendDraft(key, row, fresh, id -> { if (minecraft.gui.screen() == this) { mod.focus(id); mod.openPanel(); } }); }
	@Override public void tick() {
		var entry = mod.draftEntry(key); editor.active = !entry.sending;
		send.active = !entry.sending && row != null && mod.state().online(row.id());
		send.setMessage(Component.literal(steering() ? "Steer (Ctrl+Enter)" : "Send (Ctrl+Enter)"));
	}
	@Override public boolean keyPressed(KeyEvent e) { if (e.isConfirmation() && e.hasControlDown()) { submit(); return true; } return super.keyPressed(e); }
	@Override public void removed() { if (!mod.draftEntry(key).status.equals("Sent to T3")) mod.saveDraft(key, editor.getValue()); mod.flushDrafts(); }
	@Override public boolean isPauseScreen() { return false; }
	@Override public boolean isInGameUi() { return false; }
	com.google.gson.JsonObject inspectFixture() {
		var out = new com.google.gson.JsonObject(); out.addProperty("threadId", row == null ? null : row.id());
		out.addProperty("owner", row == null ? null : row.ownerKey()); out.addProperty("delivery", mod.draftEntry(key).delivery);
		out.addProperty("text", editor.getValue()); return out;
	}
	@Override public void extractRenderState(GuiGraphicsExtractor g, int mx, int my, float delta) {
		paper(g, 8, 8, width - 8, height - 8); g.text(font, fresh ? "Compose a new chat" : steering() ? "Steer this active run" : "Compose message", 20, 18, INK, false);
		g.text(font, T3Hud.ellipsize(font, mod.draftEntry(key).status + " · Enter newline · Ctrl+Enter send", width - 40), 20, 34, MUTED, false); super.extractRenderState(g, mx, my, delta);
	}
}

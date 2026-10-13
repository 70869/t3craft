package dev.agentcraft.client.t3;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.Component;

/** Setup follows actual pairing and desk state; it never creates or sends work. */
final class T3GuideScreen extends T3MenuScreen {
    private final T3CraftClient mod;
    private String message = "";
    T3GuideScreen(T3CraftClient mod) { super(Component.literal("Getting started")); this.mod = mod; }
    private int left() { return Math.max(16, (width - 500) / 2); }
    @Override protected void init() {
        int x = left(), w = width - 2 * x, gap = Math.max(36, Math.min(44, (height - 110) / 4));
        menuAction("1. Connect a machine", "Copy a pairing link from T3 Settings > Connections", x, 54, w, b -> mod.openConnections());
        menuAction("2. Choose a conversation", "Search existing chats; select a machine or project", x, 54 + gap, w, b -> mod.openPanel());
        menuAction("3. Give it a desk", "Pin the selected chat, or reassign a resident", x, 54 + gap * 2, w, b -> {
            var row = mod.state().snapshot().focusedRow();
            if (row == null) { mod.openPanel(); return; }
            if (!mod.pinned(row)) mod.togglePin(row);
            if (mod.pinned(row)) mod.openTask(row.id()); else message = "Choose a chat and free a pinned desk first.";
        });
        menuAction("4. Continue the conversation", "Compose a reply; Send runs it in T3 when you choose", x, 54 + gap * 3, w, b -> {
            var row = mod.state().snapshot().focusedRow();
            if (row == null) mod.openPanel(); else mod.openComposer(row.id(), false);
        });
        int bw = (w - 6) / 2;
        addRenderableWidget(Button.builder(Component.literal("Your work"), b -> mod.openWork()).bounds(x, height - 30, bw, 20).build());
        addRenderableWidget(Button.builder(Component.literal("Done"), b -> onClose()).bounds(x + bw + 6, height - 30, bw, 20).build());
    }
    @Override public void extractRenderState(GuiGraphicsExtractor g, int mx, int my, float delta) {
        int x = left(), w = width - 2 * x; paper(g, x - 10, 10, width - x + 10, height - 8);
        g.text(font, "Getting started", x, 22, INK, false);
        var row = mod.state().snapshot().focusedRow();
        String progress = !mod.paired() ? "Start by pairing your T3 Code machine." : row == null ? "Connected. Choose an existing conversation." : !mod.pinned(row) ? "Selected: " + row.title() : "Desk pinned. Your conversation is ready.";
        g.text(font, T3Hud.ellipsize(font, message.isEmpty() ? progress : message, w), x, 38, MUTED, false);
        super.extractRenderState(g, mx, my, delta);
    }
}

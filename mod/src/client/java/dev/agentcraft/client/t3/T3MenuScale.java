package dev.agentcraft.client.t3;

import com.mojang.blaze3d.platform.Window;
import net.minecraft.client.Minecraft;

/** T3 menus use a readable desktop canvas without changing the player's GUI preference. */
public final class T3MenuScale {
    private T3MenuScale() {}

    public static int calculate(Window window, int requested, boolean unicode) {
        int vanilla = window.calculateScale(requested, unicode);
        var minecraft = Minecraft.getInstance();
        if (minecraft.gui == null || !(minecraft.gui.screen() instanceof T3MenuScreen)) return vanilla;
        // At 1080p this is scale 2: 960 x 540 instead of the crowded 480 x 270.
        // Preserve smaller user-selected scales and adapt to lower-resolution windows.
        int preferred = Math.max(2, Math.min(window.getWidth() / 960, window.getHeight() / 540));
        int fits = Math.max(1, Math.min(window.getWidth() / 640, window.getHeight() / 360));
        int limit = Math.min(preferred, fits);
        int scale = Math.min(vanilla, limit);
        if (unicode && scale > 1 && scale % 2 != 0) scale--;
        return scale;
    }

    public static void screenChanged(Minecraft minecraft) {
        var window = minecraft.getWindow();
        window.setGuiScale(calculate(window, minecraft.options.guiScale().get(), minecraft.isEnforceUnicode()));
    }
}

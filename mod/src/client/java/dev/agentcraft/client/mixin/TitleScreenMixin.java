package dev.agentcraft.client.mixin;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.LogoRenderer;
import net.minecraft.client.gui.screens.TitleScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/** Brand the entry screen while retaining Minecraft's native menu and fade. */
@Mixin(TitleScreen.class)
public abstract class TitleScreenMixin {
    @Redirect(method = "extractRenderState", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/components/LogoRenderer;extractRenderState(Lnet/minecraft/client/gui/GuiGraphicsExtractor;IF)V"))
    private void agentcraft$title(LogoRenderer logo, GuiGraphicsExtractor g, int width, float alpha) {
        var font = Minecraft.getInstance().font;
        String title = "T3 Craft";
        float scale = Math.min(4f, (width - 40f) / font.width(title));
        int color = Math.max(0, Math.min(255, (int) (alpha * 255))) << 24 | 0xFFFFFF;
        if ((color >>> 24) < 4) return;
        g.pose().pushMatrix();
        g.pose().translate(width / 2f, 30f);
        g.pose().scale(scale, scale);
        g.text(font, title, -font.width(title) / 2, 0, color, true);
        g.pose().popMatrix();
    }
}

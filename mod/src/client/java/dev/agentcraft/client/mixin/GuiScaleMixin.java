package dev.agentcraft.client.mixin;

import dev.agentcraft.client.t3.T3MenuScale;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.screens.Screen;
import org.objectweb.asm.Opcodes;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Apply the destination screen's scale before native initialization and mouse routing. */
@Mixin(Gui.class)
public abstract class GuiScaleMixin {
    @Inject(method = "setScreen", at = @At(value = "FIELD",
        target = "Lnet/minecraft/client/gui/Gui;screen:Lnet/minecraft/client/gui/screens/Screen;",
        opcode = Opcodes.PUTFIELD, shift = At.Shift.AFTER))
    private void agentcraft$menuScale(Screen screen, CallbackInfo ci) {
        T3MenuScale.screenChanged(Minecraft.getInstance());
    }
}

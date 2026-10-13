package dev.agentcraft.client.agents;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import dev.agentcraft.client.ui.WorldUi;
import net.minecraft.client.renderer.SubmitNodeCollector;

/** A cosmetic keyboard on the detected desk, following moved/rotated chairs without world writes. */
final class DeskKeyboard {
	private DeskKeyboard() {}
	static void submit(AgentRenderState s, PoseStack ps, SubmitNodeCollector c) {
		if (!Float.isFinite(s.deskSurface) || s.deskSurface < .3f || s.deskSurface > 1.5f || s.isInvisible || s.distanceToCameraSq > 24 * 24) return;
		ps.pushPose();
		ps.translate(0, s.deskSurface + .009f, 0);
		ps.rotateDegrees(Axis.YP, -s.bodyRot);
		ps.translate(0, 0, .57f);
		// Positive local depth must lift the keys above the case and the desktop.
		ps.rotateDegrees(Axis.XP, -90);
		// WorldUi uses GUI winding (y down); keep the front face pointing upward.
		ps.scale(1f / 48, -1f / 48, 1f / 48);
		int light = s.agentLight, body = 0xFF292C32, key = 0xFFBDC4CB, pressed = 0xFF6D909B;
		WorldUi.submitFill(ps, c, -15, -6, 15, 6, body, light);
		ps.translate(0, 0, .12f);
		for (int row = 0; row < 3; row++) for (int col = 0; col < 9; col++) {
			float x = -13.5f + col * 3, y = -4.5f + row * 2.6f;
			boolean down = (col == 2 && row == 1 && s.keyRight > .55f) || (col == 6 && row == 0 && s.keyLeft > .55f);
			WorldUi.submitFill(ps, c, x, y, x + 2.2f, y + 1.8f, down ? pressed : key, light);
		}
		WorldUi.submitFill(ps, c, -7, 3.5f, 6, 5, key, light);
		WorldUi.submitFill(ps, c, -22, -2, -18, 4, body, light);
		ps.translate(0, 0, .12f);
		WorldUi.submitFill(ps, c, -21.5f, -1.5f, -18.5f, 0, s.mouse > .4f ? pressed : key, light);
		ps.popPose();
	}
}

package dev.agentcraft.client.agents;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.agentcraft.client.ui.UiStyle;
import dev.agentcraft.client.ui.WorldUi;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.util.Mth;

/** Small pixel thought faces, independent of the skin's eyes, mask or helmet. */
final class AgentExpressions {
	static final float HEIGHT = 20, WIDTH = 19;
	private static final String[][] FACES = {
		{},
		{"##     ##", " ##   ## ", "  #   #  ", "         ", "   ###   "},
		{"         ", "         ", " #  #  # ", "         ", "         "},
		{"   ###   ", "  #   #  ", "     #   ", "    #    ", "         ", "    #    "},
		{" ##   ## ", "#       #", "         ", " #     # ", "  #####  "},
		{"##     ##", "  #   #  ", "         ", "   ###   ", "  #   #  "},
		{"         ", " #     # ", "         ", "   ###   ", "   ###   "},
		{"#       #", " #     # ", "  #   #  ", "   # #   ", "    #    "}
	};
	private AgentExpressions() {}
	static boolean visible(AgentRenderState s) {
		if (s.expression == AgentExpression.NONE || s.bubble > .05f) return false;
		return s.distanceToCameraSq <= (s.expression == AgentExpression.FOCUSED ? 36 : 100);
	}
	static void submit(AgentRenderState s, PoseStack ps, SubmitNodeCollector c, float bottom, int light) {
		float bob = .7f * Mth.sin(s.timeSeconds * 2.5f);
		ps.pushPose(); ps.translate(0, bottom - HEIGHT + 2 + bob, .4f);
		// Warm opaque paper keeps these readable regardless of the selected dark/light theme.
		int paper = 0xFFF4EFE6, ink = 0xFF29251F;
		WorldUi.submitFill(ps, c, -8.5f, 1, 8.5f, 13, paper, light);
		WorldUi.submitFill(ps, c, -7.5f, 0, 7.5f, 14, paper, light);
		WorldUi.submitFill(ps, c, 2, 14, 5, 16, paper, light);
		ps.translate(0, 0, .3f);
		String[] rows = FACES[s.expression.ordinal()];
		int color = switch (s.expression) {
			case WAITING -> UiStyle.status("waiting"); case PLEASED -> UiStyle.status("done"); case FRUSTRATED -> UiStyle.status("error"); default -> ink;
		};
		for (int y = 0; y < rows.length; y++) for (int x = 0; x < rows[y].length(); x++) if (rows[y].charAt(x) == '#') {
			float offset = s.expression == AgentExpression.THINKING ? -.9f * Math.max(0, Mth.sin(s.timeSeconds * 4 - x * .5f)) : 0;
			WorldUi.submitFill(ps, c, x - 4.5f, y + 4 + offset, x - 3.5f, y + 5 + offset, color, light);
		}
		ps.popPose();
	}
}

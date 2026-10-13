package dev.agentcraft.client.agents;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import dev.agentcraft.client.ui.WorldUi;
import net.minecraft.client.renderer.SubmitNodeCollector;

/** Small held props for the finished-resident lounge loop. They are render-only. */
final class ResidentProps {
	private ResidentProps() {}

	static void submit(AgentRenderState state, PoseStack pose, SubmitNodeCollector collector) {
		if (state.isInvisible || state.distanceToCameraSq > 24 * 24) return;
		if (state.gesture != ResidentGestures.Gesture.PHONE && state.gesture != ResidentGestures.Gesture.EAT) return;
		pose.pushPose();
		pose.rotateDegrees(Axis.YP, -state.bodyRot);
		pose.translate(0, state.sitDrop + (state.gesture == ResidentGestures.Gesture.EAT ? 1.18 : 1.02), .48);
		pose.scale(1f / 48f, -1f / 48f, 1f / 48f);
		if (state.gesture == ResidentGestures.Gesture.PHONE) {
			WorldUi.submitFill(pose, collector, -4, -7, 4, 7, 0xFF20252D, state.agentLight);
			WorldUi.submitFill(pose, collector, -3, -5, 3, 4, 0xFF76C7D1, state.agentLight);
			WorldUi.submitFill(pose, collector, -1, 5, 1, 6, 0xFFD5D9DC, state.agentLight);
		} else {
			WorldUi.submitFill(pose, collector, -5, -3, 5, 3, 0xFFB77A43, state.agentLight);
			WorldUi.submitFill(pose, collector, -3, -2, 3, 2, 0xFFE9C76A, state.agentLight);
			WorldUi.submitFill(pose, collector, -1, -1, 1, 1, 0xFF6E994C, state.agentLight);
		}
		pose.popPose();
	}
}

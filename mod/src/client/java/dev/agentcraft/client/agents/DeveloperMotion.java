package dev.agentcraft.client.agents;

import net.minecraft.util.Mth;

/** Small, bounded hand motions. Tick time and a resident's phase keep the cast out of sync. */
public final class DeveloperMotion {
	private DeveloperMotion() {}
	public static float tap(float ticks, float phase, boolean left) {
		float pulse = Math.max(0, Mth.sin(ticks * (left ? .81f : 1.03f) + phase + (left ? 2.1f : 0)));
		return pulse * pulse * pulse;
	}
	public static void apply(float[] pose, float ticks, float phase, float typing, float mouse) {
		float right = tap(ticks, phase, false), left = tap(ticks, phase, true);
		pose[AgentLife.P_RAX] += typing * (.24f * right - .065f + .035f * Mth.sin(ticks * .13f + phase));
		pose[AgentLife.P_LAX] += typing * (.24f * left - .065f + .035f * Mth.sin(ticks * .17f + phase));
		pose[AgentLife.P_RAY] += typing * .055f * Mth.sin(ticks * .15f + phase);
		pose[AgentLife.P_LAY] += typing * .055f * Mth.sin(ticks * .12f + phase + 1);
		pose[AgentLife.P_RAZ] += typing * .025f * Mth.sin(ticks * .19f + phase);
		pose[AgentLife.P_LAZ] -= typing * .025f * Mth.sin(ticks * .16f + phase);
		pose[AgentLife.P_RHAND] = typing * .38f * right;
		pose[AgentLife.P_LHAND] = typing * .38f * left;
		// The right hand slides to a mouse; the left rests over the keyboard.
		pose[AgentLife.P_RAY] -= mouse * (.34f + .035f * Mth.sin(ticks * .12f + phase));
		pose[AgentLife.P_RAX] += mouse * .035f * Mth.sin(ticks * .23f + phase);
		pose[AgentLife.P_LEAN] += typing * .009f * Mth.sin(ticks * .11f + phase);
	}
}

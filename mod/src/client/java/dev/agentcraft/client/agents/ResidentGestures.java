package dev.agentcraft.client.agents;

import net.minecraft.util.Mth;

/** Small, staggered gestures layered over the current activity, without changing T3 state. */
public final class ResidentGestures {
	public enum Gesture { NONE, GREET, SEATED_STRETCH, SHIFT, POINT, CHECK_SCREEN, EAT, PHONE, DEV_IDLE }
	private ResidentGestures() {}

	public static Gesture select(AgentLife.Posture posture, float time, float phase, boolean greeting) {
		return select(posture, time, phase, greeting, false);
	}

	public static Gesture select(AgentLife.Posture posture, float time, float phase, boolean greeting, boolean leisure) {
		if (posture == AgentLife.Posture.WALK || posture == AgentLife.Posture.SCRATCH
			|| posture == AgentLife.Posture.SIT_SCRATCH || posture == AgentLife.Posture.TALK) return Gesture.NONE;
		if (greeting && (posture == AgentLife.Posture.IDLE || posture == AgentLife.Posture.SIT_IDLE
			|| posture == AgentLife.Posture.SIT_TYPE || posture == AgentLife.Posture.TYPE_STAND)) return Gesture.GREET;
		float cycle = cycle(time, phase);
		if (leisure && (posture == AgentLife.Posture.IDLE || posture == AgentLife.Posture.SIT_IDLE || posture == AgentLife.Posture.RELAX)) {
			if (cycle < 42) return Gesture.SEATED_STRETCH;
			if (cycle >= 100 && cycle < 148) return Gesture.EAT;
			if (cycle >= 230 && cycle < 278) return Gesture.PHONE;
			if (cycle >= 360 && cycle < 408) return Gesture.DEV_IDLE;
		}
		return switch (posture) {
			case SIT_IDLE -> cycle < 42 ? Gesture.SEATED_STRETCH : Gesture.NONE;
			case IDLE, WAIT -> Gesture.SHIFT;
			case REVIEW -> cycle < 42 ? Gesture.POINT : Gesture.NONE;
			case WATCH, LEAN_BENCH -> Gesture.CHECK_SCREEN;
			default -> Gesture.NONE;
		};
	}

	public static void apply(float[] pose, Gesture gesture, float time, float phase, float greetingRemaining) {
		float wave = Mth.sin(time * .16f + phase);
		float start = switch (gesture) { case EAT -> 100; case PHONE -> 230; case DEV_IDLE -> 360; default -> 0; };
		float duration = gesture == Gesture.EAT || gesture == Gesture.PHONE || gesture == Gesture.DEV_IDLE ? 48 : 42;
		float pulse = Mth.sin(Mth.clamp((cycle(time, phase) - start) / duration, 0, 1) * Mth.PI);
		pulse *= pulse;
		switch (gesture) {
			case GREET -> {
				float weight = Math.min(1, Math.max(0, greetingRemaining / 8f));
				pose[AgentLife.P_RAX] = Mth.lerp(weight, pose[AgentLife.P_RAX], -2.45f);
				pose[AgentLife.P_RAZ] = Mth.lerp(weight, pose[AgentLife.P_RAZ], .4f + .18f * wave);
				pose[AgentLife.P_HEADROLL] += weight * .035f * wave;
			}
			case SEATED_STRETCH -> {
				pose[AgentLife.P_RAX] = Mth.lerp(pulse, pose[AgentLife.P_RAX], -3.05f);
				pose[AgentLife.P_LAX] = Mth.lerp(pulse, pose[AgentLife.P_LAX], -3.05f);
				pose[AgentLife.P_RAZ] -= pulse * .22f; pose[AgentLife.P_LAZ] += pulse * .22f;
				pose[AgentLife.P_LEAN] -= pulse * .045f;
			}
			case SHIFT -> {
				pose[AgentLife.P_BODYROLL] += .035f * Mth.sin(time * .045f + phase);
				pose[AgentLife.P_RAX] += .07f * wave; pose[AgentLife.P_LAX] -= .045f * wave;
			}
			case POINT -> {
				pose[AgentLife.P_RAX] -= pulse * .42f; pose[AgentLife.P_RAY] -= pulse * .12f;
				pose[AgentLife.P_LEAN] += pulse * .035f;
			}
			case CHECK_SCREEN -> {
				pose[AgentLife.P_LEAN] += .035f * Mth.sin(time * .065f + phase);
				pose[AgentLife.P_RAX] += .06f * wave; pose[AgentLife.P_HEADROLL] += .025f * wave;
			}
			case EAT -> {
				pose[AgentLife.P_RAX] = Mth.lerp(pulse, pose[AgentLife.P_RAX], -2.25f);
				pose[AgentLife.P_RAY] = Mth.lerp(pulse, pose[AgentLife.P_RAY], -.28f);
				pose[AgentLife.P_LAX] = Mth.lerp(pulse, pose[AgentLife.P_LAX], -1.05f);
				pose[AgentLife.P_LEAN] -= .025f * pulse;
			}
			case PHONE -> {
				pose[AgentLife.P_RAX] = Mth.lerp(pulse, pose[AgentLife.P_RAX], -1.5f);
				pose[AgentLife.P_RAY] = Mth.lerp(pulse, pose[AgentLife.P_RAY], -.12f);
				pose[AgentLife.P_LAX] = Mth.lerp(pulse, pose[AgentLife.P_LAX], -1.35f);
				pose[AgentLife.P_LAY] = Mth.lerp(pulse, pose[AgentLife.P_LAY], .15f);
				pose[AgentLife.P_HEADROLL] += pulse * .04f;
			}
			case DEV_IDLE -> {
				pose[AgentLife.P_RAX] = Mth.lerp(pulse, pose[AgentLife.P_RAX], -.28f);
				pose[AgentLife.P_LAX] = Mth.lerp(pulse, pose[AgentLife.P_LAX], -1.72f);
				pose[AgentLife.P_LEAN] -= .035f * pulse;
			}
			case NONE -> {}
		}
	}

	private static float cycle(float time, float phase) { return (time + phase * 100) % 520; }
}

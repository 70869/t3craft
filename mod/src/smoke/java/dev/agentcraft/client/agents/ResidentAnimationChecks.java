package dev.agentcraft.client.agents;

/** Samples bounded animation and meaningful live/offline expression priorities. */
public final class ResidentAnimationChecks {
	private static void check(boolean value, String message) { if (!value) throw new AssertionError(message); }
	public static void main(String[] args) {
		float[] pose = new float[AgentLife.N];
		float low = 1, high = 0, phaseDifference = 0;
		for (int frame = 0; frame < 600; frame++) {
			float time = frame / 3f, tap = DeveloperMotion.tap(time, .5f, false);
			low = Math.min(low, tap); high = Math.max(high, tap);
			phaseDifference += Math.abs(tap - DeveloperMotion.tap(time, 2.3f, false));
			java.util.Arrays.fill(pose, 0);
			DeveloperMotion.apply(pose, time, .5f, 1, 1);
			for (float channel : pose) check(Float.isFinite(channel) && Math.abs(channel) < .5f, "Hand motion must stay bounded");
			check(pose[AgentLife.P_RHAND] >= 0 && pose[AgentLife.P_LHAND] >= 0, "Taps stay above the keyboard baseline");
		}
		check(low < .01 && high > .98 && phaseDifference > 50, "Hands release keys and residents have different timing");
		java.util.Arrays.fill(pose, 0); DeveloperMotion.apply(pose, 200, 1, 0, 0);
		for (float channel : pose) check(channel == 0, "Inactive animation adds no work motion");
		AgentView view = new AgentView("marlow"); view.active = true; view.family = "working";
		check(AgentExpression.of(view, false, false) == AgentExpression.FOCUSED, "Working expression");
		view.family = "thinking"; check(AgentExpression.of(view, false, false) == AgentExpression.THINKING, "Thinking expression");
		view.family = "error"; check(AgentExpression.of(view, true, true) == AgentExpression.FRUSTRATED, "Failure outranks speech/completion");
		view.awaitingUser = true; check(AgentExpression.of(view, true, true) == AgentExpression.WAITING, "Request outranks other expressions");
		view.stale = true; check(AgentExpression.of(view, true, true) == AgentExpression.OFFLINE, "Offline outranks remembered activity");
		view.active = false; check(AgentExpression.of(view, true, true) == AgentExpression.NONE, "Unassigned residents show no work expression");
		view.active = true; view.stale = false; view.awaitingUser = false; view.family = "done";
		check(AgentExpression.of(view, false, false) == AgentExpression.NONE, "Old completed history does not celebrate");
		check(AgentExpression.of(view, false, true) == AgentExpression.PLEASED, "Only live completion celebrates");
		view.paused = true; check(AgentExpression.of(view, true, true) == AgentExpression.NONE, "Paused work is quiet");
		var gestures = java.util.EnumSet.noneOf(ResidentGestures.Gesture.class);
		var leisureGestures = java.util.EnumSet.noneOf(ResidentGestures.Gesture.class);
		for (var posture : AgentLife.Posture.values()) for (int tick = 0; tick < 1040; tick++) {
			var gesture = ResidentGestures.select(posture, tick, .7f, tick < 40);
			gestures.add(gesture); java.util.Arrays.fill(pose, 0);
			ResidentGestures.apply(pose, gesture, tick, .7f, 40 - tick);
			for (float channel : pose) check(Float.isFinite(channel) && Math.abs(channel) <= 3.1f, "Gesture channels stay bounded");
			if (posture == AgentLife.Posture.WALK || posture == AgentLife.Posture.SCRATCH || posture == AgentLife.Posture.SIT_SCRATCH)
				check(gesture == ResidentGestures.Gesture.NONE, "Greetings cannot replace movement/failure");
			if (gesture == ResidentGestures.Gesture.SEATED_STRETCH) check(posture == AgentLife.Posture.SIT_IDLE, "Stretch preserves seated idle legs");
		}
		for (int tick = 0; tick < 1040; tick++) {
			var gesture = ResidentGestures.select(AgentLife.Posture.SIT_IDLE, tick, .7f, false, true);
			leisureGestures.add(gesture); java.util.Arrays.fill(pose, 0);
			ResidentGestures.apply(pose, gesture, tick, .7f, 0);
			for (float channel : pose) check(Float.isFinite(channel) && Math.abs(channel) <= 3.1f, "Lounge gestures stay bounded");
		}
		check(leisureGestures.containsAll(java.util.EnumSet.of(ResidentGestures.Gesture.EAT, ResidentGestures.Gesture.PHONE,
			ResidentGestures.Gesture.DEV_IDLE, ResidentGestures.Gesture.SEATED_STRETCH)), "Finished residents need staggered lounge activities");
		gestures.addAll(leisureGestures);
		for (var gesture : java.util.List.of(ResidentGestures.Gesture.EAT, ResidentGestures.Gesture.PHONE, ResidentGestures.Gesture.DEV_IDLE)) {
			java.util.Arrays.fill(pose, 0);
			float peak = gesture == ResidentGestures.Gesture.EAT ? 124 : gesture == ResidentGestures.Gesture.PHONE ? 254 : 384;
			ResidentGestures.apply(pose, gesture, peak, 0, 0);
			check(pose[gesture == ResidentGestures.Gesture.DEV_IDLE ? AgentLife.P_LAX : AgentLife.P_RAX] < -1.2f, "Lounge activity must visibly move the hand: " + gesture);
		}
		check(gestures.size() == ResidentGestures.Gesture.values().length, "All gesture types are reachable");
		check(ResidentGestures.select(AgentLife.Posture.SIT_IDLE, 10, 0, false) != ResidentGestures.select(AgentLife.Posture.SIT_IDLE, 10, 2, false), "Residents stretch at different times");
		System.out.println("PASS bounded, asynchronous hand taps; inactive motion; exact request/offline priorities and live-only completion expressions");
	}
}

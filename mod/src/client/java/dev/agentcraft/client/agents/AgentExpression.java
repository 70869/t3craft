package dev.agentcraft.client.agents;

/** Cosmetic reading of the same live state as the resident's lamp and request marker. */
public enum AgentExpression {
	NONE, FOCUSED, THINKING, WAITING, PLEASED, FRUSTRATED, TALKING, OFFLINE;
	public static AgentExpression of(AgentView view, boolean speaking, boolean justCompleted) {
		if (view.stale && view.active) return OFFLINE;
		if (!view.active || view.paused || view.stale) return NONE;
		if (view.needsYou()) return WAITING;
		if (view.dotFamily().equals("error")) return FRUSTRATED;
		if (justCompleted) return PLEASED;
		if (speaking) return TALKING;
		return switch (view.dotFamily()) { case "thinking" -> THINKING; case "working" -> FOCUSED; default -> NONE; };
	}
}

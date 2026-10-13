package dev.agentcraft;

import java.util.List;

/** Saved presentation choices; all displayed data still comes from T3. */
public final class DisplayViews {
	public static final List<String> IDS = List.of("default", "overview", "status", "activity", "usage");
	private DisplayViews() {}
	public static boolean valid(String id) { return id != null && IDS.contains(id); }
	public static String label(String id) {
		return switch (id) { case "overview" -> "Chat overview"; case "status" -> "Status graph"; case "activity" -> "Recent activity"; case "usage" -> "Tokens & limits"; default -> "Original display"; };
	}
}

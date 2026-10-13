package dev.agentcraft;

import java.util.List;
import java.util.Map;

/** Cosmetic identities only; desk IDs and the owning T3 conversations stay unchanged. */
public final class NpcLooks {
	public static final List<String> IDS = List.of("spartan", "naruto", "snake", "mario", "yoshi", "megaman", "link", "sonic", "samus", "kirby", "pikachu", "luigi", "peach", "zelda", "donkeykong", "pacman", "lara", "crash");
	public static final List<String> EXTRA_RESIDENTS = IDS.subList(6, IDS.size());
	private static final Map<String, String> DEFAULTS = Map.of("marlow", "spartan", "juniper", "naruto", "kit", "snake", "wren", "mario", "rowan", "yoshi", "tove", "megaman");
	private NpcLooks() {}
	public static boolean valid(String id) { return id != null && IDS.contains(id); }
	public static String defaultFor(String cast) { return DEFAULTS.getOrDefault(cast, IDS.contains(cast) ? cast : "spartan"); }
	public static String label(String id) {
		return switch (id) {
			case "spartan" -> "Shadow Scout";
			case "naruto" -> "Garden Keeper";
			case "snake" -> "Night Agent";
			case "mario" -> "Crimson Runner";
			case "yoshi" -> "Blue Craftsman";
			case "megaman" -> "Blue Android";
			case "link" -> "Indigo Scholar";
			case "sonic" -> "Blue Ranger";
			case "samus" -> "Iron Guard";
			case "kirby" -> "Rose Gardener";
			case "pikachu" -> "Tangerine Creature";
			case "luigi" -> "Golden Mechanic";
			case "peach" -> "Violet Adept";
			case "zelda" -> "Sienna Explorer";
			case "donkeykong" -> "Pumpkin Walker";
			case "pacman" -> "Silver Automaton";
			case "lara" -> "Frost Wanderer";
			case "crash" -> "Moss Explorer";
			default -> "Shadow Scout";
		};
	}
	public static boolean slim(String cast, String skin) {
		if (valid(skin) && !skin.equals("default")) return false;
		var member = Cast.get(cast); return member != null && member.slim();
	}
}

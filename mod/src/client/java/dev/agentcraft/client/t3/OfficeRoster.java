// Adapted from maxwellyoung/t3craft (MIT), copyright 2026 Maxwell Young.
// See THIRD-PARTY-NOTICES.md and licenses/t3craft-client-MIT.txt.
package dev.agentcraft.client.t3;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Saved spatial identity; names and activity order never identify a machine or a desk. */
final class OfficeRoster {
	static final int DESKS = 6;
	static final int MAX_NPC_NAME_LENGTH = 32;
	record Pin(String owner, String threadId, String title) {
		String key() { return OfficeRoster.key(owner, threadId); }
	}
	static final class Preferences {
		List<Pin> pins = new ArrayList<>();
		Map<String, Integer> desks = new LinkedHashMap<>();
		Map<String, String> npcNames = new LinkedHashMap<>();
		String npcName(String cast) {
			var member = dev.agentcraft.Cast.get(cast);
			return npcNames.getOrDefault(cast, member == null ? cast : member.name());
		}
		void renameNpc(String cast, String name) {
			if (dev.agentcraft.Cast.get(cast) == null) throw new IllegalArgumentException("Unknown NPC.");
			String error = npcNameError(name);
			if (error != null) throw new IllegalArgumentException(error);
			String value = name.strip();
			if (value.equals(dev.agentcraft.Cast.get(cast).name())) npcNames.remove(cast);
			else npcNames.put(cast, value);
		}
		boolean pinned(T3State.ThreadRow row) { return pins.stream().anyMatch(p -> p.key().equals(key(row))); }
		boolean pin(T3State.ThreadRow row) {
			if (pinned(row)) return true;
			if (pins.size() >= DESKS) return false;
			pins.add(new Pin(row.ownerKey(), row.id(), row.title())); return true;
		}
		void unpin(Pin pin) { pins.removeIf(p -> p.key().equals(pin.key())); }
		void normalize() {
			if (pins == null) pins = new ArrayList<>();
			if (desks == null) desks = new LinkedHashMap<>();
			if (npcNames == null) npcNames = new LinkedHashMap<>();
			npcNames.entrySet().removeIf(e -> dev.agentcraft.Cast.get(e.getKey()) == null || npcNameError(e.getValue()) != null);
			npcNames.replaceAll((cast, name) -> name.strip());
			pins.removeIf(p -> p == null || p.owner() == null || p.threadId() == null);
		}
	}
	static String npcNameError(String name) {
		if (name == null || name.isBlank()) return "Enter a name.";
		if (name.strip().length() > MAX_NPC_NAME_LENGTH) return "Use 32 characters or fewer.";
		if (name.codePoints().anyMatch(c -> Character.isISOControl(c) || c == 0xA7)) return "Use plain text for the name.";
		return null;
	}
	record Resident(T3State.ThreadRow row, int desk) {}
	static String key(String owner, String thread) { return owner + "\n" + thread; }
	static String key(T3State.ThreadRow row) { return key(row.ownerKey(), row.id()); }
	static String projectKey(T3State.ThreadRow row) { return key(row.ownerKey(), row.raw().has("projectId") && !row.raw().get("projectId").isJsonNull() ? row.raw().get("projectId").getAsString() : ""); }

	/** Input is already newest first. Reserve pins, then waiting and working threads. */
	static List<Resident> sync(List<T3State.ThreadRow> rows, List<String> owners, Preferences prefs) {
		prefs.normalize();
		List<Resident> result = new ArrayList<>();
		Set<String> retain = new HashSet<>();
		{
			List<T3State.ThreadRow> candidates = new ArrayList<>(rows.stream().filter(r -> owners.contains(r.ownerKey())).toList());
			Map<String, Integer> pinOrder = new HashMap<>();
			for (int i = 0; i < prefs.pins.size(); i++) pinOrder.put(prefs.pins.get(i).key(), i);
			candidates.sort(Comparator.comparingInt((T3State.ThreadRow r) -> pinOrder.containsKey(key(r)) ? 0 : r.status() == T3State.Status.NEEDS_YOU ? 1 : r.status() == T3State.Status.WORKING ? 2 : 3)
				.thenComparingInt(r -> pinOrder.getOrDefault(key(r), Integer.MAX_VALUE)));
			if (candidates.size() > DESKS) candidates = new ArrayList<>(candidates.subList(0, DESKS));
			boolean[] used = new boolean[DESKS];
			Map<String, Integer> assigned = new LinkedHashMap<>();
			// Keep selected residents in place before allocating any newcomers.
			for (T3State.ThreadRow row : candidates) {
				String key = key(row); Integer desk = prefs.desks.get(key);
				if (desk != null && desk >= 0 && desk < DESKS && !used[desk]) { assigned.put(key, desk); used[desk] = true; }
			}
			for (T3State.ThreadRow row : candidates) {
				String key = key(row);
				if (!assigned.containsKey(key)) for (int desk = 0; desk < DESKS; desk++) if (!used[desk]) { assigned.put(key, desk); used[desk] = true; break; }
				int desk = assigned.get(key); prefs.desks.put(key, desk); retain.add(key); result.add(new Resident(row, desk));
			}
		}
		// Preserve a pin through empty reconnect snapshots. An unavailable pin has no resident,
		// so its desk may be used temporarily; on return pins win any slot conflict.
		for (Pin pin : prefs.pins) retain.add(pin.key());
		prefs.desks.keySet().retainAll(retain);
		return result;
	}
}

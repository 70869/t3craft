package dev.agentcraft.client.t3;

import com.google.gson.JsonObject;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public final class OfficeChecks {
	public static void main(String[] args) throws Exception {
		String owner = "http://127.0.0.1:3773";
		var rows = new ArrayList<T3State.ThreadRow>();
		for (int i = 0; i < 10; i++) { var raw = new JsonObject(); raw.addProperty("projectId", "p"); rows.add(new T3State.ThreadRow("t" + i, "Chat " + i, "Project", "Machine", owner, i == 8 ? T3State.Status.NEEDS_YOU : T3State.Status.IDLE, Instant.now(), null, raw)); }
		var prefs = new OfficeRoster.Preferences(); prefs.pin(rows.get(9));
		var residents = OfficeRoster.sync(rows, List.of(owner), prefs);
		check(residents.size() == 6, "HQ has six desk slots");
		check(residents.stream().anyMatch(r -> r.row().id().equals("t9")), "pinned older chat is visible");
		check(residents.stream().anyMatch(r -> r.row().id().equals("t8")), "waiting older chat is visible");
		var before = Map.copyOf(prefs.desks); java.util.Collections.reverse(rows); OfficeRoster.sync(rows, List.of(owner), prefs);
		for (var r : residents) if (prefs.desks.containsKey(OfficeRoster.key(r.row()))) check(prefs.desks.get(OfficeRoster.key(r.row())).equals(before.get(OfficeRoster.key(r.row()))), "retained identities do not move desks");
		OfficeRoster.sync(List.of(), List.of(owner), prefs); check(prefs.pins.size() == 1, "pin survives offline snapshot");
		String second="http://127.0.0.1:3774";
		var remote=new T3State.ThreadRow("remote", "Remote waiting", "Other project", "Second machine", second, T3State.Status.NEEDS_YOU, Instant.now(), null, new JsonObject());
		rows.add(remote); residents=OfficeRoster.sync(rows,List.of(owner,second),prefs);
		check(residents.size()==6,"capacity is global across machines");
		check(residents.stream().anyMatch(r->r.row().ownerKey().equals(second)),"waiting chat from second machine is represented");
		check(residents.stream().map(OfficeRoster.Resident::desk).distinct().count()==6,"each resident has its own physical desk");
		var full=new OfficeRoster.Preferences(); for(int i=0;i<6;i++) check(full.pin(rows.get(i)),"six pins allowed");
		check(!full.pin(remote),"seventh pin rejected across machines");
		var restored=new com.google.gson.Gson().fromJson(new com.google.gson.Gson().toJson(prefs),OfficeRoster.Preferences.class);
		var after=OfficeRoster.sync(rows,List.of(owner,second),restored);
		for(var resident:residents) check(after.stream().anyMatch(r->r.row().id().equals(resident.row().id()) && r.desk()==resident.desk()),"saved desk identity survives restart");
		checkNpcNames(rows, owner, second);
		System.out.println("PASS six desk capacity, pin and waiting priority, stable identity, offline pins and saved NPC names");
	}
	private static void checkNpcNames(List<T3State.ThreadRow> rows, String owner, String second) throws Exception {
		String cast = dev.agentcraft.Cast.ids().getFirst();
		String original = dev.agentcraft.Cast.get(cast).name();
		var prefs = new OfficeRoster.Preferences();
		check(prefs.npcName(cast).equals(original), "original name is the default");
		prefs.renameNpc(cast, "  Café Builder  ");
		check(prefs.npcName(cast).equals("Café Builder"), "custom name supports Unicode and trims edges");
		OfficeRoster.sync(rows, List.of(owner, second), prefs);
		OfficeRoster.sync(List.of(), List.of(owner, second), prefs);
		check(prefs.npcName(cast).equals("Café Builder"), "name survives roster changes and offline snapshots");
		for (String invalid : List.of("   ", "x".repeat(33), "Two\nLines", "§aGreen")) {
			try { prefs.renameNpc(cast, invalid); throw new AssertionError("invalid name accepted"); }
			catch (IllegalArgumentException expected) { }
			check(prefs.npcName(cast).equals("Café Builder"), "invalid edits preserve the saved name");
		}
		var config = new T3Config();
		config.officePreferences.put("world-one|overworld", prefs);
		config.officePreferences.put("world-two|overworld", new OfficeRoster.Preferences());
		java.nio.file.Path file = java.nio.file.Files.createTempFile("t3craft-npc-names-", ".json");
		try {
			check(config.save(file), "NPC preferences can be saved to disk");
			var loaded = T3Config.load(file);
			check(loaded.officePreferences.get("world-one|overworld").npcName(cast).equals("Café Builder"), "NPC name survives config reload");
			check(loaded.officePreferences.get("world-two|overworld").npcName(cast).equals(original), "custom name stays in its own world");
		} finally { java.nio.file.Files.deleteIfExists(file); }
		prefs.renameNpc(cast, original);
		check(prefs.npcName(cast).equals(original) && !prefs.npcNames.containsKey(cast), "reset removes the custom name");
		var legacy = new com.google.gson.Gson().fromJson("{\"pins\":[],\"desks\":{},\"npcNames\":null}", OfficeRoster.Preferences.class);
		legacy.normalize();
		check(legacy.npcName(cast).equals(original), "older config without names remains supported");
	}
	private static void check(boolean result, String message) { if (!result) throw new AssertionError(message); }
}

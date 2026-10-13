package dev.agentcraft.client.t3;

import com.google.gson.JsonObject;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public final class OfficeChecks {
	public static void main(String[] args) throws Exception {
		var defaultLooks = new OfficeRoster.Preferences();
		check(defaultLooks.npcSkin("marlow").equals("spartan"), "Master Chief is visible without manually cycling looks");
		check(defaultLooks.npcSkin("juniper").equals("naruto"), "Naruto is visible by default");
		check(defaultLooks.npcSkin("kit").equals("snake"), "Solid Snake is visible by default");
		check(defaultLooks.npcSkin("wren").equals("mario"), "Mario is visible by default");
		check(OfficeRoster.desiredCount(List.of(), defaultLooks) >= 4, "idle campus still shows all four gaming characters");
		defaultLooks.npcSkins.put("juniper", "default");
		check(defaultLooks.npcSkin("juniper").equals("naruto"), "legacy original preference migrates to gaming default");
		check(defaultLooks.npcSkin("rowan").equals("yoshi") && defaultLooks.npcSkin("tove").equals("megaman"), "Yoshi and Mega Man replace the remaining original defaults");
		check(dev.agentcraft.Cast.ids().size() == OfficeRoster.DESKS, "every resident has a desk identity");
		check(dev.agentcraft.Cast.ids().stream().map(dev.agentcraft.NpcLooks::defaultFor).distinct().count() == OfficeRoster.DESKS, "every default character is distinct");
		check(!dev.agentcraft.NpcLooks.slim("juniper", "naruto"), "gaming looks have their own wide-arm model");
		check(!dev.agentcraft.NpcLooks.valid(null), "null skin preference is rejected without a crash");
		String owner = "http://127.0.0.1:3773";
		var rows = new ArrayList<T3State.ThreadRow>();
		for (int i = 0; i < 24; i++) { var raw = new JsonObject(); raw.addProperty("projectId", "p"); rows.add(new T3State.ThreadRow("t" + i, "Chat " + i, "Project", "Machine", owner, i == 8 ? T3State.Status.NEEDS_YOU : T3State.Status.IDLE, Instant.now(), null, raw)); }
		var prefs = new OfficeRoster.Preferences(); prefs.pin(rows.get(9));
		var residents = OfficeRoster.sync(rows, List.of(owner), prefs);
		check(residents.size() == 18, "HQ has 18 desk slots");
		check(residents.stream().anyMatch(r -> r.row().id().equals("t9")), "pinned older chat is visible");
		check(residents.stream().anyMatch(r -> r.row().id().equals("t8")), "waiting older chat is visible");
		var before = Map.copyOf(prefs.desks); java.util.Collections.reverse(rows); OfficeRoster.sync(rows, List.of(owner), prefs);
		for (var r : residents) if (prefs.desks.containsKey(OfficeRoster.key(r.row()))) check(prefs.desks.get(OfficeRoster.key(r.row())).equals(before.get(OfficeRoster.key(r.row()))), "retained identities do not move desks");
		OfficeRoster.sync(List.of(), List.of(owner), prefs); check(prefs.pins.size() == 1, "pin survives offline snapshot");
		String second="http://127.0.0.1:3774";
		var remote=new T3State.ThreadRow("remote", "Remote waiting", "Other project", "Second machine", second, T3State.Status.NEEDS_YOU, Instant.now(), null, new JsonObject());
		rows.add(remote); residents=OfficeRoster.sync(rows,List.of(owner,second),prefs);
		check(residents.size()==18,"capacity is global across machines");
		check(residents.stream().anyMatch(r->r.row().ownerKey().equals(second)),"waiting chat from second machine is represented");
		check(residents.stream().map(OfficeRoster.Resident::desk).distinct().count()==18,"each resident has its own physical desk");
		var full=new OfficeRoster.Preferences(); for(int i=0;i<18;i++) check(full.pin(rows.get(i)),"18 pins allowed");
		check(!full.pin(rows.get(22)),"nineteenth pin rejected across machines");
		check(OfficeRoster.desiredCount(List.of(), full) == 18, "automatic staffing never hides pinned desks");
		checkThemes();
		full.displayViews.put("atrium", "status"); full.displayViews.put("bad", "unknown"); full.normalize();
		var displayRestored = new com.google.gson.Gson().fromJson(new com.google.gson.Gson().toJson(full), OfficeRoster.Preferences.class);
		check(displayRestored.displayViews.get("atrium").equals("status") && !displayRestored.displayViews.containsKey("bad"), "valid display views persist and unknown views are rejected");
		var restored=new com.google.gson.Gson().fromJson(new com.google.gson.Gson().toJson(prefs),OfficeRoster.Preferences.class);
		var after=OfficeRoster.sync(rows,List.of(owner,second),restored);
		for(var resident:residents) check(after.stream().anyMatch(r->r.row().id().equals(resident.row().id()) && r.desk()==resident.desk()),"saved desk identity survives restart");
		checkNpcNames(rows, owner, second);
		System.out.println("PASS 18 desk capacity, pin and waiting priority, stable identity, offline pins and saved NPC names");
	}
	private static void checkNpcNames(List<T3State.ThreadRow> rows, String owner, String second) throws Exception {
		String cast = dev.agentcraft.Cast.ids().getFirst();
		String original = new OfficeRoster.Preferences().npcName(cast);
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
	private static void checkThemes() throws Exception {
		var themes = dev.agentcraft.client.ui.T3Theme.presets();
		check(themes.size() == 6, "all six pinned T3 Code palettes are shipped");
		check(dev.agentcraft.client.ui.T3Theme.normalize("old-missing-theme").equals("t3-code"), "unknown themes recover to stock T3 Code");
		for (var preset : themes) for (boolean dark : List.of(false, true)) {
			dev.agentcraft.client.ui.T3Theme.apply(preset.id(), dark, "");
			var colors = dark ? preset.dark() : preset.light();
			check(dev.agentcraft.client.ui.T3Theme.color("canvas") == colors.get("canvas"), "canvas matches pinned upstream palette");
			check(dev.agentcraft.client.ui.UiStyle.color("paper.text") == colors.get("text"), "retained screen text follows the same palette");
			check(dev.agentcraft.client.monitor.ScreenStyle.current().text() == colors.get("text"), "world monitors follow the screen theme");
			long revision = dev.agentcraft.client.ui.T3Theme.revision(); dev.agentcraft.client.ui.T3Theme.apply(preset.id(), dark, "");
			check(revision == dev.agentcraft.client.ui.T3Theme.revision(), "unchanged theme does not invalidate per-frame caches");
			for (String accent : List.of("#FFFFFF", "#000000", "#00FF00", "#8B5CF6")) {
				dev.agentcraft.client.ui.T3Theme.apply(preset.id(), dark, accent);
				check(dev.agentcraft.client.ui.T3Theme.contrast(dev.agentcraft.client.ui.T3Theme.color("accent"), dev.agentcraft.client.ui.T3Theme.color("accentForeground")) >= 4.5, "custom accent button labels remain readable");
				check(dev.agentcraft.client.ui.T3Theme.contrast(dev.agentcraft.client.ui.T3Theme.color("accentText"), colors.get("canvas")) >= 4.5, "accent text remains readable on the selected canvas");
			}
		}
		check(!dev.agentcraft.client.ui.T3Theme.validAccent("#123") && !dev.agentcraft.client.ui.T3Theme.validAccent("red"), "invalid accents are rejected");
		var config = new T3Config(); config.theme = "ocean"; config.darkTheme = true; config.accentColor = "#8B5CF6";
		var path = java.nio.file.Files.createTempFile("t3craft-appearance-", ".json");
		try { check(config.save(path), "appearance can be persisted"); var loaded = T3Config.load(path); check(loaded.darkTheme && loaded.theme.equals("ocean") && loaded.accentColor.equals("#8B5CF6"), "palette, appearance and accent survive restart"); }
		finally { java.nio.file.Files.deleteIfExists(path); }
		dev.agentcraft.client.ui.T3Theme.apply("t3-code", false, "");
	}
}

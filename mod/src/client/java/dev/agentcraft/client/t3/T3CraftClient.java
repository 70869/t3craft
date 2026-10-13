// Adapted from maxwellyoung/t3craft (MIT), copyright 2026 Maxwell Young.
// See THIRD-PARTY-NOTICES.md and licenses/t3craft-client-MIT.txt.
package dev.agentcraft.client.t3;

import com.mojang.blaze3d.platform.InputConstants;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.ChatFormatting;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.toasts.SystemToast;
import net.minecraft.client.gui.screens.PauseScreen;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundEvents;
import org.slf4j.Logger;

import java.nio.file.Path;
import java.util.List;

import static net.fabricmc.fabric.api.client.command.v2.ClientCommands.argument;
import static net.fabricmc.fabric.api.client.command.v2.ClientCommands.literal;

/**
 * T3 Code inside Minecraft. Everything is client-side: prompts go straight to your T3
 * environment over HTTP and never touch server chat, so it works on any server.
 */
public final class T3CraftClient implements ClientModInitializer {
	public static final String MOD_ID = "agentcraft";
	public static final Logger LOGGER = T3Log.LOGGER;
	private static final SystemToast.SystemToastId TOAST = new SystemToast.SystemToastId(6000L);

	private static T3CraftClient instance;

	// Overridable so tests can pair with a sandbox without touching the real pairing.
	private final String configOverride = System.getProperty("t3craft.config", System.getenv("T3CRAFT_CONFIG"));
	private final Path configPath = configOverride != null
		? Path.of(configOverride)
		: FabricLoader.getInstance().getConfigDir().resolve("t3craft.json");
	private T3Config config;
	private T3Drafts drafts;
	private boolean draftsDirty;
	private long lastDraftSave;
	private T3State state;
	T3Screen.Navigation chatNavigation;
	private KeyMapping openKey;
	private KeyMapping decisionsKey;
	private boolean openPanelNextTick;
	private boolean initialSetupOffered;
	private int connectionRevision;
	private boolean pairingInProgress;
	private final java.util.Set<String> respondingRequests = new java.util.HashSet<>();
	private String lastPingThread;
	private long lastPingAt;
	private static final long PING_JUMP_WINDOW_MS = 60_000;

	public static T3CraftClient get() {
		return instance;
	}

	public T3State state() {
		return state;
	}
	public void retryConnections() { state.machines().forEach(machine -> state.retry(machine.owner())); }

	public boolean paired() {
		return config.paired();
	}
	boolean darkTheme() { return config.darkTheme; }
	String themeId() { return dev.agentcraft.client.ui.T3Theme.normalize(config.theme); }
	String accentColor() { return config.accentColor == null ? "" : config.accentColor; }
	boolean saveAppearance(String theme, boolean dark, String accent) {
		if (!dev.agentcraft.client.ui.T3Theme.validAccent(accent)) return false;
		String beforeTheme = config.theme, beforeAccent = config.accentColor; boolean beforeDark = config.darkTheme;
		config.theme = dev.agentcraft.client.ui.T3Theme.normalize(theme); config.darkTheme = dark; config.accentColor = accent == null || accent.isBlank() ? null : accent.toUpperCase(java.util.Locale.ROOT);
		if (!config.save(configPath)) { config.theme = beforeTheme; config.accentColor = beforeAccent; config.darkTheme = beforeDark; return false; }
		T3MenuScreen.applyTheme(dark); return true;
	}
	boolean toggleDarkTheme() {
		boolean previous = config.darkTheme;
		config.darkTheme = !previous;
		if (!config.save(configPath)) { config.darkTheme = previous; return false; }
		return true;
	}
	public boolean notificationVisible() { return Minecraft.getInstance().gui.toastManager().getToast(SystemToast.class,TOAST)!=null; }

	@Override
	public void onInitializeClient() {
		instance = this;
		config = T3Config.load(configPath);
		T3MenuScreen.applyTheme(config.darkTheme);
		drafts = new T3Drafts(config.drafts);
		state = new T3State(event -> Minecraft.getInstance().execute(() -> announce(event)));
		connectAll();
		ScreenEvents.AFTER_INIT.register((client, screen, w, h) -> {
			if (screen instanceof PauseScreen) {
				int bw = Math.min(220, w - 32);
				Button settings = Button.builder(Component.literal("T3Craft Settings"), b -> client.gui.setScreen(new T3ModSettingsScreen(this, screen)))
					.bounds((w - bw) / 2, h - 38, bw, 20).build();
				((dev.agentcraft.client.mixin.ScreenAccessor) screen).agentcraft$addRenderableWidget(settings);
			}
		});

		// One set of bindings shared with the HQ stations; duplicate bindings can reopen a closed panel.
		dev.agentcraft.client.hud.Keys.ensureRegistered();
		openKey = dev.agentcraft.client.hud.Keys.console;
		decisionsKey = dev.agentcraft.client.hud.Keys.decisions;

		ClientTickEvents.END_CLIENT_TICK.register(client -> {
			if (!initialSetupOffered && client.level != null && client.player != null && client.gui.screen() == null) {
				initialSetupOffered = true;
				if (!config.paired()) openConnections();
			}
			while (dev.agentcraft.client.hud.Keys.campus.consumeClick()) if (client.gui.screen() == null) openCampus();
			if (draftsDirty && System.currentTimeMillis() - lastDraftSave > 500) flushDrafts();
			while (decisionsKey.consumeClick()) if (client.gui.screen() == null) openDecisions();
			while (openKey.consumeClick()) {
				if (client.gui.screen() != null) continue;
				openPanelNextTick = true;
				// Answering a ping: open the thread that pinged, not whatever was focused before.
				if (lastPingThread != null && System.currentTimeMillis() - lastPingAt < PING_JUMP_WINDOW_MS) focus(lastPingThread);
				lastPingThread = null;
			}
			if (openPanelNextTick && client.gui.screen() == null) {
				openPanelNextTick = false;
				if (config.paired() && !config.workflowGuideSeen) openGuide(); else openPanel();
			}
		});
		net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents.CLIENT_STOPPING.register(client -> flushDrafts());

		HudElementRegistry.addLast(Identifier.fromNamespaceAndPath(MOD_ID, "status"), new T3Hud(this));
		ClientCommandRegistrationCallback.EVENT.register((dispatcher, context) -> registerCommands(dispatcher));
		for(String kind:List.of("board","library","reviews")) dev.agentcraft.client.dev.DevBridge.registerScreen("t3-"+kind, mc->studioScreen(kind,null));
        dev.agentcraft.client.dev.DevBridge.registerScreen("t3", mc -> new T3Screen(this));
		dev.agentcraft.client.dev.DevBridge.registerScreen("t3-mod-settings", mc -> new T3ModSettingsScreen(this, null));
		dev.agentcraft.client.dev.DevBridge.registerScreen("t3-appearance", mc -> new T3AppearanceScreen(this, new T3ModSettingsScreen(this, null)));
		dev.agentcraft.client.dev.DevBridge.registerScreen("t3-displays", mc -> new T3DisplayScreen(this, null, "atrium", "feed"));
		dev.agentcraft.client.dev.DevBridge.registerScreen("t3-look", mc -> new T3NpcLookScreen(this, "marlow", new T3ModSettingsScreen(this, null)));
		dev.agentcraft.client.dev.DevBridge.registerScreen("t3-campus", mc -> new T3CampusScreen(this));
		dev.agentcraft.client.dev.DevBridge.registerScreen("t3-work", mc -> new T3WorkScreen(this));
		dev.agentcraft.client.dev.DevBridge.registerScreen("t3-guide", mc -> new T3GuideScreen(this));
		dev.agentcraft.client.dev.DevBridge.registerScreen("t3-connections", mc -> new T3ConnectionsScreen(this));
		dev.agentcraft.client.dev.DevBridge.registerScreen("t3-history", mc -> state.focusedThreadId() == null ? new T3Screen(this) : new T3HistoryScreen(this, state.focusedThreadId()));
		dev.agentcraft.client.dev.DevBridge.registerScreen("t3-settings", mc -> state.snapshot().focusedRow() == null ? new T3Screen(this) : new T3SettingsScreen(this, state.snapshot().focusedRow()));
		for (String tool : List.of("tools", "archive", "options", "queue", "workspace", "worktrees", "terminal", "tasks", "schedules", "git"))
			dev.agentcraft.client.dev.DevBridge.registerScreen("t3-" + tool, mc -> {
				var row = state.snapshot().focusedRow(); if (row == null) return new T3Screen(this);
				return switch (tool) {
					case "archive" -> new T3ArchiveScreen(this, row); case "options" -> new T3OptionsScreen(this, row);
					case "queue" -> new T3QueueScreen(this, row); case "workspace" -> new T3WorkspaceScreen(this, row, false);
					case "worktrees" -> new T3WorkspaceScreen(this, row, true); case "terminal" -> new T3TerminalScreen(this, row);
					case "tasks" -> new T3TasksScreen(this, row); case "schedules" -> new T3SchedulesScreen(this, row);
					case "git" -> new T3GitScreen(this, row); default -> new T3ToolsScreen(this, row);
				};
			});
		dev.agentcraft.client.dev.DevBridge.register("dev.t3", 5_000, "{} -> T3 connection and history counts (no credentials or chat contents)", (req, mc) -> dev.agentcraft.client.dev.DevBridge.onClient(mc, () -> {
			var result = new com.google.gson.JsonObject(); var snap = state.snapshot();
			result.addProperty("paired", paired()); result.addProperty("connected", snap.connected()); result.addProperty("live", state.live()); result.addProperty("threads", snap.threads().size());
			result.addProperty("focusedMessages", snap.focus() == null ? 0 : snap.focus().messages().size()); result.addProperty("residents", residents().size());
			return result;
		}));
		dev.agentcraft.client.dev.DevBridge.register("dev.t3.question", 5_000, "{click?:{x,y}, scroll?:number} -> fixture-only question dialog QA", (req, mc) -> dev.agentcraft.client.dev.DevBridge.onClient(mc, () -> {
			if (!(mc.gui.screen() instanceof T3QuestionScreen question)) throw new IllegalStateException("Open an agent question first.");
			var before = question.inspectFixture();
			if (req.has("click")) {
				var click = req.getAsJsonObject("click");
				question.mouseClicked(new net.minecraft.client.input.MouseButtonEvent(click.get("x").getAsDouble(), click.get("y").getAsDouble(), new net.minecraft.client.input.MouseButtonInfo(com.mojang.blaze3d.platform.InputConstants.MOUSE_BUTTON_LEFT, 0)), false);
			}
			if (req.has("scroll")) question.mouseScrolled(before.get("x").getAsInt() + 20, before.get("bodyY").getAsInt() + 10, 0, req.get("scroll").getAsDouble());
			return question.inspectFixture();
		}));
		dev.agentcraft.client.dev.DevBridge.register("dev.t3.residentWalk", 5_000, "{agent, from:{x,y,z}, to:{x,y,z}} or {clear:true} -> temporary fixture-only resident route QA", (req, mc) -> dev.agentcraft.client.dev.DevBridge.onClient(mc, () -> {
			if (state.api() == null || !state.api().fixtureMode() || state.machines().stream().anyMatch(machine -> { var api = state.apiForOwner(machine.owner()); return api == null || !api.fixtureMode(); })) throw new IllegalStateException("Resident movement QA requires isolated fixtures.");
			var manager = dev.agentcraft.client.agents.AgentManager.get();
			var fields = dev.agentcraft.client.dev.Fields.of(req);
			if (fields.optBool("clear", false)) manager.clearPreviews();
			else {
				var from = fields.obj("from"); var to = fields.obj("to");
				manager.previewWalk(fields.nonBlank("agent"), new net.minecraft.world.phys.Vec3(from.num("x"), from.num("y"), from.num("z")), new net.minecraft.world.phys.Vec3(to.num("x"), to.num("y"), to.num("z")));
			}
			var out = new com.google.gson.JsonObject(); out.addProperty("ok", true); return out;
		}));
		dev.agentcraft.client.dev.DevBridge.register("dev.t3.menu", 5_000, "{click?:label} -> fixture-only native menu layout and interaction QA", (req, mc) -> dev.agentcraft.client.dev.DevBridge.onClient(mc, () -> {
			boolean unpairedSetup = !paired() && mc.gui.screen() instanceof T3ConnectionsScreen && !req.has("click") && !req.has("clickAt") && !req.has("focusThread");
			if (!unpairedSetup && (state.api() == null || !state.api().fixtureMode() || state.machines().stream().anyMatch(machine -> { var api = state.apiForOwner(machine.owner()); return api == null || !api.fixtureMode(); }))) throw new IllegalStateException("Menu QA requires isolated fixtures, or a read-only unpaired Connections inspection.");
			if (req.has("focusThread")) { String id = req.get("focusThread").getAsString(); if (state.snapshot().threads().stream().noneMatch(r -> r.id().equals(id))) throw new IllegalArgumentException("Unknown fixture thread"); focus(id); }
			if (!(mc.gui.screen() instanceof T3MenuScreen menu)) throw new IllegalStateException("Open a T3 menu first.");
			if (req.has("clickAt")) {
				var point = req.getAsJsonObject("clickAt"); double x = point.get("x").getAsDouble(), y = point.get("y").getAsDouble();
				if (!Double.isFinite(x) || !Double.isFinite(y) || x < 0 || y < 0 || x >= menu.width || y >= menu.height) throw new IllegalArgumentException("Click must be inside the fixture menu");
				menu.mouseClicked(new net.minecraft.client.input.MouseButtonEvent(x, y, new net.minecraft.client.input.MouseButtonInfo(InputConstants.MOUSE_BUTTON_LEFT, 0)), false);
			}
			if (req.has("click")) {
				String label = req.get("click").getAsString();
				var widget = menu.children().stream().filter(child -> child instanceof net.minecraft.client.gui.components.AbstractWidget w && w.getMessage().getString().equals(label) && w.visible).findFirst().orElseThrow();
				var w = (net.minecraft.client.gui.components.AbstractWidget) widget;
				menu.mouseClicked(new net.minecraft.client.input.MouseButtonEvent(w.getX() + w.getWidth() / 2.0, w.getY() + w.getHeight() / 2.0, new net.minecraft.client.input.MouseButtonInfo(InputConstants.MOUSE_BUTTON_LEFT, 0)), false);
			}
			var out = new com.google.gson.JsonObject(); out.addProperty("screen", mc.gui.screen() == null ? null : mc.gui.screen().getClass().getSimpleName());
			out.addProperty("theme", themeId()); out.addProperty("darkTheme", config.darkTheme); out.addProperty("accentColor", accentColor());
			var colors = new com.google.gson.JsonObject(); for (String role : List.of("canvas", "text", "textMuted", "accent", "accentForeground", "codeBackground")) colors.addProperty(role, String.format("#%06X", dev.agentcraft.client.ui.T3Theme.color(role) & 0xFFFFFF)); out.add("colors", colors);
			var widgets = new com.google.gson.JsonArray();
			for (var child : menu.children()) if (child instanceof net.minecraft.client.gui.components.AbstractWidget widget && widget.visible) {
				var item = new com.google.gson.JsonObject(); item.addProperty("label", widget.getMessage().getString()); item.addProperty("active", widget.active);
				item.addProperty("x", widget.getX()); item.addProperty("y", widget.getY()); item.addProperty("width", widget.getWidth()); item.addProperty("height", widget.getHeight()); widgets.add(item);
			}
			out.add("widgets", widgets); if (menu instanceof T3Screen chat) out.add("chat", chat.inspectFixture());
			if (menu instanceof T3TaskScreen npc) out.add("npc", npc.inspectFixture());
			if (menu instanceof T3DraftEditorScreen draft) out.add("draft", draft.inspectFixture());
			if (menu instanceof T3TerminalScreen terminal) out.add("terminal", terminal.inspectFixture());
			if (menu instanceof T3FormScreen form && form.row != null) { out.addProperty("threadId", form.row.id()); out.addProperty("owner", form.row.ownerKey()); }
			return out;
		}));
		dev.agentcraft.client.dev.DevBridge.register("dev.t3.review", 5_000, "{selectFile?:path} -> fixture-only checkpoint file selection and readiness", (req, mc) -> dev.agentcraft.client.dev.DevBridge.onClient(mc, () -> {
			if (!(mc.gui.screen() instanceof T3ReviewScreen review)) throw new IllegalStateException("Open a checkpoint review first.");
			return review.inspectFixture(req.has("selectFile") ? req.get("selectFile").getAsString() : null);
		}));
		dev.agentcraft.client.dev.DevBridge.register("dev.t3.form", 5_000, "{fields?:{label:value}, action?:label} -> fixture-only workflow form QA", (req, mc) -> dev.agentcraft.client.dev.DevBridge.onClient(mc, () -> {
			if (!(mc.gui.screen() instanceof T3FormScreen form) || form.api == null || !form.api.fixtureMode()) throw new IllegalStateException("Open a workflow screen connected to an isolated fixture first.");
			if (req.has("fields")) { for (var value : req.getAsJsonObject("fields").entrySet()) { var field = form.fields.stream().filter(f -> f.label.equals(value.getKey())).findFirst().orElseThrow(); String text = value.getValue().getAsString(); if (!field.choices.isEmpty() && !field.choices.contains(text)) throw new IllegalArgumentException("Invalid field choice"); field.value = text; } form.refreshWidgets(); }
			if (req.has("action")) { if (form.busy) throw new IllegalStateException("Wait for the current fixture request."); form.actions.stream().filter(a -> a.label().equals(req.get("action").getAsString())).findFirst().orElseThrow().run().run(); }
			var result = new com.google.gson.JsonObject(); result.addProperty("busy", form.busy); result.addProperty("message", form.message); result.addProperty("output", form.output); var fields = new com.google.gson.JsonObject(); for (var field : form.fields) fields.addProperty(field.label, field.value); result.add("fields", fields); return result;
		}));
	}

	private static String worldKey() {
		var mc = Minecraft.getInstance();
		if (mc.level == null) return null;
		String world = mc.getSingleplayerServer() != null
			? mc.getSingleplayerServer().getWorldPath(net.minecraft.world.level.storage.LevelResource.ROOT).toAbsolutePath().toString()
			: mc.getCurrentServer() == null ? "unknown" : mc.getCurrentServer().ip;
		return world + "|" + mc.level.dimension().identifier();
	}
	OfficeRoster.Preferences officePreferences() {
		String world = worldKey();
		return world == null ? new OfficeRoster.Preferences() : config.officePreferences.computeIfAbsent(world, k -> new OfficeRoster.Preferences());
	}
	boolean canPin() { return worldKey() != null; }
	public String npcName(String cast) { return officePreferences().npcName(cast); }
	public String displayView(String key) { var prefs = officePreferences(); return prefs.displayViews == null ? "default" : prefs.displayViews.getOrDefault(key, "default"); }
	public boolean setDisplayView(String key, String view) {
		if (!canPin() || !dev.agentcraft.DisplayViews.valid(view)) return false;
		var p = officePreferences(); p.normalize(); String previous = p.displayViews.put(key, view);
		if (config.save(configPath)) return true;
		if (previous == null) p.displayViews.remove(key); else p.displayViews.put(key, previous); return false;
	}
	public void openDisplay(String key, String binding) { Minecraft.getInstance().gui.setScreen(new T3DisplayScreen(this, Minecraft.getInstance().gui.screen(), key, binding)); }
	String defaultNpcName(String cast) { String skin = npcSkin(cast); return skin.equals("default") ? dev.agentcraft.Cast.get(cast).name() : dev.agentcraft.NpcLooks.label(skin); }
	static boolean validSkin(String skin) { return dev.agentcraft.NpcLooks.valid(skin); }
	public String npcSkin(String cast) { return officePreferences().npcSkin(cast); }
	public String npcSkinResource(String cast) { String selected = npcSkin(cast); return selected.equals("default") ? cast : selected; }
	boolean setNpcSkin(String cast, String skin) {
		if (!canPin() || dev.agentcraft.Cast.get(cast) == null || !validSkin(skin)) return false;
		var p = officePreferences(); String previous = p.npcSkins.put(cast, skin);
		if (config.save(configPath)) return true;
		if (previous == null) p.npcSkins.remove(cast); else p.npcSkins.put(cast, previous); return false;
	}
	public boolean showProjectLabels() { return officePreferences().showProjectLabels; }
	void cycleNpcSkin(String cast) {
		var prefs = officePreferences(); var choices = dev.agentcraft.NpcLooks.IDS;
		int next = (choices.indexOf(npcSkin(cast)) + 1) % choices.size();
		prefs.npcSkins.put(cast, choices.get(next));
		saveSettings();
	}
	boolean gamingRoster() {
		var p = officePreferences();
		if (!canPin()) return false;
		var previous = new java.util.LinkedHashMap<>(p.npcSkins); boolean auto = p.autoStaffing; int count = p.fixedNpcCount;
		for (String cast : dev.agentcraft.Cast.ids()) if (!dev.agentcraft.NpcLooks.defaultFor(cast).equals("default")) p.npcSkins.put(cast, dev.agentcraft.NpcLooks.defaultFor(cast));
		p.autoStaffing = false; p.fixedNpcCount = OfficeRoster.DESKS;
		if (config.save(configPath)) return true;
		p.npcSkins = previous; p.autoStaffing = auto; p.fixedNpcCount = count; return false;
	}
	void toggleStaffing() { officePreferences().autoStaffing = !officePreferences().autoStaffing; saveSettings(); }
	void cycleFixedCount() { var p = officePreferences(); p.fixedNpcCount = p.fixedNpcCount % OfficeRoster.DESKS + 1; saveSettings(); }
	void toggleProjectLabels() { var p = officePreferences(); p.showProjectLabels = !p.showProjectLabels; saveSettings(); }
	public int visibleNpcCount() { return OfficeRoster.desiredCount(state.snapshot().threads(), officePreferences()); }
	public boolean automaticStaffing() { return officePreferences().autoStaffing; }
	private void saveSettings() { if (canPin() && !config.save(configPath)) chat(Component.literal("Couldn't save T3Craft settings; check the config folder.")); }
	Path updateMetadataPath() { return FabricLoader.getInstance().getConfigDir().resolve("t3craft-updater.json"); }
	com.google.gson.JsonObject updateMetadata() {
		try {
			String json = java.nio.file.Files.readString(updateMetadataPath());
			if (!json.isEmpty() && json.charAt(0) == '\uFEFF') json = json.substring(1);
			return com.google.gson.JsonParser.parseString(json).getAsJsonObject();
		}
		catch (Exception ignored) { return new com.google.gson.JsonObject(); }
	}
	public void openModSettings(net.minecraft.client.gui.screens.Screen parent) { Minecraft.getInstance().gui.setScreen(new T3ModSettingsScreen(this, parent)); }
	String renameNpc(String cast, String name) {
		if (!canPin()) return "Join a world to rename an NPC.";
		var prefs = officePreferences();
		String previousName = prefs.npcNames.get(cast);
		try { prefs.renameNpc(cast, name); }
		catch (IllegalArgumentException e) { return e.getMessage(); }
		if (config.save(configPath)) return null;
		if (previousName == null) prefs.npcNames.remove(cast);
		else prefs.npcNames.put(cast, previousName);
		return "Could not save the name. Check Minecraft's config folder permissions.";
	}
	boolean pinned(T3State.ThreadRow row) { return row != null && officePreferences().pinned(row); }
	void togglePin(T3State.ThreadRow row) {
		if (row == null || !canPin()) return;
		var prefs = officePreferences();
		var previous = new java.util.ArrayList<>(prefs.pins);
		if (prefs.pinned(row)) prefs.pins.removeIf(p -> p.key().equals(OfficeRoster.key(row)));
		else if (!prefs.pin(row)) { chat(Component.literal("All 18 desks are pinned. Unpin one first.")); return; }
		if (!config.save(configPath)) { prefs.pins = previous; chat(Component.literal("Could not save this desk pin.")); }
	}
	void unpin(OfficeRoster.Pin pin) { officePreferences().unpin(pin); config.save(configPath); }
	String machineLabel(String owner) { return config.labelFor(owner); }
	public List<T3State.ThreadRow> residents() {
		return studioBindings().values().stream().toList();
	}
	public java.util.Map<String, T3State.ThreadRow> studioBindings() {
		var prefs = officePreferences(); var before = new java.util.LinkedHashMap<>(prefs.desks);
		var residents = OfficeRoster.sync(state.snapshot().threads(), config.environments.stream().map(e -> T3Config.ownerKey(e.baseUrl)).toList(), prefs);
		if (canPin() && !before.equals(prefs.desks)) config.save(configPath);
		var ids = dev.agentcraft.Cast.ids().stream().toList();
		var result = new java.util.LinkedHashMap<String, T3State.ThreadRow>();
		int visible = OfficeRoster.desiredCount(state.snapshot().threads(), prefs);
		for (int i = 0; i < Math.min(visible, residents.size()); i++) {
			var resident = residents.get(i);
			result.put(ids.get(resident.desk()), resident.row());
		}
		state.observeStudio(result.values().stream().map(T3State.ThreadRow::id).collect(java.util.stream.Collectors.toSet()));
		return java.util.Collections.unmodifiableMap(result);
	}
	public com.google.gson.JsonArray decisionProjection(java.util.Map<String, String> bindings) {
		var result = new com.google.gson.JsonArray();
		for (var entry : state.decisions()) {
			if (entry.requestId() == null) continue;
			if (!state.online(entry.thread().id())) continue;
			String agent = bindings.entrySet().stream().filter(e -> e.getValue().equals(entry.thread().id())).map(java.util.Map.Entry::getKey).findFirst().orElse("");
			var decision = new com.google.gson.JsonObject();
			decision.addProperty("id", entry.thread().id() + ":" + entry.requestId()); decision.addProperty("agentId", agent);
			decision.addProperty("kind", entry.kind().equals("Question") ? "question" : "permission");
			decision.addProperty("question", entry.detail()); decision.addProperty("status", "open"); decision.add("options", new com.google.gson.JsonArray());
			decision.addProperty("taskId", entry.thread().id());
			decision.addProperty("createdAt", entry.requestedAt() == null ? 0 : entry.requestedAt().toEpochMilli());
			result.add(decision);
		}
		return result;
	}
	private List<T3State.Connection> connections() {
		List<T3State.Connection> connections = new java.util.ArrayList<>();
		for (T3Config.Environment env : config.environments) {
			connections.add(new T3State.Connection(new T3Api(env.baseUrl, env.accessToken), env.label));
		}
		return connections;
	}

	private void connectAll() { state.connect(connections(), config.threadId); }
	List<T3Config.Environment> environments() { return List.copyOf(config.environments); }
	public void openConnections() { Minecraft.getInstance().gui.setScreen(new T3ConnectionsScreen(this)); }
	boolean removeEnvironment(String owner) {
		connectionRevision++;
		var previous = new java.util.ArrayList<>(config.environments);
		String previousFocus = config.threadId;
		var row = state.snapshot().focusedRow();
		if (row != null && owner.equals(row.ownerKey()) || config.environments.size() == 1) config.threadId = null;
		config.environments.removeIf(e -> T3Config.ownerKey(e.baseUrl).equals(owner));
		if (!config.save(configPath)) { config.environments = previous; config.threadId = previousFocus; return false; }
		state.reconfigure(connections(), config.threadId); return true;
	}

	/** Both entry points share the exchange; callback messages never contain the link or token. */
	void pairEnvironment(String link, String label, String expectedOwner, java.util.function.Consumer<String> result) {
		if (pairingInProgress) { result.accept("A pairing is already in progress. Wait for it to finish."); return; }
		pairingInProgress = true;
		int revision = connectionRevision;
		Thread thread = new Thread(() -> {
			String error = null;
			try {
				if (expectedOwner != null && !expectedOwner.equals(T3Api.pairingAddress(link)))
					throw new java.io.IOException("This link belongs to another machine. Use Add machine, or copy a link from the selected machine.");
				T3Api.Pairing pairing = T3Api.pair(link, "Minecraft");
				String name = label == null || label.isBlank() ? T3Api.environmentLabel(pairing.baseUrl()) : label.trim();
				Minecraft.getInstance().execute(() -> {
					pairingInProgress = false;
					if (revision != connectionRevision) { result.accept("Connections changed during pairing. Try a fresh link."); return; }
					connectionRevision++;
					var previous = new java.util.ArrayList<>(config.environments);
					config.upsert(new T3Config.Environment(name, pairing.baseUrl(), pairing.accessToken()));
					if (!config.save(configPath)) { config.environments = previous; result.accept("Could not save this pairing. Check Minecraft's config folder permissions, then try a fresh link."); return; }
					state.reconfigure(connections(), config.threadId); result.accept(null);
				});
				return;
			} catch (T3Api.UnsupportedVersionException e) { error = e.getMessage(); }
			catch (java.io.IOException e) {
				error = e.getClass() == java.io.IOException.class ? e.getMessage() : "Could not reach this machine. Check T3 Code, the address and network, then try again.";
			} catch (Exception e) { error = "Pairing could not finish. Check T3 Code and try a fresh link."; }
			String message = error;
			Minecraft.getInstance().execute(() -> { pairingInProgress = false; result.accept(message); });
		}, "t3craft-pair");
		thread.setDaemon(true); thread.start();
	}

	public void openTools() {
        var row = state.snapshot().focusedRow();
        if (row == null) { openPanel(); return; }
        Minecraft.getInstance().gui.setScreen(new T3ToolsScreen(this, row));
    }
	public void openCampus() { Minecraft.getInstance().gui.setScreen(new T3CampusScreen(this)); }
	public void openWork() { Minecraft.getInstance().gui.setScreen(new T3WorkScreen(this)); }
	public void openGuide() {
		if (!config.workflowGuideSeen) { config.workflowGuideSeen = true; config.save(configPath); }
		Minecraft.getInstance().gui.setScreen(new T3GuideScreen(this));
	}
	T3Workflow.Action nextAction(T3State.ThreadRow row) {
		boolean request = state.decisions().stream().anyMatch(e -> e.thread().id().equals(row.id()) && e.requestId() != null);
		return T3Workflow.next(row, state.online(row.id()), request, reviewReady(row.id()));
	}
	public boolean reviewReady(String id) {
		var detail = state.studioDetail(id);
		return detail != null && T3Protocol.array(detail, "checkpoints").asList().stream()
			.anyMatch(v -> v.isJsonObject() && "ready".equals(T3Protocol.string(v.getAsJsonObject(), "status")));
	}
	public void openNextAction(String id) {
		var row = state.snapshot().threads().stream().filter(r -> r.id().equals(id)).findFirst().orElse(null);
		if (row == null) { openWork(); return; }
		focus(id);
		switch (nextAction(row)) {
			case CONNECT -> openConnections();
			case ANSWER -> openThreadDecisions(id);
			case REVIEW -> openReview(id);
			case CHAT -> openPanel();
		}
	}
	void openComposer(String id, boolean steer) {
		var row = state.snapshot().threads().stream().filter(r -> r.id().equals(id)).findFirst().orElse(null);
		if (row == null || !state.online(id)) { openConnections(); return; }
		String key = T3Drafts.key(row.ownerKey(), row.id(), false); var entry = draftEntry(key);
		if (entry.sending) { focus(id); openPanel(); return; }
		if (entry.prepared == null) entry.delivery = steer ? "steer" : "queue";
		draftChanged(); focus(id); Minecraft.getInstance().gui.setScreen(new T3DraftEditorScreen(this, row, key, false));
	}
	public void openPanel() {
		Minecraft minecraft = Minecraft.getInstance();
		if (!config.paired()) {
			openConnections();
			return;
		}
		minecraft.gui.setScreen(new T3Screen(this));
	}


	public void openDecisions() {
		if (paired()) Minecraft.getInstance().gui.setScreen(new T3DeskScreen(this));
		else openPanel();
	}
	public void openThreadDecisions(String threadId) {
		var entry=state.decisions().stream().filter(e->e.thread().id().equals(threadId)&&e.requestId()!=null).findFirst().orElse(null);
		if(entry!=null) openRequest(entry); else { focus(threadId); openPanel(); }
	}

	void openRequest(T3Decisions.Entry entry) {
		focus(entry.thread().id());
		Minecraft.getInstance().gui.setScreen(entry.kind().equals("Question")
			? questionScreen(entry.thread().id(), entry.requestId(), null)
			: new T3Screen(this, entry.requestId()));
	}

	private T3QuestionScreen questionScreen(String threadId, String requestId, String cast) {
		if (cast == null) cast = studioBindings().entrySet().stream().filter(e -> e.getValue().id().equals(threadId))
			.map(java.util.Map.Entry::getKey).findFirst().orElse(null);
		return new T3QuestionScreen(this, threadId, requestId, cast);
	}

	public void openReview(String threadId) {
		if (threadId != null) Minecraft.getInstance().gui.setScreen(new T3ReviewScreen(this, threadId));
	}
	public void openBoard() { Minecraft.getInstance().gui.setScreen(new T3BoardScreen(this)); }
	public net.minecraft.client.gui.screens.Screen studioScreen(String kind, String id) {
		return switch (kind) {
			case "agent" -> { var row=studioBindings().get(id); yield new T3TaskScreen(this,row==null?null:row.id(),id); }
			case "task" -> new T3TaskScreen(this,id,null);
			case "library" -> new T3LibraryScreen(this,false);
			case "reviews" -> new T3LibraryScreen(this,true);
			case "decisions" -> new T3DeskScreen(this);
			case "board" -> new T3BoardScreen(this);
			default -> new T3Screen(this);
		};
	}
	/** Only checkpoints actually fetched from T3 are advertised on the physical review desk. */
	public List<T3State.ThreadRow> readyReviews() {
		var details=state.studioDetails();
		return state.snapshot().threads().stream().filter(row->{
			if(!state.online(row.id())) return false;
			var detail=details.get(row.id());
			if(detail==null || !detail.has("checkpoints") || !detail.get("checkpoints").isJsonArray()) return false;
			for(var value:detail.getAsJsonArray("checkpoints")) if(value.isJsonObject() && "ready".equals(T3Protocol.string(value.getAsJsonObject(),"status"))) return true;
			return false;
		}).toList();
	}
	public void openLibrary() { Minecraft.getInstance().gui.setScreen(new T3LibraryScreen(this, false)); }
	public void openReviews() { Minecraft.getInstance().gui.setScreen(new T3LibraryScreen(this, true)); }
	public void openTask(String id) { Minecraft.getInstance().gui.setScreen(new T3TaskScreen(this,id,null)); }
	public void openAgent(String cast) {
		var row = studioBindings().get(cast);
		Minecraft.getInstance().gui.setScreen(new T3TaskScreen(this, row == null ? null : row.id(), cast));
	}
	public void openHistory(String id) { Minecraft.getInstance().gui.setScreen(new T3HistoryScreen(this,id)); }
	public boolean assignDesk(String cast,T3State.ThreadRow row) {
		if (!canPin()) return false;
		var ids=dev.agentcraft.Cast.ids().stream().toList(); int desk=ids.indexOf(cast); if(desk<0)return false;
		var prefs=officePreferences(); var displaced=prefs.desks.entrySet().stream().filter(e->e.getValue()==desk).map(java.util.Map.Entry::getKey).collect(java.util.stream.Collectors.toSet());
		var previousPins=new java.util.ArrayList<>(prefs.pins); var previousDesks=new java.util.LinkedHashMap<>(prefs.desks);
		prefs.pins.removeIf(p->displaced.contains(p.key())); prefs.desks.keySet().removeAll(displaced);
		if(!prefs.pin(row)) { prefs.pins=previousPins; prefs.desks=previousDesks; chat(Component.literal("All 18 desks are pinned. Unpin one first.")); return false; }
		prefs.desks.put(OfficeRoster.key(row),desk);
		if(!config.save(configPath)) { prefs.pins=previousPins; prefs.desks=previousDesks; chat(Component.literal("Could not save this desk assignment.")); return false; }
		return true;
	}
	public void openHistory() {
		String threadId = state.focusedThreadId();
		if (threadId != null) Minecraft.getInstance().gui.setScreen(new T3HistoryScreen(this, threadId)); else openPanel();
	}

	/** Remembers the focused thread across sessions. */
	public void focus(String threadId) {
		state.focus(threadId);
		config.threadId = threadId;
		config.save(configPath);
	}

	/** Sends to the focused thread, or starts a new thread beside it. Runs off-thread; reports back in chat. */
	public void send(String text, boolean newThread) {
		send(text, newThread, null);
	}

	/** {@code model} overrides the thread's model for this turn (and the thread from then on); null keeps it. */
	public void send(String text, boolean newThread, com.google.gson.JsonObject model) {
		T3State.Snapshot snapshot = state.snapshot();
		// A new thread only borrows project and settings, so any recent thread can stand in.
		T3State.ThreadRow row = snapshot.focusedRow();
		if (row == null && newThread && !snapshot.threads().isEmpty()) row = snapshot.threads().getFirst();
		if (row == null) {
			chat(Component.literal("Pick a thread first (` then click one, or /t3 threads).").withStyle(ChatFormatting.RED));
			return;
		}
		String key = T3Drafts.key(row.ownerKey(), row.id(), newThread);
		saveDraft(key, text); draftEntry(key).model = model; sendDraft(key, row, newThread, id -> {});
	}

	void sendDraft(String key, T3State.ThreadRow target, boolean newThread, java.util.function.Consumer<String> accepted) {
		T3Drafts.Entry entry = draftEntry(key);
		if (entry.sending || (entry.text.isBlank() && entry.files.isEmpty())) return;
		entry.sending = true; entry.status = "Sending…"; flushDrafts();
		// The target's own environment: a thread from the home server goes to the home server.
		T3Api api = state.apiFor(target.id());
		state.run(() -> {
			if (api == null || !state.online(target.id()) || api != state.apiFor(target.id())) throw new java.io.IOException("Reconnect this T3 machine before sending.");
			if (entry.prepared == null) {
				api.validateAttachments(entry.files.stream().map(Path::of).toList());
				var attachments = new com.google.gson.JsonArray();
				for (String path : entry.files) attachments.add(api.upload(Path.of(path)));
				var template = newThread ? target.raw() : api.thread(target.id(), 4);
				var prepared = api.prepareSend(template, entry.text, entry.model, newThread, entry.delivery, attachments);
				onMain(() -> { entry.prepared = prepared; if (!flushDrafts()) throw new IllegalStateException("Could not save the retry state. Your message was not sent."); });
			}
			api.deliver(entry.prepared, () -> onMain(this::flushDrafts));
			String id = entry.prepared.threadId;
			Minecraft.getInstance().execute(() -> {
				drafts.accepted(key); flushDrafts();
				if (newThread && java.util.Objects.equals(state.focusedThreadId(), target.id())) focus(id);
				state.watch(id); accepted.accept(id);
			});
		}, error -> Minecraft.getInstance().execute(() -> { drafts.failed(key, error.getMessage()); flushDrafts(); reportError(error); }));
	}
	private static void onMain(Runnable action) {
		var completed = new java.util.concurrent.CompletableFuture<Void>();
		Minecraft.getInstance().execute(() -> { try { action.run(); completed.complete(null); } catch (Exception e) { completed.completeExceptionally(e); } });
		completed.join();
	}

	public void respond(T3State.Approval approval, String decision) {
		var focus = state.snapshot().focus();
		if (focus != null) respond(focus.threadId(), approval, decision);
	}

	void respond(String threadId, T3State.Approval approval, String decision) {
		String key = threadId + ":" + approval.requestId(); if (!respondingRequests.add(key)) return;
		state.respond(threadId, approval.requestId(), decision, null,
			error -> Minecraft.getInstance().execute(() -> { respondingRequests.remove(key); reportError(error); }),
			() -> Minecraft.getInstance().execute(() -> {
				respondingRequests.remove(key);
				SystemToast.addOrUpdate(Minecraft.getInstance().gui.toastManager(), TOAST, Component.literal("Response accepted"), Component.literal("T3 acknowledged your decision."));
			}));
	}
	boolean responding(String threadId, String requestId) { return respondingRequests.contains(threadId + ":" + requestId); }

	public void answer(T3State.UserInput input, com.google.gson.JsonObject answers) {
		var focus = state.snapshot().focus();
		if (focus != null) answer(focus.threadId(), input, answers);
	}

	void answer(String threadId, T3State.UserInput input, com.google.gson.JsonObject answers) {
		state.respond(threadId, input.requestId(), null, answers.deepCopy(), this::reportError);
	}

	/** Unsent composer text per thread ("new" for a new thread), so switching threads never loses a draft. */
	String draft(String key) {
		return key == null ? "" : drafts.get(key).text;
	}
	T3Drafts.Entry draftEntry(String key) { return drafts.get(key); }

	void saveDraft(String key, String text) {
		if (key == null) return;
		if (drafts.update(key, text == null ? "" : text)) draftsDirty = true;
	}
	boolean flushDrafts() {
		lastDraftSave = System.currentTimeMillis(); boolean saved = config.save(configPath); draftsDirty = !saved; return saved;
	}
	void draftChanged() { draftsDirty = true; }

	public void interrupt() {
		interrupt(state.focusedThreadId());
	}
	public void interrupt(String threadId) {
		T3Api api=state.apiFor(threadId);
		if(threadId!=null) state.run(()->{if(api==null || !state.online(threadId) || api!=state.apiFor(threadId)) throw new java.io.IOException("Reconnect the owning T3 machine before stopping its run."); api.interrupt(threadId);},this::reportError);
	}

	private void reportError(Exception e) {
		LOGGER.warn("T3 request failed", e);
		Minecraft.getInstance().execute(() -> chat(Component.literal(e.getMessage() == null ? e.toString() : e.getMessage())
			.withStyle(ChatFormatting.RED)));
	}

	/** The point of the mod: tell the player when an agent finishes or needs them, wherever they are. */
	private void announce(T3State.Event event) {
		Minecraft minecraft = Minecraft.getInstance();
		String title = event.thread().title();
		Component heading;
		switch (event.status()) {
			case NEEDS_YOU -> {
				heading = Component.literal("T3 · Needs you").withStyle(ChatFormatting.GOLD);
				minecraft.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.NOTE_BLOCK_CHIME, 1.0f));
			}
			case ERROR -> {
				heading = Component.literal("T3 · Failed").withStyle(ChatFormatting.RED);
				minecraft.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.NOTE_BLOCK_BASS, 0.8f));
			}
			default -> {
				heading = Component.literal("T3 · Done").withStyle(ChatFormatting.GREEN);
				minecraft.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.NOTE_BLOCK_BELL, 1.2f));
			}
		}
		// One signal per event: the toast (and sound). The corner status only shows ongoing state,
		// and ` right after a ping opens the thread that pinged, which replaces a chat [open] link.
		lastPingThread = event.thread().id();
		lastPingAt = System.currentTimeMillis();
		
		// Already looking at this thread in the panel: the sound is enough, a toast would cover it.
		if (minecraft.gui.screen() instanceof T3Screen && event.thread().id().equals(state.focusedThreadId())) return;
		// Short text keeps the toast at its 190-px minimum, so it never grows over the corner status.
		Component toastTitle = heading.copy().append(Component.literal(" · ` to open").withStyle(ChatFormatting.GRAY));
		SystemToast.addOrUpdate(minecraft.gui.toastManager(), TOAST, toastTitle,
			Component.literal(T3Hud.ellipsize(minecraft.font, title, 160)));
	}

	private static void chat(Component message) {
		Minecraft minecraft = Minecraft.getInstance();
		if (minecraft.player == null) {
			LOGGER.info(message.getString());
			return;
		}
		minecraft.gui.hud.getChat().addClientSystemMessage(
			Component.empty().append(Component.literal("[T3] ").withStyle(ChatFormatting.DARK_AQUA)).append(message));
	}

	private void registerCommands(CommandDispatcher<FabricClientCommandSource> dispatcher) {
		dispatcher.register(literal("t3")
			.executes(ctx -> {
				openPanelNextTick = true;
				return 1;
			})
			.then(literal("connections").executes(ctx -> { openConnections(); return 1; }))
			.then(literal("work").executes(ctx -> { openWork(); return 1; }))
			.then(literal("guide").executes(ctx -> { openGuide(); return 1; }))
			.then(literal("pair").executes(ctx -> { openConnections(); return 1; }).then(argument("link", StringArgumentType.greedyString()).executes(this::pair)))
			.then(literal("unpair").executes(ctx -> {
				connectionRevision++;
				var previous = new java.util.ArrayList<>(config.environments);
				String previousFocus = config.threadId;
				config.environments.clear(); config.threadId = null;
				if (!config.save(configPath)) {
					config.environments = previous; config.threadId = previousFocus;
					chat(Component.literal("Could not save removal. Your machines remain paired.").withStyle(ChatFormatting.RED)); return 0;
				}
				state.connect(List.of(), null);
				chat(Component.literal("Forgot all environments. Revoke \"Minecraft\" in each T3's Settings → Connections too."));
				return 1;
			}))
			.then(literal("threads").executes(ctx -> listThreads()))
			.then(literal("decisions").executes(ctx -> { openDecisions(); return 1; }))
			.then(literal("board").executes(ctx -> { openBoard(); return 1; }))
			.then(literal("library").executes(ctx -> { openLibrary(); return 1; }))
			.then(literal("review").executes(ctx -> { openReview(state.focusedThreadId()); return 1; }))
			.then(literal("pins").executes(ctx -> { Minecraft.getInstance().gui.setScreen(new T3PinsScreen(this)); return 1; }))
			.then(literal("use").then(argument("n", IntegerArgumentType.integer(1)).executes(ctx -> {
				List<T3State.ThreadRow> rows = state.snapshot().threads();
				int n = IntegerArgumentType.getInteger(ctx, "n");
				if (n > rows.size()) {
					chat(Component.literal("No thread #" + n + ". Try /t3 threads.").withStyle(ChatFormatting.RED));
					return 0;
				}
				focus(rows.get(n - 1).id());
				chat(Component.literal("Now talking to: " + rows.get(n - 1).title()));
				return 1;
			})))
			.then(literal("open").then(argument("id", StringArgumentType.word()).executes(ctx -> {
				focus(StringArgumentType.getString(ctx, "id"));
				openPanelNextTick = true;
				return 1;
			})))
			.then(literal("ask").then(argument("prompt", StringArgumentType.greedyString()).executes(ctx -> prompt(ctx, false))))
			.then(literal("new").then(argument("prompt", StringArgumentType.greedyString()).executes(ctx -> prompt(ctx, true))))
			.then(literal("approve").executes(ctx -> answer("accept")))
			.then(literal("deny").executes(ctx -> answer("decline")))
			.then(literal("stop").executes(ctx -> {
				interrupt();
				chat(Component.literal("Asked the agent to stop."));
				return 1;
			})));
	}

	private int pair(CommandContext<FabricClientCommandSource> ctx) {
		chat(Component.literal("Pairing…"));
		pairEnvironment(StringArgumentType.getString(ctx, "link"), null, null, error -> chat(Component.literal(error == null
			? "Paired. Press ` to open the panel; /t3 connections to manage machines." : error)
			.withStyle(error == null ? ChatFormatting.GREEN : ChatFormatting.RED)));
		return 1;
	}

	private int listThreads() {
		T3State.Snapshot snapshot = state.snapshot();
		if (!snapshot.connected()) {
			chat(Component.literal(snapshot.error() == null ? "Connecting…" : snapshot.error()).withStyle(ChatFormatting.RED));
			return 0;
		}
		int shown = Math.min(20, snapshot.threads().size());
		if (snapshot.threads().size() > shown) {
			chat(Component.literal("Most recent " + shown + " of " + snapshot.threads().size() + " threads; press ` for all of them.")
				.withStyle(ChatFormatting.GRAY));
		}
		for (int i = 0; i < shown; i++) {
			int n = i + 1;
			T3State.ThreadRow row = snapshot.threads().get(i);
			boolean focused = row.id().equals(state.focusedThreadId());
			chat(Component.literal((focused ? "▶ " : "  ") + n + ". ")
				.append(Component.literal(row.title()).withStyle(style -> style
					.withColor(focused ? ChatFormatting.WHITE : ChatFormatting.GRAY)
					.withClickEvent(new ClickEvent.RunCommand("/t3 use " + n))))
				.append(Component.literal("  " + row.projectTitle() + " · " + T3Hud.label(row.status()))
					.withStyle(ChatFormatting.DARK_GRAY)));
		}
		return 1;
	}

	private int prompt(CommandContext<FabricClientCommandSource> ctx, boolean newThread) {
		send(StringArgumentType.getString(ctx, "prompt"), newThread);
		return 1;
	}

	private int answer(String decision) {
		T3State.Focus focus = state.snapshot().focus();
		if (focus == null || focus.approvals().isEmpty()) {
			chat(Component.literal("Nothing waiting for approval on this thread."));
			return 0;
		}
		respond(focus.approvals().getFirst(), decision);
		return 1;
	}
}

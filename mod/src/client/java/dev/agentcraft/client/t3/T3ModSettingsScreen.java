package dev.agentcraft.client.t3;

import dev.agentcraft.Cast;
import dev.agentcraft.hq.HqFeature;
import dev.agentcraft.hq.HqBuilders;
import dev.agentcraft.world.HqWorld;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/** Save-local staffing and appearance controls, reachable from the vanilla pause menu. */
final class T3ModSettingsScreen extends T3MenuScreen {
	private int section, characterPage;
	private final T3CraftClient mod;
	private final Screen parent;
	private String notice = "Appearance is local; characters are saved with this world.";
	private boolean confirmUpgrade;
	private boolean confirmUpdate;

	T3ModSettingsScreen(T3CraftClient mod, Screen parent) {
		super(Component.literal("T3Craft settings"));
		this.mod = mod;
		this.parent = parent;
	}

	@Override public boolean isPauseScreen() { return true; }
	@Override public void onClose() { minecraft.gui.setScreen(parent); }

	private int left() { return Math.max(18, (width - 540) / 2); }

	@Override protected void init() {
        int x = left(), w = width - 2 * x, gap = 8, tab = (w - 2 * gap) / 3;
        String[] sections = {"General", "Characters", "Updates"};
        for (int i = 0; i < sections.length; i++) {
            int choice = i;
            addRenderableWidget(Button.builder(Component.literal((section == i ? "• " : "") + sections[i]), b -> { section = choice; rebuildWidgets(); }).bounds(x + i * (tab + gap), 58, tab, 20).build());
        }
        if (section == 0) {
            menuAction("Appearance · " + dev.agentcraft.client.ui.T3Theme.label(mod.themeId()), "T3 themes, light / dark mode and custom accent", x, 90, w, b -> minecraft.gui.setScreen(new T3AppearanceScreen(mod, this)));
            var prefs = mod.officePreferences(); int half = (w - gap) / 2;
            Button staffing = menuAction(prefs.autoStaffing ? "Staffing · Auto" : "Staffing · Fixed", prefs.autoStaffing ? "6–18 NPCs; pinned chats stay represented" : "A steady cast for recording", x, 130, half, b -> { mod.toggleStaffing(); rebuildWidgets(); }); staffing.active = mod.canPin();
            Button count = menuAction("NPC count · " + prefs.fixedNpcCount, "Click to change the fixed count", x + half + gap, 130, half, b -> { mod.cycleFixedCount(); rebuildWidgets(); }); count.active = mod.canPin() && !prefs.autoStaffing;
            Button roster = menuAction("Resident cast · Show all 18", "18 creator-made community skins", x, 170, half, b -> { notice = mod.gamingRoster() ? "Community skins applied. All 18 NPCs are visible." : "Couldn't save the cast. Check the config folder."; rebuildWidgets(); }); roster.active = mod.canPin();
            menuAction("Display options", "Atrium view; right-click any monitor to change it", x + half + gap, 170, half, b -> mod.openDisplay("atrium", "feed"));
            addRenderableWidget(Button.builder(Component.literal("Getting started"), b -> mod.openGuide()).bounds(x, height - 60, half, 20).build());
            addRenderableWidget(Button.builder(Component.literal("Your work"), b -> mod.openWork()).bounds(x + half + gap, height - 60, half, 20).build());
        } else if (section == 1) {
            int cols = width >= 520 ? 2 : 1, cw = (w - (cols - 1) * gap) / cols;
            var cast = Cast.members().values().stream().toList(); int count = cols * Math.max(1, (height - 178) / 40);
            characterPage = Math.min(characterPage, (cast.size() - 1) / count);
            for (int i = characterPage * count; i < Math.min(cast.size(), (characterPage + 1) * count); i++) {
                var member = cast.get(i); int n = i - characterPage * count;
                Button skin = menuAction(mod.npcName(member.id()), "Change look · desk and chat stay assigned", x + n % cols * (cw + gap), 90 + n / cols * 40, cw, b -> { minecraft.gui.setScreen(new T3NpcLookScreen(mod, member.id(), this)); }); skin.active = mod.canPin();
            }
            Button prev = addRenderableWidget(Button.builder(Component.literal("Previous"), b -> { characterPage--; rebuildWidgets(); }).bounds(x, height - 66, 70, 20).build()); prev.active = characterPage > 0;
            Button next = addRenderableWidget(Button.builder(Component.literal("Next"), b -> { characterPage++; rebuildWidgets(); }).bounds(x + 78, height - 66, 60, 20).build()); next.active = (characterPage + 1) * count < cast.size();
            Button projects = addRenderableWidget(Button.builder(Component.literal("Project labels · " + (mod.officePreferences().showProjectLabels ? "On" : "Off")), b -> { mod.toggleProjectLabels(); rebuildWidgets(); }).bounds(x + w - Math.min(150, w - 146), height - 66, Math.min(150, w - 146), 20).build()); projects.active = mod.canPin();
        } else {
            Button hq = menuAction(confirmUpgrade ? "Confirm modern HQ upgrade" : "Upgrade HQ · Modern campus", confirmUpgrade ? "Rebuilds changed sections and keeps your edits" : "Player-edited blocks are kept", x, 90, w, b -> upgradeHq()); hq.active = canUpgradeHq();
            boolean ready = hasUpdater();
            menuAction(ready ? confirmUpdate ? "Confirm update & restart" : "Update mod · Back up & restart" : "Update via Prism", ready ? "Save, close, back up, install and relaunch" : "Download the matching release and back up your instance", x, 130, w, b -> updateMod());
        }
        addRenderableWidget(Button.builder(Component.literal("Back to game"), b -> onClose()).bounds(x, height - 34, w, 20).build());
    }

	private boolean canUpgradeHq() {
		var server = minecraft.getSingleplayerServer();
		return server != null && HqWorld.isHq(server) && minecraft.player != null && minecraft.player.isCreative()
			&& HqBuilders.get("modern") != null;
	}

	private void upgradeHq() {
		if (!canUpgradeHq()) { notice = "Open the local creative AgentCraft HQ to upgrade it."; return; }
		if (!confirmUpgrade) { confirmUpgrade = true; notice = "Review the rebuild, then confirm. Your changed blocks are preserved."; rebuildWidgets(); return; }
		confirmUpgrade = false;
		var server = minecraft.getSingleplayerServer();
		if (server == null) return;
		server.execute(() -> {
			try {
				HqFeature.buildAndPublish(server.overworld(), HqBuilders.get("modern"));
				server.saveEverything(false, true, true);
				minecraft.execute(() -> notice = "Modern campus applied. Your edited blocks were kept.");
			} catch (RuntimeException e) {
				minecraft.execute(() -> notice = "HQ upgrade failed: " + e.getMessage());
			}
		});
		notice = "Applying the modern campus…";
	}

	private Path updaterScript() {
		try {
			if (!System.getProperty("os.name", "").toLowerCase(java.util.Locale.ROOT).startsWith("windows")) return null;
			var metadata = mod.updateMetadata();
			if (!metadata.has("prismData") || !metadata.has("instanceId")) return null;
			Path script = metadata.has("updaterScript") ? Path.of(metadata.get("updaterScript").getAsString())
				: metadata.has("repoRoot") ? Path.of(metadata.get("repoRoot").getAsString()).resolve("tools/update-after-close.ps1") : null;
			return script != null && Files.isRegularFile(script) ? script : null;
		} catch (RuntimeException ignored) { return null; }
	}
	private boolean hasUpdater() { return updaterScript() != null; }

	private void updateMod() {
		if (!hasUpdater()) {
			notice = "Close Minecraft, back up the instance in Prism, then replace T3Craft and Fabric API with the matching release.";
			return;
		}
		if (!confirmUpdate) { confirmUpdate = true; notice = "Minecraft will save and close; the updater backs up your HQ before restarting."; rebuildWidgets(); return; }
		try {
			var metadata = mod.updateMetadata();
			Path helper = updaterScript();
			new ProcessBuilder("powershell.exe", "-NoProfile", "-WindowStyle", "Hidden", "-ExecutionPolicy", "Bypass", "-File", helper.toString(),
				"-GameProcessId", Long.toString(ProcessHandle.current().pid()), "-PrismData", metadata.get("prismData").getAsString(),
				"-InstanceId", metadata.get("instanceId").getAsString())
				.redirectOutput(ProcessBuilder.Redirect.DISCARD).redirectError(ProcessBuilder.Redirect.DISCARD).start();
			Minecraft.getInstance().stop();
		} catch (Exception e) {
			confirmUpdate = false;
			notice = "Couldn't start the updater. Run Update-T3Craft.cmd after closing the game.";
		}
	}

	@Override public void extractRenderState(GuiGraphicsExtractor g, int mx, int my, float delta) {
		int x = left(); paper(g, x - 12, 12, width - x + 12, height - 8);
		g.text(font, "T3Craft settings", x, 22, INK, false);
		g.text(font, T3Hud.ellipsize(font, notice, width - 2 * x), x, 40, MUTED, false);

		super.extractRenderState(g, mx, my, delta);
	}
}

package dev.agentcraft.client.monitor;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.agentcraft.client.t3.T3CraftClient;
import dev.agentcraft.client.t3.T3Usage;
import dev.agentcraft.client.ui.TextUtil;
import dev.agentcraft.client.ui.WorldUi;
import dev.agentcraft.client.world.StationRenderer;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.core.Direction;

/** Theme-aware wall display with isolated quota boxes and native token/cost reporting. */
public final class UsageWall {
	private static final int PPB = 64;
	record Data(int width, int height, T3Usage.Report report, int page, int pages, int limitPage, int limitPages) {}
	static Data extract(int w, int h) {
		var reports = T3Usage.visible(T3CraftClient.get().state());
		long step = System.currentTimeMillis() / 15_000;
		int page = reports.isEmpty() ? 0 : (int) (step % reports.size());
		var report = reports.isEmpty() ? new T3Usage.Report("All environments", List.of(),
			List.of(T3CraftClient.get().paired() ? "Loading native usage..." : "Pair T3 Code to see your usage"), "") : reports.get(page);
		int pages = Math.max(1, (report.limits().size() + 1) / 2);
		return new Data(w * PPB, h * PPB, report, page, reports.size(), (int) (step / Math.max(1, reports.size()) % pages), pages);
	}
	static void submit(Data d, Direction facing, int panelHeight, PoseStack ps, SubmitNodeCollector c) {
		ps.pushPose();
		StationRenderer.toFace(ps, facing, MonitorRenderer.SCREEN_DEPTH, PPB);
		ps.translate(0, -(panelHeight - 1) * PPB, 0);
		draw(d, ps, c);
		ps.popPose();
	}
	public static void submitBillboard(PoseStack ps, SubmitNodeCollector c) {
		ps.pushPose(); ps.scale(320f/448f, 170f/256f, 1);
		draw(extract(7, 4), ps, c); ps.popPose();
	}
	private static void draw(Data d, PoseStack ps, SubmitNodeCollector c) {
		// Fit all content even on a smaller player-built usage panel.
		float scale = Math.min(d.width / 448f, d.height / 256f);
		ps.scale(scale, scale, 1);
		int light = WorldUi.uiLight();
		var rects = new DisplayDraw.Rects();
		rects.add(0, 0, d.width / scale, d.height / scale, 0, dev.agentcraft.client.ui.T3Theme.color("canvas"), light);
		int first = d.limitPage * 2;
		for (int i = first; i < Math.min(first + 2, d.report.limits().size()); i++) {
			var limit = d.report.limits().get(i); int x = 16 + (i - first) * 212;
			rects.add(x, 48, x + 204, 110, DisplayDraw.Z_STEP, dev.agentcraft.client.ui.T3Theme.color("surface"), light);
			rects.add(x + 10, 88, x + 194, 94, 2 * DisplayDraw.Z_STEP, dev.agentcraft.client.ui.T3Theme.color("border"), light);
			rects.add(x + 10, 88, x + 10 + (float) (184 * limit.remaining() / 100), 94, 3 * DisplayDraw.Z_STEP, dev.agentcraft.client.ui.T3Theme.color("accent"), light);
		}
		rects.submit(ps, c);
		ps.translate(0, 0, 4 * DisplayDraw.Z_STEP);
		text(ps, c, "My token usage & my spend", 16, 14, 410, dev.agentcraft.client.ui.T3Theme.color("text"));
		text(ps, c, d.report.machine() + (d.pages > 1 ? " · " + (d.page + 1) + "/" + d.pages + " machines" : ""), 16, 30, 410, dev.agentcraft.client.ui.T3Theme.color("textMuted"));
		if (d.report.limits().isEmpty()) text(ps, c, "Session / weekly limits unavailable", 16, 68, 410, dev.agentcraft.client.ui.T3Theme.color("textMuted"));
		for (int i = first; i < Math.min(first + 2, d.report.limits().size()); i++) {
			var limit = d.report.limits().get(i); int x = 26 + (i - first) * 212;
			text(ps, c, limit.label(), x, 57, 184, dev.agentcraft.client.ui.T3Theme.color("text"));
			text(ps, c, String.format(java.util.Locale.ROOT, "%.0f%% left", limit.remaining()), x, 73, 184, dev.agentcraft.client.ui.T3Theme.color("text"));
			text(ps, c, reset(limit.resetsAt()), x, 99, 184, dev.agentcraft.client.ui.T3Theme.color("textMuted"));
		}
		int y = 124;
		for (String line : d.report.lines()) { text(ps, c, line, 16, y, 410, dev.agentcraft.client.ui.T3Theme.color("text")); y += 13; }
		String checked = d.report.readAt();
		if (checked.isBlank() && !d.report.limits().isEmpty()) checked = d.report.limits().get(first).checkedAt();
		text(ps, c, "Checked " + (checked.isBlank() ? "unavailable" : checked)
			+ (d.limitPages > 1 ? " · limits " + (d.limitPage + 1) + "/" + d.limitPages : ""), 16, 238, 410, dev.agentcraft.client.ui.T3Theme.color("textMuted"));
	}
	private static void text(PoseStack ps, SubmitNodeCollector c, String value, int x, int y, int width, int color) {
		WorldUi.submitFaceText(ps, c, TextUtil.ellipsize(Minecraft.getInstance().font, value, width), x, y, color, WorldUi.uiLight());
	}
	private static String reset(String timestamp) {
		try {
			long minutes = java.time.Duration.between(java.time.Instant.now(), java.time.Instant.parse(timestamp)).toMinutes();
			if (minutes <= 0) return "Reset due · awaiting T3 update";
			long days = minutes / 1440, hours = minutes % 1440 / 60;
			return "Resets in " + (days > 0 ? days + "d " : "") + (hours > 0 ? hours + "h " : "") + minutes % 60 + "m";
		} catch (RuntimeException e) { return "Reset time unavailable"; }
	}
}

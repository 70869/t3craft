package dev.agentcraft.client.monitor;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.agentcraft.client.t3.T3CraftClient;
import dev.agentcraft.client.t3.T3State;
import dev.agentcraft.client.t3.StudioProjection;
import dev.agentcraft.client.ui.T3Theme;
import dev.agentcraft.client.ui.TextUtil;
import dev.agentcraft.client.ui.WorldUi;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.SubmitNodeCollector;

/** Current T3 state, captured during extraction. Graphs show counts, never invented history. */
public final class StudioDashboard {
	public record Data(String view, String title, String subtitle, List<String> lines, List<Integer> counts, int offline) {}
	private static final List<T3State.Status> STATES = List.of(T3State.Status.WORKING, T3State.Status.NEEDS_YOU, T3State.Status.DONE, T3State.Status.ERROR, T3State.Status.IDLE);
	private static final List<String> LABELS = List.of("Working", "Needs you", "Done", "Error", "Idle");
	public static Data extract(String view, String agent) {
		var client = T3CraftClient.get(); var snapshot = client.state().snapshot();
		var row = dev.agentcraft.client.foreman.T3Projection.agentThread(agent);
		if (row == null) row = snapshot.focusedRow();
		String title = row == null ? "T3 Code studio" : row.title();
		String subtitle = !snapshot.connected() ? "Disconnected · last known state" : row == null ? "Connected · choose a chat" : row.projectTitle() + " · " + StudioProjection.label(row.status());
		int offline = (int) snapshot.threads().stream().filter(r -> !client.state().online(r.id())).count();
		List<Integer> counts = STATES.stream().map(status -> (int) snapshot.threads().stream().filter(r -> client.state().online(r.id()) && r.status() == status).count()).toList();
		List<String> lines;
		if (view.equals("activity")) {
			lines = snapshot.threads().stream().limit(10).map(r -> (client.state().online(r.id()) ? StudioProjection.label(r.status()) : "Offline") + " · " + r.title()).toList();
		} else {
			lines = List.of(snapshot.threads().size() + " saved chats", counts.get(0) + " working · " + counts.get(1) + " need you", client.residents().size() + " assigned desks · " + client.visibleNpcCount() + " residents", offline + " chats on offline machines");
		}
		return new Data(view, title, subtitle, lines, counts, offline);
	}

	/** Face pixel coordinates, with -z toward the viewer. Caller supplies size and orientation. */
	public static void submit(Data d, float width, float height, PoseStack ps, SubmitNodeCollector c) {
		ps.pushPose();
		float scale = Math.min(width / 320f, height / 170f);
		ps.scale(scale, scale, 1);
		int light = WorldUi.uiLight();
		var r = new DisplayDraw.Rects();
		r.add(0, 0, width / scale, height / scale, 0, T3Theme.color("surface"), light);
		r.add(0, 0, 3, height / scale, DisplayDraw.Z_STEP, T3Theme.color("accent"), light);
		if (d.view.equals("status")) {
			int total = Math.max(1, d.counts.stream().mapToInt(Integer::intValue).sum());
			for (int i = 0; i < d.counts.size(); i++) {
				float y = 50 + i * 19;
				r.add(88, y, 276, y + 8, DisplayDraw.Z_STEP, T3Theme.color("border"), light);
				r.add(88, y, 88 + 188f * d.counts.get(i) / total, y + 8, 2 * DisplayDraw.Z_STEP, i == 1 ? T3Theme.color("accentText") : i == 3 ? T3Theme.color("errorText") : T3Theme.color("accent"), light);
			}
		}
		r.submit(ps, c);
		ps.translate(0, 0, 4 * DisplayDraw.Z_STEP);
		text(ps, c, d.view.equals("status") ? "Chat status · now" : d.view.equals("activity") ? "Recent chats · activity" : d.title, 14, 12, 292, T3Theme.color("text"));
		text(ps, c, d.subtitle, 14, 28, 292, T3Theme.color("textMuted"));
		if (d.view.equals("status")) {
			for (int i = 0; i < d.counts.size(); i++) {
				text(ps, c, LABELS.get(i), 14, 50 + i * 19, 70, T3Theme.color("text"));
				text(ps, c, d.counts.get(i).toString(), 282, 50 + i * 19, 24, T3Theme.color("text"));
			}
			text(ps, c, d.offline + " offline · excluded from graph", 14, 151, 292, T3Theme.color("textMuted"));
		} else {
			int y = 54;
			for (String line : d.lines) { if (y > 147) break; text(ps, c, line, 14, y, 292, T3Theme.color("text")); y += d.view.equals("activity") ? 13 : 23; }
			text(ps, c, "Right-click to change display", 14, 153, 292, T3Theme.color("textMuted"));
		}
		ps.popPose();
	}
	private static void text(PoseStack ps, SubmitNodeCollector c, String value, int x, int y, int width, int color) {
		WorldUi.submitFaceText(ps, c, TextUtil.ellipsize(Minecraft.getInstance().font, value, width), x, y, color, WorldUi.uiLight());
	}
}

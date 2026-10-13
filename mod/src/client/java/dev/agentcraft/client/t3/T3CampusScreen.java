package dev.agentcraft.client.t3;

import dev.agentcraft.layout.Anchor;
import dev.agentcraft.layout.AnchorNames;
import dev.agentcraft.layout.Anchors;
import dev.agentcraft.world.HqWorld;
import java.util.List;
import java.util.Set;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;

/** Directory usable without pairing. Visits are restricted to the local creative HQ. */
final class T3CampusScreen extends T3MenuScreen {
	private final T3CraftClient mod;
	private int page;
	private String message = "Choose a place to visit, or open your T3 work below.";
	private record Place(String name, String detail, String anchor) {}
	private static final List<Place> PLACES = List.of(
		new Place("Reception", "Return to the entrance", AnchorNames.SPAWN),
		new Place("Work floor", "Agent desks & live monitors", "campus_work"),
		new Place("Library", "Saved conversations", AnchorNames.slot(AnchorNames.LIBRARY, 1)),
		new Place("Meeting room", "Glass room on the work floor", "campus_meeting"),
		new Place("Upper gallery", "Collaboration floor & quiet studios", "campus_upper_gallery"),
		new Place("Lower archive", "Library & project planning", "campus_lower_archive"),
		new Place("Stair hall", "Connect all three floors", "campus_stairs"),
		new Place("Coffee terrace", "Covered outdoor seating", "campus_terrace"),
		new Place("Garden studio", "A quiet pavilion behind HQ", "campus_studio"),
		new Place("Usage room", "My token usage & my spend", "campus_usage"),
		new Place("Focus garden", "Water, shade & a reading bench", "campus_garden"),
		new Place("Waterfront", "Island shore & ocean views", "campus_waterfront"),
		new Place("Lounge", "Take a break with the team", "campus_lounge"));
	T3CampusScreen(T3CraftClient mod) { super(Component.literal("HQ directory")); this.mod = mod; }
	private int left() { return Math.max(20, (width - 520) / 2); }
	private int columns() { return width >= 560 ? 2 : 1; }
	private int perPage() { return columns() * Math.max(1, (height - 126) / 40); }
	@Override protected void init() {
		int x = left(), cols = columns(), cw = (width - 2 * x - (cols - 1) * 10) / cols;
		int count = perPage(); page = Math.min(page, (PLACES.size() - 1) / count);
		for (int i = page * count; i < Math.min(PLACES.size(), (page + 1) * count); i++) {
			Place place = PLACES.get(i); int n = i - page * count;
			Button button = menuAction(place.name(), place.detail(), x + n % cols * (cw + 10), 62 + n / cols * 40, cw, b -> visit(place));
			button.active = canVisit() && Anchors.get(place.anchor()) != null;
		}
		Button prev = addRenderableWidget(Button.builder(Component.literal("Previous"), b -> { page--; rebuildWidgets(); }).bounds(x, height - 60, 72, 20).build()); prev.active = page > 0;
		Button next = addRenderableWidget(Button.builder(Component.literal("Next"), b -> { page++; rebuildWidgets(); }).bounds(x + 78, height - 60, 60, 20).build()); next.active = (page + 1) * count < PLACES.size();
        addRenderableWidget(Button.builder(Component.literal("Done"), b -> minecraft.gui.setScreen(null)).bounds(width - x - 64, height - 60, 64, 20).build());
        navigation(mod, x, height - 34, width - 2 * x, "Campus");
	}
	private boolean canVisit() {
		var server = minecraft.getSingleplayerServer();
		return server != null && HqWorld.isHq(server) && minecraft.player != null && minecraft.player.isCreative();
	}
	private void visit(Place place) {
		Anchor anchor = Anchors.get(place.anchor()); var server = minecraft.getSingleplayerServer();
		if (!canVisit() || anchor == null || server == null) return;
		var id = minecraft.player.getUUID();
		server.execute(() -> {
			var player = server.getPlayerList().getPlayer(id);
			if (player == null || !player.isCreative() || player.level() != server.overworld()) return;
			var level = player.level(); var floor = BlockPos.containing(anchor.x(), anchor.y() - 1, anchor.z());
			var box = player.getBoundingBox().move(anchor.x() - player.getX(), anchor.y() - player.getY(), anchor.z() - player.getZ());
			if (!level.noCollision(player, box) || level.getBlockState(floor).getCollisionShape(level, floor).isEmpty()) {
				minecraft.execute(() -> message = "That spot is obstructed. Clear it before visiting."); return;
			}
			player.teleportTo(level, anchor.x(), anchor.y(), anchor.z(), Set.of(), anchor.yaw(), anchor.pitch(), true);
			minecraft.execute(() -> { if (minecraft.gui.screen() == this) minecraft.gui.setScreen(null); });
		});
	}
	@Override public void extractRenderState(GuiGraphicsExtractor g, int mx, int my, float delta) {
		int x = left(); paper(g, x - 12, 10, width - x + 12, height - 8);
		g.text(font, "HQ directory", x, 22, INK, false);
		String hint = canVisit() ? message : "Visits are available in your local creative HQ. T3 menus work anywhere.";
		g.text(font, T3Hud.ellipsize(font, hint, width - 2 * x), x, 40, MUTED, false);
		super.extractRenderState(g, mx, my, delta);
	}
}

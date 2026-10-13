package dev.agentcraft.hq;

import dev.agentcraft.AgentCraft;
import dev.agentcraft.block.*;
import dev.agentcraft.layout.AnchorNames;
import dev.agentcraft.layout.Anchors;
import java.util.Locale;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.state.BlockState;
import org.jspecify.annotations.Nullable;

/** A daylight campus office. All furniture leaves side approaches and a continuous main aisle. */
public final class ModernHqBuilder implements HqBuilder {
	public static final String ID = "modern";
	private static final int FLOOR = 65, FEET = 66;
	private static final int[] SITE = StudioHqBuilder.SITE;
	private static final BlockState AIR = Blocks.AIR.defaultBlockState();
	private static final BlockState WHITE = Blocks.SMOOTH_QUARTZ.defaultBlockState();
	private static final BlockState INK = St.concrete(DyeColor.GRAY);
	private static final BlockState GLASS = Blocks.STAINED_GLASS.pick(DyeColor.LIGHT_GRAY).defaultBlockState();
	private static final BlockState WOOD = Blocks.PALE_OAK_PLANKS.defaultBlockState();
	private static final BlockState STONE = Blocks.SMOOTH_STONE.defaultBlockState();
	private static final Direction N = Direction.NORTH, S = Direction.SOUTH, E = Direction.EAST, W = Direction.WEST;

	@Override public String id() { return ID; }
	@Override public String description() { return "an island campus with three connected floors, a glazed stair hall and waterfront gardens"; }
	@Override public void build(ServerLevel level, Anchors.Builder anchors) { build(level, anchors, Options.DEFAULT); }
	static boolean hasSavedPlan(MinecraftServer server) {
		int cells = (SITE[3] - SITE[0] + 1) * (SITE[4] - SITE[1] + 1) * (SITE[5] - SITE[2] + 1);
		return PlanStore.load(server, ID, SITE, cells) != null || PlanStore.load(server, StudioHqBuilder.ID, SITE, cells) != null;
	}
	@Override public @Nullable String build(ServerLevel level, Anchors.Builder a, Options options) {
		Plan p = plan(a);
		BlockState[] previous = PlanStore.load(level.getServer(), ID, SITE, p.size());
		if (previous == null) previous = PlanStore.load(level.getServer(), StudioHqBuilder.ID, SITE, p.size());
		Plan.Stats stats = p.apply(level, previous, options.force());
		PlanStore.save(level.getServer(), ID, SITE, p.cells());
		String report = String.format(Locale.ROOT, "%d blocks updated; kept %d player changes", stats.changed() + stats.connected(), stats.kept());
		AgentCraft.LOGGER.info("Modern HQ: {}", report); return report;
	}

	/** Assemble the complete site before applying its diff to a live world. */
	static Plan plan(Anchors.Builder a) {
		Plan p = new Plan(SITE[0], SITE[1], SITE[2], SITE[3], SITE[4], SITE[5], 64,
			y -> y > 64 ? AIR : y == 64 ? Blocks.GRASS_BLOCK.defaultBlockState() : y >= 61 ? Blocks.DIRT.defaultBlockState() : Blocks.STONE.defaultBlockState());
		landscape(p);
		pavilion(p, -24, -12, 24, 9, 83);
		pavilion(p, -10, 9, 10, 26, 77);
		// The two volumes share a wide threshold; the lobby skylight reveals the work floor ahead.
		p.fill(-5, FEET, 9, 5, 73, 9, AIR);
		p.fill(-2, FEET, 26, 2, 70, 26, AIR);
		p.fill(-2, FLOOR, 26, 2, FLOOR, 28, STONE);
		p.fill(-2, FLOOR, 29, 2, FLOOR, 29, St.stairs(Blocks.SMOOTH_QUARTZ_STAIRS, N, false));
		// Floating entrance canopy with inset lighting and a planted approach.
		p.fill(-7, 72, 26, 7, 72, 31, WHITE);
		p.fill(-7, 73, 26, 7, 73, 31, St.slab(Blocks.SMOOTH_QUARTZ_SLAB, false));
		p.fill(-4, 72, 30, 4, 72, 30, ModBlocks.GLOW_PANEL.defaultBlockState());
		p.fill(-7, FEET, 30, -7, 71, 30, INK); p.fill(7, FEET, 30, 7, 71, 30, INK);
		desks(p, a); library(p, a); engineering(p, a); lounge(p, a); meeting(p, a); lobby(p, a);
		// A three-block circulation spine connects reception, the work bays and the stair hall.
		p.fill(-23, FLOOR, 6, 23, FLOOR, 8, STONE);
		lowerLevel(p, a);
		upperLevel(p, a);
		campus(p, a);
		// Acoustic oak ceiling panels and restrained continuous work lights.
		for (int z : new int[]{-6, 5}) {
			p.fill(-21, 73, z, 21, 73, z, WOOD);
			p.fill(-20, 72, z, -5, 72, z, ModBlocks.GLOW_PANEL.defaultBlockState());
			p.fill(5, 72, z, 20, 72, z, ModBlocks.GLOW_PANEL.defaultBlockState());
		}
		for (int x = -23; x <= 23; x += 4) for (int z = -11; z <= 25; z += 4) p.setIfAir(x, 68, z, St.light(15));
		for (int x : new int[]{-6, 0, 6}) for (int z : new int[]{11, 19, 24}) p.setIfAir(x, 72, z, St.light(14));
		for (int x = -2; x <= 2; x++) {
			p.set(x, 79, 15, ModBlocks.STATUS_LAMP.defaultBlockState().setValue(StatusLampBlock.STATUS, LampStatus.IDLE));
			p.bind(x, 79, 15, StudioHqBuilder.BEACON);
		}
		p.fill(-3, 78, 15, 3, 78, 15, INK); p.fill(-3, 80, 15, 3, 80, 15, INK);
		HqLandscape.tree(p, -30, 28, HqLandscape.Kind.CHERRY, 820);
		HqLandscape.islandWoodland(p);
		// Build last so nearby woodland can never grow through the room or its display.
		usageRoom(p, a);
		stairHall(p, a);
		expandedResidents(p, a);
		entranceWordmark(p, a);
		for (int x = -20; x <= 20; x += 5) p.setIfAir(x, 68, 7, St.light(15));
		p.settleGrass();
		cameras(a);
		a.bounds(-23, 60, -11, 37, 83, 25);
		a.put(AnchorNames.ENTRANCE, 0.5, FEET, 24.5, 180, 0);
		a.put(AnchorNames.SPAWN, 0.5, FEET, 24.5, 180, 0);
		a.put("campus_work", .5, FEET, -4.5, 180, 0);
		a.put("campus_meeting", 7.5, FEET, .5, -90, 0);
		a.put("campus_lounge", -9.5, FEET, 2.5, 90, 0);
		a.put("campus_terrace", -18.5, FEET, 22.5, 180, 0);
		a.put("campus_waterfront", .5, 65, 65.5, 0, 0);
		return p;
	}

	/** Add lounge capacity for residents whose threads have settled. */
	private static void expandedResidents(Plan p, Anchors.Builder a) {
		for (int i = 0; i < 6; i++) {
			int x = 8 + (i % 3)*3, z = i < 3 ? -2 : 2;
			a.put(AnchorNames.slot(AnchorNames.LOUNGE, 7+i), x+.5, FEET, z+.5, i < 3 ? 0 : 180, 0);
		}
		for (int i = 0; i < 6; i++) {
			int x = 8 + (i % 3)*3, z = i < 3 ? -1 : 3;
			a.put(AnchorNames.slot(AnchorNames.LOUNGE, 13+i), x+.5, 61, z+.5, i < 3 ? 0 : 180, 0);
		}
	}
	/** A block-pixel T3 plaque over the front canopy, clear of the entrance and player paths. */
	private static void entranceWordmark(Plan p, Anchors.Builder a) {
		p.fill(-7, 74, 31, 7, 80, 31, INK);
		String[] pixels = {"TTTTT.33333", "..T.......3", "..T....3333", "..T.......3", "..T...33333"};
		for (int row = 0; row < pixels.length; row++) for (int col = 0; col < pixels[row].length(); col++) {
			char pixel = pixels[row].charAt(col);
			if (pixel != '.') p.set(col-5, 79-row, 32, pixel == 'T' ? WHITE : St.concrete(DyeColor.CYAN));
		}
		for (int x : new int[]{-6, 6}) p.set(x, 74, 32, ModBlocks.GLOW_PANEL.defaultBlockState());
		a.cameraLookAt("t3_wordmark", 0.5, 76, 44, .5, 77, 32);
	}

	private static void pavilion(Plan p, int x0, int z0, int x1, int z1, int roof) {
		p.fill(x0, FLOOR, z0, x1, FLOOR, z1, STONE);
		p.fill(x0 + 1, FLOOR, z0 + 1, x1 - 1, FLOOR, z1 - 1, WOOD);
		int center = (x0 + x1) / 2;
		p.fill(center - 2, FLOOR, z0, center + 2, FLOOR, z1, STONE);
		for (int x = x0; x <= x1; x++) for (int z : new int[]{z0, z1}) {
			boolean mullion = x == x0 || x == x1 || Math.floorMod(x, 6) == 0;
			p.fill(x, FEET, z, x, roof - 1, z, mullion ? INK : GLASS);
		}
		for (int z = z0; z <= z1; z++) for (int x : new int[]{x0, x1}) {
			p.fill(x, FEET, z, x, roof - 1, z, z == z0 || z == z1 || Math.floorMod(z, 6) == 0 ? INK : GLASS);
		}
		p.fill(x0 - 1, roof, z0 - 1, x1 + 1, roof, z1 + 1, WHITE);
		p.fill(x0 - 1, roof + 1, z0 - 1, x1 + 1, roof + 1, z1 + 1, St.slab(Blocks.SMOOTH_QUARTZ_SLAB, false));
		int skyX = Math.min(3, (x1 - x0 - 2) / 4);
		p.fill(center - skyX, roof, z0 + 3, center + skyX, roof + 1, z1 - 3, GLASS);
		for (int z = z0 + 3; z <= z1 - 3; z += 4) p.fill(center - skyX, roof, z, center + skyX, roof, z, INK);
	}

	private static void desks(Plan p, Anchors.Builder a) {
		var ids = StudioHqBuilder.deskIds();
		for (int i = 0; i < 6; i++) {
			int x = StudioHqBuilder.DESK_X[i]; String id = ids.get(i);
			p.fill(x - 1, FEET, -9, x + 1, FEET, -9, St.slab(Blocks.PALE_OAK_SLAB, true));
			for (int mx = x - 1; mx <= x + 1; mx++) for (int y = FEET + 1; y <= FEET + 2; y++) {
				p.set(mx, y, -9, ModBlocks.MONITOR.defaultBlockState().setValue(PanelBlock.FACING, S).setValue(MonitorBlock.LIT, true)); p.bind(mx, y, -9, id);
			}
			p.set(x - 1, FEET, -8, St.stairs(Blocks.WOOL_STAIRS.pick(DyeColor.GRAY), S, false));
			p.set(x, 70, -12, ModBlocks.STATUS_LAMP.defaultBlockState().setValue(StatusLampBlock.STATUS, LampStatus.IDLE)); p.bind(x, 70, -12, "agent:" + id);
			a.put(AnchorNames.desk(id), x - 0.5, FEET, -7.5, 180, 0);
			a.put("seat_" + id, x - 0.5, FEET, -7.5, 180, 0);
			a.put(AnchorNames.monitor(id), x + 0.5, 68, -8.748, 0, 0);
			a.cameraLookAt("desk_" + id, x + 2, 68.2, -5.2, x + 0.2, 67.8, -8.75);
		}
		// All eighteen T3 residents share the main daylight work floor. The original six
		// stations remain intact; the remaining twelve fit along a narrow open-hall workbench.
		var cast = dev.agentcraft.Cast.ids();
		for (int i = 6; i < cast.size(); i++) {
			int x = -21 + (i - 6) * 2;
			int z = -4;
			String id = cast.get(i);
			p.set(x, FEET, z, St.slab(Blocks.PALE_OAK_SLAB, true));
			for (int y = FEET + 1; y <= FEET + 2; y++) {
				p.set(x, y, z, ModBlocks.MONITOR.defaultBlockState().setValue(PanelBlock.FACING, S).setValue(MonitorBlock.LIT, true));
				p.bind(x, y, z, id);
			}
			p.set(x, 70, z - 1, ModBlocks.STATUS_LAMP.defaultBlockState().setValue(StatusLampBlock.STATUS, LampStatus.IDLE));
			p.bind(x, 70, z - 1, "agent:" + id);
			p.set(x, FEET, z + 1, St.stairs(Blocks.WOOL_STAIRS.pick(DyeColor.GRAY), S, false));
			a.put(AnchorNames.desk(id), x + .5, FEET, z + 1.5, 180, 0);
			a.put("seat_" + id, x + .5, FEET, z + 1.5, 180, 0);
			a.put(AnchorNames.monitor(id), x + .5, FEET + 2, z + .25, 0, 0);
			a.cameraLookAt("desk_" + id, x + 2.5, FEET + 2.2, z + 3.8, x + .2, FEET + 1.8, z + .25);
		}
		for (int x : new int[]{-19, -5, 5, 19}) planter(p, x, -10);
	}
	private static void library(Plan p, Anchors.Builder a) {
		p.fill(-24, FEET, -6, -24, 71, 6, WHITE);
		for (int z = -4; z <= 4; z++) for (int y = FEET; y <= 68; y++) {
			p.set(-23, y, z, ModBlocks.MEMORY_ARCHIVE.defaultBlockState().setValue(FacingEntityBlock.FACING, E)); p.bind(-23, y, z, "shared");
		}
		p.set(-23, 67, 0, ModBlocks.MEMORY_CATALOG.defaultBlockState().setValue(HorizontalDirectionalBlock.FACING, E));
		p.fill(-23, 69, -4, -23, 69, 4, WOOD);
		for (int i = 1; i <= 4; i++) a.put(AnchorNames.slot(AnchorNames.LIBRARY, i), -21.5, FEET, -4.5 + i * 2, 90, 0);
		planter(p, -22, -7); planter(p, -22, 5);
	}
	private static void engineering(Plan p, Anchors.Builder a) {
		p.fill(24, FEET, -7, 24, 72, 6, WHITE);
		for (int z = -6; z <= -2; z++) p.set(23, FEET, z, St.slab(Blocks.PALE_OAK_SLAB, true));
		for (int i = 1; i <= 3; i++) { p.set(24, 68, -6 + i, ModBlocks.STATUS_LAMP.defaultBlockState().setValue(StatusLampBlock.STATUS, LampStatus.OFF)); p.bind(24, 68, -6 + i, "ci:#" + i); a.put(AnchorNames.slot(AnchorNames.TESTBENCH, i), 21.5, FEET, -8.5 + i * 2, -90, 0); }
		p.set(23, 67, -4, ModBlocks.CONSOLE_TERMINAL.defaultBlockState().setValue(FacingEntityBlock.FACING, W));
		for (int z = -1; z <= 1; z++) p.set(23, FEET, z, ModBlocks.MERGE_STATION.defaultBlockState().setValue(FacingEntityBlock.FACING, W).setValue(MergeStationBlock.ACTIVE, false));
		p.set(24, 69, 0, ModBlocks.STATUS_LAMP.defaultBlockState().setValue(StatusLampBlock.STATUS, LampStatus.OFF)); p.bind(24, 69, 0, "merge");
		for (int i = 1; i <= 2; i++) { a.put(AnchorNames.slot(AnchorNames.MERGESTATION, i), 21.5, FEET, -2.5 + i * 2, -90, 0); p.set(19 + (i - 1) * 2, FEET, -10, ModBlocks.CONSOLE_TERMINAL.defaultBlockState().setValue(FacingEntityBlock.FACING, S)); a.put(AnchorNames.slot(AnchorNames.TERMINAL, i), 19.5 + (i - 1) * 2, FEET, -8.5, 180, 0); }
		planter(p, 23, 3);
	}
	private static void lounge(Plan p, Anchors.Builder a) {
		p.fill(-16, FLOOR, -2, -7, FLOOR, 4, St.concrete(DyeColor.LIGHT_GRAY));
		for (int i = 0; i < 6; i++) {
			int x = -14 + (i % 3) * 3, z = i < 3 ? 0 : 3;
			p.set(x, FEET, z, St.stairs(Blocks.WOOL_STAIRS.pick(DyeColor.LIGHT_GRAY), i < 3 ? N : S, false));
			a.put(AnchorNames.slot(AnchorNames.LOUNGE, i + 1), x + 0.5, FEET, z + 0.5, i < 3 ? 0 : 180, 0);
		}
		// Tables stay out of the direct routes between seat approaches and the main aisle.
		p.fill(-14, FEET, 2, -13, FEET, 2, St.slab(Blocks.SMOOTH_QUARTZ_SLAB, true));
		p.set(-14, 67, 2, Blocks.POTTED_FERN.defaultBlockState());
		p.fill(-17, FEET, 5, -7, FEET, 5, St.slab(Blocks.PALE_OAK_SLAB, true));
		p.set(-16, 67, 5, Blocks.POTTED_FERN.defaultBlockState());
		// Kitchen island and coffee bar against the glass, with a clear entry at its east end.
		p.fill(-17, 67, 5, -10, 68, 5, WHITE);
		p.set(-12, 67, 4, Blocks.CAULDRON.defaultBlockState());
		p.set(-10, 67, 4, Blocks.SMOKER.defaultBlockState());
		planter(p, -17, -1);
	}
	private static void meeting(Plan p, Anchors.Builder a) {
		p.fill(6, FLOOR, -3, 16, FLOOR, 4, St.concrete(DyeColor.LIGHT_GRAY));
		for (int x = 6; x <= 16; x++) for (int z : new int[]{-3, 4}) { p.fill(x, FEET, z, x, 71, z, GLASS); p.set(x, 72, z, INK); }
		for (int z = -3; z <= 4; z++) for (int x : new int[]{6, 16}) { p.fill(x, FEET, z, x, 71, z, GLASS); p.set(x, 72, z, INK); }
		p.fill(6, FEET, -1, 6, 70, 1, AIR);
		p.fill(8, FEET, 0, 14, FEET, 0, St.slab(Blocks.PALE_OAK_SLAB, true));
		p.set(14, 67, 0, Blocks.POTTED_FERN.defaultBlockState());
		for (int i = 0; i < 6; i++) {
			int x = 8 + (i % 3) * 3, z = i < 3 ? -2 : 2;
			p.set(x, FEET, z, St.stairs(Blocks.WOOL_STAIRS.pick(DyeColor.GRAY), i < 3 ? N : S, false));
			a.put(AnchorNames.slot(AnchorNames.MEETING, i + 1), x + 0.5, FEET, z + 0.5, i < 3 ? 0 : 180, 0);
		}
		p.fill(10, 69, 3, 14, 70, 3, St.concrete(DyeColor.BLACK));
		p.fill(8, 72, 0, 14, 72, 0, ModBlocks.GLOW_PANEL.defaultBlockState());
	}
	private static void lobby(Plan p, Anchors.Builder a) {
		p.fill(-10, FEET, 11, -10, 73, 20, WHITE);
		for (int z = 12; z <= 18; z++) for (int y = 67; y <= 70; y++) p.set(-9, y, z, ModBlocks.TASK_BOARD.defaultBlockState().setValue(PanelBlock.FACING, E));
		p.fill(-9, 71, 12, -9, 71, 18, INK); p.fill(-9, FEET, 12, -9, FEET, 18, INK);
		a.put(AnchorNames.TASK_WALL, -8.873, 69, 15.5, -90, 0);
		p.set(0, FEET, 15, ModBlocks.STATUS_LAMP.defaultBlockState().setValue(StatusLampBlock.STATUS, LampStatus.IDLE)); p.bind(0, FEET, 15, "goal:atrium");
		a.put(AnchorNames.GOAL_ATRIUM, 0.5, FEET, 15.5, 180, 0);
		p.set(6, FEET, 15, ModBlocks.DECISION_PODIUM.defaultBlockState().setValue(FacingEntityBlock.FACING, W).setValue(DecisionPodiumBlock.OPEN, false));
		a.put(AnchorNames.DECISION_PODIUM, 6.5, FEET + .95, 15.5, 90, 0);
		p.fill(9, FEET, 12, 9, 71, 18, WOOD);
		p.set(9, 69, 15, ModBlocks.STATUS_LAMP.defaultBlockState().setValue(StatusLampBlock.STATUS, LampStatus.OFF)); p.bind(9, 69, 15, "decisions");
		p.set(7, FEET, 19, ModBlocks.CONSOLE_TERMINAL.defaultBlockState().setValue(FacingEntityBlock.FACING, W));
		a.put("podium_user", 5.5, FEET, 15.5, -90, 0);
		for (int i = 1; i <= 3; i++) a.put(AnchorNames.slot(AnchorNames.USER, i), 3.5, FEET, 11.5 + i * 2, -90, 0);
		p.fill(4, FEET, 23, 7, FEET, 23, St.slab(Blocks.SMOOTH_QUARTZ_SLAB, true));
		p.set(6, 67, 23, Blocks.POTTED_FERN.defaultBlockState());
		planter(p, -7, 23); planter(p, 8, 22);
	}
	/** A tucked-in lower level for archives, quiet work and project planning. */
	private static void lowerLevel(Plan p, Anchors.Builder a) {
		p.fill(-22, 61, -10, 22, 64, 8, AIR);
		p.fill(-22, 60, -10, 22, 60, 8, STONE);
		for (int x = -22; x <= 22; x++) {
			p.fill(x, 61, -10, x, 63, -10, WHITE); p.fill(x, 61, 8, x, 63, 8, WHITE);
		}
		for (int z = -9; z <= 7; z++) {
			p.fill(-22, 61, z, -22, 63, z, WHITE); p.fill(22, 61, z, 22, 63, z, WHITE);
		}
		// Warm timber ceiling strips keep the lower rooms readable without lowering the headroom.
		for (int x = -20; x <= 20; x += 5) p.fill(x, 64, -8, x, 64, 6, WOOD);
		for (int x = -20; x <= 20; x += 5) for (int z : new int[]{-8, -1, 6})
			p.setIfAir(x, 63, z, St.light(15));
		// Quiet archive on the west, collaboration table to the east, with an open central spine.
		p.fill(-21, 61, -7, -21, 63, -3, WOOD);
		for (int z = -7; z <= -3; z++) for (int y = 61; y <= 63; y++) {
			p.set(-20, y, z, ModBlocks.MEMORY_ARCHIVE.defaultBlockState().setValue(FacingEntityBlock.FACING, E));
			p.bind(-20, y, z, "shared");
		}
		p.set(-20, 62, -1, ModBlocks.MEMORY_CATALOG.defaultBlockState().setValue(HorizontalDirectionalBlock.FACING, E));
		p.fill(7, 61, 1, 15, 61, 1, St.slab(Blocks.PALE_OAK_SLAB, true));
		for (int x : new int[]{8, 11, 14}) for (int z : new int[]{-1, 3})
			p.set(x, 61, z, St.stairs(Blocks.WOOL_STAIRS.pick(DyeColor.GRAY), z < 0 ? N : S, false));
		p.fill(17, 61, -7, 20, 63, -7, WHITE);
		p.set(18, 62, -6, Blocks.POTTED_FERN.defaultBlockState());
		a.put("campus_lower_archive", -18.5, 61, -5.5, 90, 0);
		a.put("campus_lower_collaboration", 10.5, 61, 4.5, 180, 0);
	}
	/** Upper collaboration floor with a garden-facing gallery and its own broad stair. */
	private static void upperLevel(Plan p, Anchors.Builder a) {
		p.fill(-23, 74, -11, 23, 74, 8, WOOD);
		p.fill(-23, 74, 5, 23, 74, 8, STONE);
		// The pavilion's full-height glass shell frames the quiet studios and shared gallery.
		for (int x : new int[]{-11, 7}) {
			p.fill(x, 75, -10, x, 80, -3, GLASS);
			p.fill(x, 75, 1, x, 80, 4, GLASS);
		}
		for (int z : new int[]{-7, 2}) {
			p.fill(-21, 75, z, -14, 75, z, St.slab(Blocks.PALE_OAK_SLAB, true));
			for (int x : new int[]{-20, -17, -14})
				p.set(x, 75, z - 1, St.stairs(Blocks.WOOL_STAIRS.pick(DyeColor.GRAY), N, false));
		}
		p.fill(10, 75, -6, 18, 75, -6, St.slab(Blocks.PALE_OAK_SLAB, true));
		for (int x : new int[]{11, 14, 17}) for (int z : new int[]{-8, -4})
			p.set(x, 75, z, St.stairs(Blocks.WOOL_STAIRS.pick(DyeColor.LIGHT_GRAY), z < -6 ? N : S, false));
		// Long project table and soft seating make the upper gallery a destination, not an empty attic.
		p.fill(-3, 75, -5, 4, 75, -5, St.slab(Blocks.PALE_OAK_SLAB, true));
		for (int x : new int[]{-2, 0, 2, 3}) {
			p.set(x, 75, -6, St.stairs(Blocks.WOOL_STAIRS.pick(DyeColor.LIGHT_GRAY), N, false));
			p.set(x, 75, -4, St.stairs(Blocks.WOOL_STAIRS.pick(DyeColor.LIGHT_GRAY), S, false));
		}
		for (int x : new int[]{-19, -5, 5, 19}) {
			p.set(x, 75, -10, WHITE);
			p.set(x, 76, -10, Blocks.POTTED_FERN.defaultBlockState());
			p.setIfAir(x, 81, 0, St.light(14));
		}
		p.fill(-23, 83, -11, 23, 83, 9, WHITE);
		p.fill(-7, 83, -8, 7, 84, 6, GLASS);
		for (int z : new int[]{-8, -1, 6}) p.fill(-7, 83, z, 7, 83, z, INK);
		for (int x = -20; x <= 20; x += 5) for (int z : new int[]{-8, -1, 6})
			p.setIfAir(x, 77, z, St.light(15));
		a.put("campus_upper_gallery", .5, 75, 6.5, 180, 0);
		a.put("campus_upper_studio", -18.5, 75, .5, 90, 0);
	}

	/** The flights, landings and voids are confined to their own glazed east wing. */
	private static void stairHall(Plan p, Anchors.Builder a) {
		p.fill(25, 60, -12, 38, 84, 9, AIR);
		p.fill(25, 60, -12, 38, 60, 9, STONE);
		for (int y : new int[]{65, 74}) p.fill(25, y, -12, 38, y, 9, WOOD);
		for (int x = 25; x <= 38; x++) for (int z : new int[]{-12, 9})
			p.fill(x, 61, z, x, 82, z, x == 25 || x == 38 || x % 6 == 0 ? INK : GLASS);
		for (int z = -12; z <= 9; z++) for (int x : new int[]{25, 38})
			p.fill(x, 61, z, x, 82, z, z % 6 == 0 ? INK : GLASS);
		// The buried level has finished retaining walls rather than glass looking straight into soil.
		for (int z : new int[]{-12, 9}) p.fill(25, 61, z, 38, 64, z, WHITE);
		for (int x : new int[]{25, 38}) p.fill(x, 61, -12, x, 64, 9, WHITE);
		for (int y : new int[]{60, 65, 74}) {
			p.fill(25, y, -12, 38, y, -12, WHITE); p.fill(25, y, 9, 38, y, 9, WHITE);
			p.fill(25, y, -12, 25, y, 9, WHITE); p.fill(38, y, -12, 38, y, 9, WHITE);
		}
		// Upper stairs: five risers to a broad turn, then four back to the upper gallery.
		p.fill(26, 74, -4, 36, 74, 3, AIR);
		p.fill(26, 70, -4, 36, 70, -1, WHITE);
		flight(p, 27, 4, 66, 5, -1);
		flight(p, 33, 0, 71, 4, 1);
		rail(p, 26, -4, 36, -4, 70);
		rail(p, 26, -3, 26, -1, 70); rail(p, 36, -3, 36, -1, 70);
		rail(p, 26, -4, 36, -4, 74);
		rail(p, 26, -3, 26, 3, 74); rail(p, 36, -3, 36, 3, 74);
		rail(p, 26, 4, 32, 4, 74);
		// Lower stairs occupy a separate northern well, with a turn and a protected ground landing.
		p.fill(26, 65, -11, 36, 65, -8, AIR);
		p.fill(26, 65, -7, 30, 65, -5, AIR);
		p.fill(27, 63, -11, 35, 63, -10, WHITE);
		flight(p, 27, -7, 61, 3, -1);
		flight(p, 33, -9, 64, 2, 1);
		rail(p, 26, -11, 26, -5, 65); rail(p, 30, -7, 30, -5, 65);
		rail(p, 26, -5, 30, -5, 65); rail(p, 36, -11, 36, -8, 65);
		// Doors align with the clear south spine upstairs and downstairs; the archive uses the north end.
		for (int floor : new int[]{65, 74}) {
			p.fill(24, floor, 5, 26, floor, 7, STONE);
			p.fill(24, floor + 1, 5, 25, floor + 5, 7, AIR);
			p.fill(24, floor + 6, 5, 25, floor + 6, 7, WHITE);
		}
		p.fill(22, 60, -4, 26, 60, -2, STONE);
		p.fill(22, 61, -4, 26, 64, -2, AIR);
		for (int z : new int[]{-5, -1}) p.fill(22, 61, z, 25, 64, z, WHITE);
		p.fill(22, 64, -4, 25, 64, -2, WHITE);
		p.fill(25, 83, -12, 39, 83, 10, WHITE);
		p.fill(25, 84, -12, 39, 84, 10, St.slab(Blocks.SMOOTH_QUARTZ_SLAB, false));
		p.fill(28, 83, -10, 35, 84, 6, GLASS);
		for (int z : new int[]{-10, -3, 4}) p.fill(28, 83, z, 35, 83, z, INK);
		for (int floor : new int[]{60, 65, 74}) for (int x : new int[]{27, 31, 35}) for (int z : new int[]{-9, -3, 6})
			p.setIfAir(x, floor + 3, z, St.light(15));
		p.setIfAir(31, 73, -2, St.light(15)); p.setIfAir(31, 66, -10, St.light(15));
		for (int y : new int[]{64, 73, 82}) p.fill(26, y, 7, 37, y, 7, ModBlocks.GLOW_PANEL.defaultBlockState());
		a.put("campus_stairs", 35.5, FEET, 6.5, 180, 0);
		a.cameraLookAt("stair_hall", 36.5, 69, 7.5, 29, 71, -1);
		a.cameraLookAt("upper_gallery", 20, 77, 6.5, -2, 77, -2);
	}

	private static void flight(Plan p, int x, int z, int firstY, int steps, int dz) {
		for (int i = 0; i < steps; i++) {
			int y = firstY + i, atZ = z + i * dz;
			p.fill(x, y, atZ, x + 2, y, atZ, St.stairs(Blocks.SMOOTH_QUARTZ_STAIRS, dz < 0 ? N : S, false));
			p.fill(x, y - 1, atZ, x + 2, y - 1, atZ, WHITE);
			p.fill(x, y + 1, atZ, x + 2, y + 3, atZ, AIR);
			rail(p, x - 1, atZ, x - 1, atZ, y); rail(p, x + 3, atZ, x + 3, atZ, y);
		}
	}

	private static void rail(Plan p, int x0, int z0, int x1, int z1, int floor) {
		p.fill(x0, floor + 1, z0, x1, floor + 1, z1, Blocks.GLASS_PANE);
	}
	private static void planter(Plan p, int x, int z) {
		p.set(x, FEET, z, St.concrete(DyeColor.WHITE)); p.set(x, 67, z, St.leaves(Blocks.AZALEA_LEAVES)); p.set(x, 68, z, St.leaves(Blocks.FLOWERING_AZALEA_LEAVES));
	}
	private static void landscape(Plan p) {
		// Continuous landforms extend into the woodland; architecture has a level, generous apron.
		HqLandscape.islandGround(p);
		levelLawn(p, 25, -16, 43, 14);
		// Level the designed courtyard while letting the perimeter hills retain their relief.
		for (int x = -28; x <= 28; x++) for (int z = 10; z <= 46; z++) {
			p.fill(x, 65, z, x, p.top(x, z) + 1, z, AIR);
			p.set(x, 64, z, Blocks.GRASS_BLOCK); p.setTop(x, z, 64);
		}
		path(p, -9, 28, 9, 35);
		path(p, -3, 35, 3, 54);
		path(p, -28, 29, 28, 31);
		path(p, -28, 8, -26, 45);
		path(p, -28, 43, 25, 45);
		path(p, 26, 12, 28, 45);
		p.fill(-25, FLOOR, 10, -12, FLOOR, 24, WOOD);
		p.fill(-25, FLOOR, 10, -25, FLOOR, 24, WHITE);
		for (int z : new int[]{10, 24}) p.fill(-25, FLOOR, z, -12, FLOOR, z, WHITE);
		for (int z : new int[]{13, 19}) { p.fill(-23, FEET, z, -20, FEET, z, St.stairs(Blocks.WOOL_STAIRS.pick(DyeColor.WHITE), N, false)); p.fill(-17, FEET, z, -15, FEET, z, St.slab(Blocks.PALE_OAK_SLAB, true)); }
		for (int x : new int[]{-25, -12}) for (int z : new int[]{10, 24}) { p.fill(x, FEET, z, x, 72, z, INK); planter(p, x + 1, z + (z == 10 ? 1 : -1)); }
		for (int z = 10; z <= 24; z += 2) p.fill(-25, 73, z, -12, 73, z, St.slab(Blocks.PALE_OAK_SLAB, false));
		for (int z : new int[]{12, 20}) p.fill(-22, 72, z, -15, 72, z, ModBlocks.GLOW_PANEL.defaultBlockState());
		p.fill(-19, FLOOR, 24, -16, FLOOR, 24, WOOD);
		p.fill(-19, FLOOR, 25, -16, FLOOR, 25, St.stairs(Blocks.SMOOTH_QUARTZ_STAIRS, N, false));
		path(p, -19, 26, -16, 29);
		// A sunken, still reflecting pool with underwater lights and a low planted island.
		p.fill(12, 63, 12, 25, 63, 27, WHITE);
		p.fill(12, 64, 12, 25, 64, 27, WHITE);
		p.fill(13, 64, 13, 24, 64, 26, Blocks.WATER.defaultBlockState());
		for (int x : new int[]{14, 23}) for (int z : new int[]{14, 25}) p.set(x, 63, z, Blocks.SEA_LANTERN);
		p.fill(18, 64, 18, 20, 64, 20, WHITE);
		p.fill(18, 65, 18, 20, 65, 20, St.leaves(Blocks.AZALEA_LEAVES));
		p.set(19, 66, 19, St.leaves(Blocks.FLOWERING_AZALEA_LEAVES));
		for (int side : new int[]{-1, 1}) {
			garden(p, side < 0 ? -23 : 12, 34, side < 0 ? -12 : 23, 41);
			garden(p, side < 0 ? -9 : 6, 37, side < 0 ? -6 : 9, 41);
			int bx = side * 18;
			p.set(bx - 3, 65, 46, INK); p.set(bx + 3, 65, 46, INK);
			p.fill(bx - 2, 65, 46, bx + 2, 65, 46, St.stairs(Blocks.PALE_OAK_STAIRS, N, false));
			for (int z : new int[]{33, 42, 50}) {
				p.set(side * 5, 65, z, INK); p.set(side * 5, 66, z, Blocks.SEA_LANTERN);
				p.set(side * 5, 67, z, St.slab(Blocks.SMOOTH_STONE_SLAB, false));
			}
		}
		// Long bands of planting soften the white roofline without hiding the glass.
		for (int x = -23; x <= 23; x++) if (Math.abs(x) > 11) {
			if (x > 0) p.set(x, 65, 10, St.leaves(Blocks.AZALEA_LEAVES));
			p.set(x, 65, -14, St.leaves(HqLandscape.hash(x, -14, 81) % 4 == 0 ? Blocks.FLOWERING_AZALEA_LEAVES : Blocks.AZALEA_LEAVES));
		}
		for (int x : new int[]{-8, 8}) for (int z : new int[]{29, 34}) p.setIfAir(x, 66, z, St.light(12));
	}
	/** A connected north studio and east reading court; both leave the existing agent routes intact. */
	private static void campus(Plan p, Anchors.Builder a) {
		levelLawn(p, -24, -34, 24, -15);
		levelLawn(p, 30, 14, 44, 34);
		path(p, -2, -21, 2, -13);
		path(p, -20, -18, 42, -16);
		path(p, 40, -18, 42, 13);
		path(p, 26, 11, 42, 13);
		path(p, 29, 29, 41, 31);
		p.fill(-2, FEET, -12, 2, 70, -12, AIR);
		p.fill(-2, FLOOR, -19, 2, FLOOR, -13, STONE);
		pavilion(p, -12, -32, 12, -20, 73);
		p.fill(-2, FEET, -20, 2, 70, -20, AIR);
		p.fill(-2, FLOOR, -20, 2, FLOOR, -19, STONE);
		p.fill(-2, FLOOR, -18, 2, FLOOR, -18, STONE);
		// Shelving, a pin-up wall and a long shared table give the studio its own purpose.
		p.fill(-12, FEET, -31, -12, 71, -21, WOOD);
		p.fill(-11, FEET, -30, -11, 68, -27, Blocks.BOOKSHELF.defaultBlockState());
		p.fill(-11, 68, -25, -11, 70, -22, WHITE);
		for (int z : new int[]{-25, -23}) p.fill(-10, 69, z, -10, 70, z, Blocks.TERRACOTTA.defaultBlockState());
		p.fill(4, FEET, -29, 4, FEET, -23, St.slab(Blocks.PALE_OAK_SLAB, true));
		for (int z : new int[]{-28, -25}) {
			p.set(2, FEET, z, St.stairs(Blocks.WOOL_STAIRS.pick(DyeColor.GRAY), W, false));
			p.set(6, FEET, z, St.stairs(Blocks.WOOL_STAIRS.pick(DyeColor.GRAY), E, false));
		}
		p.fill(-8, FEET, -29, -4, FEET, -29, St.stairs(Blocks.WOOL_STAIRS.pick(DyeColor.CYAN), N, false));
		p.fill(-7, FEET, -26, -5, FEET, -26, St.slab(Blocks.SMOOTH_QUARTZ_SLAB, true));
		p.set(-6, 67, -26, Blocks.POTTED_FERN.defaultBlockState());
		p.set(4, 67, -24, Blocks.POTTED_FERN.defaultBlockState());
		for (int x : new int[]{-8, 0, 8}) {
			p.fill(x, 72, -30, x, 72, -22, WOOD);
			for (int z : new int[]{-29, -25, -21}) p.setIfAir(x, 69, z, St.light(15));
			p.set(x, 71, -25, ModBlocks.GLOW_PANEL.defaultBlockState());
		}
		for (int x : new int[]{-16, 16}) {
			garden(p, x - 2, -30, x + 2, -22);
			p.fill(x - 2, 65, -20, x + 2, 65, -20, St.stairs(Blocks.PALE_OAK_STAIRS, S, false));
			p.set(x, 65, -15, Blocks.SEA_LANTERN);
			p.set(x, 66, -15, St.slab(Blocks.SMOOTH_STONE_SLAB, false));
		}
		// The garden is a smaller, sheltered water feature rather than another full-size plaza.
		p.fill(32, 64, 17, 43, 64, 26, STONE);
		p.fill(34, 64, 19, 40, 64, 23, WHITE);
		p.fill(35, 64, 20, 39, 64, 22, Blocks.WATER.defaultBlockState());
		p.fill(35, 63, 20, 39, 63, 22, WHITE); p.set(37, 63, 21, Blocks.SEA_LANTERN);
		p.fill(32, 65, 17, 43, 65, 17, St.leaves(Blocks.AZALEA_LEAVES));
		for (int z : new int[]{18, 25}) p.fill(32, 65, z, 32, 65, z + 1, St.leaves(Blocks.FLOWERING_AZALEA_LEAVES));
		p.fill(35, 65, 25, 40, 65, 25, St.stairs(Blocks.PALE_OAK_STAIRS, S, false));
		p.fill(33, 71, 17, 42, 71, 26, St.slab(Blocks.PALE_OAK_SLAB, false));
		for (int x : new int[]{33, 42}) for (int z : new int[]{17, 26}) p.fill(x, 65, z, x, 70, z, INK);
		// Slits admit daylight and leave a delicate oak shadow over the court.
		for (int z = 18; z <= 25; z += 2) p.fill(34, 71, z, 41, 71, z, AIR);
		for (int x : new int[]{34, 40}) for (int z : new int[]{18, 24}) p.setIfAir(x, 67, z, St.light(14));
		p.set(42, 65, 28, Blocks.FIREFLY_BUSH.defaultBlockState());
		p.set(33, 65, 28, Blocks.FIREFLY_BUSH.defaultBlockState());
		HqLandscape.tree(p, 38, 36, HqLandscape.Kind.CHERRY, 921);
		HqLandscape.tree(p, 44, 21, HqLandscape.Kind.SPRUCE, 922);
		HqLandscape.tree(p, 44, 30, HqLandscape.Kind.POPLAR, 923);
		path(p, 36, 27, 38, 30);
		a.put("campus_studio", .5, FEET, -21.5, 180, 0);
		a.put("campus_garden", 37.5, 65, 24.5, 180, 0);
		a.cameraLookAt("garden_studio", 18, 73, -16, 0, 69, -26);
		a.cameraLookAt("focus_garden", 29, 70, 36, 38, 67, 22);
	}
	private static void usageRoom(Plan p, Anchors.Builder a) {
		levelLawn(p, -27, -33, -14, -19);
		p.fill(-26, FEET, -32, -15, 74, -20, AIR);
		pavilion(p, -26, -32, -15, -20, 73);
		p.fill(-23, FEET, -20, -20, 70, -20, AIR);
		p.fill(-23, FLOOR, -20, -20, FLOOR, -18, STONE);
		p.fill(-23, FLOOR, -17, -20, FLOOR, -17, St.stairs(Blocks.SMOOTH_QUARTZ_STAIRS, N, false));
		p.fill(-26, FEET, -32, -15, 72, -32, WHITE);
		for (int x = -24; x <= -18; x++) for (int y = 67; y <= 70; y++) {
			p.set(x, y, -31, ModBlocks.MONITOR.defaultBlockState().setValue(PanelBlock.FACING, S));
			p.bind(x, y, -31, "usage");
		}
		p.fill(-24, FEET, -24, -18, FEET, -24, St.stairs(Blocks.WOOL_STAIRS.pick(DyeColor.GRAY), S, false));
		p.fill(-24, 72, -29, -18, 72, -23, ModBlocks.GLOW_PANEL.defaultBlockState());
		planter(p, -25, -22);
		a.put("campus_usage", -21.5, FEET, -22.5, 180, 0);
		a.cameraLookAt("usage_room", -21.5, 68.5, -23, -21, 69, -31);
	}
	private static void levelLawn(Plan p, int x0, int z0, int x1, int z1) {
		for (int x = x0; x <= x1; x++) for (int z = z0; z <= z1; z++) {
			p.fill(x, 65, z, x, p.top(x, z) + 1, z, AIR);
			p.set(x, 64, z, Blocks.GRASS_BLOCK); p.setTop(x, z, 64);
		}
	}
	private static void path(Plan p, int x0, int z0, int x1, int z1) {
		for (int x = x0; x <= x1; x++) for (int z = z0; z <= z1; z++) {
			p.fill(x, 65, z, x, p.top(x, z) + 1, z, AIR);
			p.set(x, 64, z, x == x0 || x == x1 || z == z0 || z == z1 ? Blocks.ANDESITE.defaultBlockState() : STONE);
			p.setTop(x, z, 64);
		}
	}
	private static void garden(Plan p, int x0, int z0, int x1, int z1) {
		for (int x = x0; x <= x1; x++) for (int z = z0; z <= z1; z++) {
			boolean rim = x == x0 || x == x1 || z == z0 || z == z1;
			p.set(x, 64, z, rim ? STONE : Blocks.GRASS_BLOCK.defaultBlockState());
			if (rim) continue;
			// White and violet drifts, rather than a noisy checkerboard of random colours.
			int band = Math.floorMod(x + z / 3, 7);
			p.set(x, 65, z, switch (band) {
				case 0, 1 -> Blocks.ALLIUM.defaultBlockState();
				case 2, 3 -> Blocks.LILY_OF_THE_VALLEY.defaultBlockState();
				case 4 -> Blocks.OXEYE_DAISY.defaultBlockState();
				default -> St.flowerBed(Blocks.PINK_PETALS, Direction.NORTH, 4);
			});
		}
	}
	private static void cameras(Anchors.Builder a) {
		a.cameraLookAt("exterior_hero", 59, 94, 68, 0, 72, 8);
		a.cameraLookAt("island_aerial", 104, 132, 112, 0, 65, 9);
		a.cameraLookAt("night", 24, 72, 44, 0, 70, 10);
		a.cameraLookAt("entrance_atrium", .5, 68, 24, .5, 69, 8);
		a.cameraLookAt("task_wall", -5.5, 69, 15.5, -8.9, 69, 15.5);
		a.cameraLookAt("decision_podium", 3, 68, 18, 7, 68, 15);
		a.cameraLookAt("wide_interior", -3, 69, 7, 7, 68, -5);
		a.cameraLookAt("hall", .5, 68.2, 9, .5, 68, -10);
		a.cameraLookAt("library", -17, 68.5, 5, -23, 68, 0);
		a.cameraLookAt("console", 4, 68, 22, 7, 67, 19);
		a.cameraLookAt("merge_station", 19, 68, 3, 23, 67, 0);
		a.cameraLookAt("testbench", 18, 68.5, -1, 23, 67, -4);
		a.cameraLookAt("lounge", -5, 69, -3, -12, 67, 3);
		a.cameraLookAt("agent_desk", -8, 68, -6, -11, 67.6, -8.4);
		a.cameraLookAt("meeting", 3, 68.5, 3, 12, 68, 1);
		a.cameraLookAt("terrace", -12, 70, 30, -19, 67, 17);
	}
}

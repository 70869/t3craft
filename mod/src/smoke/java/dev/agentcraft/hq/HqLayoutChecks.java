package dev.agentcraft.hq;

import dev.agentcraft.block.ModBlocks;
import dev.agentcraft.client.agents.GridPathfinder;
import dev.agentcraft.layout.Anchors;
import dev.agentcraft.world.HqIsland;
import net.minecraft.SharedConstants;
import net.minecraft.core.Direction;
import net.minecraft.core.BlockPos;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.phys.Vec3;

/** Exercises the assembled block plan, including floor openings and occupied office rooms. */
public final class HqLayoutChecks {
	public static void main(String[] args) throws ReflectiveOperationException {
		try { dev.agentcraft.world.HqWorldChecks.run(); }
		catch (Exception e) { throw new RuntimeException("HQ world identity checks failed", e); }
		WorldTemplateChecks.run();
		SharedConstants.tryDetectVersion();
		// Fabric registers blocks before vanilla seals the registries. Reproduce that order here.
		var bootstrapped = Bootstrap.class.getDeclaredField("isBootstrapped");
		bootstrapped.setAccessible(true);
		bootstrapped.setBoolean(null, true);
		ModBlocks.init();
		bootstrapped.setBoolean(null, false);
		Bootstrap.bootStrap();
		var anchors = Anchors.builder(ModernHqBuilder.ID);
		Plan plan = ModernHqBuilder.plan(anchors);
		var layout = anchors.build();
		for (int x = -23; x <= 23; x++) for (int z = -11; z <= 8; z++) {
			check(!plan.isAir(x, 65, z), "office floor has an unguarded opening at " + x + ",65," + z);
			for (int y = 60; y <= 74; y++)
				check(!plan.get(x, y, z).is(Blocks.SMOOTH_QUARTZ_STAIRS),
					"circulation stairs clash with an occupied office room at " + x + "," + y + "," + z);
		}
		for (int floor : new int[]{65, 74}) for (int x = -20; x <= 25; x++) for (int z = 6; z <= 7; z++) {
			check(!plan.isAir(x, floor, z), "corridor has no support at " + x + "," + floor + "," + z);
			clear(plan, x, floor + 1, z, 3);
		}
		flight(plan, 27, 4, 66, 5, -1);
		flight(plan, 33, 0, 71, 4, 1);
		flight(plan, 27, -7, 61, 3, -1);
		flight(plan, 33, -9, 64, 2, 1);
		for (int x = 27; x <= 35; x++) {
			check(!plan.isAir(x, 70, -1), "upper turn landing is missing"); clear(plan, x, 71, -1, 3);
			check(!plan.isAir(x, 63, -10), "lower turn landing is missing"); clear(plan, x, 64, -10, 3);
		}
		for (int x = 22; x <= 26; x++) for (int z = -4; z <= -2; z++) {
			check(!plan.isAir(x, 60, z), "archive connection is unsupported"); clear(plan, x, 61, z, 3);
		}
		check(plan.get(30, 66, -5).is(Blocks.GLASS_PANE), "lower well needs a guard at its ground landing");
		check(plan.get(30, 75, 4).is(Blocks.GLASS_PANE), "upper well needs a guard at its upper landing");
		for (int[] point : new int[][]{{-46, -36}, {46, -36}, {-46, 54}, {46, 54}})
			check(HqIsland.groundHeight(point[0], point[1]) >= 64, "shoreline reaches a recorded campus corner");
		for (int[] point : new int[][]{{100, 0}, {-100, 0}, {0, -100}, {0, 100}, {2000, 2000}}) {
			check(HqIsland.terrain(point[0], 63, point[1]).is(Blocks.WATER), "ocean missing beyond island");
			check(HqIsland.terrain(point[0], 64, point[1]).isAir(), "grass remains above ocean horizon");
		}
		check(HqIsland.terrain(0, 64, 65).is(Blocks.SAND), "waterfront should stand on a dry sandy shore");
		check(HqIsland.groundHeight(-62,-47)>=82, "northwest ridge needs substantial height above the campus");
		check(HqIsland.groundHeight(53,-51)>=75, "northeast woodland needs its own landform");
		check(HqIsland.groundHeight(-61,34)>=68, "western headland should rise above the beach");
		check(HqIsland.terrain(76,63,23).is(Blocks.WATER), "eastern cove should cut into the coast");
		check(HqIsland.groundHeight(65,-15)>=64 && HqIsland.groundHeight(65,62)>=64,
			"cove should sit between two distinct headlands");
		check(HqIsland.terrain(1000,49,1000).is(Blocks.WATER), "deep ocean should descend below the coastal shelf");
		check(HqIsland.groundHeight(0,80)>HqIsland.groundHeight(0,120), "beach shelf should grade into deeper water");
		for (int x=-100; x<=100; x++) for (int z=-95; z<=95; z++) {
			if (!HqIsland.onTrail(x,z)) continue;
			int h=HqIsland.groundHeight(x,z);
			check(h>HqIsland.SEA_LEVEL,"trail enters water at "+x+","+z);
			for (int[] offset:new int[][]{{1,0},{0,1}}) if (HqIsland.onTrail(x+offset[0],z+offset[1]))
				check(Math.abs(h-HqIsland.groundHeight(x+offset[0],z+offset[1]))<=1,
					"trail has an unclimbable terrain step at "+x+","+z);
		}
		BlockGetter blocks = new BlockGetter() {
			@Override public BlockState getBlockState(BlockPos pos) {
				return plan.inXZ(pos.getX(),pos.getZ()) ? plan.get(pos.getX(),pos.getY(),pos.getZ())
					: HqIsland.terrain(pos.getX(),pos.getY(),pos.getZ());
			}
			@Override public FluidState getFluidState(BlockPos pos) { return getBlockState(pos).getFluidState(); }
			@Override public BlockEntity getBlockEntity(BlockPos pos) { return null; }
			@Override public int getHeight() { return plan.maxY - plan.minY + 1; }
			@Override public int getMinY() { return plan.minY; }
		};
		var pf = new GridPathfinder(blocks, layout.bounds());
		java.util.Set<BlockPos> deskSeats = new java.util.HashSet<>();
		for (String id : dev.agentcraft.Cast.ids()) {
			var desk = layout.get("desk_" + id);
			check(desk != null && layout.get("monitor_" + id) != null, "resident needs a desk and monitor: " + id);
			check(Math.abs(desk.y() - 66) < 0.001 && desk.x() >= -22 && desk.x() <= 16,
				"resident work desk must be on the main daylight floor: " + id);
			var seat = dev.agentcraft.client.agents.Seats.detect(blocks, desk, pf);
			check(seat != null && seat.approach() != null && !Double.isNaN(seat.deskTop()), "desk needs an accessible chair and typing surface: " + id);
			check(deskSeats.add(BlockPos.containing(seat.sit())), "resident desk shares another chair: " + id);
			int index = dev.agentcraft.Cast.ids().indexOf(id) + 1;
			var lounge = layout.get(dev.agentcraft.layout.AnchorNames.slot("lounge", index));
			var loungeSeat = dev.agentcraft.client.agents.Seats.detect(blocks, lounge, pf);
			check(loungeSeat != null && loungeSeat.approach() != null, "resident needs an accessible lounge seat: " + id);
			check(new GridPathfinder(blocks, layout.bounds()).find(loungeSeat.approach(), seat.approach()) != null,
				"resident cannot reach its desk from lounge: " + id);
			check(new GridPathfinder(blocks, layout.bounds()).find(seat.approach(), loungeSeat.approach()) != null,
				"resident cannot return to lounge: " + id);
		}
		check(deskSeats.size() == 18, "modern campus has 18 real desk chairs");
		check(plan.get(0, 77, 32).isAir() && plan.get(-3, 77, 32).is(Blocks.SMOOTH_QUARTZ), "T3 wordmark pixels missing");
		for (String destination : new String[]{"campus_stairs", "campus_upper_gallery", "campus_lower_archive", "campus_upper_studio", "campus_lower_collaboration"}) {
			var from = layout.get("campus_work"); var to = layout.get(destination);
			check(new GridPathfinder(blocks, layout.bounds()).find(from.pos(), to.pos()) != null,
				"actual block collision shapes have no route from work floor to " + destination);
			check(new GridPathfinder(blocks, layout.bounds()).find(to.pos(), from.pos()) != null,
				"actual block collision shapes have no return route from " + destination);
		}
		// Test each walk in sections: the NPC's bounded search budget deliberately discourages
		// long hill climbs, while these short connected routes prove visitors can walk the entire trail.
		for (int[][] trail : new int[][][]{
			{{-28,44},{-43,47},{-55,39},{-63,21},{-60,0},{-53,-23},{-55,-42},{-66,-59}},
			{{41,-18},{48,-30},{51,-42},{60,-51},{66,-61}},
			{{28,44},{40,48},{48,57},{53,67},{61,71}},
			{{0,54},{0,65}}
		}) {
			Vec3 from=layout.get("entrance").pos();
			for (int[] point : trail) {
				Vec3 to=new Vec3(point[0]+.5,HqIsland.groundHeight(point[0],point[1])+1,point[1]+.5);
				check(new GridPathfinder(blocks,null).find(from,to)!=null,"coastal walk has no route from "+from+" to "+to);
				check(new GridPathfinder(blocks,null).find(to,from)!=null,"coastal walk has no return from "+to+" to "+from);
				from=to;
			}
		}
		System.out.println("PASS 18 accessible desks and lounges, bidirectional resident routes, T3 sign, office floors, clear corridors, 3-wide stairs and headroom, guarded wells, turn landings, archive access, bidirectional routes between all floors, coastal ridges, cove, walkable trails, beach shelf and deep ocean");
	}

	private static void flight(Plan plan, int x, int z, int firstY, int steps, int dz) {
		for (int i = 0; i < steps; i++) for (int lane = 0; lane < 3; lane++) {
			int y = firstY + i, atZ = z + i * dz;
			var stair = plan.get(x + lane, y, atZ);
			check(stair.is(Blocks.SMOOTH_QUARTZ_STAIRS), "stair flight must stay three blocks wide");
			check(stair.getValue(StairBlock.FACING) == (dz < 0 ? Direction.NORTH : Direction.SOUTH), "stair faces away from its ascent");
			check(!plan.isAir(x + lane, y - 1, atZ), "stair has no stringer support");
			clear(plan, x + lane, y + 1, atZ, 3);
		}
	}

	private static void clear(Plan plan, int x, int y, int z, int height) {
		for (int dy = 0; dy < height; dy++) {
			var block = plan.get(x, y + dy, z);
			check(block.isAir() || block.is(Blocks.LIGHT), "route is obstructed at " + x + "," + (y + dy) + "," + z);
		}
	}

	private static void check(boolean condition, String message) {
		if (!condition) throw new AssertionError(message);
	}
}

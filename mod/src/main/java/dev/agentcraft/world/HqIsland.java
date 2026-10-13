package dev.agentcraft.world;

import dev.agentcraft.hq.ModernHqBuilder;
import dev.agentcraft.layout.AnchorNames;
import dev.agentcraft.layout.Anchors;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerChunkEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LeavesBlock;
import net.minecraft.world.level.block.VegetationBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.levelgen.Heightmap;
import org.jspecify.annotations.Nullable;

/** Coastal terrain and woodland, including previously generated HQ worlds. */
public final class HqIsland {
	public static final int SEA_LEVEL = 63, SEABED = 48;
	private static final int FLAGS = Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE | Block.UPDATE_SUPPRESS_DROPS
		| Block.UPDATE_SKIP_BLOCK_ENTITY_SIDEEFFECTS | Block.UPDATE_SKIP_ON_PLACE;
	private static @Nullable State state;
	// Trails begin on existing campus walks. Wide bends climb the wooded ridge; the southern
	// approach stays low and open so there is an ocean view from the office and entrance.
	private static final int[][][] TRAILS = {
		{{-28,44},{-43,47},{-55,39},{-63,21},{-60,0},{-53,-23},{-55,-42},{-66,-59}},
		{{41,-18},{48,-30},{51,-42},{60,-51},{66,-61}},
		{{0,54},{1,59},{0,63}},
		{{28,44},{40,48},{48,57},{53,67},{61,71}}
	};

	private HqIsland() {}

	private static final class State {
		final MinecraftServer server;
		final Set<Long> pending = new LinkedHashSet<>();
		final Set<Long> processed = new HashSet<>();
		boolean enabled;
		State(MinecraftServer server) { this.server = server; }
	}

	public static void init() {
		ServerChunkEvents.CHUNK_LOAD.register((level, chunk, generated) -> {
			if (level.dimension() != Level.OVERWORLD || !HqWorld.isHq(level.getServer())) return;
			queue(getState(level.getServer()), chunk.getPos().pack());
		});
		ServerLifecycleEvents.SERVER_STARTED.register(HqIsland::refresh);
		ServerLifecycleEvents.SERVER_STOPPED.register(server -> {
			if (state != null && state.server == server) state = null;
		});
		ServerTickEvents.END_SERVER_TICK.register(HqIsland::tick);
	}

	/** Enable after a modern build and queue already-loaded chunks without generating distant terrain. */
	public static void refresh(MinecraftServer server) {
		if (!HqWorld.isHq(server)) return;
		State current = getState(server);
		current.enabled = ModernHqBuilder.ID.equals(Anchors.current().name());
		if (!current.enabled) return;
		var spawn = Anchors.get(AnchorNames.SPAWN);
		int cx = spawn == null ? 0 : ((int) Math.floor(spawn.x())) >> 4;
		int cz = spawn == null ? 0 : ((int) Math.floor(spawn.z())) >> 4;
		queueLoaded(current, cx, cz);
		for (var player : server.getPlayerList().getPlayers())
			if (player.level() == server.overworld()) queueLoaded(current, player.chunkPosition().x(), player.chunkPosition().z());
	}

	private static State getState(MinecraftServer server) {
		if (state == null || state.server != server) state = new State(server);
		return state;
	}

	private static void queueLoaded(State current, int cx, int cz) {
		int radius = current.server.getPlayerList().getViewDistance() + 2;
		for (int x = cx - radius; x <= cx + radius; x++) for (int z = cz - radius; z <= cz + radius; z++)
			if (current.server.overworld().getChunkSource().getChunkNow(x, z) != null)
				queue(current, ChunkPos.pack(x, z));
	}

	private static void queue(State current, long pos) {
		if (!current.processed.contains(pos)) current.pending.add(pos);
	}

	private static void tick(MinecraftServer server) {
		State current = state;
		if (current == null || current.server != server || !current.enabled) return;
		// Spread existing-world conversion over ticks so opening a world does not stall on its horizon.
		long started = System.nanoTime();
		for (int n = 0; n < 4 && !current.pending.isEmpty(); n++) {
			var iterator = current.pending.iterator();
			long pos = iterator.next(); iterator.remove();
			LevelChunk chunk = server.overworld().getChunkSource().getChunkNow(ChunkPos.getX(pos), ChunkPos.getZ(pos));
			if (chunk == null) continue;
			shape(server.overworld(), chunk);
			current.processed.add(pos);
			if (System.nanoTime() - started > 8_000_000) break;
		}
	}

	/** Keep the original recorded site box intact, including the lower floor and player edits. */
	public static boolean inCampus(int x, int z) {
		return x >= -46 && x <= 46 && z >= -36 && z <= 54;
	}

	public static int groundHeight(int x, int z) {
		return column(x, z).ground;
	}

	private static double smooth(double from, double to, double value) {
		double t = Math.clamp((value - from) / (to - from), 0, 1);
		return t * t * (3 - 2 * t);
	}

	private static double ellipse(int x, int z, double cx, double cz, double rx, double rz) {
		return (1 - Math.hypot((x - cx) / rx, (z - cz) / rz)) * Math.min(rx, rz);
	}

	private static double hill(int x, int z, double cx, double cz, double rx, double rz) {
		double dx = (x - cx) / rx, dz = (z - cz) / rz;
		return Math.exp(-dx * dx - dz * dz);
	}

	/** Positive on land: lobed headlands, two deep coves and small offshore rock stacks. */
	private static double coast(int x, int z) {
		double land = ellipse(x,z,0,3,76,75);
		land = Math.max(land, ellipse(x,z,-49,-46,48,42));
		land = Math.max(land, ellipse(x,z,45,-43,38,43));
		land = Math.max(land, ellipse(x,z,-52,44,39,36));
		land = Math.max(land, ellipse(x,z,48,60,29,30));
		land = Math.min(land, -ellipse(x,z,83,23,25,30));
		land = Math.min(land, -ellipse(x,z,-16,93,26,17));
		land += 1.8 * Math.sin(x * .11 + z * .065) + 1.3 * Math.cos(z * .15 - x * .04);
		// The saved campus box must remain inland, regardless of shoreline perturbations.
		if (inCampus(x,z)) land = Math.max(land, 14);
		land = Math.max(land, ellipse(x,z,-99,-49,5,7));
		return Math.max(land, ellipse(x,z,93,-31,4,6));
	}

	public static boolean onTrail(int x, int z) { return trailDistance(x,z) <= 1.6; }

	private static double trailDistance(int x, int z) {
		double best = Double.MAX_VALUE;
		for (int[][] trail : TRAILS) for (int i=1; i<trail.length; i++) {
			int[] a=trail[i-1], b=trail[i];
			double dx=b[0]-a[0], dz=b[1]-a[1];
			double t=Math.clamp(((x-a[0])*dx+(z-a[1])*dz)/(dx*dx+dz*dz),0,1);
			best=Math.min(best,Math.hypot(x-a[0]-t*dx,z-a[1]-t*dz));
		}
		return best;
	}

	private record Column(int ground, boolean beach, boolean rocky, boolean trail, int variant) {
		BlockState at(int y) {
			if (y > Math.max(ground, SEA_LEVEL)) {
				if (y == ground+1 && ground>SEA_LEVEL && !beach && !rocky && !trail) {
					if (variant % 13 == 0) return Blocks.FERN.defaultBlockState();
					if (variant % 7 == 0) return Blocks.SHORT_GRASS.defaultBlockState();
					if (variant % 97 == 0) return Blocks.OXEYE_DAISY.defaultBlockState();
				}
				return Blocks.AIR.defaultBlockState();
			}
			if (y > ground) {
				if (y==ground+1 && ground>=53 && ground<=61 && variant%11==0)
					return Blocks.SEAGRASS.defaultBlockState();
				return Blocks.WATER.defaultBlockState();
			}
			if (y < ground-3) return Blocks.STONE.defaultBlockState();
			if (trail && y == ground) return Blocks.GRAVEL.defaultBlockState();
			if (rocky) {
				if (y == ground && ground>SEA_LEVEL+3 && variant%5<2) return Blocks.GRASS_BLOCK.defaultBlockState();
				return (y+variant)%5==0 ? Blocks.ANDESITE.defaultBlockState() : Blocks.STONE.defaultBlockState();
			}
			if (ground<=SEA_LEVEL || beach) return Blocks.SAND.defaultBlockState();
			return y==ground ? Blocks.GRASS_BLOCK.defaultBlockState() : Blocks.DIRT.defaultBlockState();
		}
	}

	private static Column column(int x, int z) {
		double shore=coast(x,z);
		boolean rocky=(x < -76 && z < 16) || (x > 66 && z < -18);
		int ground;
		if (shore < 0) ground = (int)Math.round(63-15*smooth(0,28,-shore));
		else {
			// The campus apron grades continuously into three different landforms. No rectangular berm.
			double apron=Math.hypot(Math.max(-34-x,Math.max(0,x-44)),Math.max(-34-z,Math.max(0,z-54)));
			double relief=24*hill(x,z,-62,-47,28,29)+15*hill(x,z,53,-51,24,25)
				+7*hill(x,z,-61,34,24,25)+5*hill(x,z,51,63,20,17);
			relief *= smooth(0,22,apron) * smooth(0,rocky?7:14,shore);
			ground=(int)Math.round(64+relief);
			if (rocky && shore < 4) ground += (int)Math.round(3*smooth(0,3,shore));
		}
		boolean beach=!rocky && ground<=67 && shore<(z>55?13:7);
		return new Column(ground,beach,rocky,onTrail(x,z),hash(x,z,17));
	}

	/** Desired shore/ocean column. The saved campus uses the same height field at its margins. */
	public static BlockState terrain(int x, int y, int z) {
		return column(x,z).at(y);
	}

	private static int hash(int x,int z,int seed) {
		long h=x*73856093L ^ z*19349663L ^ seed*83492791L;
		h ^= h>>>13; h *= 0x5bd1e995L; h ^= h>>>15;
		return (int)(h & 0x7fffffff);
	}

	/** Trees are chosen from a world-coordinate grid, so crowns are identical across chunk edges. */
	private static Map<BlockPos,BlockState> woodland(ChunkPos chunk) {
		Map<BlockPos,BlockState> trees = new HashMap<>();
		int minX=chunk.getMinBlockX(), minZ=chunk.getMinBlockZ();
		for (int gx=Math.floorDiv(minX-8,11); gx<=Math.floorDiv(minX+23,11); gx++)
			for (int gz=Math.floorDiv(minZ-8,11); gz<=Math.floorDiv(minZ+23,11); gz++) {
				int seed=hash(gx,gz,71), x=gx*11+seed%7-3, z=gz*11+(seed/7)%7-3;
				if (x>=-51 && x<=51 && z>=-41 && z<=59) continue;
				Column col=column(x,z);
				boolean grove=x<-47 || z<-42 || (x>47 && z>50 && seed%3==0);
				if (!grove || col.ground<65 || col.beach || col.rocky || trailDistance(x,z)<5 || coast(x,z)<8) continue;
				boolean spruce=z<-38 && seed%3!=0;
				int base=col.ground+1, h=(spruce?11:7)+seed%4;
				BlockState log=(spruce?Blocks.SPRUCE_LOG:seed%3==0?Blocks.BIRCH_LOG:Blocks.OAK_LOG).defaultBlockState();
				BlockState leaf=(spruce?Blocks.SPRUCE_LEAVES:seed%3==0?Blocks.BIRCH_LEAVES:Blocks.OAK_LEAVES)
					.defaultBlockState().setValue(LeavesBlock.PERSISTENT,true);
				// Keep the last two conifer tiers entirely leafy; a trunk at the tip reads as a pole.
				for (int y=base; y<base+h-(spruce?2:0); y++) treeCell(trees,chunk,x,y,z,log,true);
				for (int y=base+(spruce?3:h-3); y<=base+h+2; y++) {
					double radius=spruce?Math.max(.6,3.7*(1-(y-base-3)/(double)(h-1))):
						3.9*Math.sqrt(Math.max(0,1-Math.pow((y-base-h+1)/4.0,2)));
					if (spruce && y%2==0) radius*=.78;
					for (int dx=-4; dx<=4; dx++) for (int dz=-4; dz<=4; dz++)
						if (Math.hypot(dx,dz)+(hash(x+dx,z+dz,y)%5)*.1<=radius)
							treeCell(trees,chunk,x+dx,y,z+dz,leaf,false);
				}
			}
		return trees;
	}

	private static void treeCell(Map<BlockPos,BlockState> trees,ChunkPos chunk,int x,int y,int z,BlockState block,boolean trunk) {
		if (inCampus(x,z) || Math.floorDiv(x,16)!=chunk.x() || Math.floorDiv(z,16)!=chunk.z()) return;
		BlockPos pos=new BlockPos(x,y,z);
		if (trunk) trees.put(pos,block); else trees.putIfAbsent(pos,block);
	}

	private static void shape(ServerLevel level, LevelChunk chunk) {
		BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
		Map<BlockPos,BlockState> trees=woodland(chunk.getPos());
		for (int dx = 0; dx < 16; dx++) for (int dz = 0; dz < 16; dz++) {
			int x = chunk.getPos().getMinBlockX() + dx, z = chunk.getPos().getMinBlockZ() + dz;
			boolean campus = inCampus(x, z);
			Column column=column(x,z);
			int top = campus ? 59 : Math.max(column.ground+18, Math.min(110, chunk.getHeight(Heightmap.Types.WORLD_SURFACE, dx, dz)));
			for (int y = SEABED; y <= top; y++) {
				pos.set(x, y, z);
				BlockState before = chunk.getBlockState(pos);
				BlockState desired = campus ? Blocks.STONE.defaultBlockState() : trees.getOrDefault(pos,column.at(y));
				if (before != desired && natural(before)) level.setBlock(pos, desired, FLAGS);
			}
		}
	}

	private static boolean natural(BlockState block) {
		return block.isAir() || block.is(Blocks.STONE) || block.is(Blocks.DIRT) || block.is(Blocks.GRASS_BLOCK)
			|| block.is(Blocks.SAND) || block.is(Blocks.GRAVEL) || block.is(Blocks.WATER) || block.is(Blocks.ANDESITE)
			|| block.getBlock() instanceof VegetationBlock;
	}
}

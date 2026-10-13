package dev.agentcraft.client.agents;

import dev.agentcraft.AgentCraft;
import dev.agentcraft.client.foreman.Foreman;
import dev.agentcraft.client.foreman.ForemanState;
import dev.agentcraft.client.foreman.Protocol;
import dev.agentcraft.client.foreman.Protocol.Agent;
import dev.agentcraft.client.foreman.Protocol.AgentState;
import dev.agentcraft.layout.Anchor;
import dev.agentcraft.layout.AnchorNames;
import dev.agentcraft.layout.Anchors;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * Keeps one {@link ClientAgentEntity} per Foreman agent in the client level, in sync with the
 * state model and the anchor layout (client thread, every client tick):
 * <ul>
 *   <li>new agent: spawned standing at its target anchor (no walk-in from nowhere);</li>
 *   <li>station/active change: walks there along a {@link GridPathfinder} route, waiting on safe
 *       ground and retrying when new construction blocks it;</li>
 *   <li>agent gone after a snapshot: removed;</li>
 *   <li>Foreman link down: agents stay where they are with a dimmed "Foreman offline" plate;</li>
 *   <li>layout republished ({@code /agentcraft hq}): everyone is placed at their new anchors.</li>
 * </ul>
 * Without a layout, agents stand in a row near the world spawn so they are still visible.
 *
 * <p>Phase 3: a station anchor with a seat block ({@link Seats}) is walked to via a free cell next
 * to the seat, then the agent steps in and sits; leaving a seat starts with standing up. An agent
 * that is {@code waiting_user} walks to the user spot by the podium, or, when you are inside the
 * HQ (not spectating), to a free spot about two blocks from you and waits there facing you
 * ({@link #userSpot}). The derived "waiting on you" status ({@link AgentView#awaitingUser}) comes
 * from the open decisions.
 */
public final class AgentManager {
	private static final AgentManager INSTANCE = new AgentManager();
	/** Bound campus routes; longer routes wait instead of phasing through the world. */
	private static final double MAX_WALK = 192;
	/** Ticks it takes to get up from a seat before walking off. */
	private static final int STAND_UP_TICKS = 8;
	/** A waiting agent re-approaches you once you moved this far from where it chose its spot (blocks). */
	private static final double FOLLOW_SLACK = 2.6;
	/** How far from you a waiting agent stands (blocks). */
	static final double USER_DISTANCE = 3.2;

	private final Map<String, ClientAgentEntity> entities = new LinkedHashMap<>();
	private final Map<Integer, ClientAgentEntity> byEntityId = new HashMap<>();
	private final StationAssigner assigner = new StationAssigner();
	private final Seats seats = new Seats();
	private final Map<String, UserSpot> userSpots = new HashMap<>();
	private final Map<String, String> awaiting = new HashMap<>();
	private final Map<String, Integer> awaitingCounts = new HashMap<>();
	private long awaitingRevision = -1;
	private @Nullable ClientLevel level;
	private long layoutRevision = -1;
	private int nextEntityId = -10_000;
	private int pathFailures;
	private long ticks;
	private record Retry(Anchor target, long after) {}
	private final Map<String, Retry> retryAfter = new HashMap<>();
	private final Map<String, Retry> previewTargets = new HashMap<>();

	/** Fixture-only DevBridge caller supplies a bounded preview; no saved assignment/world writes. */
	public void previewWalk(String id, Vec3 from, Vec3 to) {
		ClientAgentEntity e = entities.get(id);
		if (e == null || level == null) throw new IllegalArgumentException("Unknown resident");
		var pf = new GridPathfinder(level, Anchors.current().bounds());
		if (!pf.clear(from, from) || !pf.clear(to, to) || pf.find(from, to) == null)
			throw new IllegalArgumentException("Preview endpoints must be reachable floor positions");
		Anchor start = new Anchor("qa@" + id, from.x, from.y, from.z, e.getYRot(), 0);
		e.life().setSeat(null);
		place(e, start);
		previewTargets.put(id, new Retry(new Anchor(start.name(), to.x, to.y, to.z, e.getYRot(), 0), ticks + 1200));
	}
	public void clearPreviews() { previewTargets.clear(); }

	/** Where a waiting agent stands near the player, and where the player was when it was chosen. */
	private record UserSpot(Anchor spot, Vec3 playerAt) {
	}

	private AgentManager() {
	}

	public static AgentManager get() {
		return INSTANCE;
	}

	/** Live agent entities by agent id (client thread). */
	public Map<String, ClientAgentEntity> entities() {
		return Collections.unmodifiableMap(entities);
	}

	public @Nullable ClientAgentEntity entity(String agentId) {
		return entities.get(agentId);
	}

	public @Nullable ClientAgentEntity byEntityId(int id) {
		return byEntityId.get(id);
	}

	public int pathFailures() {
		return pathFailures;
	}

	/**
	 * A snapshot rebuilds the view: forget sticky slots so the assignment depends only on the state
	 * (Foreman order), not on the history of this session. Agents that change slot walk there.
	 */
	void onSnapshot() {
		if (!Foreman.isT3()) assigner.clear();
		awaitingRevision = -1;
	}

	public int movingCount() {
		int n = 0;
		for (ClientAgentEntity e : entities.values()) {
			if (e.motion().walking()) {
				n++;
			}
		}
		return n;
	}

	void tick(Minecraft mc) {
		ClientLevel lvl = mc.level;
		if (lvl != level) {
			entities.clear(); // the old level and its entities are gone
			byEntityId.clear();
			assigner.clear();
			seats.clear();
			userSpots.clear();
			retryAfter.clear();
			level = lvl;
			previewTargets.clear();
			layoutRevision = -1;
		}
		if (lvl == null) {
			return;
		}
		ticks++;
		ForemanState st = Foreman.state();
		if (st == null || !st.hasData()) {
			removeAll();
			return;
		}
		Anchors.Layout layout = Anchors.current();
		boolean relayout = layout.revision() != layoutRevision;
		layoutRevision = layout.revision();
		if (relayout) {
			seats.clear();
			userSpots.clear();
			retryAfter.clear();
		}
		if (relayout) previewTargets.clear();
		List<Agent> agents = new ArrayList<>(st.agents().values());
		Map<String, Anchor> targets = layout.isEmpty() ? fallbackTargets(agents, lvl) : assigner.assign(agents, layout);
		boolean stale = st.isStale();
		updateAwaiting(st);
		GridPathfinder pf = layout.isEmpty() ? null : new GridPathfinder(lvl, layout.bounds());
		Vec3 playerFeet = layout.isEmpty() ? null : playerInHq(mc, layout, pf);
		int waitingIndex = 0;
		// Two stable request owners approach; the rest remain visible at their own desks.
		Set<String> approaching = new java.util.LinkedHashSet<>();
		int waitingCount = 0;
		if (playerFeet != null) {
			for (Agent a : agents) {
				if (followsPlayer(a) && (!Foreman.isT3() || !dev.agentcraft.client.foreman.T3Projection.agentOffline(a.id())) && approaching.size() < 2) {
					approaching.add(a.id());
					waitingCount++;
				}
			}
		}

		Set<String> keep = new HashSet<>();
		List<Vec3> reserved = new ArrayList<>();
		for (Agent a : agents) {
			Anchor target = targets.get(a.id());
			if (target == null) {
				continue;
			}
			keep.add(a.id());
			ClientAgentEntity e = entities.get(a.id());
			boolean created = e == null || e.isRemoved() || e.level() != lvl;
			if (created) {
				e = spawn(lvl, a, target);
				entities.put(a.id(), e);
				byEntityId.put(e.getId(), e);
				showRecentSay(st, e);
			} else if (!e.getSkin().equals(AgentSkins.get(a.id(), a.skin()))) {
				e.setSkin(AgentSkins.get(a.id(), a.skin()));
			}
			AgentView v = e.view();
			boolean agentStale = stale || Foreman.isT3() && dev.agentcraft.client.foreman.T3Projection.agentOffline(a.id());
			v.update(a, agentStale, awaiting.get(a.id()), awaitingCounts.getOrDefault(a.id(), 0));
			var chat = Foreman.isT3() ? dev.agentcraft.client.foreman.T3Projection.agentThread(a.id()) : null;
			v.threadTitle = chat == null ? "" : chat.title();
			v.reviewReady = chat != null && !agentStale && (chat.status() == dev.agentcraft.client.t3.T3State.Status.DONE || chat.status() == dev.agentcraft.client.t3.T3State.Status.IDLE)
				&& dev.agentcraft.client.t3.T3CraftClient.get().reviewReady(chat.id());
			v.station = StationAssigner.stationKey(a);
			v.anchor = target.name();
			if (playerFeet != null && !agentStale && approaching.contains(a.id())) {
				Anchor near = userSpot(a.id(), e, playerFeet, waitingIndex++, waitingCount, pf);
				if (near != null) {
					target = near;
				}
			} else {
				userSpots.remove(a.id());
				if (Foreman.isT3() && followsPlayer(a)) {
					Anchor desk = StationAssigner.workDesk(layout, a.id());
					if (desk != null) { target = desk; v.station = "desk"; v.anchor = desk.name(); }
				}
			}
			Retry preview = previewTargets.get(a.id());
			if (preview != null) {
				if (ticks < preview.after()) target = preview.target();
				else previewTargets.remove(a.id());
			}
			Seats.Seat seat = pf == null ? null : seats.at(lvl, target, ticks, pf);
			Vec3 chair = seat == null ? null : seat.sit();
			if (seat != null && (reserved.stream().anyMatch(p -> ResidentSpacing.overlaps(p, chair))
				|| taken(e.agentId(), seat.sit()))) seat = null;
			Anchor effective = seat != null ? seat.target() : target;
			if (seat == null && pf != null) {
				Anchor free = freeStanding(target, e, pf, reserved);
				if (free == null) { e.life().setSeat(null); e.motion().stop(); continue; }
				effective = free;
			}
			reserved.add(effective.pos());
			if (relayout || created) {
				e.life().setSeat(seat);
				place(e, effective);
			} else if (!agentStale) {
				retarget(lvl, layout, e, effective, seat);
				avoidCrowd(e, pf);
			}
		}
		for (var it = entities.entrySet().iterator(); it.hasNext();) {
			var en = it.next();
			if (!keep.contains(en.getKey())) {
				remove(lvl, en.getValue());
				byEntityId.remove(en.getValue().getId());
				it.remove();
			}
		}
	}

	boolean crowdClear(ClientAgentEntity e, Vec3 from, Vec3 to) {
		for (ClientAgentEntity other : entities.values()) {
			if (other != e && !other.isRemoved() && !ResidentSpacing.clear(from, to, other.position())) return false;
		}
		return true;
	}

	/** Try a short, floor-checked right/left detour after yielding; never push through bodies/walls. */
	private void avoidCrowd(ClientAgentEntity e, @Nullable GridPathfinder pf) {
		Vec3 next = e.motion().nextPoint();
		if (pf == null || next == null || e.motion().crowdWait() < 12 || e.motion().crowdWait() % 12 != 0) return;
		List<Vec3> detour = ResidentSpacing.detour(e.position(), next, (from, to) -> pf.clear(from, to) && crowdClear(e, from, to));
		if (!detour.isEmpty()) e.motion().detour(detour);
	}

	private static boolean followsPlayer(Agent a) {
		return a.state() == AgentState.WAITING_USER && a.isActive() && !a.isPaused();
	}

	/**
	 * agentId -> the first open decision that agent <b>owns</b>. Every open decision has exactly one
	 * owner, so the HQ shows one "!" per decision waiting on the user:
	 * <ul>
	 *   <li>a merge belongs to the worker whose task it merges (the lead files it, but it is the
	 *       worker's finished work that waits; "t4 awaiting your merge"), or to the agent that filed
	 *       it when the task has no known assignee;</li>
	 *   <li>a question or permission prompt belongs to the agent that asked, whatever task it is
	 *       about (a question about Juniper's task is Marlow's question, not Juniper's).</li>
	 * </ul>
	 * An agent's own questions/permissions come before the merges it owns.
	 */
	private void updateAwaiting(ForemanState st) {
		if (st.revision() == awaitingRevision) {
			return;
		}
		awaitingRevision = st.revision();
		awaiting.clear();
		awaitingCounts.clear();
		List<Protocol.Decision> open = st.openDecisions();
		for (int pass = 0; pass < 2; pass++) {
			for (Protocol.Decision d : open) {
				boolean merge = d.kind() == Protocol.DecisionKind.MERGE;
				if (merge != (pass == 1)) {
					continue;
				}
				String owner = owner(st, d);
				awaiting.putIfAbsent(owner, d.id());
				awaitingCounts.merge(owner, 1, Integer::sum);
			}
		}
	}

	/** The agent an open decision belongs to (see {@link #updateAwaiting}). */
	public static String owner(ForemanState st, Protocol.Decision d) {
		if (d.kind() == Protocol.DecisionKind.MERGE && d.taskId() != null) {
			Protocol.Task t = st.task(d.taskId());
			if (t != null && t.assignee() != null && st.agent(t.assignee()) != null) {
				return t.assignee();
			}
		}
		return d.agentId();
	}

	/** The player's feet on the HQ floor when they are inside the HQ and not spectating, else null. */
	private static @Nullable Vec3 playerInHq(Minecraft mc, Anchors.Layout layout, @Nullable GridPathfinder pf) {
		LocalPlayer p = mc.player;
		Anchors.Bounds b = layout.bounds();
		if (p == null || pf == null || b == null || p.isSpectator()) {
			return null;
		}
		// feet a hair below a block top (64.99999) belong to the block above
		BlockPos bp = BlockPos.containing(p.getX(), p.getY() + 0.05, p.getZ());
		if (!b.contains(bp.getX(), bp.getY(), bp.getZ()) && !b.contains(bp.getX(), bp.getY() - 2, bp.getZ())) {
			return null;
		}
		for (int dy = 0; dy <= 3; dy++) {
			double f = pf.floor(bp.getX(), bp.getY() - dy, bp.getZ());
			if (!Double.isNaN(f)) {
				return new Vec3(p.getX(), f, p.getZ());
			}
		}
		return null;
	}

	/**
	 * A free walkable spot about three blocks from the player, on the agent's side (several waiting
	 * agents fan out), facing the player. Three blocks is a conversation distance: the agent, its
	 * plate and its "!" fit on screen at eye level (at two blocks the plate filled the upper middle
	 * of the view and the "!" was cut off). Sticky until the player moves {@value #FOLLOW_SLACK}
	 * blocks away from where they were when it was chosen.
	 */
	private @Nullable Anchor userSpot(String agentId, ClientAgentEntity e, Vec3 player, int index, int count, GridPathfinder pf) {
		UserSpot prev = userSpots.get(agentId);
		if (prev != null && prev.playerAt().distanceTo(player) < FOLLOW_SLACK && pf.clear(prev.spot().pos(), prev.spot().pos())) {
			return prev.spot();
		}
		double base = Math.atan2(e.getZ() - player.z, e.getX() - player.x);
		if (e.position().distanceToSqr(player) < 0.25) {
			base = 0;
		}
		double spread = Math.toRadians(42);
		double fan = (index - (count - 1) / 2.0) * spread;
		double[] radii = {USER_DISTANCE, USER_DISTANCE + 0.5, USER_DISTANCE - 0.6};
		double[] offs = {0, 0.45, -0.45, 0.9, -0.9, 1.4, -1.4, 2.0, -2.0, Math.PI};
		for (double r : radii) {
			for (double o : offs) {
				double ang = base + fan + o;
				double x = player.x + Math.cos(ang) * r;
				double z = player.z + Math.sin(ang) * r;
				int bx = (int) Math.floor(x);
				int bz = (int) Math.floor(z);
				int by = (int) Math.floor(player.y + 0.01);
				for (int dy : new int[] {0, 1, -1}) {
					double f = pf.floor(bx, by + dy, bz);
					if (Double.isNaN(f)) {
						continue;
					}
					Vec3 at = new Vec3(x, f, z);
					if (!pf.clear(at, at) || taken(agentId, at)) {
						continue;
					}
					float yaw = (float) Math.toDegrees(Math.atan2(-(player.x - x), player.z - z));
					Anchor spot = new Anchor(AnchorNames.USER + "@player", x, f, z, yaw, 0);
					userSpots.put(agentId, new UserSpot(spot, player));
					return spot;
				}
			}
		}
		return null;
	}

	private boolean taken(String agentId, Vec3 at) {
		for (var en : userSpots.entrySet()) {
			if (!en.getKey().equals(agentId) && en.getValue().spot().pos().distanceToSqr(at) < 1.2 * 1.2) {
				return true;
			}
		}
		for (ClientAgentEntity o : entities.values()) {
			if (!o.agentId().equals(agentId)) {
				Anchor destination = o.motion().target();
				if (destination != null && ResidentSpacing.overlaps(destination.pos(), at)) return true;
				if (!o.motion().walking() && ResidentSpacing.overlaps(o.position(), at)) return true;
			}
		}
		return false;
	}

	/** A fresh agent shows what it said in the last few seconds (e.g. after a reconnect). */
	private static void showRecentSay(ForemanState st, ClientAgentEntity e) {
		Protocol.AgentSay say = st.lastSay(e.agentId());
		if (say == null) {
			return;
		}
		long ago = System.currentTimeMillis() - say.ts();
		if (ago >= 0 && ago < 8000) {
			e.life().bubble.showLate(say, e.life().age(), (int) (ago / 50));
		}
	}

	private ClientAgentEntity spawn(ClientLevel lvl, Agent a, Anchor target) {
		ClientAgentEntity e = new ClientAgentEntity(lvl, a.id(), AgentSkins.get(a.id(), a.skin()));
		// Negative ids never collide with server-assigned entity ids.
		e.setId(nextEntityId--);
		Seats.Seat seat = seats.at(lvl, target, ticks, new GridPathfinder(lvl, Anchors.current().bounds()));
		e.life().setSeat(seat);
		place(e, seat != null ? seat.target() : target);
		lvl.addEntity(e);
		AgentCraft.LOGGER.info("Agent {} appeared at {}", a.id(), target.name());
		return e;
	}

	private static void place(ClientAgentEntity e, Anchor target) {
		Vec3 p = e.motion().placeAt(target);
		e.snapTo(p, target.yaw());
	}

	private void retarget(ClientLevel lvl, Anchors.Layout layout, ClientAgentEntity e, Anchor target, Seats.@Nullable Seat seat) {
		Anchor current = e.motion().target();
		Retry retry = retryAfter.get(e.agentId());
		if (retry != null && retry.target().name().equals(target.name()) && retry.target().pos().distanceToSqr(target.pos()) < 1e-4
			&& Math.abs(net.minecraft.util.Mth.wrapDegrees(retry.target().yaw() - target.yaw())) < .1 && ticks < retry.after()) return;
		if (current != null && current.name().equals(target.name()) && current.pos().distanceToSqr(target.pos()) < 1e-4
			&& Math.abs(net.minecraft.util.Mth.wrapDegrees(current.yaw() - target.yaw())) < .1
			&& (e.motion().walking() || e.position().distanceToSqr(target.pos()) < 1e-4)) {
			e.life().setSeat(seat); // refresh removed chairs and changed desk surfaces even without movement
			return;
		}
		GridPathfinder pf = new GridPathfinder(lvl, layout.bounds());
		AgentLife life = e.life();
		List<Vec3> route = new ArrayList<>();
		Vec3 start = e.position();
		int delay = 0;
		Seats.Seat from = Seats.detect(lvl, new Anchor("departing", e.getX(), e.getY(), e.getZ(), e.getYRot(), 0), pf);
		if (from != null && life.sitAmount() > 0f && !e.motion().walking()) {
			// get up first, then step out of the seat to its free side
			delay = STAND_UP_TICKS;
			if (from.approach() != null) {
				route.add(start);
				start = from.approach();
			}
		}
		Vec3 dest = seat != null && seat.approach() != null ? seat.approach() : target.pos();
		List<Vec3> path = start.distanceToSqr(dest) < 1e-6 ? List.of(start, dest) : pf.find(start, dest);
		if (path == null || length(path) > MAX_WALK) {
			pathFailures++;
			retryAfter.put(e.agentId(), new Retry(target, ticks + 40));
			AgentCraft.LOGGER.debug("Agent {}: route to {} unavailable ({}); holding on safe ground", e.agentId(), target.name(),
				path == null ? "no path" : "too far");
			// Stay on safe ground if a player sealed the destination; do not phase through walls.
			life.setSeat(null);
			if (pf.cellAt(e.position()) == null) {
				Anchor safe = freeStanding(new Anchor(target.name(), e.getX(), e.getY(), e.getZ(), target.yaw(), 0), e, pf);
				if (safe != null) place(e, safe);
			}
			e.motion().stop();
			return;
		}
		route.addAll(path);
		if (seat != null && seat.approach() != null) {
			route.add(target.pos()); // the last step: onto the seat
		}
		life.setSeat(seat);
		retryAfter.remove(e.agentId());
		e.motion().walkTo(target, route, delay);
	}

	/** Immediate next-step guard, independent of the less frequent station/seat refresh. */
	boolean canAdvance(ClientAgentEntity e) {
		Vec3 next = e.motion().nextPoint();
		if (next == null || level == null) return true;
		Seats.Seat seat = e.life().seat();
		if (seat != null && e.position().distanceToSqr(seat.sit()) < 2.25 && next.distanceToSqr(seat.sit()) < .01
			&& Seats.detect(level, seat.anchor(), null) != null) return true;
		var pf = new GridPathfinder(level, Anchors.current().bounds());
		Seats.Seat from = Seats.detect(level, new Anchor("departing", e.getX(), e.getY(), e.getZ(), e.getYRot(), 0), pf);
		if (from != null && next.distanceToSqr(e.position()) < 2.25 && pf.clear(next, next)) return true;
		Vec3 delta = next.subtract(e.position());
		Vec3 to = e.position().add(delta.scale(Math.min(1, .4 / Math.max(.001, delta.length()))));
		// Vertical path segments use the already-checked stair cells; still check landing headroom.
		if (Math.abs(delta.y) > .2) return pf.cellAt(to) != null;
		return pf.clear(e.position(), to);
	}

	private @Nullable Anchor freeStanding(Anchor target, ClientAgentEntity e, GridPathfinder pf) {
		return freeStanding(target, e, pf, List.of());
	}

	private @Nullable Anchor freeStanding(Anchor target, ClientAgentEntity e, GridPathfinder pf, List<Vec3> reserved) {
		if (pf.clear(target.pos(), target.pos()) && !taken(e.agentId(), target.pos())
			&& reserved.stream().noneMatch(p -> ResidentSpacing.overlaps(p, target.pos()))) return target;
		for (int radius = 1; radius <= 3; radius++) for (int dx = -radius; dx <= radius; dx++) for (int dz = -radius; dz <= radius; dz++) {
			if (Math.max(Math.abs(dx), Math.abs(dz)) != radius) continue;
			int x = (int) Math.floor(target.x()) + dx, z = (int) Math.floor(target.z()) + dz;
			double floor = pf.floor(x, (int) Math.floor(target.y()), z);
			if (Double.isNaN(floor)) continue;
			Vec3 at = new Vec3(x + .5, floor, z + .5);
			if (pf.clear(at, at) && !taken(e.agentId(), at) && reserved.stream().noneMatch(p -> ResidentSpacing.overlaps(p, at))) return new Anchor(target.name(), at.x, at.y, at.z, target.yaw(), target.pitch());
		}
		return null;
	}

	private static double length(List<Vec3> route) {
		double d = 0;
		for (int i = 1; i < route.size(); i++) {
			d += route.get(i).distanceTo(route.get(i - 1));
		}
		return d;
	}

	/** Snap every agent to its target now (QA: no one mid-walk in a screenshot). */
	public int settle() {
		int n = 0;
		for (ClientAgentEntity e : entities.values()) {
			Anchor t = e.motion().target();
			if (t != null && e.motion().walking()) {
				place(e, t);
				n++;
			}
		}
		return n;
	}

	private void removeAll() {
		if (level != null) {
			for (ClientAgentEntity e : entities.values()) {
				remove(level, e);
			}
		}
		entities.clear();
		byEntityId.clear();
	}

	private static void remove(ClientLevel lvl, ClientAgentEntity e) {
		lvl.removeEntity(e.getId(), Entity.RemovalReason.DISCARDED);
	}

	/** No layout yet: a row in front of the world spawn, facing it. */
	private static Map<String, Anchor> fallbackTargets(List<Agent> agents, ClientLevel lvl) {
		Map<String, Anchor> out = new LinkedHashMap<>();
		BlockPos spawn = lvl.getRespawnData().pos();
		int n = agents.size();
		for (int i = 0; i < n; i++) {
			double x = spawn.getX() + 0.5 + (i - (n - 1) / 2.0) * 1.4;
			out.put(agents.get(i).id(), new Anchor(AnchorNames.LOUNGE + "@spawn" + i, x, spawn.getY(), spawn.getZ() + 4.5, 180, 0));
		}
		return out;
	}
}

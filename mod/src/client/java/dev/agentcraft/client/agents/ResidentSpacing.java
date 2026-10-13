package dev.agentcraft.client.agents;

import net.minecraft.world.phys.Vec3;
import java.util.List;
import java.util.function.BiPredicate;

/** Body clearance for client-only residents, including the swept segment between ticks. */
public final class ResidentSpacing {
	public static final double SPACE = .78;
	private ResidentSpacing() {}

	public static boolean overlaps(Vec3 a, Vec3 b) {
		return Math.abs(a.y - b.y) < 1.65 && horizontalSquared(a, b) < SPACE * SPACE;
	}

	public static boolean clear(Vec3 from, Vec3 to, Vec3 other) {
		if (Math.min(from.y, to.y) >= other.y + 1.65 || Math.max(from.y, to.y) <= other.y - 1.65) return true;
		double dx = to.x - from.x, dz = to.z - from.z;
		double ox = from.x - other.x, oz = from.z - other.z;
		double before = ox * ox + oz * oz, dot = ox * dx + oz * dz;
		// A loaded/changed layout may already overlap: permit only outward recovery.
		if (before < SPACE * SPACE) return dot >= -1e-9 && horizontalSquared(to, other) > before + 1e-9;
		double length = dx * dx + dz * dz;
		double t = length < 1e-12 ? 0 : Math.max(0, Math.min(1, -dot / length));
		double x = ox + dx * t, z = oz + dz * t;
		return x * x + z * z >= SPACE * SPACE - 1e-9;
	}

	/** Prefer the resident's right side so oncoming walkers choose opposite sides. */
	public static Vec3 sidestep(Vec3 from, Vec3 next, double side) {
		double dx = next.x - from.x, dz = next.z - from.z;
		double length = Math.hypot(dx, dz);
		if (length < .001) return from.add(side, 0, 0);
		return from.add(-dz / length * side, 0, dx / length * side);
	}

	private static double horizontalSquared(Vec3 a, Vec3 b) {
		double dx = a.x - b.x, dz = a.z - b.z;
		return dx * dx + dz * dz;
	}

	/** A boxed-in crossing needs a retreat before turning around the outside of the group. */
	public static List<Vec3> retreat(Vec3 from, Vec3 next, double side) {
		Vec3 direction = next.subtract(from).multiply(1, 0, 1).normalize();
		Vec3 back = from.subtract(direction.scale(1.2));
		Vec3 beside = sidestep(from, next, side);
		return List.of(back, sidestep(back, back.add(direction), side), beside, beside.add(direction.scale(2)));
	}

	public static List<Vec3> detour(Vec3 from, Vec3 next, BiPredicate<Vec3, Vec3> clear) {
		for (double side : new double[] {1.05, -1.05, .7, -.7}) {
			Vec3 at = sidestep(from, next, side);
			if (clear.test(from, at)) return List.of(at);
		}
		for (double side : new double[] {1.8, -1.8}) {
			List<Vec3> route = retreat(from, next, side);
			Vec3 p = from;
			boolean available = true;
			for (Vec3 at : route) {
				if (!clear.test(p, at)) { available = false; break; }
				p = at;
			}
			if (available) return route;
		}
		return List.of();
	}
}

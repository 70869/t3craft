package dev.agentcraft.client.agents;

import dev.agentcraft.layout.Anchor;
import java.util.List;
import net.minecraft.world.phys.Vec3;

/** Two residents using the same aisle must keep body space and both arrive. */
public final class ResidentMovementChecks {
	private static void check(boolean value, String message) { if (!value) throw new AssertionError(message); }

	public static void main(String[] args) {
		AgentMotion a = new AgentMotion(), b = new AgentMotion();
		Vec3 pa = new Vec3(-3, 66, 0), pb = new Vec3(3, 66, 0);
		a.placeAt(new Anchor("a", pa.x, pa.y, pa.z, -90, 0));
		b.placeAt(new Anchor("b", pb.x, pb.y, pb.z, 90, 0));
		a.walkTo(new Anchor("a", 3, 66, 0, -90, 0), List.of(pa, pb));
		b.walkTo(new Anchor("b", -3, 66, 0, 90, 0), List.of(pb, pa));
		for (int tick = 0; tick < 300; tick++) {
			if (a.crowdWait() >= 12 && a.nextPoint() != null) a.sidestep(ResidentSpacing.sidestep(pa, a.nextPoint(), 1.05));
			if (b.crowdWait() >= 12 && b.nextPoint() != null) b.sidestep(ResidentSpacing.sidestep(pb, b.nextPoint(), 1.05));
			Vec3 fromA = pa, otherB = pb;
			pa = a.step(pa, point -> ResidentSpacing.clear(fromA, point, otherB));
			Vec3 fromB = pb, otherA = pa;
			pb = b.step(pb, point -> ResidentSpacing.clear(fromB, point, otherA));
			check(pa.distanceTo(pb) >= .7, "Crossing residents overlap at tick " + tick);
		}
		check(!a.walking() && !b.walking(), "Both residents finish crossing");
		check(ResidentSpacing.clear(Vec3.ZERO, new Vec3(.1, 0, 0), Vec3.ZERO), "Coincident loaded residents can separate");
		check(!ResidentSpacing.clear(new Vec3(.2, 0, 0), new Vec3(.1, 0, 0), Vec3.ZERO), "Existing overlap cannot deepen");
		check(!ResidentSpacing.clear(new Vec3(-1, 0, 0), new Vec3(1, 0, 0), Vec3.ZERO), "Swept movement cannot pass through a body");
		check(ResidentSpacing.clear(Vec3.ZERO, new Vec3(1, 0, 0), new Vec3(.5, 4, 0)), "Separate floors do not block movement");
		check(ResidentSpacing.detour(Vec3.ZERO, new Vec3(3, 0, 0), (from, to) -> false).isEmpty(), "A sealed aisle holds instead of walking through a wall");
		crossingFour();
		System.out.println("PASS resident crossing space and arrival");
	}

	private static void crossingFour() {
		Vec3[] positions = {new Vec3(-3,66,0),new Vec3(3,66,0),new Vec3(0,66,-3),new Vec3(0,66,3)};
		AgentMotion[] motion = new AgentMotion[4];
		for (int i=0;i<4;i++) {
			motion[i] = new AgentMotion();
			Vec3 from=positions[i],to=positions[i^1];
			motion[i].placeAt(new Anchor("resident"+i,from.x,from.y,from.z,0,0));
			motion[i].walkTo(new Anchor("resident"+i,to.x,to.y,to.z,0,0),List.of(from,to));
		}
		for (int tick=0;tick<600;tick++) for (int i=0;i<4;i++) {
			final int id=i; Vec3 from=positions[i];
			java.util.function.BiPredicate<Vec3,Vec3> clear=(start,end)-> {
				for(int other=0;other<4;other++) if(other!=id && !ResidentSpacing.clear(start,end,positions[other])) return false;
				return true;
			};
			if(motion[i].crowdWait()>=12 && motion[i].crowdWait()%12==0 && motion[i].nextPoint()!=null) {
				var route=ResidentSpacing.detour(from,motion[i].nextPoint(),clear);
				if(!route.isEmpty()) motion[i].detour(route);
			}
			positions[i]=motion[i].step(from,to->clear.test(from,to));
			for(int other=0;other<4;other++) if(other!=i) check(!ResidentSpacing.overlaps(positions[i],positions[other]),"Four-way crossing overlaps");
		}
		for(var walker:motion) check(!walker.walking(),"Four-way crossing cannot deadlock");
	}
}

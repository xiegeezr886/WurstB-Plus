import net.minecraft.world.phys.Vec2;
import net.wurstclient.util.MovementPlanner;

/** Functional check for the MovementPlanner.getMoveVector fix. */
public class MovementPlannerTest
{
	static final class VanillaInput
	{
		public Vec2 getMoveVector()
		{
			return new Vec2(0.25F, -1.0F);
		}
	}

	record Keys(boolean forward, boolean backward, boolean left,
		boolean right)
	{}

	static final class RecordInput
	{
		public Keys keyPresses = new Keys(true, false, false, true);
	}

	static final class LegacyInput
	{
		public float forwardImpulse = 0.5F;
		public float leftImpulse = -0.5F;
	}

	public static void main(String[] args)
	{
		check("vanilla(1.21.6+)", MovementPlanner.getMoveVector(
			new VanillaInput()), 0.25F, -1.0F);
		check("record(keyPresses)", MovementPlanner.getMoveVector(
			new RecordInput()), -1.0F, 1.0F);
		check("legacy(1.20.x)", MovementPlanner.getMoveVector(
			new LegacyInput()), -0.5F, 0.5F);
		check("null", MovementPlanner.getMoveVector(null), 0F, 0F);
		System.out.println("ALL OK");
	}

	private static void check(String name, Vec2 vector, float expectedX,
		float expectedY)
	{
		if(Math.abs(vector.x - expectedX) > 1.0E-4F
			|| Math.abs(vector.y - expectedY) > 1.0E-4F)
			throw new AssertionError(name + ": got (" + vector.x + ","
				+ vector.y + ") expected (" + expectedX + "," + expectedY
				+ ")");

		System.out.println(name + ": (" + vector.x + "," + vector.y + ")");
	}
}

/* FO RepairLogic adapted from FO (CC0-1.0). */
package net.wurstclient.util;

public final class AutoLeaveTriggers
{
	private AutoLeaveTriggers() {}

	public static boolean damagedArmor(int maximum, int damage,
		boolean damageable, double threshold)
	{
		return damageable && maximum > 0
			&& Math.max(0, 100 - damage * 100.0 / maximum) < threshold;
	}

	public static boolean nearPosition(double x, double z, int targetX,
		int targetZ, double range)
	{
		double dx = x - (targetX + 0.5);
		double dz = z - (targetZ + 0.5);
		return dx * dx + dz * dz < range * range;
	}
}

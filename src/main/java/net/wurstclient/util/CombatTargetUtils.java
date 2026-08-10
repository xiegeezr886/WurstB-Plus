package net.wurstclient.util;

import java.util.Comparator;
import java.util.List;
import java.util.function.Function;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ambient.AmbientCreature;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.animal.WaterAnimal;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.npc.AbstractVillager;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.wurstclient.WurstClient;
import net.wurstclient.clickgui2.GuiPreferences;
import net.wurstclient.clickgui2.GuiPreferences.TargetType;
import net.wurstclient.settings.filterlists.EntityFilterList;

public enum CombatTargetUtils
{
	;

	private static final Minecraft MC = WurstClient.MC;

	public static Entity get(double range, double fov,
		Function<Entity, Vec3> aimPoint, EntityFilterList filters,
		boolean checkLOS, Priority priority)
	{
		List<Entity> targets = getList(range, fov, aimPoint, filters, checkLOS,
			priority, 1);
		return targets.isEmpty() ? null : targets.get(0);
	}

	public static List<Entity> getList(double range, double fov,
		Function<Entity, Vec3> aimPoint, EntityFilterList filters,
		boolean checkLOS, Priority priority, int maxCount)
	{
		if(maxCount < 1 || !isValidRange(range) || !isValidFov(fov)
			|| aimPoint == null || filters == null || priority == null
			|| MC.player == null || MC.level == null)
			return List.of();

		return EntityUtils.getAttackableEntities().sequential()
			.filter(entity -> isValid(entity, range, fov, aimPoint, filters,
				checkLOS))
			.sorted(priority.getComparator(aimPoint)).limit(maxCount).toList();
	}

	public static boolean isValid(Entity entity, double range, double fov,
		Function<Entity, Vec3> aimPoint, EntityFilterList filters,
		boolean checkLOS)
	{
		if(MC.player == null || MC.level == null
			|| entity == null || aimPoint == null || filters == null
			|| !isValidRange(range) || !isValidFov(fov)
			|| !EntityUtils.IS_ATTACKABLE.test(entity)
			|| distanceToBoxSqr(entity) > range * range
			|| !filters.testOne(entity) || !isGlobalTargetEnabled(entity))
			return false;

		Vec3 hitVec = aimPoint.apply(entity);
		if(!isFinite(hitVec))
			return false;
		if(fov < 360
			&& RotationUtils.getAngleToLookVec(hitVec) > fov / 2)
			return false;

		return !checkLOS || BlockUtils.hasLineOfSight(hitVec);
	}

	public static double getScore(Entity entity, Priority priority,
		Function<Entity, Vec3> aimPoint)
	{
		if(entity == null || priority == null)
			return Double.MAX_VALUE;
		double score = switch(priority)
		{
			case DISTANCE -> Math.sqrt(distanceToBoxSqr(entity));
			case ANGLE -> aimPoint == null ? Double.MAX_VALUE
				: angleScore(aimPoint.apply(entity));
			case HEALTH -> entity instanceof LivingEntity living
				? living.getHealth() + living.getAbsorptionAmount()
				: Double.MAX_VALUE;
			case HURT_TIME -> entity instanceof LivingEntity living
				? Math.max(0, living.hurtTime) : Integer.MAX_VALUE;
		};
		return Double.isFinite(score) ? score : Double.MAX_VALUE;
	}

	public static double distanceToBoxSqr(Entity entity)
	{
		if(MC.player == null || entity == null)
			return Double.MAX_VALUE;

		Vec3 eyes = MC.player.getEyePosition();
		AABB box = entity.getBoundingBox();
		if(!isFinite(eyes) || !isFinite(box))
			return Double.MAX_VALUE;
		double x = Math.max(box.minX, Math.min(eyes.x, box.maxX));
		double y = Math.max(box.minY, Math.min(eyes.y, box.maxY));
		double z = Math.max(box.minZ, Math.min(eyes.z, box.maxZ));
		double distanceSq = eyes.distanceToSqr(x, y, z);
		return Double.isFinite(distanceSq) ? distanceSq : Double.MAX_VALUE;
	}

	private static boolean isValidRange(double range)
	{
		return Double.isFinite(range) && range >= 0
			&& range <= Math.sqrt(Double.MAX_VALUE);
	}

	private static boolean isValidFov(double fov)
	{
		return Double.isFinite(fov) && fov >= 0;
	}

	private static double angleScore(Vec3 point)
	{
		return isFinite(point) ? RotationUtils.getAngleToLookVec(point)
			: Double.MAX_VALUE;
	}

	private static boolean isFinite(Vec3 point)
	{
		return point != null && Double.isFinite(point.x)
			&& Double.isFinite(point.y) && Double.isFinite(point.z);
	}

	private static boolean isFinite(AABB box)
	{
		return box != null && Double.isFinite(box.minX)
			&& Double.isFinite(box.minY) && Double.isFinite(box.minZ)
			&& Double.isFinite(box.maxX) && Double.isFinite(box.maxY)
			&& Double.isFinite(box.maxZ);
	}

	private static boolean isGlobalTargetEnabled(Entity entity)
	{
		GuiPreferences preferences = WurstClient.INSTANCE.getGuiPreferences();
		if(entity instanceof Player)
			return preferences.isTargetEnabled(TargetType.PLAYERS)
				&& (preferences.isTargetEnabled(TargetType.TEAMS)
					|| !MC.player.isAlliedTo(entity));
		if(entity instanceof AbstractVillager)
			return preferences.isTargetEnabled(TargetType.VILLAGERS);
		if(entity instanceof Enemy)
			return preferences.isTargetEnabled(TargetType.MONSTERS);
		if(entity instanceof Animal || entity instanceof AmbientCreature
			|| entity instanceof WaterAnimal)
			return preferences.isTargetEnabled(TargetType.ANIMALS);
		return true;
	}

	public enum Priority
	{
		DISTANCE("Distance"),
		ANGLE("Angle"),
		HEALTH("Health"),
		HURT_TIME("Hurt time");

		private final String name;

		private Priority(String name)
		{
			this.name = name;
		}

		public Comparator<Entity> getComparator(
			Function<Entity, Vec3> aimPoint)
		{
			if(MC.player == null)
				return Comparator.comparingInt(Entity::getId);

			Comparator<Entity> distance = Comparator.comparingDouble(
				CombatTargetUtils::distanceToBoxSqr);
			Comparator<Entity> comparator = Comparator.comparingDouble(
				entity -> getScore(entity, this, aimPoint));
			if(this != DISTANCE)
				comparator = comparator.thenComparing(distance);

			return comparator.thenComparingInt(Entity::getId);
		}

		@Override
		public String toString()
		{
			return name;
		}
	}
}

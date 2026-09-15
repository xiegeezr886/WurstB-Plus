package net.wurstclient.util;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.function.Function;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ambient.AmbientCreature;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.animal.WaterAnimal;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.npc.AbstractVillager;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.state.BlockState;
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
		
		/*
		 * SCORE 走独立路径：它的键（护甲结算、邻向遮挡）比距离贵得多，必须每个候选
		 * 只算一次，不能交给比较器反复算。
		 */
		if(priority == Priority.SCORE)
			return getListByScore(range, fov, aimPoint, filters, checkLOS,
				maxCount, TargetScore.Weights.DEFAULT);
		
		return EntityUtils.getAttackableEntities().sequential()
			.filter(entity -> isValid(entity, range, fov, aimPoint, filters,
				checkLOS))
			.sorted(priority.getComparator(aimPoint)).limit(maxCount).toList();
	}
	
	/**
	 * 用参考项目 CakeSlayers/OpenEpsilon 的加权打分挑目标（**可选路径**）。
	 *
	 * <p>
	 * 与 {@link #getList} 的区别在"什么时候算键"：那个按单一键排序，键是在比较器里
	 * 现算的；这个先给每个候选**只算一次** {@link TargetScore.Inputs}，再用
	 * {@link TargetScore#rank} 排出下标。因为护甲结算与邻向遮挡查询比距离贵得多，
	 * 放进比较器会被放大 O(n log n) 倍。
	 *
	 * <p>
	 * 这是新增入口，**没有改变任何现有 hack 的默认选人行为**；要用打分排序的模块
	 * 自行改调这个方法。
	 */
	public static List<Entity> getListByScore(double range, double fov,
		Function<Entity, Vec3> aimPoint, EntityFilterList filters,
		boolean checkLOS, int maxCount, TargetScore.Weights weights)
	{
		if(maxCount < 1)
			return List.of();
		
		List<Entity> candidates = getList(range, fov, aimPoint, filters,
			checkLOS, Priority.DISTANCE, Integer.MAX_VALUE);
		
		if(candidates.isEmpty())
			return List.of();
		
		List<TargetScore.Inputs> inputs = new ArrayList<>(candidates.size());
		for(Entity entity : candidates)
			inputs.add(scoreInputs(entity, aimPoint));
		
		int[] order = TargetScore.rank(inputs, weights);
		int limit = Math.min(maxCount, order.length);
		List<Entity> result = new ArrayList<>(limit);
		for(int i = 0; i < limit; i++)
			result.add(candidates.get(order[i]));
		return List.copyOf(result);
	}
	
	private static TargetScore.Inputs scoreInputs(Entity entity,
		Function<Entity, Vec3> aimPoint)
	{
		double distance = Math.sqrt(distanceToBoxSqr(entity));
		// 非生物按"满血"处理，血量因子自然归零
		float health = TargetScore.HEALTH_SATURATION;
		float armorDamage = 0F;
		
		if(entity instanceof LivingEntity living)
		{
			health = living.getHealth() + living.getAbsorptionAmount();
			armorDamage = DamageUtils.nominalExplosionDamage(living,
				TargetScore.ARMOR_REFERENCE_DAMAGE);
		}
		
		float yaw = 0F;
		if(aimPoint != null)
		{
			Vec3 point = aimPoint.apply(entity);
			if(isFinite(point))
				yaw = (float)RotationUtils.getAngleToLookVec(point);
		}
		
		return new TargetScore.Inputs((float)distance, health, armorDamage,
			countExposedSides(entity), yaw);
	}
	
	/**
	 * 四个水平邻向里"没有遮挡"的个数。
	 *
	 * <p>
	 * 参考判的是这些邻向**抗不抗爆**；这里用"空气或碰撞箱为空"代替，因为 1.20.1
	 * 取爆炸抗性要构造 {@code Explosion} 上下文，代价与不确定性都更高。这是**刻意
	 * 的近似**，记在 {@code docs/openeepsilon-refactor.md}。
	 */
	private static int countExposedSides(Entity entity)
	{
		if(MC.level == null || entity == null)
			return 0;
		
		BlockPos base = entity.blockPosition();
		int exposed = 0;
		
		for(Direction direction : Direction.Plane.HORIZONTAL)
		{
			BlockPos pos = base.relative(direction);
			BlockState state = MC.level.getBlockState(pos);
			
			if(state.isAir()
				|| state.getCollisionShape(MC.level, pos).isEmpty())
				exposed++;
		}
		
		return exposed;
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
			/*
			 * SCORE 正常情况下不会走到这里（getList 已经把它分流到打分路径）。
			 * 真被直接调用时退化为距离，避免有人在比较器里拿到昂贵的综合打分。
			 */
			case SCORE -> Math.sqrt(distanceToBoxSqr(entity));
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
		HURT_TIME("Hurt time"),
		/**
		 * 参考项目 CakeSlayers/OpenEpsilon 的加权综合打分。**默认不选它**，
		 * 所以升级不会改变任何人现有的选人行为。
		 *
		 * <p>
		 * 它的排序由 {@link CombatTargetUtils#getList} 分流到
		 * {@link CombatTargetUtils#getListByScore}，**不经过**
		 * {@link #getComparator}——因为打分的键比距离贵得多。
		 */
		SCORE("Score");

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

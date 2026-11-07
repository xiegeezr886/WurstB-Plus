/*
 * Copyright (c) 2026 WurstB+ Plus contributors
 *
 * This source code is subject to the terms of the GNU Affero General Public
 * License, version 3. If a copy of the AGPL was not distributed with this
 * file, You can obtain one at: https://www.gnu.org/licenses/agpl-3.0.txt
 */
package net.wurstclient.hacks.aura;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.function.IntFunction;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;

import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.util.Mth;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.wurstclient.WurstClient;
import net.wurstclient.WurstRenderLayers;
import net.wurstclient.util.FakePlayerEntity;
import net.wurstclient.util.RenderUtils;
import net.wurstclient.util.Rotation;
import net.wurstclient.util.RotationUtils;

/**
 * Targeting and rotation math ported from OpenOpal's KillAura
 * ({@code KillAuraTargeting}, {@code RotationUtility}).
 */
public final class OpenAura
{
	public static final int TARGET_PLAYERS = 1;
	public static final int TARGET_HOSTILE = 1 << 1;
	public static final int TARGET_PASSIVE = 1 << 2;
	public static final int TARGET_FRIENDLY = 1 << 3;

	private static final long ATTACK_WINDOW_MS = 470;

	private OpenAura() {}

	public record Config(double range, double fov, int targetFlags,
		boolean throughWalls)
	{}

	public static final class Target
	{
		public final LivingEntity entity;
		public final Rotation rotation;
		public final Vec3 hitVec;
		public final double distance;
		private long lastAttackTime;
		private double lastAttackDamage;

		private Target(LivingEntity entity, Rotation rotation, Vec3 hitVec,
			double distance)
		{
			this.entity = entity;
			this.rotation = rotation;
			this.hitVec = hitVec;
			this.distance = distance;
		}

		public boolean isAttackAvailable()
		{
			if(lastAttackTime == 0)
				return true;

			double damage = attackDamage();
			if(entity.getHealth() <= damage)
				return true;

			return System.currentTimeMillis()
				- lastAttackTime >= ATTACK_WINDOW_MS
				|| damage > lastAttackDamage;
		}

		public void onAttack()
		{
			lastAttackTime = System.currentTimeMillis();
			lastAttackDamage = attackDamage();
		}

		/**
		 * Restores the per-entity attack history saved by a previous tick.
		 * Targets are recreated by {@link #collect(Config)} every tick, so
		 * without this the attack window and the SWITCH ordering would never
		 * see an earlier attack.
		 */
		public void restoreAttackState(AttackRecord record)
		{
			if(record == null)
				return;

			lastAttackTime = record.time();
			lastAttackDamage = record.damage();
		}

		public AttackRecord attackRecord()
		{
			return new AttackRecord(lastAttackTime, lastAttackDamage);
		}

		public long getLastAttackTime()
		{
			return lastAttackTime;
		}
	}

	/** Cross-tick attack history for one entity. */
	public record AttackRecord(long time, double damage)
	{}

	public static List<Target> collect(Config config)
	{
		if(WurstClient.MC.player == null || WurstClient.MC.level == null)
			return List.of();

		Player player = WurstClient.MC.player;
		Vec3 eyes = player.getEyePosition();
		AABB area = player.getBoundingBox().inflate(config.range());
		List<LivingEntity> entities = WurstClient.MC.level
			.getEntitiesOfClass(LivingEntity.class, area,
				e -> isValidTarget(e, config.targetFlags()));

		List<Target> targets = new ArrayList<>();
		for(LivingEntity entity : entities)
		{
			if(!isInFov(entity, eyes, config.fov()))
				continue;

			AABB box = entity.getBoundingBox()
				.inflate(entity.getPickRadius());
			Vec3 closest = closestPoint(eyes, box);
			double distance = eyes.distanceTo(closest);
			if(distance > config.range())
				continue;

			Target target = raycastTarget(entity, eyes, closest,
				config.range(), config.throughWalls());
			if(target != null)
				targets.add(target);
		}
		return targets;
	}

	/**
	 * OpenOpal's selection order: players first, then lowest health; switch
	 * mode additionally prefers targets that have not been hit recently.
	 */
	public static Target select(List<Target> targets, boolean switchMode)
	{
		return select(targets, switchMode, id -> null);
	}

	/**
	 * Same as {@link #select(List, boolean)} but restores each target's attack
	 * history before sorting, so SWITCH can actually prefer targets that have
	 * not been hit recently.
	 */
	public static Target select(List<Target> targets, boolean switchMode,
		IntFunction<AttackRecord> history)
	{
		if(targets.isEmpty())
			return null;

		if(history != null)
			for(Target target : targets)
				target.restoreAttackState(history.apply(target.entity.getId()));

		targets.sort(Comparator.comparingDouble(t ->
		{
			double score = t.entity.getHealth();
			return t.entity instanceof Player ? score : score * 2.0D;
		}));

		if(switchMode && targets.size() > 1)
			targets.sort(Comparator.comparingDouble(t ->
				-((t.entity.getHealth() * 25.0D) + t.getLastAttackTime())));

		return targets.get(0);
	}

	public static Target selectNearest(List<Target> targets)
	{
		if(targets.isEmpty())
			return null;
		return targets.stream()
			.min(Comparator.comparingDouble(t -> t.distance)).orElse(null);
	}

	public static Target raycastTarget(LivingEntity entity, Vec3 eyes,
		Vec3 closest, double range, boolean throughWalls)
	{
		Rotation base = rotationTo(eyes, closest);
		EntityHitResult hit = raycastEntity(entity, eyes, base, range);
		if(hit != null && (throughWalls || hasLineOfSight(eyes,
			hit.getLocation())))
			return new Target(entity, base, hit.getLocation(),
				eyes.distanceTo(closest));

		AABB box = entity.getBoundingBox().inflate(entity.getPickRadius());
		Vec3 center = box.getCenter();
		double sizeX = box.getXsize();
		double sizeY = box.getYsize();
		double sizeZ = box.getZsize();

		Target best = null;
		double bestAngle = Double.MAX_VALUE;
		for(int ix = -1; ix <= 1; ix++)
			for(int iy = -1; iy <= 1; iy++)
				for(int iz = -1; iz <= 1; iz++)
				{
					if(ix == 0 && iy == 0 && iz == 0)
						continue;

					Vec3 point = center.add(sizeX * ix * 0.35D,
						sizeY * iy * 0.35D, sizeZ * iz * 0.35D);
					Rotation rotation = rotationTo(eyes, point);
					EntityHitResult sample = raycastEntity(entity, eyes,
						rotation, range);
					if(sample == null)
						continue;
					if(!throughWalls && !hasLineOfSight(eyes,
						sample.getLocation()))
						continue;

					double angle = rotation.getAngleTo(base);
					if(angle < bestAngle)
					{
						bestAngle = angle;
						best = new Target(entity, rotation,
							sample.getLocation(), eyes.distanceTo(point));
					}
				}
		return best;
	}

	public static EntityHitResult raycastEntity(LivingEntity entity,
		Vec3 eyes, Rotation rotation, double range)
	{
		Vec3 end = eyes.add(rotation.toLookVec().scale(range));
		AABB search = WurstClient.MC.player.getBoundingBox()
			.expandTowards(end.subtract(eyes)).inflate(1);
		EntityHitResult hit = ProjectileUtil.getEntityHitResult(
			WurstClient.MC.player, eyes, end, search, e -> e == entity,
			range * range);
		return hit != null && hit.getEntity() == entity ? hit : null;
	}

	public static boolean hasLineOfSight(Vec3 start, Vec3 end)
	{
		BlockHitResult hit = WurstClient.MC.level.clip(
			new ClipContext(start, end, ClipContext.Block.COLLIDER,
				ClipContext.Fluid.NONE, WurstClient.MC.player));
		return hit.getType() == HitResult.Type.MISS;
	}

	public static Rotation rotationTo(Vec3 from, Vec3 to)
	{
		double x = to.x - from.x;
		double y = to.y - from.y;
		double z = to.z - from.z;
		double horizontal = Math.sqrt(x * x + z * z);
		float yaw = (float)Math.toDegrees(-Math.atan2(x, z));
		float pitch = (float)-Math.toDegrees(Math.atan2(y, horizontal));
		return Rotation.wrapped(yaw, pitch);
	}

	public static Vec3 closestPoint(Vec3 point, AABB box)
	{
		return new Vec3(Mth.clamp(point.x, box.minX, box.maxX),
			Mth.clamp(point.y, box.minY, box.maxY),
			Mth.clamp(point.z, box.minZ, box.maxZ));
	}

	public static double attackDamage()
	{
		Player player = WurstClient.MC.player;
		if(player == null)
			return 0.5D;

		double damage = player.getAttributeValue(Attributes.ATTACK_DAMAGE);
		if(damage < 0.5D)
			damage = 0.5D;
		if(canCritical() && player.fallDistance > 0.0F)
			damage *= 1.5D;
		return damage;
	}

	private static boolean canCritical()
	{
		Player player = WurstClient.MC.player;
		return player != null && !player.isInWater() && !player.onClimbable()
			&& !player.hasEffect(MobEffects.BLINDNESS) && !player.isPassenger();
	}

	private static boolean isValidTarget(LivingEntity entity, int flags)
	{
		Player player = WurstClient.MC.player;
		if(entity == player || !entity.isAlive() || entity.isSpectator()
			|| !entity.isPickable())
			return false;

		// Match EntityUtils.IS_ATTACKABLE: never attack Wurst's client-side
		// fake players (the server does not know their entity id) or players
		// that AntiBot has already identified as bots.
		if(entity instanceof FakePlayerEntity)
			return false;

		if(WurstClient.INSTANCE.getFriends() != null
			&& WurstClient.INSTANCE.getFriends().isFriend(entity))
			return false;

		if(entity instanceof Player otherPlayer
			&& WurstClient.INSTANCE.getHax() != null
			&& WurstClient.INSTANCE.getHax().antiBotHack.isBot(otherPlayer))
			return false;

		if(entity instanceof Player)
			return (flags & TARGET_PLAYERS) != 0;
		if(entity instanceof Enemy)
			return (flags & TARGET_HOSTILE) != 0;
		if(entity instanceof Animal)
			return (flags & TARGET_PASSIVE) != 0;
		return (flags & TARGET_FRIENDLY) != 0;
	}

	private static boolean isInFov(LivingEntity entity, Vec3 eyes, double fov)
	{
		// fov is the total field of view (360 = everything), like upstream
		// Wurst's KillAura. The check uses the full 3D angle to the look
		// vector, so entities above/below the crosshair are excluded too.
		if(fov >= 360)
			return true;

		return RotationUtils
			.getAngleToLookVec(entity.getBoundingBox().getCenter()) <= fov / 2.0D;
	}

	// ------------------------------------------------------------- visuals
	/**
	 * OpenOpal-style halo: 64 animated segments with five glow layers.
	 */
	public static void drawHalo(PoseStack matrices, Vec3 pos, LivingEntity target)
	{
		double now = System.currentTimeMillis() / 1000.0D;
		double ringY = pos.y + target.getBbHeight() * 0.5D
			+ Math.sin(now * 4.0D) * 0.45D + 0.22D;
		double radius = Math.max(0.52D, target.getBbWidth() * 0.85D);
		int segments = 64;
		int glowLayers = 5;

		RenderType layer = WurstRenderLayers.getLines(false);
		RenderUtils.submit(matrices, layer, buffer -> {

		for(int glow = 0; glow < glowLayers; glow++)
		{
			float alpha = 0.85F * (float)Math.pow(0.72D, glow);
			double y = ringY + glow * 0.012D;

			for(int i = 0; i < segments; i++)
			{
				double a1 = Math.PI * 2 * i / segments;
				double a2 = Math.PI * 2 * (i + 1) / segments;
				Vec3 start = new Vec3(pos.x + Math.sin(a1) * radius, y,
					pos.z + Math.cos(a1) * radius);
				Vec3 end = new Vec3(pos.x + Math.sin(a2) * radius, y,
					pos.z + Math.cos(a2) * radius);

				float raw = (float)(((now * 0.75D)
					+ ((double)i / segments)) % 1.0D);
				float pingpong = raw < 0.5F ? raw * 2F
					: 1F - (raw - 0.5F) * 2F;
				int color = WurstClient.INSTANCE.getGui().getTheme()
					.accent(alpha * (0.45F + 0.55F * pingpong));
				RenderUtils.drawLine(matrices, buffer, start, end, color);
			}
		}

		});
	}

	public static void drawBox(PoseStack matrices, AABB box, int color)
	{
		Vec3 offset = RenderUtils.getCameraPos().reverse();
		AABB moved = box.move(offset);

		Vec3[] corners = {new Vec3(moved.minX, moved.minY, moved.minZ),
			new Vec3(moved.maxX, moved.minY, moved.minZ),
			new Vec3(moved.maxX, moved.minY, moved.maxZ),
			new Vec3(moved.minX, moved.minY, moved.maxZ),
			new Vec3(moved.minX, moved.maxY, moved.minZ),
			new Vec3(moved.maxX, moved.maxY, moved.minZ),
			new Vec3(moved.maxX, moved.maxY, moved.maxZ),
			new Vec3(moved.minX, moved.maxY, moved.maxZ)};
		int[][] edges = {{0, 1}, {1, 2}, {2, 3}, {3, 0}, {4, 5}, {5, 6},
			{6, 7}, {7, 4}, {0, 4}, {1, 5}, {2, 6}, {3, 7}};

		RenderType layer = WurstRenderLayers.getLines(false);
		RenderUtils.submit(matrices, layer, buffer -> {
		for(int[] edge : edges)
			RenderUtils.drawLine(matrices, buffer, corners[edge[0]],
				corners[edge[1]], color);
		});
	}

	public static void drawBox(PoseStack matrices, Vec3 pos,
		LivingEntity target, int color)
	{
		double half = target.getBbWidth() / 2.0D;
		double x1 = pos.x - half;
		double x2 = pos.x + half;
		double y1 = pos.y;
		double y2 = pos.y + target.getBbHeight();
		double z1 = pos.z - half;
		double z2 = pos.z + half;

		Vec3[] corners = {new Vec3(x1, y1, z1), new Vec3(x2, y1, z1),
			new Vec3(x2, y1, z2), new Vec3(x1, y1, z2),
			new Vec3(x1, y2, z1), new Vec3(x2, y2, z1),
			new Vec3(x2, y2, z2), new Vec3(x1, y2, z2)};
		int[][] edges = {{0, 1}, {1, 2}, {2, 3}, {3, 0}, {4, 5}, {5, 6},
			{6, 7}, {7, 4}, {0, 4}, {1, 5}, {2, 6}, {3, 7}};

		RenderType layer = WurstRenderLayers.getLines(false);
		RenderUtils.submit(matrices, layer, buffer -> {
		for(int[] edge : edges)
			RenderUtils.drawLine(matrices, buffer, corners[edge[0]],
				corners[edge[1]], color);
		});
	}
}

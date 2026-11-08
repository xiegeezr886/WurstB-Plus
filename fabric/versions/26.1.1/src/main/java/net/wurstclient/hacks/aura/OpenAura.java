/*
 * Copyright (c) 2026 WurstB+ Plus contributors
 *
 * This source code is subject to the terms of the GNU Affero General Public
 * License, version 3. If a copy of the AGPL was not distributed with this
 * file, You can obtain one at: https://www.gnu.org/licenses/agpl-3.0.txt
 */
package net.wurstclient.hacks.aura;

import net.wurstclient.opal.LastAttackData;
import net.wurstclient.opal.RandomUtility;
import net.wurstclient.hacks.scaffold.ScaffoldUtils;
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
		boolean throughWalls, boolean yawFov) {
        public Config(double range, double fov, int flags, boolean throughWalls) {
            this(range, fov, flags, throughWalls, false);
        }
    }

	public static final class Target
	{
		public final LivingEntity entity;
		public final Rotation rotation;
		public final Vec3 hitVec;
		public final double distance;
		private LastAttackData lastAttackData;

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
			if(lastAttackData == null)
				return true;
			double damage = attackDamage();
			if(entity.getHealth() + entity.getAbsorptionAmount() <= damage)
				return true;
			return lastAttackData.getTime() >= ATTACK_WINDOW_MS
				|| damage > lastAttackData.getDamage();
		}

		public void onAttack(boolean reset)
		{
			double damage = attackDamage();
			if(lastAttackData == null)
				lastAttackData = new LastAttackData(damage);
			else
				lastAttackData.reset(reset, damage);
		}

		public void restoreAttackState(AttackRecord record)
		{
			lastAttackData = record == null ? null : record.data();
		}

		public AttackRecord attackRecord()
		{
			return new AttackRecord(lastAttackData);
		}

		public long getLastAttackTime()
		{
			return lastAttackData == null ? 0 : lastAttackData.getTime();
		}
	}

	public record AttackRecord(LastAttackData data)
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
			Target target = target(entity, config);
			if(target != null)
				targets.add(target);
		}
		return targets;
	}

    /** Reference closest-distance test occurs before line-of-sight conversion. */
    public static boolean hasSwingTarget(Config config) {
        if(WurstClient.MC.player == null || WurstClient.MC.level == null) return false;
        var player = WurstClient.MC.player;
        Vec3 eyes = player.getEyePosition();
        return WurstClient.MC.level.getEntitiesOfClass(LivingEntity.class,
            player.getBoundingBox().inflate(config.range()), e -> isValidTarget(e, config.targetFlags()))
            .stream().anyMatch(e -> isInYawFov(e, eyes, config.fov())
                && eyes.distanceTo(closestPoint(eyes, e.getBoundingBox())) <= config.range());
    }

	/** Rebuilds the hit from current world state at both planning and execution. */
	public static Target target(LivingEntity entity, Config config)
	{
		if(WurstClient.MC.player == null || WurstClient.MC.level == null
			|| entity == null || !isValidTarget(entity, config.targetFlags())
			|| WurstClient.MC.level.getEntity(entity.getId()) != entity)
			return null;
		Vec3 eyes = WurstClient.MC.player.getEyePosition();
		if(!(config.yawFov() ? isInYawFov(entity, eyes, config.fov()) : isInFov(entity, eyes, config.fov())))
			return null;
		Vec3 closest = closestPoint(eyes, entity.getBoundingBox()
			.inflate(entity.getPickRadius()));
		if(eyes.distanceTo(closest) > config.range())
			return null;
		return raycastTarget(entity, eyes, closest, config.range(),
			config.throughWalls());
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

		Comparator<Target> order = Comparator.comparingDouble((Target t) ->
			(t.entity.getHealth() + t.entity.getAbsorptionAmount())
				* (t.entity instanceof Player ? 1.0D : 2.0D));
		if(switchMode)
			order = Comparator.comparingDouble((Target t) ->
				-((t.entity.getHealth() + t.entity.getAbsorptionAmount()) * 25.0D
					+ t.getLastAttackTime())).thenComparing(order);
		targets.sort(order.thenComparingDouble(t -> t.distance)
			.thenComparingInt(t -> t.entity.getId()));

		return targets.get(0);
	}

	public static Target selectNearest(List<Target> targets)
	{
		if(targets.isEmpty())
			return null;
		return targets.stream()
			.min(Comparator.comparingDouble((Target t) -> t.entity.distanceToSqr(WurstClient.MC.player)
                * (t.entity instanceof Player ? 1.0D : 2.0D))
				.thenComparingInt(t -> t.entity.getId())).orElse(null);
	}

	/** Direct port of RotationUtility.getRotationFromRaycastedEntity. */
	public static Target raycastTarget(LivingEntity entity, Vec3 eyes,
		Vec3 closest, double range, boolean throughWalls)
	{
		float spread = (float)RandomUtility.getJoinRandomDouble(0.01D, 0.05D);
		Rotation base = rotationTo(eyes, closest);
		Rotation closestRotation = ScaffoldUtils.getVanillaRotation(new Rotation(
			base.yaw() + RandomUtility.getRandomFloat(-spread, spread),
			base.pitch() + RandomUtility.getRandomFloat(-spread, spread)));
		EntityHitResult closestHit = raycastEntity(entity, eyes, closestRotation, range);
		if(closestHit != null)
			return throughWalls || hasLineOfSight(eyes, closestHit.getLocation())
				? new Target(entity, closestRotation, closestHit.getLocation(), eyes.distanceTo(closest)) : null;

		AABB box = entity.getBoundingBox().inflate(entity.getPickRadius());
		Vec3 center = box.getCenter();
		double widthX = box.getXsize(), height = box.getYsize(), widthZ = box.getZsize();
		if(widthX <= 0 || height <= 0 || widthZ <= 0)
			return null;
		float step = 8.F - RandomUtility.RANDOM.nextFloat() * 0.25F;
		Target best = null;
		double bestAngle = Double.MAX_VALUE;
		for(double x = -widthX; x < widthX; x += widthX / step)
			for(double y = -height; y < height; y += height / step)
				for(double z = -widthZ; z < widthZ; z += widthZ / step)
				{
					Rotation rotation = ScaffoldUtils.getVanillaRotation(
						rotationTo(eyes, center.add(x, y, z)));
					EntityHitResult hit = raycastEntity(entity, eyes, rotation, range);
					if(hit == null)
						continue;
					double angle = rotation.getAngleTo(closestRotation);
					if(angle < bestAngle)
					{
						bestAngle = angle;
						best = new Target(entity, rotation, hit.getLocation(), eyes.distanceTo(closest));
					}
				}
		return best != null && (throughWalls || hasLineOfSight(eyes, best.hitVec)) ? best : null;
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

		double damage = 0;
        var modifiers = player.getMainHandItem().getOrDefault(
            net.minecraft.core.component.DataComponents.ATTRIBUTE_MODIFIERS,
            net.minecraft.world.item.component.ItemAttributeModifiers.EMPTY);
        for(var entry : modifiers.modifiers())
            if(entry.attribute().equals(Attributes.ATTACK_DAMAGE)
                && entry.slot() == net.minecraft.world.entity.EquipmentSlotGroup.MAINHAND)
                damage += entry.modifier().amount();
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

    private static boolean isInYawFov(LivingEntity entity, Vec3 eyes, double fov) {
        if(fov >= 180) return true;
        float yaw = net.wurstclient.opal.rotation.RotationHelper.getClientHandler()
            .getYawOr(WurstClient.MC.player.getYRot());
        return Math.abs(Mth.wrapDegrees(yaw - rotationTo(eyes, entity.position()).yaw())) < fov;
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

    public static void drawBox(PoseStack matrices, Vec3 pos, LivingEntity target, int color) {
        double half = target.getBbWidth() / 2.0D;
        AABB box = new AABB(pos.x - half, pos.y, pos.z - half,
            pos.x + half, pos.y + target.getBbHeight(), pos.z + half).move(RenderUtils.getCameraPos());
        RenderUtils.drawSolidBox(matrices, box, color & 0xFFFFFF | 0x40000000, true);
    }
}

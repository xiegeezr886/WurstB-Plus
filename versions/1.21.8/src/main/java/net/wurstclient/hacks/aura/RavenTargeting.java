/*
 * Copyright (c) 2026 WurstB+ Plus contributors
 * SPDX-License-Identifier: AGPL-3.0-only
 * NeoRaven targeting adaptation; see META-INF/licenses/neoraven/LICENSE.
 */
package net.wurstclient.hacks.aura;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.UUID;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.Vec3;
import net.wurstclient.WurstClient;
import net.wurstclient.util.FakePlayerEntity;
import net.wurstclient.util.Rotation;
import static net.wurstclient.WurstClient.MC;

public final class RavenTargeting
{
	private final AuraTargetCycle<UUID> cycle = new AuraTargetCycle<>();

	public record Config(double searchRange, double attackRange, double fov,
		int flags, boolean walls, boolean invisible, boolean teammates) {}
	public record Target(LivingEntity entity, Vec3 point, double distance,
		double yaw, Rotation rotation) {}
	public enum Priority { DISTANCE, HEALTH, HURT_TIME, YAW }

	public Target select(Config config, Priority priority, boolean switching,
		long switchDelay, int targetCount, long now)
	{
		var candidates = new ArrayList<Target>();
		Vec3 eyes = MC.player.getEyePosition();
		double radius = config.searchRange;
		for(LivingEntity entity : MC.level.getEntitiesOfClass(LivingEntity.class,
			new AABB(eyes, eyes).inflate(radius)))
		{
			Target target = inspect(entity, config);
			if(target != null) candidates.add(target);
		}
		// NeoRaven prefers attackable targets over targets only in search range.
		if(candidates.stream().anyMatch(t -> t.distance <= config.attackRange))
			candidates.removeIf(t -> t.distance > config.attackRange);
		Comparator<Target> comparator = switch(priority) {
			case DISTANCE -> Comparator.comparingDouble(Target::distance);
			case HEALTH -> Comparator.comparingDouble(t -> t.entity.getHealth());
			case HURT_TIME -> Comparator.comparingInt(t -> t.entity.hurtTime);
			case YAW -> Comparator.comparingDouble(Target::yaw);
		};
		candidates.sort(comparator.thenComparingInt(t -> t.entity.getId()));
		if(candidates.size() > targetCount) candidates.subList(Math.max(1, targetCount), candidates.size()).clear();
		UUID id = cycle.select(candidates.stream().map(t -> t.entity.getUUID()).toList(),
			switching, switchDelay, now);
		return candidates.stream().filter(t -> t.entity.getUUID().equals(id))
			.findFirst().orElse(null);
	}

	public Target inspect(LivingEntity entity, Config config)
	{
		if(!valid(entity, config)) return null;
		Vec3 eyes = MC.player.getEyePosition();
		float viewYaw = net.wurstclient.opal.rotation.RotationHelper.getClientHandler()
			.getYawOr(MC.player.getYRot());
		double yaw = Math.abs(Mth.wrapDegrees(viewYaw
			- OpenAura.rotationTo(eyes, entity.getBoundingBox().getCenter()).yaw()));
		// Preserve existing saved FOV: this setting is a half-angle (180 = all).
		if(yaw > config.fov) return null;
		if(eyes.distanceTo(OpenAura.closestPoint(eyes, entity.getBoundingBox()))
			> config.searchRange) return null;
		Vec3 point = aimPoint(entity, eyes, config);
		if(point == null) return null;
		return new Target(entity, point, eyes.distanceTo(point), yaw,
			OpenAura.rotationTo(eyes, point));
	}

	private boolean valid(LivingEntity entity, Config config)
	{
		if(MC.player == null || MC.level == null || entity == MC.player
			|| MC.level.getEntity(entity.getId()) != entity || !entity.isAlive()
			|| entity.isSpectator() || !entity.isPickable()
			|| entity instanceof FakePlayerEntity) return false;
		if(!config.invisible && entity.isInvisible()) return false;
		if(!config.teammates && MC.player.isAlliedTo(entity)) return false;
		var wurst = WurstClient.INSTANCE;
		if(wurst.getFriends().isFriend(entity)) return false;
		if(entity instanceof Player player && wurst.getHax().antiBotHack.isBot(player))
			return false;
		int flag = entity instanceof Player ? OpenAura.TARGET_PLAYERS
			: entity instanceof Enemy ? OpenAura.TARGET_HOSTILE
			: entity instanceof Animal ? OpenAura.TARGET_PASSIVE : OpenAura.TARGET_FRIENDLY;
		return (config.flags & flag) != 0;
	}

	/** Bounded visible-point search, replacing the old random 3-D grid. */
	private Vec3 aimPoint(LivingEntity entity, Vec3 eyes, Config config)
	{
		AABB box = entity.getBoundingBox();
		var points = new ArrayList<Vec3>(11);
		points.add(OpenAura.closestPoint(eyes, box.deflate(0.001)));
		points.add(box.getCenter());
		points.add(entity.getEyePosition());
		for(double x : new double[]{0.15, 0.85})
			for(double y : new double[]{0.2, 0.8})
				for(double z : new double[]{0.15, 0.85})
					points.add(new Vec3(Mth.lerp(x, box.minX, box.maxX),
						Mth.lerp(y, box.minY, box.maxY), Mth.lerp(z, box.minZ, box.maxZ)));
		points.sort(Comparator.comparingDouble(eyes::distanceToSqr));
		for(Vec3 point : points)
			if(eyes.distanceTo(point) <= config.searchRange
				&& (config.walls || OpenAura.hasLineOfSight(eyes, point))) return point;
		return null;
	}

	/** Check the actual sent aim against all pickable entities, not just target. */
	public boolean canHit(Target target, Rotation rotation, Config config)
	{
		if(rotation == null || !valid(target.entity, config)) return false;
		Vec3 eyes = MC.player.getEyePosition();
		Vec3 end = eyes.add(rotation.toLookVec().scale(config.attackRange));
		EntityHitResult hit = ProjectileUtil.getEntityHitResult(MC.player, eyes, end,
			new AABB(eyes, end).inflate(1), e -> e != MC.player
				&& !e.isSpectator() && e.isPickable(), config.attackRange * config.attackRange);
		return hit != null && hit.getEntity() == target.entity
			&& (config.walls || OpenAura.hasLineOfSight(eyes, hit.getLocation()));
	}

	public void reset() { cycle.reset(); }
}

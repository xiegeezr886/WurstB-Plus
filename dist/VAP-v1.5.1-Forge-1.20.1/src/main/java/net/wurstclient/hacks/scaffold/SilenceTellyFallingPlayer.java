/*
 * Copyright (c) 2026 WurstB+ Plus contributors
 *
 * This source code is subject to the terms of the GNU Affero General Public
 * License, version 3. If a copy of the AGPL was not distributed with this
 * file, You can obtain one at: https://www.gnu.org/licenses/agpl-3.0.txt
 */
package net.wurstclient.hacks.scaffold;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.wurstclient.WurstClient;
import net.wurstclient.util.Rotation;

/**
 * Ported from OpenOpal's {@code SilenceTellyFallingPlayer}. Predicts the next
 * few ticks of movement for the falling rescue check.
 */
final class SilenceTellyFallingPlayer
{
	private double x;
	private double y;
	private double z;
	private Vec3 motion;
	private Vec3 eyePos;
	private final float yaw;
	private final float strafe;
	private final float forward;
	private final float jumpMovementFactor;
	private boolean onGround;

	SilenceTellyFallingPlayer(float fallbackForward, float fallbackSideways)
	{
		var player = WurstClient.MC.player;
		x = player.getX();
		y = player.getY();
		z = player.getZ();
		motion = player.getDeltaMovement();
		yaw = player.getYRot();
		float inputForward = player.input.forwardImpulse;
		float inputStrafe = player.input.leftImpulse;
		strafe = Math.abs(inputStrafe) > 1.0E-4F
			|| Math.abs(fallbackSideways) <= 1.0E-4F ? inputStrafe
				: fallbackSideways;
		forward = Math.abs(inputForward) > 1.0E-4F
			|| Math.abs(fallbackForward) <= 1.0E-4F ? inputForward
				: fallbackForward;
		onGround = player.onGround();
		jumpMovementFactor = player.isSprinting() ? 0.026F : 0.02F;
		eyePos = player.getEyePosition();
	}

	void calculate(int ticks)
	{
		for(int i = 0; i < ticks; i++)
			calculateForTick();
	}

	Vec3 getEyePos()
	{
		return eyePos;
	}

	Vec3 getPos()
	{
		return new Vec3(x, y, z);
	}

	double getY()
	{
		return y;
	}

	private void calculateForTick()
	{
		updateVelocity(jumpMovementFactor, new Vec3(strafe, 0.0D, forward));
		x += motion.x;
		y += motion.y;
		z += motion.z;
		updateGroundState();
		motion = motion.add(0.0D, -0.08D, 0.0D);
		eyePos = new Vec3(x,
			y + WurstClient.MC.player.getEyeHeight(), z);
		motion = new Vec3(motion.x * 0.91D, motion.y * 0.98D,
			motion.z * 0.91D);
	}

	private void updateVelocity(float speed, Vec3 input)
	{
		double lengthSquared = input.lengthSqr();
		if(lengthSquared < 1.0E-7D)
			return;

		Vec3 normalized = (lengthSquared > 1.0D ? input.normalize()
			: input).scale(speed);
		float sin = Mth.sin(yaw * Mth.DEG_TO_RAD);
		float cos = Mth.cos(yaw * Mth.DEG_TO_RAD);
		double inputX = normalized.x * cos - normalized.z * sin;
		double inputZ = normalized.z * cos + normalized.x * sin;
		motion = motion.add(inputX, 0.0D, inputZ);
	}

	private void updateGroundState()
	{
		BlockHitResult result = rayTraceHit(new Vec3(x, y, z),
			new Vec3(x, y - 0.2D, z));
		onGround = result != null
			&& result.getDirection() == Direction.UP;
	}

	private BlockHitResult rayTraceHit(Vec3 start, Vec3 end)
	{
		if(WurstClient.MC.level == null
			|| start.distanceTo(end) < 1.0E-7D)
			return null;
		Vec3 diff = end.subtract(start);
		float yaw = Mth.wrapDegrees(
			(float)Math.toDegrees(Math.atan2(diff.z, diff.x)) - 90.0F);
		float pitch = Mth.wrapDegrees((float)-Math.toDegrees(Math.atan2(
			diff.y, Math.sqrt(diff.x * diff.x + diff.z * diff.z))));
		return SilenceTellyRaycast.getFacedBlock(start,
			new Rotation(yaw, pitch));
	}
}

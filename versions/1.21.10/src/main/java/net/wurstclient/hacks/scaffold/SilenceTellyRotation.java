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
import net.minecraft.world.phys.Vec3;
import net.wurstclient.WurstClient;
import net.wurstclient.util.Rotation;

/**
 * Ported from OpenOpal's {@code SilenceTellyRotationUtility}.
 */
final class SilenceTellyRotation
{
	private static final double[] FACE_OFFSETS = {0.0D, 0.24D, -0.24D,
		0.36D, -0.36D, 0.45D, -0.45D};

	private SilenceTellyRotation()
	{}

	static Rotation getClosestToBlockFace(BlockPos pos, Direction face,
		Rotation reference)
	{
		return getClosestToBlockFace(pos, face, reference,
			WurstClient.MC.player == null ? null
				: WurstClient.MC.player.getEyePosition());
	}

	static Rotation getClosestToBlockFace(BlockPos pos, Direction face,
		Rotation reference, Vec3 eyePos)
	{
		return getClosestToBlockFace(pos, face, reference, eyePos, false);
	}

	static Rotation getClosestToBlockFace(BlockPos pos, Direction face,
		Rotation reference, Vec3 eyePos, boolean fixSensitivity)
	{
		if(WurstClient.MC.player == null || pos == null || face == null
			|| eyePos == null)
			return null;

		Rotation bestRotation = null;
		double bestDifference = Double.MAX_VALUE;
		for(double firstOffset : FACE_OFFSETS)
			for(double secondOffset : FACE_OFFSETS)
			{
				if(Math.abs(firstOffset) == 0.45D
					&& Math.abs(secondOffset) == 0.45D)
					continue;
				Vec3 hitVec = getFaceHitVec(pos, face, firstOffset,
					secondOffset);
				Rotation raw = getRotationTo(eyePos, hitVec);
				Rotation rotation = fixSensitivity
					? patchSensitivity(raw, reference) : raw;
				if(!SilenceTellyRaycast.didHitBlockFace(eyePos, rotation,
					pos, face, true))
					continue;

				double difference = getRotationDifference(reference,
					rotation);
				if(difference < bestDifference)
				{
					bestDifference = difference;
					bestRotation = rotation;
				}
			}

		if(bestRotation != null)
			return bestRotation;

		Rotation center = getRotationTo(eyePos,
			getFaceHitVec(pos, face, 0.0D, 0.0D));
		Rotation fallback = fixSensitivity
			? patchSensitivity(center, reference) : center;
		return SilenceTellyRaycast.didHitBlockFace(eyePos, fallback, pos,
			face, true) ? fallback : null;
	}

	static Rotation getRotationTo(Vec3 from, Vec3 to)
	{
		double diffX = to.x - from.x;
		double diffY = to.y - from.y;
		double diffZ = to.z - from.z;
		float yaw = Mth.wrapDegrees(
			(float)Math.toDegrees(Math.atan2(diffZ, diffX)) - 90.0F);
		float pitch = Mth.clamp(
			Mth.wrapDegrees((float)-Math.toDegrees(Math.atan2(diffY,
				Math.sqrt(diffX * diffX + diffZ * diffZ)))),
			-90.0F, 90.0F);
		return new Rotation(yaw, pitch);
	}

	static Rotation patchSensitivity(Rotation rotation, Rotation reference)
	{
		if(rotation == null || reference == null)
			return rotation;
		return ScaffoldUtils.patchConstantRotation(rotation, reference);
	}

	static float yawDiffDirectly(float target, float current)
	{
		return Mth.wrapDegrees(target - current);
	}

	static float smooth(float diff, float max)
	{
		return Mth.clamp(diff, -Math.abs(max), Math.abs(max));
	}

	static double getRotationDifference(Rotation from, Rotation to)
	{
		if(from == null || to == null)
			return 0.0D;
		float yaw = Mth.wrapDegrees(to.yaw() - from.yaw());
		float pitch = to.pitch() - from.pitch();
		return Math.sqrt(yaw * yaw + pitch * pitch);
	}

	private static Vec3 getFaceHitVec(BlockPos pos, Direction face,
		double firstOffset, double secondOffset)
	{
		double x = pos.getX() + 0.5D + face.getStepX() * 0.5D;
		double y = pos.getY() + 0.5D + face.getStepY() * 0.5D;
		double z = pos.getZ() + 0.5D + face.getStepZ() * 0.5D;

		switch(face.getAxis())
		{
			case X:
			y += firstOffset;
			z += secondOffset;
			break;
			case Y:
			x += firstOffset;
			z += secondOffset;
			break;
			case Z:
			x += firstOffset;
			y += secondOffset;
			break;
		}
		return new Vec3(x, y, z);
	}
}

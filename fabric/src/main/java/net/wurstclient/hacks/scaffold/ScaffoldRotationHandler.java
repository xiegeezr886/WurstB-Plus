/*
 * Copyright (c) 2026 WurstB+ Plus contributors
 *
 * This source code is subject to the terms of the GNU Affero General Public
 * License, version 3. If a copy of the AGPL was not distributed with this
 * file, You can obtain one at: https://www.gnu.org/licenses/agpl-3.0.txt
 */
package net.wurstclient.hacks.scaffold;

import net.minecraft.client.player.LocalPlayer;
import net.minecraft.util.Mth;
import net.wurstclient.WurstClient;
import net.wurstclient.util.Rotation;

/**
 * Tracks the rotation that Scaffold wants the server to see.
 *
 * <p>OpenOpal feeds synthetic mouse deltas into the real player, which also
 * changes the direction the player walks unless Movement Fix compensates.
 * Wurst's {@code RotationFaker} already spoofs the outgoing movement packet,
 * so the clean equivalent is to keep the real client rotation untouched and
 * only advance this fake rotation.
 */
public final class ScaffoldRotationHandler
{
	private Rotation current;
	private Rotation original;
	private Rotation target;
	private ScaffoldRotationModel model = ScaffoldRotationModel.INSTANT;
	private float speed = 180.0F;
	private boolean active;
	private boolean reversing;

	public void rotate(Rotation rotation, ScaffoldRotationModel model,
		float speed)
	{
		if(rotation == null || WurstClient.MC.player == null)
			return;
		if(!active || original == null)
		{
			original = getPlayerRotation();
			current = original;
		}
		target = rotation;
		this.model = model;
		this.speed = speed;
		active = true;
		reversing = false;
		apply();
	}

	public void rotateHypixel(Rotation rotation, boolean diagonalYaw,
		int airTicks)
	{
		if(rotation == null || WurstClient.MC.player == null)
			return;
		if(!active || original == null)
		{
			original = getPlayerRotation();
			current = original;
		}
		target = rotation;
		model = null;
		active = true;
		reversing = false;
		current = ScaffoldRotationModel.tickHypixel(current, target, 1.0F,
			diagonalYaw, airTicks);
	}

	public void reverse()
	{
		if(!active || original == null)
			return;
		target = original;
		reversing = true;
	}

	public void tickReverse()
	{
		if(!active || !reversing || target == null)
			return;
		if(model == null)
			current = ScaffoldRotationModel.tickHypixel(current, target,
				1.0F, false, 0);
		else
			current = model.tick(current, target, speed);
		if(distance(current, target) <= 1.0E-4D)
			clear();
	}

	public void reset()
	{
		clear();
	}

	public boolean isActive()
	{
		return active;
	}

	/**
	 * @return the rotation that the server should receive this tick. Falls
	 *         back to the real client rotation when nothing is being spoofed.
	 */
	public Rotation getRotation()
	{
		return current != null ? current : getPlayerRotation();
	}

	private Rotation getPlayerRotation()
	{
		LocalPlayer player = WurstClient.MC.player;
		return player == null ? new Rotation(0, 0)
			: new Rotation(player.getYRot(), player.getXRot());
	}

	private void apply()
	{
		if(target == null)
			return;
		current = model == null
			? ScaffoldRotationModel.tickHypixel(current, target, 1.0F, false,
				0)
			: model.tick(current, target, speed);
	}

	private void clear()
	{
		active = false;
		reversing = false;
		target = null;
		original = null;
		current = null;
	}

	private double distance(Rotation a, Rotation b)
	{
		float yaw = Mth.wrapDegrees(a.yaw() - b.yaw());
		float pitch = a.pitch() - b.pitch();
		return Math.sqrt(yaw * yaw + pitch * pitch);
	}
}

/*
 * Copyright (c) 2026 WurstB+ Plus contributors
 *
 * This source code is subject to the terms of the GNU Affero General Public
 * License, version 3. If a copy of the AGPL was not distributed with this
 * file, You can obtain one at: https://www.gnu.org/licenses/agpl-3.0.txt
 */
package net.wurstclient.hacks.scaffold;

import net.wurstclient.WurstClient;
import net.wurstclient.hacks.ScaffoldWalkHack;

/**
 * OpenOpal's {@code HypixelScaffold} is a {@code HeypixelScaffold} with telly
 * permanently enabled, five telly ticks, 180 degree/tick rotations and an
 * optional sideways base yaw.
 */
public final class HypixelScaffoldMode extends HeypixelScaffoldMode
{
	public HypixelScaffoldMode(ScaffoldWalkHack hack)
	{
		super(hack, true);
	}

	@Override
	protected float resolveBaseYaw()
	{
		if(hack().rotationModel
			.getSelected() != ScaffoldRotationModel.SIDEWAYS)
			return super.resolveBaseYaw();
		return ScaffoldUtils.moveDirectionDegrees(
			WurstClient.MC.player == null ? 0
				: WurstClient.MC.player.getYRot());
	}

	@Override
	protected boolean shouldTrackMovementYawDuringTelly()
	{
		return hack().rotationModel
			.getSelected() == ScaffoldRotationModel.SIDEWAYS;
	}

	@Override
	protected boolean isTellyEnabled()
	{
		return true;
	}

	@Override
	protected int getTellyTick()
	{
		return 5;
	}

	@Override
	protected float getRotateSpeed()
	{
		return 180.0F;
	}

	@Override
	protected float getRotateBackSpeed()
	{
		return 180.0F;
	}

	@Override
	protected String getModeName()
	{
		return "Hypixel";
	}
}

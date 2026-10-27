/*
 * Copyright (c) 2026 WurstB+ Plus contributors
 *
 * This source code is subject to the terms of the GNU Affero General Public
 * License, version 3. If a copy of the AGPL was not distributed with this
 * file, You can obtain one at: https://www.gnu.org/licenses/agpl-3.0.txt
 */
// Ported from Soar Client v7.1.2 (LGPL-3.0).
package net.wurstclient.hud2.mods;

import net.wurstclient.events.UpdateListener;
import net.wurstclient.hud2.nanovg.HudIcon;
import net.wurstclient.hud2.SimpleHUDMod;

public final class JumpResetIndicatorMod extends SimpleHUDMod
	implements UpdateListener
{
	private int hurtAge;
	private int jumpAge;
	private int lastHurtTime;
	private boolean wasOnGround;
	private long lastTime;

	public JumpResetIndicatorMod()
	{
		super("JumpResetIndicator", "Jump Reset Indicator",
			"Shows whether the last jump was a good knockback reset.");
		setTranslationKey("jump_reset_indicator");
		setDefaultPosition(180, 674);
	}

	@Override
	protected void onEnable()
	{
		hurtAge = 0;
		jumpAge = 0;
		lastTime = 0;
		if(MC.player != null)
		{
			lastHurtTime = MC.player.hurtTime;
			wasOnGround = MC.player.onGround();
		}
		EVENTS.add(UpdateListener.class, this);
	}

	@Override
	protected void onDisable()
	{
		EVENTS.remove(UpdateListener.class, this);
	}

	@Override
	public void onUpdate()
	{
		if(MC.player == null)
			return;

		long now = System.currentTimeMillis();
		if(MC.player.hurtTime > lastHurtTime)
		{
			hurtAge = MC.player.tickCount;
			lastTime = now;
		}
		lastHurtTime = MC.player.hurtTime;

		boolean onGround = MC.player.onGround();
		if(wasOnGround && !onGround
			&& MC.player.getDeltaMovement().y > 0.05)
		{
			jumpAge = MC.player.tickCount;
			lastTime = now;
		}
		wasOnGround = onGround;
	}

	@Override
	public String getText()
	{
		if(System.currentTimeMillis() - lastTime > 2500
			|| Math.abs(jumpAge - hurtAge) >= 10)
			return "No Jump";
		if(jumpAge == hurtAge + 1)
			return "Perfect!";
		if(hurtAge + 1 < jumpAge)
			return "Late: " + (jumpAge - hurtAge + 1) + " Tick";
		return "Early: " + (hurtAge + 1 - jumpAge) + " Tick";
	}

	@Override
	public String getIcon()
	{
		return HudIcon.ACTIVITY;
	}
}

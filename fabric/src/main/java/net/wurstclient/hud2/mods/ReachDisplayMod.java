/*
 * Copyright (c) 2026 WurstB+ Plus contributors
 *
 * This source code is subject to the terms of the GNU Affero General Public
 * License, version 3. If a copy of the AGPL was not distributed with this
 * file, You can obtain one at: https://www.gnu.org/licenses/agpl-3.0.txt
 */
// Ported from Soar Client v7.1.2 (LGPL-3.0).
package net.wurstclient.hud2.mods;

import net.minecraft.world.entity.Entity;
import net.wurstclient.events.PlayerAttacksEntityListener;
import net.wurstclient.hud2.nanovg.HudIcon;
import net.wurstclient.hud2.SimpleHUDMod;

public final class ReachDisplayMod extends SimpleHUDMod
	implements PlayerAttacksEntityListener
{
	private static final long TIMEOUT = 2_000_000_000L;

	private double lastReach;
	private long lastHit;

	public ReachDisplayMod()
	{
		super("ReachDisplay", "Reach Display",
			"Shows the distance of the last hit.");
		setTranslationKey("reach_display");
		setDefaultPosition(10, 584);
	}

	@Override
	protected void onEnable()
	{
		lastHit = 0;
		EVENTS.add(PlayerAttacksEntityListener.class, this);
	}

	@Override
	protected void onDisable()
	{
		EVENTS.remove(PlayerAttacksEntityListener.class, this);
		lastHit = 0;
	}

	@Override
	public void onPlayerAttacksEntity(Entity target)
	{
		if(MC.player == null)
			return;
		lastReach = MC.player.distanceTo(target);
		lastHit = System.nanoTime();
	}

	@Override
	public String getText()
	{
		if(lastHit == 0 || System.nanoTime() - lastHit > TIMEOUT)
			return "0.00 Reach";
		return String.format("%.2f Reach", lastReach);
	}

	@Override
	public String getIcon()
	{
		return HudIcon.ACTIVITY;
	}
}

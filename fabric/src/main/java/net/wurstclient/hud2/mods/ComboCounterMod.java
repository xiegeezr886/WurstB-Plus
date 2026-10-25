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
import net.wurstclient.events.UpdateListener;
import net.wurstclient.hud2.nanovg.HudIcon;
import net.wurstclient.hud2.SimpleHUDMod;

public final class ComboCounterMod extends SimpleHUDMod
	implements PlayerAttacksEntityListener, UpdateListener
{
	private static final long TIMEOUT = 2_000_000_000L;

	private Entity lastTarget;
	private int combo;
	private long lastHit;

	public ComboCounterMod()
	{
		super("ComboCounter", "Combo Counter",
			"Counts consecutive hits on the same entity.");
		setTranslationKey("combo_counter");
		setDefaultPosition(10, 562);
	}

	@Override
	protected void onEnable()
	{
		reset();
		EVENTS.add(PlayerAttacksEntityListener.class, this);
		EVENTS.add(UpdateListener.class, this);
	}

	@Override
	protected void onDisable()
	{
		EVENTS.remove(PlayerAttacksEntityListener.class, this);
		EVENTS.remove(UpdateListener.class, this);
		reset();
	}

	@Override
	public void onPlayerAttacksEntity(Entity target)
	{
		long now = System.nanoTime();
		if(target == lastTarget && now - lastHit <= TIMEOUT)
			combo++;
		else
			combo = 1;
		lastTarget = target;
		lastHit = now;
	}

	@Override
	public void onUpdate()
	{
		if(MC.player == null || MC.player.hurtTime > 0
			|| MC.player.getHealth() <= 0
			|| lastHit != 0 && System.nanoTime() - lastHit > TIMEOUT)
			reset();
	}

	private void reset()
	{
		combo = 0;
		lastTarget = null;
		lastHit = 0;
	}

	@Override
	public String getText()
	{
		return combo + " Combo";
	}

	@Override
	public String getIcon()
	{
		return HudIcon.ACTIVITY;
	}
}

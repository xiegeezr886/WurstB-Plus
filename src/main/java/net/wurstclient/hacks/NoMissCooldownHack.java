/*
 * Copyright (c) 2015-2026 CCBlueX
 * Copyright (c) 2025-2026 Penguin
 *
 * This source code is subject to the terms of the GNU General Public
 * License, version 3. If a copy of the GPL was not distributed with this
 * file, You can obtain one at: https://www.gnu.org/licenses/gpl-3.0.txt
 */
package net.wurstclient.hacks;

import net.minecraft.world.phys.HitResult;
import net.wurstclient.Category;
import net.wurstclient.SearchTags;
import net.wurstclient.events.LeftClickListener;
import net.wurstclient.events.UpdateListener;
import net.wurstclient.hack.Hack;
import net.wurstclient.mixinterface.IMinecraftClient;
import net.wurstclient.settings.CheckboxSetting;

@SearchTags({"no miss cooldown", "miss cooldown"})
public final class NoMissCooldownHack extends Hack
	implements UpdateListener, LeftClickListener
{
	private final CheckboxSetting removeAttackCooldown = new CheckboxSetting(
		"Remove attack cooldown", "Removes the 10-tick delay after a missed hit.",
		true);
	private final CheckboxSetting cancelAttackOnMiss = new CheckboxSetting(
		"Cancel attack on miss", "Cancels empty swings before vanilla applies its delay.",
		false);

	public NoMissCooldownHack()
	{
		super("NoMissCooldown");
		setCategory(Category.COMBAT);
		addSetting(removeAttackCooldown);
		addSetting(cancelAttackOnMiss);
	}

	@Override
	protected void onEnable()
	{
		EVENTS.add(UpdateListener.class, this);
		EVENTS.add(LeftClickListener.class, this);
	}

	@Override
	protected void onDisable()
	{
		EVENTS.remove(UpdateListener.class, this);
		EVENTS.remove(LeftClickListener.class, this);
	}

	@Override
	public void onUpdate()
	{
		if(removeAttackCooldown.isChecked())
			((IMinecraftClient)MC).setMissTime(0);
	}

	@Override
	public void onLeftClick(LeftClickEvent event)
	{
		if(cancelAttackOnMiss.isChecked() && MC.hitResult != null
			&& MC.hitResult.getType() == HitResult.Type.MISS)
			event.cancel();
	}
}

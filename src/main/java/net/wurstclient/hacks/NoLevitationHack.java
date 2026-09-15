/*
 * Copyright (c) 2014-2025 Wurst-Imperium and contributors.
 *
 * This source code is subject to the terms of the GNU General Public
 * License, version 3. If a copy of the GPL was not distributed with this
 * file, You can obtain one at: https://www.gnu.org/licenses/gpl-3.0.txt
 */
package net.wurstclient.hacks;

import net.minecraft.world.effect.MobEffects;
import net.wurstclient.Category;
import net.wurstclient.SearchTags;
import net.wurstclient.events.HasEffectListener;
import net.wurstclient.events.HasEffectListener.HasEffectEvent;
import net.wurstclient.hack.Hack;

@SearchTags({"no levitation", "levitation", "levitate"})
public final class NoLevitationHack extends Hack implements HasEffectListener
{
	public NoLevitationHack()
	{
		super("NoLevitation");
		setCategory(Category.MOVEMENT);
	}
	
	@Override
	protected void onEnable()
	{
		EVENTS.add(HasEffectListener.class, this);
	}
	
	@Override
	protected void onDisable()
	{
		EVENTS.remove(HasEffectListener.class, this);
	}
	
	@Override
	public void onHasEffect(HasEffectEvent event)
	{
		if(isEnabled() && event.getEffect() == MobEffects.LEVITATION)
			event.setHasEffect(false);
	}
	
	// See ClientPlayerEntityMixin.hasStatusEffect() and
	// ClientPlayerEntityMixin.getStatusEffect()
}

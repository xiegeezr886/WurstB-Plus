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

@SearchTags({"AntiBlindness", "NoBlindness", "anti blindness", "no blindness",
	"AntiDarkness", "NoDarkness", "anti darkness", "no darkness",
	"AntiWardenEffect", "anti warden effect", "NoWardenEffect",
	"no warden effect"})
public final class AntiBlindHack extends Hack implements HasEffectListener
{
	public AntiBlindHack()
	{
		super("AntiBlind");
		setCategory(Category.RENDER);
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
		if(isEnabled() && event.getEffect() == MobEffects.DARKNESS)
			event.setHasEffect(false);
	}
	
	// See BackgroundRendererMixin, LightmapTextureManagerMixin,
	// WorldRendererMixin, ClientPlayerEntityMixin.hasStatusEffect()
}

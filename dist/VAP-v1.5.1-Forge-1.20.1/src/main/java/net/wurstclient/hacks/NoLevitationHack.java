/*
 * Copyright (c) 2014-2025 Wurst-Imperium and contributors.
 *
 * This source code is subject to the terms of the GNU General Public
 * License, version 3. If a copy of the GPL was not distributed with this
 * file, You can obtain one at: https://www.gnu.org/licenses/gpl-3.0.txt
 */
package net.wurstclient.hacks;

import net.wurstclient.Category;
import net.wurstclient.SearchTags;
import net.wurstclient.hack.Hack;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.effect.MobEffect;
import net.wurstclient.settings.TextFieldSetting;
import net.wurstclient.util.RegistryIdFilter;

@SearchTags({"no levitation", "levitation", "levitate", "NoStatusEffects", "blocked effects"})
public final class NoLevitationHack extends Hack
{
	// Configurable blocked effects adapted from Meteor Client (GPL-3.0).
	private final TextFieldSetting blockedEffects = new TextFieldSetting("Blocked effects",
		"要屏蔽的效果 ID，用逗号分隔。\n例如 levitation,jump_boost,slow_falling,dolphins_grace。",
		"levitation");
	private final RegistryIdFilter filter = new RegistryIdFilter();

	public NoLevitationHack()
	{
		super("NoLevitation");
		setCategory(Category.MOVEMENT);
		addSetting(blockedEffects);
	}

	public boolean shouldBlock(MobEffect effect)
	{
		if(!isEnabled() || effect == null)
			return false;
		var id = BuiltInRegistries.MOB_EFFECT.getKey(effect);
		return id != null && filter.contains(blockedEffects.getValue(), id.toString());
	}
	
	// See ClientPlayerEntityMixin.hasStatusEffect() and
	// ClientPlayerEntityMixin.getStatusEffect()
}

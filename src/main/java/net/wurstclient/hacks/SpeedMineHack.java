/*
 * Copyright (c) 2025 Penguin
 *
 * This source code is subject to the terms of the GNU General Public
 * License, version 3. If a copy of the GPL was not distributed with this
 * file, You can obtain one at: https://www.gnu.org/licenses/gpl-3.0.txt
 */
package net.wurstclient.hacks;

import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.wurstclient.Category;
import net.wurstclient.SearchTags;
import net.wurstclient.events.UpdateListener;
import net.wurstclient.hack.Hack;
import net.wurstclient.settings.EnumSetting;
import net.wurstclient.settings.SliderSetting;
import net.wurstclient.settings.SliderSetting.ValueDisplay;

@SearchTags({"speed mine", "speedmine", "fast mine", "haste"})
public final class SpeedMineHack extends Hack implements UpdateListener
{
	private final EnumSetting<Mode> mode = new EnumSetting<>("Mode",
		"\u00a7lHaste\u00a7r - Applies infinite Haste.\n"
			+ "\u00a7lOG\u00a7r - Reduces break cooldown.",
		Mode.values(), Mode.HASTE);

	private final SliderSetting hasteLevel = new SliderSetting(
		"Haste level", "Haste potion level (0=I, 1=II, 2=III).",
		1, 0, 2, 1, ValueDisplay.INTEGER);

	private final SliderSetting cooldown = new SliderSetting("Cooldown",
		"Ticks between mining blocks.", 1, 1, 4, 1,
		ValueDisplay.INTEGER);

	public SpeedMineHack()
	{
		super("SpeedMine");
		setCategory(Category.BLOCKS);
		addSetting(mode);
		addSetting(hasteLevel);
		addSetting(cooldown);
	}

	@Override
	protected void onEnable()
	{
		EVENTS.add(UpdateListener.class, this);
	}

	@Override
	protected void onDisable()
	{
		EVENTS.remove(UpdateListener.class, this);
		MC.player.removeEffect(MobEffects.DIG_SPEED);
	}

	@Override
	public void onUpdate()
	{
		if(mode.getSelected() == Mode.HASTE)
			MC.player.addEffect(new MobEffectInstance(
				MobEffects.DIG_SPEED, 5, hasteLevel.getValueI(), false,
				false, false));
	}

	private enum Mode
	{
		HASTE("Haste"),
		OG("OG");

		private final String name;

		Mode(String name)
		{
			this.name = name;
		}

		@Override
		public String toString()
		{
			return name;
		}
	}
}

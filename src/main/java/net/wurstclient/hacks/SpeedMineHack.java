/*
 * Copyright (c) 2025 Penguin
 *
 * This source code is subject to the terms of the GNU General Public
 * License, version 3. If a copy of the GPL was not distributed with this
 * file, You can obtain one at: https://www.gnu.org/licenses/gpl-3.0.txt
 */
package net.wurstclient.hacks;

import net.wurstclient.Category;
import net.wurstclient.SearchTags;
import net.wurstclient.events.UpdateListener;
import net.wurstclient.hack.Hack;
import net.wurstclient.hacks.speedmine.HasteSpeedMineMode;
import net.wurstclient.hacks.speedmine.OgSpeedMineMode;
import net.wurstclient.hacks.speedmine.SpeedMineMode;
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
		
		// 只清掉"我们自己加进去的那一份"，并把原本就有的急迫（药水/信标）放回去。
		// 旧实现无条件 removeEffect(DIG_SPEED)：反例是站在信标范围里用 Haste 模式后
		// 关掉 SpeedMine，客户端自己的急迫效果也被清掉（服务端要等效果变化才会重新同步）。
		// 用 appliedHaste 兜住"OG 模式从未加过效果"的情况，避免误删玩家真实的急迫。
		// 这段状态现在归 Haste 模式所有，所以对所有模式都调一次 onDisable()：
		// Haste 会收拾，OG 是空操作 —— 语义与原来"关闭时总是收拾"一致。
		for(Mode m : Mode.values())
			m.impl().onDisable();
	}

	@Override
	public void onUpdate()
	{
		// 具体行为交给模式类（见 net.wurstclient.hacks.speedmine）。
		mode.getSelected().impl().onUpdate(this);
	}

	/** 供 Haste 模式读取 Haste level 设置。 */
	public int getHasteLevel()
	{
		return hasteLevel.getValueI();
	}

	/** 供 OG 模式读取 Cooldown 设置。 */
	public int getCooldown()
	{
		return cooldown.getValueI();
	}

	private enum Mode
	{
		HASTE("Haste", new HasteSpeedMineMode()),
		OG("OG", new OgSpeedMineMode());

		private final String name;
		private final SpeedMineMode impl;

		Mode(String name, SpeedMineMode impl)
		{
			this.name = name;
			this.impl = impl;
		}

		public SpeedMineMode impl()
		{
			return impl;
		}

		@Override
		public String toString()
		{
			return name;
		}
	}
}

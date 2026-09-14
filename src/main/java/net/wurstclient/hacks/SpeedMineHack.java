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

	private MobEffectInstance previousHaste;
	private boolean appliedHaste;

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
		if(appliedHaste)
		{
			MC.player.removeEffect(MobEffects.DIG_SPEED);
			if(previousHaste != null)
				MC.player.addEffect(previousHaste);
			appliedHaste = false;
			previousHaste = null;
		}
	}

	@Override
	public void onUpdate()
	{
		if(mode.getSelected() == Mode.HASTE)
		{
			appliedHaste = true;
			
			MobEffectInstance existing =
				MC.player.getEffect(MobEffects.DIG_SPEED);
			if(existing != null
				&& existing.getAmplifier() >= hasteLevel.getValueI())
				return;
			
			if(previousHaste == null)
				previousHaste = existing;
			
			MC.player.addEffect(new MobEffectInstance(
				MobEffects.DIG_SPEED, 5, hasteLevel.getValueI(), false,
				false, false));
			return;
		}
		
		// OG 模式：把"两次挖掘之间的间隔"压到 Cooldown 设定的 tick 数。
		// 1.20.2 的 MultiPlayerGameMode.destroyDelay 是 public 字段
		// （反编译源 MultiPlayerGameMode.java:71），continueDestroyBlock() 开头
		// :201-203 只要它 > 0 就直接 return true（这一 tick 不累积破坏进度），
		// 破坏成功后 :248 把它重置为 5 ⇒ 这就是原版的挖掘间隔。
		// 旧实现完全没有读取 cooldown 设置，OG 模式（以及 Cooldown 滑块）是死代码。
		MC.gameMode.destroyDelay = cooldown.getValueI();
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

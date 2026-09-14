/*
 * Copyright (c) 2014-2025 Wurst-Imperium and contributors.
 *
 * This source code is subject to the terms of the GNU General Public
 * License, version 3. If a copy of the GPL was not distributed with this
 * file, You can obtain one at: https://www.gnu.org/licenses/gpl-3.0.txt
 */
package net.wurstclient.hacks;

import java.util.Random;
import net.minecraft.world.entity.player.PlayerModelPart;
import net.wurstclient.Category;
import net.wurstclient.SearchTags;
import net.wurstclient.events.UpdateListener;
import net.wurstclient.hack.Hack;
import net.wurstclient.settings.EnumSetting;
import net.wurstclient.settings.SliderSetting;
import net.wurstclient.settings.SliderSetting.ValueDisplay;

@SearchTags({"SpookySkin", "spooky skin", "SkinBlinker", "skin blinker"})
public final class SkinDerpHack extends Hack implements UpdateListener
{
	/** 参考实现（misc/SkinBlinker.kt:42-50）里的横向闪烁顺序。 */
	private static final PlayerModelPart[] HORIZONTAL_ORDER = {
		PlayerModelPart.LEFT_SLEEVE, PlayerModelPart.LEFT_PANTS_LEG,
		PlayerModelPart.JACKET, PlayerModelPart.HAT, PlayerModelPart.CAPE,
		PlayerModelPart.RIGHT_PANTS_LEG, PlayerModelPart.RIGHT_SLEEVE};
	
	/** 参考实现（misc/SkinBlinker.kt:51-59）里的纵向闪烁顺序。 */
	private static final PlayerModelPart[] VERTICAL_ORDER = {
		PlayerModelPart.HAT, PlayerModelPart.JACKET, PlayerModelPart.CAPE,
		PlayerModelPart.LEFT_SLEEVE, PlayerModelPart.RIGHT_SLEEVE,
		PlayerModelPart.LEFT_PANTS_LEG, PlayerModelPart.RIGHT_PANTS_LEG};
	
	private final EnumSetting<Mode> mode = new EnumSetting<>("Mode",
		"\u00a7lAll\u00a7r - 一次切换全部皮肤层（旧版行为）。\n"
			+ "\u00a7lHorizontal\u00a7r - 按横向顺序逐层闪烁。\n"
			+ "\u00a7lVertical\u00a7r - 按纵向顺序逐层闪烁。\n"
			+ "\u00a7lRandom\u00a7r - 每次随机切换一层。",
		Mode.values(), Mode.ALL);
	
	private final SliderSetting delay = new SliderSetting("Delay",
		"Two toggles are this many ticks apart.", 4, 1, 20, 1,
		ValueDisplay.INTEGER.withSuffix(" ticks"));
	
	private final Random random = new Random();
	private int timer;
	private int index;
	
	public SkinDerpHack()
	{
		super("SkinDerp");
		setCategory(Category.FUN);
		addSetting(mode);
		addSetting(delay);
	}
	
	@Override
	protected void onEnable()
	{
		timer = 0;
		index = 0;
		EVENTS.add(UpdateListener.class, this);
	}
	
	@Override
	protected void onDisable()
	{
		EVENTS.remove(UpdateListener.class, this);
		
		for(PlayerModelPart part : PlayerModelPart.values())
			MC.options.toggleModelPart(part, true);
	}
	
	@Override
	public void onUpdate()
	{
		if(++timer < delay.getValueI())
			return;
		
		timer = 0;
		
		// 旧实现相当于"每约 4 tick 把所有层一起切换一次"，
		// 保留为默认的 All 模式。
		if(mode.getSelected() == Mode.ALL)
		{
			for(PlayerModelPart part : PlayerModelPart.values())
				toggle(part);
			return;
		}
		
		if(mode.getSelected() == Mode.RANDOM)
		{
			PlayerModelPart[] parts = PlayerModelPart.values();
			toggle(parts[random.nextInt(parts.length)]);
			return;
		}
		
		PlayerModelPart[] order =
			mode.getSelected() == Mode.HORIZONTAL ? HORIZONTAL_ORDER
				: VERTICAL_ORDER;
		toggle(order[index]);
		index = (index + 1) % order.length;
	}
	
	private void toggle(PlayerModelPart part)
	{
		MC.options.toggleModelPart(part,
			!MC.options.isModelPartEnabled(part));
	}
	
	private enum Mode
	{
		ALL("All"),
		HORIZONTAL("Horizontal"),
		VERTICAL("Vertical"),
		RANDOM("Random");
		
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

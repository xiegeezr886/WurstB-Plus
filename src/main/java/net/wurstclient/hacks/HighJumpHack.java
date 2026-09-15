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
import net.wurstclient.events.JumpPowerListener;
import net.wurstclient.events.JumpPowerListener.JumpPowerEvent;
import net.wurstclient.hack.Hack;
import net.wurstclient.settings.SliderSetting;
import net.wurstclient.settings.SliderSetting.ValueDisplay;
import net.wurstclient.util.JumpHeightSolver;

@SearchTags({"high jump"})
public final class HighJumpHack extends Hack implements JumpPowerListener
{
	private final SliderSetting height = new SliderSetting("Height",
		"Jump height in blocks.\n"
			+ "Solved against vanilla jump physics, so it stays accurate\n"
			+ "at higher values as well.",
		6, 1, 100, 1, ValueDisplay.INTEGER);
	
	public HighJumpHack()
	{
		super("HighJump");
		
		setCategory(Category.MOVEMENT);
		addSetting(height);
	}
	
	@Override
	protected void onEnable()
	{
		EVENTS.add(JumpPowerListener.class, this);
	}
	
	@Override
	protected void onDisable()
	{
		EVENTS.remove(JumpPowerListener.class, this);
	}
	
	/**
	 * 把这次跳跃的跳跃力改成"最高点正好等于 {@code Height} 格"所需的初速。
	 *
	 * <p>
	 * 基准值来自事件（{@code ClientPlayerEntityMixin#getJumpPower()} 传的是
	 * {@code super.getJumpPower()}），这样跳跃提升、蜂蜜块之类改变基础跳跃力的
	 * 因素不会被重复叠加。
	 */
	@Override
	public void onJumpPower(JumpPowerEvent event)
	{
		if(!isEnabled())
			return;
		
		event.setJumpPower(
			(float)JumpHeightSolver.requiredVelocity(height.getValueF()));
	}
}

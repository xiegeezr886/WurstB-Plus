/*
 * Copyright (c) 2014-2025 Wurst-Imperium and contributors.
 *
 * This source code is subject to the terms of the GNU General Public
 * License, version 3. If a copy of the GPL was not distributed with this
 * file, You can obtain one at: https://www.gnu.org/licenses/gpl-3.0.txt
 */
package net.wurstclient.hacks;

import net.minecraft.client.player.LocalPlayer;
import net.wurstclient.Category;
import net.wurstclient.events.AutoJumpListener;
import net.wurstclient.events.AutoJumpListener.AutoJumpEvent;
import net.wurstclient.events.UpdateListener;
import net.wurstclient.hacks.step.LegitStepMode;
import net.wurstclient.hacks.step.SimpleStepMode;
import net.wurstclient.hacks.step.StepMode;
import net.wurstclient.hack.Hack;
import net.wurstclient.settings.EnumSetting;
import net.wurstclient.settings.SliderSetting;
import net.wurstclient.settings.SliderSetting.ValueDisplay;

public final class StepHack extends Hack
	implements UpdateListener, AutoJumpListener
{
	private final EnumSetting<Mode> mode = new EnumSetting<>("Mode",
		"\u00a7lSimple\u00a7r mode can step up multiple blocks (enables Height slider).\n"
			+ "\u00a7lLegit\u00a7r mode can bypass NoCheat+.",
		Mode.values(), Mode.LEGIT);
	
	private final SliderSetting height =
		new SliderSetting("Height", "Only works in \u00a7lSimple\u00a7r mode.",
			1, 1, 5, 1, ValueDisplay.INTEGER);

	private float previousStepHeight = 0.6F;
	private int stepCooldown;
	private LocalPlayer trackedPlayer;
	
	public StepHack()
	{
		super("Step");
		setCategory(Category.MOVEMENT);
		addSetting(mode);
		addSetting(height);
	}
	
	@Override
	protected void onEnable()
	{
		trackedPlayer = null;
		trackPlayer(MC.player);
		stepCooldown = 0;
		EVENTS.add(UpdateListener.class, this);
		EVENTS.add(AutoJumpListener.class, this);
	}
	
	@Override
	protected void onDisable()
	{
		EVENTS.remove(UpdateListener.class, this);
		EVENTS.remove(AutoJumpListener.class, this);
		restoreTrackedPlayer();
	}
	
	@Override
	public void onUpdate()
	{
		LocalPlayer player = MC.player;
		if(player == null || MC.level == null)
			return;
		trackPlayer(player);
		if(stepCooldown > 0)
			stepCooldown--;

		// 具体做法交给模式类（见 net.wurstclient.hacks.step）。
		mode.getSelected().impl().onUpdate(this, player);
	}

	/** 供 Simple 模式读取 Height 设置。 */
	public float getHeight()
	{
		return height.getValueF();
	}

	/** 供 Legit 模式恢复/读取玩家原本的 maxUpStep。 */
	public float getPreviousStepHeight()
	{
		return previousStepHeight;
	}

	/** 供 Legit 模式读取当前冷却。 */
	public int getStepCooldown()
	{
		return stepCooldown;
	}

	/** 供 Legit 模式设置冷却。 */
	public void setStepCooldown(int stepCooldown)
	{
		this.stepCooldown = stepCooldown;
	}

	private void trackPlayer(LocalPlayer player)
	{
		if(player == null || player == trackedPlayer)
			return;

		restoreTrackedPlayer();
		trackedPlayer = player;
		previousStepHeight = player.maxUpStep;
	}

	private void restoreTrackedPlayer()
	{
		if(trackedPlayer != null)
			trackedPlayer.maxUpStep = previousStepHeight;
		trackedPlayer = null;
	}
	
	/**
	 * Step 启用时（以及 {@code .goto} 正在跑时）关掉原版的自动跳跃 —— 与原来的
	 * {@code ClientPlayerEntityMixin} 用的是同一个判断。
	 */
	@Override
	public void onAutoJump(AutoJumpEvent event)
	{
		if(!isAutoJumpAllowed())
			event.setAutoJumpAllowed(false);
	}
	
	public boolean isAutoJumpAllowed()
	{
		return !isEnabled() && !WURST.getCmds().goToCmd.isActive();
	}
	
	private enum Mode
	{
		SIMPLE("Simple", new SimpleStepMode()),
		LEGIT("Legit", new LegitStepMode());
		
		private final String name;
		private final StepMode impl;
		
		private Mode(String name, StepMode impl)
		{
			this.name = name;
			this.impl = impl;
		}
		
		public StepMode impl()
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

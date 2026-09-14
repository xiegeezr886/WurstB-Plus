/*
 * Copyright (c) 2025 Penguin
 *
 * This source code is subject to the terms of the GNU General Public
 * License, version 3. If a copy of the GPL was not distributed with this
 * file, You can obtain one at: https://www.gnu.org/licenses/gpl-3.0.txt
 */
package net.wurstclient.hacks;

import net.minecraft.util.Mth;
import net.wurstclient.Category;
import net.wurstclient.SearchTags;
import net.wurstclient.events.UpdateListener;
import net.wurstclient.hack.Hack;
import net.wurstclient.settings.SliderSetting;
import net.wurstclient.settings.CheckboxSetting;

@SearchTags({"dank", "bobbing", "view", "camera"})
public final class DankBobbingHack extends Hack implements UpdateListener
{
	private final SliderSetting intensity =
		new SliderSetting("Intensity", 1, 0, 5, 0.1, SliderSetting.ValueDisplay.DECIMAL);
	private final CheckboxSetting noViewBob =
		new CheckboxSetting("No View Bob", false);

	/** 我们关掉"视角摇晃"之前玩家的原始设置，用于还原。 */
	private Boolean savedViewBob;

	public DankBobbingHack()
	{
		super("DankBobbing");
		setCategory(Category.FUN);
		addSetting(intensity);
		addSetting(noViewBob);
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
		restoreViewBobOption();
	}

	@Override
	public void onUpdate()
	{
		updateViewBobOption();

		if(!MC.player.onGround())
			return;

		float speed = MC.player.getSpeed() * 10;
		float bob = Mth.sin(MC.player.tickCount * 0.5F)
			* speed * intensity.getValueF();
		MC.player.walkDistO = MC.player.walkDist + bob;
	}

	/**
	 * 按勾选状态开关原版的"视角摇晃"选项，并记住原值。
	 *
	 * <p>
	 * 旧实现只调用 {@code MC.options.bobView().set(false)}，而且**从不还原**：
	 * 关掉 DankBobbing（甚至只是取消勾选 No View Bob）之后，你的视频设置里
	 * "视角摇晃"仍然是关闭状态，而且这个改动会被写进 options.txt 持久保留。
	 */
	private void updateViewBobOption()
	{
		if(shouldDisableViewBob())
		{
			if(savedViewBob == null)
			{
				savedViewBob = MC.options.bobView().get();
				MC.options.bobView().set(false);
			}
			return;
		}

		restoreViewBobOption();
	}

	private void restoreViewBobOption()
	{
		if(savedViewBob == null)
			return;

		MC.options.bobView().set(savedViewBob);
		savedViewBob = null;
	}

	public boolean shouldDisableViewBob()
	{
		return isEnabled() && noViewBob.isChecked();
	}
}

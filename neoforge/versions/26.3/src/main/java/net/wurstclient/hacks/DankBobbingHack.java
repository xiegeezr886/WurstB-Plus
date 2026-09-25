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
	}

	@Override
	public void onUpdate()
	{

		if(noViewBob.isChecked())
			MC.options.bobView().set(false);
	}


	/**
	 * Whether this hack should drive the view bob phase. Writing walkDistO
	 * directly worked while it was a public AbstractClientPlayer field; from
	 * 1.21.9 the walk distance lives in ClientAvatarState (1.21.9-1.21.11)
	 * and in the camera render state (26.1+), so the mixins reading those
	 * ask us for the value instead.
	 */
	public boolean shouldOverrideWalkDistance()
	{
		return isEnabled() && MC.player != null && MC.player.onGround();
	}

	/**
	 * The walk distance the hack wants the bob phase to use, in the same
	 * units as the old walkDistO - walkDist relationship.
	 */
	public float getForcedWalkDistance()
	{
		float speed = MC.player.getSpeed() * 10;
		return Mth.sin(MC.player.tickCount * 0.5F) * speed
			* intensity.getValueF();
	}
	public boolean shouldDisableViewBob()
	{
		return isEnabled() && noViewBob.isChecked();
	}
}

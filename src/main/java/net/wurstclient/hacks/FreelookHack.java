/*
 * Copyright (c) 2014-2026 Wurst-Imperium and contributors.
 *
 * This source code is subject to the terms of the GNU Affero General Public
 * License, version 3. If a copy of the AGPL was not distributed with this
 * file, You can obtain one at: https://www.gnu.org/licenses/agpl-3.0.txt
 */
package net.wurstclient.hacks;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;

import net.minecraft.util.Mth;
import net.wurstclient.Category;
import net.wurstclient.SearchTags;
import net.wurstclient.events.UpdateListener;
import net.wurstclient.hack.Hack;
import net.wurstclient.settings.CheckboxSetting;
import net.wurstclient.settings.SliderSetting;
import net.wurstclient.settings.SliderSetting.ValueDisplay;

@SearchTags({"free look", "freelook", "free camera", "第三人称"})
public final class FreelookHack extends Hack implements UpdateListener
{
	private final SliderSetting sensitivity = new SliderSetting("Sensitivity",
		"Mouse sensitivity multiplier while freelooking.", 1, 0.1, 3, 0.05,
		ValueDisplay.DECIMAL);

	private final CheckboxSetting invertY =
		new CheckboxSetting("Invert Y", "Invert vertical freelook movement.",
			false);

	private float yawOffset;
	private float pitchOffset;
	private float lastYaw;
	private float lastPitch;
	private boolean initialized;

	public FreelookHack()
	{
		super("Freelook");
		setCategory(Category.RENDER);
		addSetting(sensitivity);
		addSetting(invertY);
	}

	@Override
	protected void onEnable()
	{
		yawOffset = 0;
		pitchOffset = 0;
		initialized = false;
		EVENTS.add(UpdateListener.class, this);
	}

	@Override
	protected void onDisable()
	{
		EVENTS.remove(UpdateListener.class, this);
		yawOffset = 0;
		pitchOffset = 0;
		initialized = false;
	}

	@Override
	public void onUpdate()
	{
		if(MC.player == null)
			return;

		float yaw = MC.player.getYRot();
		float pitch = MC.player.getXRot();
		if(!initialized)
		{
			lastYaw = yaw;
			lastPitch = pitch;
			initialized = true;
			return;
		}

		float dy = Mth.wrapDegrees(yaw - lastYaw);
		float dp = pitch - lastPitch;
		float s = (float)sensitivity.getValue();
		yawOffset = Mth.wrapDegrees(yawOffset + dy * s);
		pitchOffset = Mth.clamp(pitchOffset + dp * s * (invertY.isChecked() ? -1 : 1),
			-90, 90);

		// keep the player's body facing the original direction
		MC.player.setYRot(lastYaw);
		MC.player.setXRot(lastPitch);
	}

	public void applyCameraRotation(PoseStack matrices)
	{
		if(!isEnabled())
			return;
		matrices.mulPose(Axis.YP.rotationDegrees(-yawOffset));
		matrices.mulPose(Axis.XP.rotationDegrees(pitchOffset));
	}
}

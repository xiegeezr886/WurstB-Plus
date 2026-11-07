/*
 * Copyright (c) 2014-2025 Wurst-Imperium and contributors.
 *
 * This source code is subject to the terms of the GNU General Public
 * License, version 3. If a copy of the GPL was not distributed with this
 * file, You can obtain one at: https://www.gnu.org/licenses/gpl-3.0.txt
 */
/*
 * Ported from Meteor Client (GPL-3.0).
 */
package net.wurstclient.hacks;

import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.wurstclient.Category;
import net.wurstclient.SearchTags;
import net.wurstclient.events.UpdateListener;
import net.wurstclient.hack.Hack;
import net.wurstclient.settings.CheckboxSetting;
import net.wurstclient.settings.SliderSetting;
import net.wurstclient.settings.SliderSetting.ValueDisplay;

@SearchTags({"reverse step", "fast fall", "fastfall"})
public final class ReverseStepHack extends Hack implements UpdateListener
{
	private final SliderSetting fallSpeed = new SliderSetting("Fall speed",
		"Downward velocity applied when stepping down.", 1, 0.1, 5, 0.1,
		ValueDisplay.DECIMAL);

	private final SliderSetting height = new SliderSetting("Height",
		"Maximum distance to scan below for ground.", 3, 0.5, 10, 0.5,
		ValueDisplay.DECIMAL.withSuffix("m"));

	private final CheckboxSetting onlyWhileSneaking = new CheckboxSetting(
		"Only while sneaking", "Only step down while sneaking.", false);

	public ReverseStepHack()
	{
		super("ReverseStep");
		setCategory(Category.MOVEMENT);
		addSetting(fallSpeed);
		addSetting(height);
		addSetting(onlyWhileSneaking);
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
		LocalPlayer player = MC.player;
		if(player == null || MC.level == null)
			return;

		if(player.isSpectator() || player.isPassenger()
			|| player.isFallFlying() || !player.onGround()
			|| player.isInWater() || player.isInLava()
			|| player.onClimbable() || player.noPhysics)
			return;

		if(MC.options.keyJump.isDown())
			return;

		if(onlyWhileSneaking.isChecked() && !player.isShiftKeyDown())
			return;

		// sneaking always counts as input, otherwise require movement
		if(!player.isShiftKeyDown() && player.zza == 0 && player.xxa == 0)
			return;

		// there must be ground within the configured height below us
		AABB box = player.getBoundingBox()
			.move(0, -(height.getValue() + 0.01), 0);

		if(MC.level.noCollision(player, box))
			return;

		Vec3 velocity = player.getDeltaMovement();
		player.setDeltaMovement(velocity.x, -fallSpeed.getValue(), velocity.z);
	}
}

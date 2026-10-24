/*
 * Copyright (c) 2014-2026 Wurst-Imperium and contributors.
 *
 * This source code is subject to the terms of the GNU General Public
 * License, version 3. If a copy of the GPL was not distributed with this
 * file, You can obtain one at: https://www.gnu.org/licenses/gpl-3.0.txt
 */
// Ported from Meteor Client (GPL-3.0).
package net.wurstclient.hacks;

import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.animal.horse.AbstractHorse;
import net.minecraft.world.entity.vehicle.AbstractBoat;
import net.minecraft.world.entity.vehicle.AbstractMinecart;
import net.minecraft.world.item.SpawnEggItem;
import net.wurstclient.Category;
import net.wurstclient.SearchTags;
import net.wurstclient.events.UpdateListener;
import net.wurstclient.hack.Hack;
import net.wurstclient.settings.CheckboxSetting;
import net.wurstclient.settings.SliderSetting;
import net.wurstclient.settings.SliderSetting.ValueDisplay;
import net.wurstclient.util.Rotation;
import net.wurstclient.util.RotationUtils;

@SearchTags({"auto mount", "mount", "ride", "horse", "boat", "minecart"})
public final class AutoMountHack extends Hack implements UpdateListener
{
	private final CheckboxSetting horses = new CheckboxSetting("Horses",
		"Mount horses, donkeys, mules and llamas.", true);

	private final CheckboxSetting boats = new CheckboxSetting("Boats",
		"Mount boats and rafts.", true);

	private final CheckboxSetting minecarts =
		new CheckboxSetting("Minecarts", "Mount minecarts.", true);

	private final CheckboxSetting checkSaddle = new CheckboxSetting(
		"Check saddle",
		"Only mounts horses that already have a saddle equipped.", false);

	private final SliderSetting range = new SliderSetting("Range",
		"How far away an entity can be to be mounted.", 4, 1, 6, 0.25,
		ValueDisplay.DECIMAL.withSuffix(" blocks"));

	private final CheckboxSetting rotate = new CheckboxSetting("Rotate",
		"Faces the entity before mounting it.", true);

	private int cooldown;

	public AutoMountHack()
	{
		super("AutoMount");
		setCategory(Category.OTHER);
		addSetting(horses);
		addSetting(boats);
		addSetting(minecarts);
		addSetting(checkSaddle);
		addSetting(range);
		addSetting(rotate);
	}

	@Override
	protected void onEnable()
	{
		cooldown = 0;
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
		if(MC.player == null || MC.level == null || MC.gameMode == null)
			return;

		if(cooldown > 0)
		{
			cooldown--;
			return;
		}

		if(MC.screen != null || MC.player.isPassenger()
			|| MC.player.isShiftKeyDown()
			|| MC.player.getMainHandItem().getItem() instanceof SpawnEggItem)
			return;

		Entity closest = findClosestEntity();
		if(closest == null)
			return;

		if(rotate.isChecked())
		{
			Rotation rotation = RotationUtils
				.getNeededRotations(closest.getBoundingBox().getCenter());
			rotation.applyToClientPlayer();

			if(MC.getConnection() != null)
				rotation.sendPlayerLookPacket();
		}

		MC.gameMode.interact(MC.player, closest, InteractionHand.MAIN_HAND);
		cooldown = 4;
	}

	private Entity findClosestEntity()
	{
		Entity closest = null;
		double closestDistance = range.getValueSq();

		for(Entity entity : MC.level.entitiesForRendering())
		{
			if(entity == MC.player || entity.isRemoved()
				|| !isConfigured(entity))
				continue;

			double distance = MC.player.distanceToSqr(entity);
			if(distance > closestDistance)
				continue;

			closest = entity;
			closestDistance = distance;
		}

		return closest;
	}

	private boolean isConfigured(Entity entity)
	{
		if(entity instanceof AbstractHorse horse)
		{
			if(!horses.isChecked())
				return false;

			return !checkSaddle.isChecked() || horse.isSaddled();
		}

		if(entity instanceof AbstractBoat)
			return boats.isChecked();

		if(entity instanceof AbstractMinecart)
			return minecarts.isChecked();

		return false;
	}
}

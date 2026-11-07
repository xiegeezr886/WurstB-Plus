/*
 * Copyright (c) 2014-2026 Wurst-Imperium and contributors.
 *
 * This source code is subject to the terms of the GNU General Public
 * License, version 3. If a copy of the GPL was not distributed with this
 * file, You can obtain one at: https://www.gnu.org/licenses/gpl-3.0.txt
 */
// Ported from Meteor Client (GPL-3.0).
package net.wurstclient.hacks;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EquipmentSlot;
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
import net.wurstclient.settings.EnumSetting;
import net.wurstclient.settings.TextFieldSetting;
import net.wurstclient.util.RegistryIdFilter;
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

	private final CheckboxSetting pigs = new CheckboxSetting("Pigs", "Mount saddled pigs.", false);
	private final CheckboxSetting striders = new CheckboxSetting("Striders", "Mount saddled striders.", false);
	private final EnumSetting<Selection> selection = new EnumSetting<>("Entity selection",
		"Use the groups above or an exact entity type list.", Selection.values(), Selection.GROUPS);
	private final TextFieldSetting entities = new TextFieldSetting("Entities",
		"Rideable entity type IDs, separated by commas.", "horse,donkey,mule,llama,trader_llama,minecart,boat");
	private final RegistryIdFilter entityFilter = new RegistryIdFilter();
	private enum Selection {GROUPS, SELECTED_TYPES}
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
		addSetting(pigs);
		addSetting(striders);
		addSetting(selection);
		addSetting(entities);
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
		String id = BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType()).toString();
		boolean pig = id.equals("minecraft:pig");
		boolean strider = id.equals("minecraft:strider");
		boolean horse = entity instanceof AbstractHorse;
		boolean boat = entity instanceof AbstractBoat;
		boolean cart = entity instanceof AbstractMinecart;
		boolean otherRideable = id.equals("minecraft:happy_ghast")
			|| id.equals("minecraft:nautilus") || id.equals("minecraft:zombie_nautilus");
		if(!horse && !boat && !cart && !pig && !strider && !otherRideable)
			return false;
		if(selection.getSelected() == Selection.SELECTED_TYPES)
		{
			if(!entityFilter.contains(entities.getValue(), id))
				return false;
		}
		else if(!(horse && horses.isChecked() || boat && boats.isChecked()
			|| cart && minecarts.isChecked() || pig && pigs.isChecked()
			|| strider && striders.isChecked()))
			return false;
		boolean llama = id.equals("minecraft:llama") || id.equals("minecraft:trader_llama");
		boolean mandatorySaddle = pig || strider || id.equals("minecraft:skeleton_horse")
			|| id.equals("minecraft:zombie_horse");
		if(mandatorySaddle
			|| checkSaddle.isChecked() && (horse || otherRideable) && !llama)
			return entity instanceof Mob mount && mount.isSaddled();
		return true;
	}
}

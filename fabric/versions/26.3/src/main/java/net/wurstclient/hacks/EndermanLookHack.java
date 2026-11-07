/*
 * Adapted from Meteor Client. Copyright (c) Meteor Development.
 * SPDX-License-Identifier: GPL-3.0
 */
package net.wurstclient.hacks;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.monster.Enderman;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.phys.Vec3;
import net.wurstclient.Category;
import net.wurstclient.SearchTags;
import net.wurstclient.events.UpdateListener;
import net.wurstclient.hack.Hack;
import net.wurstclient.settings.CheckboxSetting;
import net.wurstclient.settings.EnumSetting;
import net.wurstclient.util.Rotation;
import net.wurstclient.util.RotationQueue;
import net.wurstclient.util.RotationUtils;

@SearchTags({"enderman look", "末影人", "避免对视"})
public final class EndermanLookHack extends Hack implements UpdateListener
{
	private final EnumSetting<Mode> lookMode = new EnumSetting<>("Look mode",
		"避开末影人视线，或主动注视末影人。", Mode.values(), Mode.AWAY);
	private final CheckboxSetting stun = new CheckboxSetting("Stun hostiles",
		"在避让模式下注视已被激怒的末影人，使其停止移动。", true);
	private final RotationQueue rotations =
		new RotationQueue(RotationQueue.Priority.BACKGROUND);

	public EndermanLookHack()
	{
		super("EndermanLook");
		setCategory(Category.COMBAT);
		addSetting(lookMode);
		addSetting(stun);
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
		rotations.stop();
	}

	@Override
	public void onUpdate()
	{
		rotations.clear();
		if(MC.player == null || MC.level == null
			|| MC.player.getAbilities().instabuild
			|| MC.player.getItemBySlot(EquipmentSlot.HEAD).is(ItemTags.GAZE_DISGUISE_EQUIPMENT))
			return;

		for(Entity entity : MC.level.entitiesForRendering())
		{
			if(!(entity instanceof Enderman enderman) || !enderman.isAlive()
				|| !MC.player.hasLineOfSight(enderman))
				continue;

			if(lookMode.getSelected() == Mode.AT)
			{
				if(!enderman.isCreepy())
					lookAt(enderman);
			}else if(enderman.isCreepy() && stun.isChecked())
				lookAt(enderman);
			else if(isLookingAt(enderman))
				rotations.setRotation(new Rotation(MC.player.getYRot(), 90));
		}
	}

	private void lookAt(Enderman entity)
	{
		rotations.setRotation(RotationUtils.getNeededRotations(
			new Vec3(entity.getX(), entity.getEyeY(), entity.getZ())));
	}

	private boolean isLookingAt(Enderman entity)
	{
		Vec3 direction = entity.getEyePosition().subtract(MC.player.getEyePosition());
		double distance = direction.length();
		return distance > 0 && MC.player.getViewVector(1).normalize()
			.dot(direction.normalize()) > 1 - 0.025 / distance;
	}

	private enum Mode
	{
		AT, AWAY
	}
}

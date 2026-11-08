/*
 * Copyright (c) 2014-2026 Wurst-Imperium and contributors.
 * Adapted from Meteor Client. Copyright (c) Meteor Development.
 * SPDX-License-Identifier: GPL-3.0
 */
package net.wurstclient.hacks;

import net.minecraft.client.player.LocalPlayer;
import net.wurstclient.Category;
import net.wurstclient.SearchTags;
import net.wurstclient.hack.Hack;
import net.wurstclient.settings.CheckboxSetting;

@SearchTags({"no rotate", "norotate", "server rotation", "anti rotate"})
public final class NoRotateHack extends Hack
{
	private final CheckboxSetting preserveYaw = new CheckboxSetting(
		"Preserve yaw", "Keeps your yaw when the server rotates you.", true);
	private final CheckboxSetting preservePitch = new CheckboxSetting(
		"Preserve pitch", "Keeps your pitch when the server rotates you.", true);

	public NoRotateHack()
	{
		super("NoRotate");
		setCategory(Category.MOVEMENT);
		addSetting(preserveYaw);
		addSetting(preservePitch);
	}

	public SavedRotation capture()
	{
		LocalPlayer player = MC.player;
		if(!isEnabled() || player == null
			|| !preserveYaw.isChecked() && !preservePitch.isChecked())
			return null;
		return new SavedRotation(player, player.getYRot(), player.getXRot(),
			player.yRotO, player.xRotO, player.yHeadRot, player.yHeadRotO,
			player.yBodyRot, player.yBodyRotO);
	}

	public void restore(SavedRotation saved)
	{
		if(saved == null || !isEnabled() || MC.player != saved.player())
			return;
		LocalPlayer player = saved.player();
		if(preserveYaw.isChecked())
		{
			player.setYRot(saved.yaw());
			player.yRotO = saved.previousYaw();
			player.yHeadRot = saved.headYaw();
			player.yHeadRotO = saved.previousHeadYaw();
			player.yBodyRot = saved.bodyYaw();
			player.yBodyRotO = saved.previousBodyYaw();
		}
		if(preservePitch.isChecked())
		{
			player.setXRot(saved.pitch());
			player.xRotO = saved.previousPitch();
		}
	}

	public record SavedRotation(LocalPlayer player, float yaw, float pitch,
		float previousYaw, float previousPitch, float headYaw,
		float previousHeadYaw, float bodyYaw, float previousBodyYaw) {}
}

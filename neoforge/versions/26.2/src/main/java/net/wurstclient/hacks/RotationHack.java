/*
 * Copyright (c) 2014-2026 Wurst-Imperium and contributors.
 *
 * This source code is subject to the terms of the GNU General Public
 * License, version 3. If a copy of the GPL was not distributed with this
 * file, You can obtain one at: https://www.gnu.org/licenses/gpl-3.0.txt
 */
// Ported from Meteor Client (GPL-3.0).
package net.wurstclient.hacks;

import net.minecraft.util.Mth;
import net.wurstclient.Category;
import net.wurstclient.SearchTags;
import net.wurstclient.events.UpdateListener;
import net.wurstclient.hack.Hack;
import net.wurstclient.settings.CheckboxSetting;
import net.wurstclient.settings.EnumSetting;
import net.wurstclient.settings.SliderSetting;
import net.wurstclient.settings.SliderSetting.ValueDisplay;
import net.wurstclient.util.Rotation;

@SearchTags({"rotation lock", "yaw", "pitch", "look", "silent rotation"})
public final class RotationHack extends Hack implements UpdateListener
{
	private final EnumSetting<LockMode> yawMode =
		new EnumSetting<>("Yaw lock mode", LockMode.values(), LockMode.SIMPLE);

	private final SliderSetting yawAngle = new SliderSetting("Yaw angle",
		"Yaw angle in degrees, used by the Simple mode.", 0, 0, 360, 1,
		ValueDisplay.DEGREES);

	private final EnumSetting<LockMode> pitchMode = new EnumSetting<>(
		"Pitch lock mode", LockMode.values(), LockMode.SIMPLE);

	private final SliderSetting pitchAngle = new SliderSetting("Pitch angle",
		"Pitch angle in degrees, used by the Simple mode.", 0, -90, 90, 1,
		ValueDisplay.DEGREES);

	private final CheckboxSetting silent = new CheckboxSetting("Silent",
		"Only sends the rotation to the server, without changing your visible"
			+ " rotation.",
		false);

	public RotationHack()
	{
		super("Rotation");
		setCategory(Category.OTHER);
		addSetting(yawMode);
		addSetting(yawAngle);
		addSetting(pitchMode);
		addSetting(pitchAngle);
		addSetting(silent);
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
		if(MC.player == null || MC.getConnection() == null)
			return;

		float yaw = getYaw();
		float pitch = getPitch();
		Rotation rotation = Rotation.wrapped(yaw, pitch);

		if(silent.isChecked())
			rotation.sendPlayerLookPacket();
		else
		{
			rotation.applyToClientPlayer();
			rotation.sendPlayerLookPacket();
		}
	}

	private float getYaw()
	{
		return switch(yawMode.getSelected())
		{
			case OFF -> MC.player.getYRot();
			case SIMPLE -> yawAngle.getValueF();
			case SMART -> Mth.wrapDegrees(
				Math.round((MC.player.getYRot() + 1F) / 45F) * 45F);
		};
	}

	private float getPitch()
	{
		float pitch = switch(pitchMode.getSelected())
		{
			case OFF -> MC.player.getXRot();
			case SIMPLE -> pitchAngle.getValueF();
			case SMART -> Math.round((MC.player.getXRot() + 1F) / 30F) * 30F;
		};

		return Mth.clamp(pitch, -90, 90);
	}

	private enum LockMode
	{
		OFF("Off"),
		SIMPLE("Simple"),
		SMART("Smart");

		private final String name;

		private LockMode(String name)
		{
			this.name = name;
		}

		@Override
		public String toString()
		{
			return name;
		}
	}
}

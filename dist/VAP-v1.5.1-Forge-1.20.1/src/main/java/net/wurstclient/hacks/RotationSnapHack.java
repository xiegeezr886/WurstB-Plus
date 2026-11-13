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
import net.wurstclient.settings.CheckboxSetting;
import net.wurstclient.settings.EnumSetting;

@SearchTags({"rotation snap", "rotationsnap", "snap rotation"})
public final class RotationSnapHack extends Hack implements UpdateListener
{
	private final CheckboxSetting snapYaw = new CheckboxSetting("Snap Yaw",
		"Snaps your yaw to fixed angle intervals.", true);

	private final EnumSetting<Interval> yawInterval = new EnumSetting<>(
		"Yaw interval", "Angle interval for yaw snapping.",
		Interval.values(), Interval.D45);

	private final CheckboxSetting snapPitch = new CheckboxSetting(
		"Snap Pitch", "Snaps your pitch to fixed angle intervals.", false);

	private final EnumSetting<Interval> pitchInterval = new EnumSetting<>(
		"Pitch interval", "Angle interval for pitch snapping.",
		Interval.values(), Interval.D45);

	public RotationSnapHack()
	{
		super("RotationSnap");
		setCategory(Category.RENDER);
		addSetting(snapYaw);
		addSetting(yawInterval);
		addSetting(snapPitch);
		addSetting(pitchInterval);
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
		if(snapYaw.isChecked())
		{
			int interval = yawInterval.getSelected().angle;
			int yaw = (int)MC.player.getYRot();
			int remainder =
				Math.floorMod(yaw, interval);
			int snapped = yaw
				+ (remainder < interval / 2 ? -remainder
					: interval - remainder);
			MC.player.setYRot(snapped);
		}

		if(snapPitch.isChecked())
		{
			int interval = pitchInterval.getSelected().angle;
			int pitch = (int)MC.player.getXRot();
			int remainder =
				Math.floorMod(pitch, interval);
			int snapped = pitch
				+ (remainder < interval / 2 ? -remainder
					: interval - remainder);
			MC.player.setXRot(
				Mth.clamp(snapped, -90, 90));
		}
	}

	private enum Interval
	{
		D15("15\u00b0", 15),
		D30("30\u00b0", 30),
		D45("45\u00b0", 45),
		D90("90\u00b0", 90);

		final int angle;

		Interval(String name, int angle)
		{
			this.angle = angle;
		}

		@Override
		public String toString()
		{
			return name();
		}
	}
}

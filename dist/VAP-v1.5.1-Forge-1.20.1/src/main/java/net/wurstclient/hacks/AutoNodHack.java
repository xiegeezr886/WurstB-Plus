/*
 * Copyright (c) 2014-2026 Wurst-Imperium and contributors.
 *
 * This source code is subject to the terms of the GNU Affero General Public
 * License, version 3. If a copy of the AGPL was not distributed with this
 * file, You can obtain one at: https://www.gnu.org/licenses/agpl-3.0.txt
 */
// Ported from InvincibleMachineGun (GPL-3.0).
package net.wurstclient.hacks;

import java.util.Random;

import net.minecraft.client.CameraType;
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

@SearchTags({"auto nod", "nod", "head nod", "head shake", "sleepy"})
public final class AutoNodHack extends Hack implements UpdateListener
{
	private final EnumSetting<Mode> mode =
		new EnumSetting<>("Mode", Mode.values(), Mode.SLEEPY);

	private final EnumSetting<Visibility> visibility = new EnumSetting<>(
		"Visibility", Visibility.values(), Visibility.SMART);

	private final SliderSetting speed = new SliderSetting("Speed",
		"How fast the animation advances.", 3, 0.1, 100, 0.1,
		ValueDisplay.DECIMAL);

	private final SliderSetting angle = new SliderSetting("Angle",
		"Maximum rotation offset in degrees.", 50, 1, 360, 1,
		ValueDisplay.DEGREES);

	private final SliderSetting sleepWait = new SliderSetting("Sleep wait",
		"Fraction of the sleepy cycle spent holding the head down.", 0.3, 0,
		0.8, 0.05, ValueDisplay.DECIMAL)
			.visibleWhen(() -> mode.getSelected() == Mode.SLEEPY);

	private final SliderSetting wakeUpSpeed = new SliderSetting("Wake-up speed",
		"How quickly the head returns during the wake-up phase.", 20, 1, 50, 1,
		ValueDisplay.DECIMAL)
			.visibleWhen(() -> mode.getSelected() == Mode.SLEEPY);

	private final CheckboxSetting resetRotation = new CheckboxSetting(
		"Reset rotation",
		"Restores the original rotation when the hack is disabled.", true);

	private double timer;
	private final Random random = new Random();
	private float lastAddedYaw;
	private float lastAddedPitch;

	public AutoNodHack()
	{
		super("AutoNod");
		setCategory(Category.FUN);
		addSetting(mode);
		addSetting(visibility);
		addSetting(speed);
		addSetting(angle);
		addSetting(sleepWait);
		addSetting(wakeUpSpeed);
		addSetting(resetRotation);
	}

	@Override
	protected void onEnable()
	{
		timer = 0;
		lastAddedYaw = 0;
		lastAddedPitch = 0;
		EVENTS.add(UpdateListener.class, this);
	}

	@Override
	protected void onDisable()
	{
		EVENTS.remove(UpdateListener.class, this);

		if(resetRotation.isChecked() && MC.player != null)
		{
			MC.player.setYRot(MC.player.getYRot() - lastAddedYaw);
			MC.player.setXRot(MC.player.getXRot() - lastAddedPitch);
		}

		lastAddedYaw = 0;
		lastAddedPitch = 0;
	}

	@Override
	public void onUpdate()
	{
		if(MC.player == null)
			return;

		// Undo the previous tick's offset so that the animation is always
		// relative to the mouse-controlled rotation.
		MC.player.setYRot(MC.player.getYRot() - lastAddedYaw);
		MC.player.setXRot(MC.player.getXRot() - lastAddedPitch);
		lastAddedYaw = 0;
		lastAddedPitch = 0;

		timer += speed.getValue() * 0.05;
		double range = angle.getValue();

		float offsetX = 0;
		float offsetY = 0;

		switch(mode.getSelected())
		{
			case NOD -> offsetY = (float)(Math.sin(timer) * range);
			case SHAKE -> offsetX = (float)(Math.sin(timer) * range);
			case CIRCULAR ->
			{
				offsetX = (float)(Math.cos(timer) * range);
				offsetY = (float)(Math.sin(timer) * range);
			}
			case DIAGONAL ->
			{
				offsetX = (float)(Math.sin(timer) * range);
				offsetY = (float)(Math.sin(timer) * range);
			}
			case RANDOM ->
			{
				if((int)(timer * 10) % 2 == 0)
				{
					offsetX = (float)((random.nextDouble() - 0.5) * range);
					offsetY = (float)((random.nextDouble() - 0.5) * range);
				}
			}
			case SLEEPY ->
			{
				double cycle = 10;
				double t = (timer % cycle) / cycle;
				double wakeLen = 0.5 / wakeUpSpeed.getValue();
				double waitLen = sleepWait.getValue();

				if(waitLen + wakeLen > 0.95)
					waitLen = 0.95 - wakeLen;
				waitLen = Math.max(0, waitLen);

				double endDrop = 1 - wakeLen - waitLen;
				double startWake = 1 - wakeLen;

				if(t < endDrop)
				{
					double p = t / endDrop;
					offsetY = (float)(range * (p * p));
				}else if(t < startWake)
					offsetY = (float)range;
				else
				{
					double p = (t - startWake) / wakeLen;
					offsetY = (float)(range * (1 - p));
				}
			}
		}

		boolean usePacket = switch(visibility.getSelected())
		{
			case PACKET -> true;
			case CLIENT -> false;
			case SMART ->
				MC.options.getCameraType() == CameraType.FIRST_PERSON;
		};

		float realYaw = MC.player.getYRot();
		float realPitch = MC.player.getXRot();
		float targetYaw = realYaw + offsetX;
		float targetPitch = Mth.clamp(realPitch + offsetY, -90, 90);

		if(usePacket)
		{
			new Rotation(targetYaw, targetPitch).sendPlayerLookPacket();
			return;
		}

		MC.player.setYRot(targetYaw);
		MC.player.setXRot(targetPitch);
		lastAddedYaw = targetYaw - realYaw;
		lastAddedPitch = targetPitch - realPitch;
	}

	private enum Mode
	{
		NOD("Nod"),
		SHAKE("Shake"),
		CIRCULAR("Circular"),
		DIAGONAL("Diagonal"),
		RANDOM("Random"),
		SLEEPY("Sleepy");

		private final String name;

		private Mode(String name)
		{
			this.name = name;
		}

		@Override
		public String toString()
		{
			return name;
		}
	}

	private enum Visibility
	{
		PACKET("Packet"),
		CLIENT("Client"),
		SMART("Smart");

		private final String name;

		private Visibility(String name)
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

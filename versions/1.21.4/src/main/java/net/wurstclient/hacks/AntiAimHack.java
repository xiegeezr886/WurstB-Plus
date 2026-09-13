/*
 * Copyright (c) 2025 Penguin
 *
 * This source code is subject to the terms of the GNU General Public
 * License, version 3. If a copy of the GPL was not distributed with this
 * file, You can obtain one at: https://www.gnu.org/licenses/gpl-3.0.txt
 */
package net.wurstclient.hacks;

import java.util.Random;

import net.wurstclient.Category;
import net.wurstclient.SearchTags;
import net.wurstclient.events.UpdateListener;
import net.wurstclient.hack.Hack;
import net.wurstclient.settings.EnumSetting;
import net.wurstclient.settings.SliderSetting;
import net.wurstclient.settings.SliderSetting.ValueDisplay;

@SearchTags({"anti aim", "spinbot", "headroll", "invert", "SpinBot"})
public final class AntiAimHack extends Hack implements UpdateListener
{
	private static final Random RANDOM = new Random();

	private final EnumSetting<Mode> mode = new EnumSetting<>("Mode",
		"\u00a7lSpin\u00a7r - Spins your head around continuously.\n"
			+ "\u00a7lJitter\u00a7r - Randomly jitters your head.\n"
			+ "\u00a7lInvert\u00a7r - Faces the opposite direction.\n"
			+ "\u00a7lDown\u00a7r - Stares at the ground.\n"
			+ "\u00a7lBackwards\u00a7r - Runs backwards while looking forward.",
		Mode.values(), Mode.SPIN);

	private final SliderSetting spinSpeed = new SliderSetting("Spin Speed",
		"Degrees per tick for Spin mode.", 10, 1, 180, 1,
		ValueDisplay.DEGREES.withSuffix("/tick"));

	private float yaw;
	private float pitch;

	public AntiAimHack()
	{
		super("AntiAim");
		setCategory(Category.FUN);
		addSetting(mode);
		addSetting(spinSpeed);
	}

	@Override
	public String getRenderName()
	{
		return getName() + " [" + mode.getSelected() + "]";
	}

	@Override
	protected void onEnable()
	{
		yaw = MC.player.getYRot();
		pitch = MC.player.getXRot();
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
		switch(mode.getSelected())
		{
			case SPIN:
			yaw += spinSpeed.getValueF();
			if(yaw > 360) yaw -= 360;
			MC.player.setYRot(yaw);
			MC.player.setYHeadRot(yaw);
			MC.player.yBodyRot = yaw;
			MC.player.yRotO = yaw;
			break;

			case JITTER:
			yaw += RANDOM.nextFloat() * 60 - 30;
			pitch += RANDOM.nextFloat() * 20 - 10;
			if(yaw > 360) yaw -= 360;
			if(yaw < 0) yaw += 360;
			pitch = (float)Math.max(-90, Math.min(90, pitch));
			MC.player.setYRot(yaw);
			MC.player.setYHeadRot(yaw);
			MC.player.yBodyRot = yaw;
			MC.player.setXRot(pitch);
			break;

			case INVERT:
			MC.player.setYRot(MC.player.getYRot() + 180);
			MC.player.setYHeadRot(MC.player.getYHeadRot() + 180);
			MC.player.yBodyRot = MC.player.getYRot();
			break;

			case DOWN:
			MC.player.setXRot(90);
			break;

			case BACKWARDS:
			MC.player.input.forwardImpulse =
				-MC.player.input.forwardImpulse;
			MC.player.input.leftImpulse =
				-MC.player.input.leftImpulse;
			break;
		}
	}

	private enum Mode
	{
		SPIN("Spin"),
		JITTER("Jitter"),
		INVERT("Invert"),
		DOWN("Down"),
		BACKWARDS("Backwards");

		private final String name;

		Mode(String name)
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

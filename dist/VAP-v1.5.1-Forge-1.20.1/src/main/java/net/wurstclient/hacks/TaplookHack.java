/*
 * Copyright (c) 2025 Soar Client contributors
 *
 * This source code is subject to the terms of the GNU Affero General Public
 * License, version 3. If a copy of the AGPL was not distributed with this
 * file, You can obtain one at: https://www.gnu.org/licenses/agpl-3.0.txt
 */
// Ported from Soar Client v7.1.2 (LGPL-3.0).
package net.wurstclient.hacks;

import org.lwjgl.glfw.GLFW;
import net.wurstclient.Category;
import net.wurstclient.SearchTags;
import net.wurstclient.events.KeyPressListener;
import net.wurstclient.hack.Hack;
import net.wurstclient.settings.EnumSetting;
import net.wurstclient.settings.SliderSetting;
import net.wurstclient.settings.SliderSetting.ValueDisplay;
import net.wurstclient.util.Rotation;

@SearchTags({"tap look", "look", "snap", "yaw", "pitch", "180"})
public final class TaplookHack extends Hack implements KeyPressListener
{
	private final EnumSetting<Preset> preset = new EnumSetting<>("Preset",
		"Which direction to snap the view to when the trigger key is pressed.",
		Preset.values(), Preset.BEHIND);

	private final SliderSetting yawAngle = new SliderSetting("Yaw angle",
		"Yaw angle used by the Custom preset.", 180, 0, 360, 1,
		ValueDisplay.DEGREES);

	private final SliderSetting pitchAngle = new SliderSetting("Pitch angle",
		"Pitch angle used by the Custom preset.", 0, -90, 90, 1,
		ValueDisplay.DEGREES);

	private final EnumSetting<TriggerKey> triggerKey =
		new EnumSetting<>("Trigger key",
			"Key that snaps the view to the selected preset.",
			TriggerKey.values(), TriggerKey.P);

	public TaplookHack()
	{
		super("Taplook");
		setCategory(Category.MOVEMENT);
		addSetting(preset);
		addSetting(yawAngle);
		addSetting(pitchAngle);
		addSetting(triggerKey);
	}

	@Override
	protected void onEnable()
	{
		EVENTS.add(KeyPressListener.class, this);
	}

	@Override
	protected void onDisable()
	{
		EVENTS.remove(KeyPressListener.class, this);
	}

	@Override
	public void onKeyPress(KeyPressEvent event)
	{
		if(event.getAction() != GLFW.GLFW_PRESS)
			return;
		if(MC.player == null || MC.screen != null || MC.getConnection() == null)
			return;

		if(!triggerKey.getSelected().matches(event))
			return;

		float currentYaw = MC.player.getYRot();

		float yaw = switch(preset.getSelected())
		{
			case BEHIND -> currentYaw + 180F;
			case UP, DOWN -> currentYaw;
			case CUSTOM -> yawAngle.getValueF();
		};

		float pitch = switch(preset.getSelected())
		{
			case BEHIND -> 0;
			case UP -> -90;
			case DOWN -> 90;
			case CUSTOM -> pitchAngle.getValueF();
		};

		Rotation rotation = Rotation.wrapped(yaw, pitch);
		rotation.applyToClientPlayer();
		rotation.sendPlayerLookPacket();
	}

	private enum Preset
	{
		BEHIND("Behind (180\u00b0)"),
		UP("Up (-90\u00b0)"),
		DOWN("Down (90\u00b0)"),
		CUSTOM("Custom");

		private final String name;

		private Preset(String name)
		{
			this.name = name;
		}

		@Override
		public String toString()
		{
			return name;
		}
	}

	private enum TriggerKey
	{
		NONE(-1),
		R(GLFW.GLFW_KEY_R),
		G(GLFW.GLFW_KEY_G),
		X(GLFW.GLFW_KEY_X),
		C(GLFW.GLFW_KEY_C),
		V(GLFW.GLFW_KEY_V),
		B(GLFW.GLFW_KEY_B),
		F(GLFW.GLFW_KEY_F),
		H(GLFW.GLFW_KEY_H),
		J(GLFW.GLFW_KEY_J),
		K(GLFW.GLFW_KEY_K),
		L(GLFW.GLFW_KEY_L),
		P(GLFW.GLFW_KEY_P),
		Z(GLFW.GLFW_KEY_Z),
		LEFT_SHIFT(GLFW.GLFW_KEY_LEFT_SHIFT),
		LEFT_CONTROL(GLFW.GLFW_KEY_LEFT_CONTROL),
		LEFT_ALT(GLFW.GLFW_KEY_LEFT_ALT);

		private final int keyCode;

		private TriggerKey(int keyCode)
		{
			this.keyCode = keyCode;
		}

		private boolean matches(KeyPressEvent event)
		{
			return keyCode != -1 && event.getKeyCode() == keyCode;
		}

		@Override
		public String toString()
		{
			return name().replace('_', ' ');
		}
	}
}

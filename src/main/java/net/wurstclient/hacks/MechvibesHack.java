/*
 * Copyright (c) 2026 WurstB+ Plus contributors
 *
 * This source code is subject to the terms of the GNU Affero General Public
 * License, version 3. If a copy of the AGPL was not distributed with this
 * file, You can obtain one at: https://www.gnu.org/licenses/agpl-3.0.txt
 */
// Ported from Soar Client v7.1.2 (LGPL-3.0).
package net.wurstclient.hacks;

import org.lwjgl.glfw.GLFW;

import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.wurstclient.Category;
import net.wurstclient.SearchTags;
import net.wurstclient.events.KeyPressListener;
import net.wurstclient.events.MouseButtonListener;
import net.wurstclient.hack.Hack;
import net.wurstclient.settings.CheckboxSetting;
import net.wurstclient.settings.SliderSetting;
import net.wurstclient.settings.SliderSetting.ValueDisplay;
import net.wurstclient.settings.TextFieldSetting;

/**
 * Plays a configurable click sound whenever a keyboard or mouse button is
 * pressed.
 *
 * <p>Simplification: Soar bundled its own keyboard sample packs and loaded WAV
 * clips. This port uses vanilla sound events (or any sound ID typed into the
 * settings) through the client sound manager, so no audio assets are needed.
 */
@SearchTags({"mechvibes", "keyboard sounds", "mouse sounds", "click sound",
	"clicks", "key sounds"})
public final class MechvibesHack extends Hack
	implements KeyPressListener, MouseButtonListener
{
	private final CheckboxSetting keyboard = new CheckboxSetting("Keyboard",
		"Plays a sound when a keyboard key is pressed.", true);

	private final TextFieldSetting keyboardSound = new TextFieldSetting(
		"Keyboard sound", "Sound event ID played on key presses.",
		"minecraft:ui.button.click");

	private final CheckboxSetting mouse = new CheckboxSetting("Mouse",
		"Plays a sound when the left or right mouse button is pressed.", true);

	private final TextFieldSetting mouseSound = new TextFieldSetting(
		"Mouse sound", "Sound event ID played on mouse presses.",
		"minecraft:ui.button.click");

	private final SliderSetting volume = new SliderSetting("Volume",
		"Volume of the click sounds.", 0.5, 0, 1, 0.01,
		ValueDisplay.PERCENTAGE);

	private final SliderSetting pitch = new SliderSetting("Pitch",
		"Pitch of the click sounds.", 1, 0.5, 2, 0.01, ValueDisplay.DECIMAL);

	public MechvibesHack()
	{
		super("Mechvibes");
		setCategory(Category.OTHER);
		addSetting(keyboard);
		addSetting(keyboardSound);
		addSetting(mouse);
		addSetting(mouseSound);
		addSetting(volume);
		addSetting(pitch);
		keyboardSound.visibleWhen(keyboard::isChecked);
		mouseSound.visibleWhen(mouse::isChecked);
	}

	@Override
	protected void onEnable()
	{
		EVENTS.add(KeyPressListener.class, this);
		EVENTS.add(MouseButtonListener.class, this);
	}

	@Override
	protected void onDisable()
	{
		EVENTS.remove(KeyPressListener.class, this);
		EVENTS.remove(MouseButtonListener.class, this);
	}

	@Override
	public void onKeyPress(KeyPressEvent event)
	{
		if(!keyboard.isChecked()
			|| event.getAction() != GLFW.GLFW_PRESS)
			return;

		playSound(keyboardSound.getValue());
	}

	@Override
	public void onMouseButton(int button, int action)
	{
		if(!mouse.isChecked() || action != GLFW.GLFW_PRESS)
			return;

		if(button != GLFW.GLFW_MOUSE_BUTTON_LEFT
			&& button != GLFW.GLFW_MOUSE_BUTTON_RIGHT)
			return;

		playSound(mouseSound.getValue());
	}

	private void playSound(String soundId)
	{
		ResourceLocation id = ResourceLocation.tryParse(soundId.trim());
		if(id == null)
			return;

		SoundEvent sound = SoundEvent.createVariableRangeEvent(id);
		MC.getSoundManager().play(SimpleSoundInstance.forUI(sound,
			pitch.getValueF(), volume.getValueF()));
	}
}

/*
 * Copyright (c) 2026 WurstB+ Plus contributors
 *
 * This source code is subject to the terms of the GNU Affero General Public
 * License, version 3. If a copy of the AGPL was not distributed with this
 * file, You can obtain one at: https://www.gnu.org/licenses/agpl-3.0.txt
 */
// Ported from Soar Client v7.1.2 (LGPL-3.0).
package net.wurstclient.hacks;

import java.awt.Color;
import java.util.Locale;

import org.lwjgl.glfw.GLFW;

import com.mojang.blaze3d.platform.InputConstants;

import net.wurstclient.Category;
import net.wurstclient.SearchTags;
import net.wurstclient.events.GUIRenderListener;
import net.wurstclient.events.KeyPressListener;
import net.wurstclient.hack.Hack;
import net.wurstclient.settings.CheckboxSetting;
import net.wurstclient.settings.ColorSetting;
import net.wurstclient.settings.SliderSetting;
import net.wurstclient.settings.SliderSetting.ValueDisplay;
import net.wurstclient.settings.TextFieldSetting;
import net.minecraft.client.gui.GuiGraphicsExtractor;

/**
 * HUD stopwatch. The key cycles through running, paused and reset, matching the
 * three-press behavior of the Soar module.
 */
@SearchTags({"stopwatch", "timer", "clock", "elapsed time"})
public final class StopwatchHack extends Hack
	implements KeyPressListener, GUIRenderListener
{
	private enum State
	{
		STOPPED,
		RUNNING,
		PAUSED
	}

	private final TextFieldSetting keybind = new TextFieldSetting("Keybind",
		"Key that starts, pauses and resets the stopwatch, e.g."
			+ " key.keyboard.p.",
		"key.keyboard.p", StopwatchHack::isValidKey);

	private final SliderSetting scale = new SliderSetting("Scale",
		"Scale of the stopwatch text.", 1.5, 0.5, 5, 0.1, ValueDisplay.DECIMAL);

	private final SliderSetting posX = new SliderSetting("Position X",
		"X position of the stopwatch text.", 500, 0, 2500, 1,
		ValueDisplay.INTEGER);

	private final SliderSetting posY = new SliderSetting("Position Y",
		"Y position of the stopwatch text.", 270, 0, 2500, 1,
		ValueDisplay.INTEGER);

	private final CheckboxSetting background = new CheckboxSetting(
		"Show background", "Draws a colored box behind the stopwatch.", true);

	private final ColorSetting bgColor = new ColorSetting("Background color",
		"Color of the background box.", new Color(0, 0, 0, 150));

	private State state = State.STOPPED;
	private long accumulatedNanos;
	private long startedNanos;

	public StopwatchHack()
	{
		super("Stopwatch");
		setCategory(Category.RENDER);
		addSetting(keybind);
		addSetting(scale);
		addSetting(posX);
		addSetting(posY);
		addSetting(background);
		addSetting(bgColor);
		bgColor.visibleWhen(background::isChecked);
	}

	@Override
	protected void onEnable()
	{
		resetStopwatch();
		EVENTS.add(KeyPressListener.class, this);
		EVENTS.add(GUIRenderListener.class, this);
	}

	@Override
	protected void onDisable()
	{
		EVENTS.remove(KeyPressListener.class, this);
		EVENTS.remove(GUIRenderListener.class, this);
		resetStopwatch();
	}

	@Override
	public void onKeyPress(KeyPressEvent event)
	{
		if(event.getAction() != GLFW.GLFW_PRESS)
			return;

		if(MC.gui.screen() != null || MC.player == null)
			return;

		if(!matchesKey(event.getKeyCode()))
			return;

		switch(state)
		{
			case STOPPED:
			startedNanos = System.nanoTime();
			state = State.RUNNING;
			break;

			case RUNNING:
			accumulatedNanos += System.nanoTime() - startedNanos;
			state = State.PAUSED;
			break;

			case PAUSED:
			resetStopwatch();
			break;
		}
	}

	@Override
	public void onRenderGUI(GuiGraphicsExtractor context, float partialTicks)
	{
		if(MC.player == null)
			return;

		String text =
			String.format(Locale.ROOT, "%.2f s", getElapsedSeconds());
		double textScale = scale.getValue();
		double width = MC.font.width(text) * textScale;
		double height = MC.font.lineHeight * textScale;
		double x = posX.getValue();
		double y = posY.getValue();

		if(background.isChecked())
			context.fill((int)(x - 4), (int)(y - 4), (int)(x + width + 4),
				(int)(y + height + 4), bgColor.getColor().getRGB());

		var pose = context.pose();
		pose.pushMatrix();
		pose.translate((float)x, (float)y);
		pose.scale((float)textScale, (float)textScale);
		context.text(MC.font, text, 0, 0, 0xFFFFFFFF, true);
		pose.popMatrix();
	}

	private double getElapsedSeconds()
	{
		long elapsed = accumulatedNanos;
		if(state == State.RUNNING)
			elapsed += System.nanoTime() - startedNanos;
		return elapsed / 1_000_000_000.0;
	}

	private void resetStopwatch()
	{
		state = State.STOPPED;
		accumulatedNanos = 0;
		startedNanos = 0;
	}

	private boolean matchesKey(int keyCode)
	{
		String key = keybind.getValue().trim();
		if(key.isEmpty())
			return false;

		try
		{
			return InputConstants.getKey(key).getValue() == keyCode;
		}catch(IllegalArgumentException e)
		{
			return false;
		}
	}

	private static boolean isValidKey(String value)
	{
		String key = value.trim();
		if(key.isEmpty())
			return false;

		try
		{
			InputConstants.getKey(key);
			return true;
		}catch(IllegalArgumentException e)
		{
			return false;
		}
	}
}

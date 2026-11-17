/*
 * Copyright (c) 2026 WurstB+ Plus contributors
 *
 * This source code is subject to the terms of the GNU Affero General Public
 * License, version 3. If a copy of the AGPL was not distributed with this
 * file, You can obtain one at: https://www.gnu.org/licenses/agpl-3.0.txt
 */
// Ported from Soar Client v7.1.2 (LGPL-3.0).
package net.wurstclient.hud2.mods;

import org.lwjgl.glfw.GLFW;

import net.wurstclient.events.KeyPressListener;
import net.wurstclient.hud2.nanovg.HudIcon;
import net.wurstclient.hud2.SimpleHUDMod;
import net.wurstclient.settings.TextFieldSetting;

public final class StopwatchMod extends SimpleHUDMod
	implements KeyPressListener
{
	private enum State
	{
		STOPPED,
		RUNNING,
		PAUSED
	}

	private final TextFieldSetting keybind = new TextFieldSetting("Keybind",
		"Key that starts, pauses and resets the stopwatch.",
		"key.keyboard.p", s -> s.startsWith("key."));

	private State state = State.STOPPED;
	private long accumulated;
	private long started;

	public StopwatchMod()
	{
		super("Stopwatch", "Stopwatch", "A simple stopwatch.");
		setTranslationKey("stopwatch");
		setDefaultPosition(10, 580);
		addSetting(keybind);
	}

	@Override
	protected void onEnable()
	{
		reset();
		EVENTS.add(KeyPressListener.class, this);
	}

	@Override
	protected void onDisable()
	{
		EVENTS.remove(KeyPressListener.class, this);
		reset();
	}

	@Override
	public void onKeyPress(KeyPressEvent event)
	{
		if(event.getAction() != GLFW.GLFW_PRESS || MC.screen != null)
			return;
		if(!keyName(event.getKeyCode()).equalsIgnoreCase(keybind.getValue()))
			return;

		switch(state)
		{
			case STOPPED:
			started = System.nanoTime();
			state = State.RUNNING;
			break;

			case RUNNING:
			accumulated += System.nanoTime() - started;
			state = State.PAUSED;
			break;

			case PAUSED:
			reset();
			break;
		}
	}

	private String keyName(int keyCode)
	{
		return "key.keyboard." + GLFW.glfwGetKeyName(keyCode, 0);
	}

	private void reset()
	{
		state = State.STOPPED;
		accumulated = 0;
		started = 0;
	}

	private long elapsed()
	{
		long nanos = accumulated;
		if(state == State.RUNNING)
			nanos += System.nanoTime() - started;
		return nanos;
	}

	@Override
	public String getText()
	{
		long millis = elapsed() / 1_000_000L;
		return String.format("%02d:%02d.%03d", millis / 60_000,
			millis / 1000 % 60, millis % 1000);
	}

	@Override
	public String getIcon()
	{
		return HudIcon.WATCH;
	}
}

/*
 * Copyright (c) 2026 WurstB+ Plus contributors
 *
 * This source code is subject to the terms of the GNU Affero General Public
 * License, version 3. If a copy of the AGPL was not distributed with this
 * file, You can obtain one at: https://www.gnu.org/licenses/agpl-3.0.txt
 */
// Ported from Soar Client v7.1.2 (LGPL-3.0).
package net.wurstclient.hud2.mods;

import java.util.ArrayDeque;

import org.lwjgl.glfw.GLFW;

import net.wurstclient.events.MouseButtonListener;
import net.wurstclient.events.UpdateListener;
import net.wurstclient.hud2.nanovg.HudIcon;
import net.wurstclient.hud2.SimpleHUDMod;
import net.wurstclient.settings.CheckboxSetting;

public final class CPSDisplayMod extends SimpleHUDMod
	implements MouseButtonListener, UpdateListener
{
	private final CheckboxSetting showRight = new CheckboxSetting(
		"Right", "Also shows right clicks per second.", true);
	private final ArrayDeque<Long> leftClicks = new ArrayDeque<>();
	private final ArrayDeque<Long> rightClicks = new ArrayDeque<>();

	public CPSDisplayMod()
	{
		super("CPSDisplay", "CPS Display", "Shows clicks per second.");
		setTranslationKey("cps_display");
		setDefaultPosition(10, 540);
		addSetting(showRight);
	}

	@Override
	protected void onEnable()
	{
		EVENTS.add(MouseButtonListener.class, this);
		EVENTS.add(UpdateListener.class, this);
	}

	@Override
	protected void onDisable()
	{
		EVENTS.remove(MouseButtonListener.class, this);
		EVENTS.remove(UpdateListener.class, this);
		leftClicks.clear();
		rightClicks.clear();
	}

	@Override
	public void onMouseButton(int button, int action)
	{
		if(action != GLFW.GLFW_PRESS || MC.screen != null || MC.player == null)
			return;
		long now = System.currentTimeMillis();
		if(button == GLFW.GLFW_MOUSE_BUTTON_LEFT)
			leftClicks.addLast(now);
		else if(button == GLFW.GLFW_MOUSE_BUTTON_RIGHT)
			rightClicks.addLast(now);
	}

	@Override
	public void onUpdate()
	{
		prune();
	}

	private void prune()
	{
		long cutoff = System.currentTimeMillis() - 1000;
		while(!leftClicks.isEmpty() && leftClicks.peekFirst() < cutoff)
			leftClicks.removeFirst();
		while(!rightClicks.isEmpty() && rightClicks.peekFirst() < cutoff)
			rightClicks.removeFirst();
	}

	@Override
	public String getText()
	{
		prune();
		String text = "L: " + leftClicks.size();
		if(showRight.isChecked())
			text += "  R: " + rightClicks.size();
		return text;
	}

	@Override
	public String getIcon()
	{
		return HudIcon.MOUSE_POINTER;
	}
}

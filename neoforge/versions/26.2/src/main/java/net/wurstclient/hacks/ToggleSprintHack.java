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
import net.minecraft.client.input.KeyEvent;
import net.wurstclient.Category;
import net.wurstclient.SearchTags;
import net.wurstclient.events.KeyPressListener;
import net.wurstclient.events.UpdateListener;
import net.wurstclient.hack.Hack;
import net.wurstclient.mixinterface.IKeyBinding;

@SearchTags({"toggle sprint", "auto sprint", "sprinting"})
public final class ToggleSprintHack extends Hack
	implements KeyPressListener, UpdateListener
{
	private boolean toggled;

	public ToggleSprintHack()
	{
		super("ToggleSprint");
		setCategory(Category.MOVEMENT);
	}

	@Override
	protected void onEnable()
	{
		toggled = false;
		EVENTS.add(KeyPressListener.class, this);
		EVENTS.add(UpdateListener.class, this);
	}

	@Override
	protected void onDisable()
	{
		EVENTS.remove(KeyPressListener.class, this);
		EVENTS.remove(UpdateListener.class, this);
		toggled = false;
		IKeyBinding.get(MC.options.keySprint).resetPressedState();
	}

	@Override
	public void onKeyPress(KeyPressEvent event)
	{
		if(event.getAction() != GLFW.GLFW_PRESS)
			return;
		if(MC.player == null || MC.gui.screen() != null)
			return;

		KeyEvent keyEvent = new KeyEvent(event.getKeyCode(),
			event.getScanCode(), event.getModifiers());
		if(!MC.options.keySprint.matches(keyEvent))
			return;

		toggled = !toggled;

		if(!toggled)
			IKeyBinding.get(MC.options.keySprint).resetPressedState();
	}

	@Override
	public void onUpdate()
	{
		if(MC.player == null)
			return;

		if(toggled)
			IKeyBinding.get(MC.options.keySprint).setPressed(true);
	}
}

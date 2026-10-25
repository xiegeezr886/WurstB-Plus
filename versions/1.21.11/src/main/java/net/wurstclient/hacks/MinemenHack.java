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

import net.wurstclient.Category;
import net.wurstclient.SearchTags;
import net.wurstclient.events.KeyPressListener;
import net.wurstclient.hack.Hack;
import net.wurstclient.settings.EnumSetting;

/**
 * Sends Minemen Club utility commands on configurable keys.
 *
 * <p>
 * Soar's module watched chat for a /requeue click event and replayed it. This
 * port exposes /spawn and /refill on keys instead, as requested.
 */
@SearchTags({"minemen", "minemen club", "spawn", "refill", "quick commands"})
public final class MinemenHack extends Hack implements KeyPressListener
{
	private final EnumSetting<TriggerKey> spawnKey = new EnumSetting<>(
		"Spawn key", "Key that runs /spawn.", TriggerKey.values(),
		TriggerKey.K);

	private final EnumSetting<TriggerKey> refillKey = new EnumSetting<>(
		"Refill key", "Key that runs /refill.", TriggerKey.values(),
		TriggerKey.R);

	public MinemenHack()
	{
		super("Minemen");
		setCategory(Category.CHAT);
		addSetting(spawnKey);
		addSetting(refillKey);
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

		if(MC.screen != null || MC.player == null || MC.getConnection() == null)
			return;

		if(spawnKey.getSelected().matches(event))
			MC.getConnection().sendCommand("spawn");
		else if(refillKey.getSelected().matches(event))
			MC.getConnection().sendCommand("refill");
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
		N(GLFW.GLFW_KEY_N),
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

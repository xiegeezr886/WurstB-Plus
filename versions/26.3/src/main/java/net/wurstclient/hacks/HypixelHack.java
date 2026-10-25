/*
 * Copyright (c) 2026 WurstB+ Plus contributors
 *
 * This source code is subject to the terms of the GNU Affero General Public
 * License, version 3. If a copy of the AGPL was not distributed with this
 * file, You can obtain one at: https://www.gnu.org/licenses/agpl-3.0.txt
 */
// Ported from Soar Client v7.1.2 (LGPL-3.0).
package net.wurstclient.hacks;

import com.mojang.blaze3d.platform.InputConstants;

import net.wurstclient.Category;
import net.wurstclient.SearchTags;
import net.wurstclient.events.KeyPressListener;
import net.wurstclient.hack.Hack;
import net.wurstclient.settings.EnumSetting;

/**
 * Sends common Hypixel utility commands on configurable keys.
 *
 * <p>
 * Soar's module was an auto-GG/auto-play/tip/anti-L automation with scoreboard
 * parsing. That behaviour is replaced here with the quick lobby, hub and
 * rejoin commands requested for this port.
 */
@SearchTags({"hypixel", "lobby", "hub", "rejoin", "quick commands", "skyblock"})
public final class HypixelHack extends Hack implements KeyPressListener
{
	private final EnumSetting<TriggerKey> lobbyKey = new EnumSetting<>(
		"Lobby key", "Key that runs /lobby.", TriggerKey.values(),
		TriggerKey.L);

	private final EnumSetting<TriggerKey> hubKey = new EnumSetting<>(
		"Hub key", "Key that runs /hub.", TriggerKey.values(), TriggerKey.H);

	private final EnumSetting<TriggerKey> rejoinKey = new EnumSetting<>(
		"Rejoin key", "Key that runs /rejoin.", TriggerKey.values(),
		TriggerKey.J);

	public HypixelHack()
	{
		super("Hypixel");
		setCategory(Category.CHAT);
		addSetting(lobbyKey);
		addSetting(hubKey);
		addSetting(rejoinKey);
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
		if(event.getAction() != InputConstants.PRESS)
			return;

		if(MC.gui.screen() != null || MC.player == null
			|| MC.getConnection() == null)
			return;

		if(lobbyKey.getSelected().matches(event))
			MC.getConnection().sendCommand("lobby");
		else if(hubKey.getSelected().matches(event))
			MC.getConnection().sendCommand("hub");
		else if(rejoinKey.getSelected().matches(event))
			MC.getConnection().sendCommand("rejoin");
	}

	private enum TriggerKey
	{
		NONE(-1),
		R(InputConstants.KEY_R),
		G(InputConstants.KEY_G),
		X(InputConstants.KEY_X),
		C(InputConstants.KEY_C),
		V(InputConstants.KEY_V),
		B(InputConstants.KEY_B),
		F(InputConstants.KEY_F),
		H(InputConstants.KEY_H),
		J(InputConstants.KEY_J),
		K(InputConstants.KEY_K),
		L(InputConstants.KEY_L),
		N(InputConstants.KEY_N),
		P(InputConstants.KEY_P),
		Z(InputConstants.KEY_Z),
		LEFT_SHIFT(InputConstants.KEY_LSHIFT),
		LEFT_CONTROL(InputConstants.KEY_LCONTROL),
		LEFT_ALT(InputConstants.KEY_LALT);

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

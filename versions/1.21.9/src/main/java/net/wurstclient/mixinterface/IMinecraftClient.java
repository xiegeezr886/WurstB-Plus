/*
 * Copyright (c) 2014-2026 Wurst-Imperium and contributors.
 *
 * This source code is subject to the terms of the GNU General Public
 * License, version 3. If a copy of the GPL was not distributed with this
 * file, You can obtain one at: https://www.gnu.org/licenses/gpl-3.0.txt
 */
package net.wurstclient.mixinterface;

import net.minecraft.client.User;

public interface IMinecraftClient
{
	public IMultiPlayerGameMode getInteractionManager();

	public int getRightClickDelay();

	public void setRightClickDelay(int delay);
	
	/**
	 * Minecraft's "you missed your swing" cooldown, in ticks. It is
	 * non-zero for a short window after a failed attack, and combat
	 * hacks use it to avoid spamming attacks that the server would
	 * ignore anyway.
	 */
	public int getMissTime();

	public void setMissTime(int missTime);

	public ILocalPlayer getPlayer();
	
	public User getWurstSession();
	
	public void setWurstSession(User session);
}

/*
 * Copyright (c) 2014-2025 Wurst-Imperium and contributors.
 *
 * This source code is subject to the terms of the GNU General Public
 * License, version 3. If a copy of the GPL was not distributed with this
 * file, You can obtain one at: https://www.gnu.org/licenses/gpl-3.0.txt
 */
package net.wurstclient.hacks.step;

import net.minecraft.client.player.LocalPlayer;
import net.wurstclient.hacks.StepHack;

/**
 * Simple：直接把 {@code maxUpStep} 设成 Height 设置的值，可以一次上多格。
 * 不做任何发包，所以"能不能上"完全由客户端自己的碰撞逻辑决定。
 */
public final class SimpleStepMode implements StepMode
{
	@Override
	public String getName()
	{
		return "Simple";
	}
	
	@Override
	public void onUpdate(StepHack hack, LocalPlayer player)
	{
		player.maxUpStep = hack.getHeight();
	}
}

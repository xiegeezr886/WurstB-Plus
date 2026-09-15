/*
 * Copyright (c) 2014-2025 Wurst-Imperium and contributors.
 *
 * This source code is subject to the terms of the GNU General Public
 * License, version 3. If a copy of the GPL was not distributed with this
 * file, You can obtain one at: https://www.gnu.org/licenses/gpl-3.0.txt
 */
package net.wurstclient.hacks.speedmine;

import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.wurstclient.WurstClient;
import net.wurstclient.hacks.SpeedMineHack;

/**
 * Haste：每 tick 维持一份我们自己加的急迫效果。
 *
 * <p>
 * 关闭时**只清掉我们加进去的那一份**，并把原本就有的急迫（药水/信标）放回去 ——
 * 旧实现无条件 {@code removeEffect(DIG_SPEED)}，反例是站在信标范围里用 Haste 模式后
 * 关掉 SpeedMine、连玩家真实的急迫也被清掉。{@code appliedHaste} 就是为这个兜底的。
 */
public final class HasteSpeedMineMode implements SpeedMineMode
{
	private MobEffectInstance previousHaste;
	private boolean appliedHaste;
	
	@Override
	public String getName()
	{
		return "Haste";
	}
	
	@Override
	public void onUpdate(SpeedMineHack hack)
	{
		appliedHaste = true;
		
		MobEffectInstance existing =
			WurstClient.MC.player.getEffect(MobEffects.DIG_SPEED);
		if(existing != null && existing.getAmplifier() >= hack.getHasteLevel())
			return;
		
		if(previousHaste == null)
			previousHaste = existing;
		
		WurstClient.MC.player.addEffect(new MobEffectInstance(MobEffects.DIG_SPEED,
			5, hack.getHasteLevel(), false, false, false));
	}
	
	@Override
	public void onDisable()
	{
		if(!appliedHaste)
			return;
		
		WurstClient.MC.player.removeEffect(MobEffects.DIG_SPEED);
		if(previousHaste != null)
			WurstClient.MC.player.addEffect(previousHaste);
		appliedHaste = false;
		previousHaste = null;
	}
}

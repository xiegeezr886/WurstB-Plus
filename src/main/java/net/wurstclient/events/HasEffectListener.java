/*
 * Copyright (c) 2014-2025 Wurst-Imperium and contributors.
 *
 * This source code is subject to the terms of the GNU General Public
 * License, version 3. If a copy of the GPL was not distributed with this
 * file, You can obtain one at: https://www.gnu.org/licenses/gpl-3.0.txt
 */
package net.wurstclient.events;

import java.util.ArrayList;
import net.minecraft.world.effect.MobEffect;
import net.wurstclient.event.Event;
import net.wurstclient.event.Listener;

/**
 * 询问"玩家现在有没有这个状态效果"（原版 {@code Entity#hasEffect(MobEffect)}）。
 *
 * <p>事件初值是原版的答案，监听器可以改：设成 true 就是假装有（Fullbright 的夜视），
 * 设成 false 就是假装没有（NoLevitation 的漂浮、AntiBlind 的失明/黑暗）。
 * 用效果对象本身做判断，避免多个 hack 之间互相覆盖。
 */
public interface HasEffectListener extends Listener
{
	public void onHasEffect(HasEffectEvent event);
	
	public static class HasEffectEvent extends Event<HasEffectListener>
	{
		private final MobEffect effect;
		private boolean hasEffect;
		
		public HasEffectEvent(MobEffect effect, boolean hasEffect)
		{
			this.effect = effect;
			this.hasEffect = hasEffect;
		}
		
		public MobEffect getEffect()
		{
			return effect;
		}
		
		public boolean hasEffect()
		{
			return hasEffect;
		}
		
		public void setHasEffect(boolean hasEffect)
		{
			this.hasEffect = hasEffect;
		}
		
		@Override
		public void fire(ArrayList<HasEffectListener> listeners)
		{
			for(HasEffectListener listener : listeners)
				listener.onHasEffect(this);
		}
		
		@Override
		public Class<HasEffectListener> getListenerType()
		{
			return HasEffectListener.class;
		}
	}
}

/*
 * Copyright (c) 2025 Penguin
 *
 * This source code is subject to the terms of the GNU General Public
 * License, version 3. If a copy of the GPL was not distributed with this
 * file, You can obtain one at: https://www.gnu.org/licenses/gpl-3.0.txt
 */
package net.wurstclient.hacks;

import net.minecraft.world.entity.Entity;
import net.wurstclient.Category;
import net.wurstclient.SearchTags;
import net.wurstclient.events.PlayerAttacksEntityListener;
import net.wurstclient.hack.Hack;

@SearchTags({"delay remover", "no cooldown", "attack speed"})
public final class DelayRemoverHack extends Hack
	implements PlayerAttacksEntityListener
{
	public DelayRemoverHack()
	{
		super("DelayRemover");
		setCategory(Category.COMBAT);
	}
	
	@Override
	protected void onEnable()
	{
		EVENTS.add(PlayerAttacksEntityListener.class, this);
	}
	
	@Override
	protected void onDisable()
	{
		EVENTS.remove(PlayerAttacksEntityListener.class, this);
	}
	
	/**
	 * 旧实现用 {@code LocalPlayer.class.getDeclaredMethod(
	 * "resetAttackStrengthTicker")} 加反射来调这个 public 方法，而
	 * {@code getDeclaredMethod} 不查父类、该方法声明在 {@code Player} 上
	 * （1.20.2 反编译源 {@code Player.java:2032}），所以它必然抛
	 * {@code NoSuchMethodException}；catch 块又是空的 —— 于是这个 hack 一直是
	 * 彻底的空操作。这里改成直接调用。
	 *
	 * <p>
	 * 需要说明的是：1.20.1 的客户端本来就已经重置过这一次了
	 * （{@code Player.attack} 与 {@code MultiPlayerGameMode.attack} 各调一次
	 * {@code resetAttackStrengthTicker()}），服务端的伤害系数也无法由客户端改写，
	 * 所以本 hack 在 1.20.1 不会有可观察的效果。客户端侧真正存在的攻击延迟只剩
	 * {@code Minecraft.missTime}（反编译源 {@code Minecraft.java:1669} 是唯一的
	 * 客户端攻击门槛），那一项由 {@code NoMissCooldown} 负责。
	 */
	@Override
	public void onPlayerAttacksEntity(Entity target)
	{
		MC.player.resetAttackStrengthTicker();
	}
}

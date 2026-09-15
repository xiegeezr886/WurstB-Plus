/*
 * Copyright (c) 2025 Penguin
 *
 * This source code is subject to the terms of the GNU General Public
 * License, version 3. If a copy of the GPL was not distributed with this
 * file, You can obtain one at: https://www.gnu.org/licenses/gpl-3.0.txt
 */
package net.wurstclient.hacks;

import net.minecraft.client.player.LocalPlayer;
import net.wurstclient.Category;
import net.wurstclient.SearchTags;
import net.wurstclient.events.UpdateListener;
import net.wurstclient.hack.Hack;
import net.wurstclient.mixin.LivingEntityAccessor;

@SearchTags({"no jump delay", "jump delay"})
public final class NoJumpDelayHack extends Hack implements UpdateListener
{
	public NoJumpDelayHack()
	{
		super("NoJumpDelay");
		setCategory(Category.MOVEMENT);
	}

	@Override
	protected void onEnable()
	{
		EVENTS.add(UpdateListener.class, this);
	}

	@Override
	protected void onDisable()
	{
		EVENTS.remove(UpdateListener.class, this);
	}

	@Override
	public void onUpdate()
	{
		LocalPlayer player = MC.player;
		if(player == null)
			return;

		// 旧实现用 LocalPlayer.class.getDeclaredField("jumpDelay" /
		// "noJumpDelay" / "jumpingCooldown" / "autoJumpTime") 反射找跳跃冷却字段，
		// 但 1.20 的字段是 LivingEntity.noJumpDelay
		// （1.20.2 反编译源 LivingEntity.java:220，aiStep() 在 :2565 递减、
		// :2632 判定 noJumpDelay == 0、:2644 归零），
		// 而 Class.getDeclaredField() 只查本类、不查父类 ⇒ 四个名字全部抛
		// NoSuchFieldException、被 catch(Exception) 吞掉，jumpDelayField 永远是 null，
		// 这个模块从未生效过。
		//
		// 改成走本工程既有的 @Accessor（mixin/LivingEntityAccessor.java）：
		// 享受 refmap 重映射，运行时也不会因为字段名被映射成 SRG 名而找不到。
		// UpdateEvent 在 LocalPlayer.tick() 的 super.tick() 之前触发，
		// 而 aiStep() 里的递减/判定都在那之后，所以归零能生效。
		((LivingEntityAccessor)player).wurst_setNoJumpDelay(0);
	}
}

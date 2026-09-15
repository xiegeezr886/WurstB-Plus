/*
 * Copyright (c) 2014-2025 Wurst-Imperium and contributors.
 *
 * This source code is subject to the terms of the GNU General Public
 * License, version 3. If a copy of the GPL was not distributed with this
 * file, You can obtain one at: https://www.gnu.org/licenses/gpl-3.0.txt
 */
package net.wurstclient.hacks.velocity;

import net.minecraft.network.protocol.game.ClientboundExplodePacket;
import net.minecraft.world.phys.Vec3;
import net.wurstclient.events.PacketInputListener.PacketInputEvent;
import net.wurstclient.hacks.NoVelocityHack;

/**
 * NoVelocity 的一种实现方式（Modify / JumpReset）。
 *
 * <p>
 * 参考 OpenOpal 的 {@code velocity/impl/*} 结构。原来的写法是把模式判断散在
 * 三个地方（收到速度包、收到爆炸包、每 tick），这里收敛成接口上的三个钩子，
 * 模式自己的状态（JumpReset 的待跳计数）也搬进模式类，不再挂在 hack 上。
 */
public interface VelocityMode
{
	/** 显示在 Mode 设置里的名字。 */
	String getName();
	
	/**
	 * 收到属于自己的速度包（{@code ClientboundSetEntityMotionPacket}）。
	 *
	 * @return true 表示取消这个包，不再交给原版处理。
	 */
	boolean onEntityVelocity(NoVelocityHack hack, PacketInputEvent event,
		Vec3 incoming);
	
	/** 这个模式是否也改写爆炸击退。 */
	default boolean handlesExplosions()
	{
		return false;
	}
	
	/** 改写爆炸击退（只有 {@link #handlesExplosions()} 为 true 时会被调用）。 */
	default void onExplosion(ClientboundExplodePacket packet,
		double horizontalMultiplier, double verticalMultiplier)
	{}
	
	/** 每个 tick 的机会（只有 JumpReset 用得上）。 */
	default void onUpdate(NoVelocityHack hack)
	{}
	
	/** 开启/关闭时清掉本模式的状态。 */
	default void reset()
	{}
}

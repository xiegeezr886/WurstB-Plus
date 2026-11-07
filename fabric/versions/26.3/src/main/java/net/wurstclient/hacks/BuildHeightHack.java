/*
 * Adapted from Meteor Client. Copyright (c) Meteor Development.
 * SPDX-License-Identifier: GPL-3.0
 */
package net.wurstclient.hacks;

import net.minecraft.core.Direction;
import net.minecraft.network.protocol.game.ServerboundUseItemOnPacket;
import net.minecraft.world.phys.BlockHitResult;
import net.wurstclient.Category;
import net.wurstclient.SearchTags;
import net.wurstclient.events.PacketOutputListener;
import net.wurstclient.hack.Hack;

@SearchTags({"build height", "build limit", "高度上限交互"})
public final class BuildHeightHack extends Hack implements PacketOutputListener
{
	public BuildHeightHack()
	{
		super("BuildHeight");
		setCategory(Category.BLOCKS);
	}

	@Override
	protected void onEnable()
	{
		EVENTS.add(PacketOutputListener.class, this);
	}

	@Override
	protected void onDisable()
	{
		EVENTS.remove(PacketOutputListener.class, this);
	}

	@Override
	public void onSentPacket(PacketOutputEvent event)
	{
		if(MC.level == null
			|| !(event.getPacket() instanceof ServerboundUseItemOnPacket packet))
			return;
		BlockHitResult hit = packet.hitResult();
		int top = MC.level.getMinY() + MC.level.getHeight();
		if(hit.getLocation().y >= top && hit.getDirection() == Direction.UP)
			event.setPacket(new ServerboundUseItemOnPacket(packet.hand(),
				hit.withDirection(Direction.DOWN), packet.sequence()));
	}
}

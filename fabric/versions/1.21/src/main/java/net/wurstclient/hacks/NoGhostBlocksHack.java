/*
 * Copyright (c) 2014-2026 Wurst-Imperium and contributors.
 *
 * This source code is subject to the terms of the GNU General Public
 * License, version 3. If a copy of the GPL was not distributed with this
 * file, You can obtain one at: https://www.gnu.org/licenses/gpl-3.0.txt
 *
 * Ported from Meteor Client (GPL-3.0).
 */
package net.wurstclient.hacks;

import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;

import net.minecraft.core.BlockPos;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientboundBlockUpdatePacket;
import net.minecraft.network.protocol.game.ClientboundSectionBlocksUpdatePacket;
import net.minecraft.world.level.block.state.BlockState;
import net.wurstclient.Category;
import net.wurstclient.SearchTags;
import net.wurstclient.events.PacketInputListener;
import net.wurstclient.events.UpdateListener;
import net.wurstclient.hack.Hack;
import net.wurstclient.settings.CheckboxSetting;

/**
 * Simplified port of Meteor's NoGhostBlocks module.
 *
 * <p>
 * Block update packets arrive on the netty thread, so they are queued and the
 * client-side block states are refreshed on the next client tick. This forces
 * the client to accept the server's state instead of keeping a predicted
 * (ghost) block, both when the server says a block was broken and when it says
 * a block still exists.
 */
@SearchTags({"no ghost blocks", "ghost blocks", "ghostblock", "desync",
	"block desync"})
public final class NoGhostBlocksHack extends Hack
	implements PacketInputListener, UpdateListener
{
	private static final int MAX_PENDING = 1024;
	private static final int MAX_PER_TICK = 512;

	private final CheckboxSetting breaking = new CheckboxSetting("Breaking",
		"Refresh blocks that the server says were broken.", true);

	private final CheckboxSetting placing = new CheckboxSetting("Placing",
		"Refresh blocks that the server says were placed.", true);

	private final Queue<Packet<?>> pending = new ConcurrentLinkedQueue<>();

	public NoGhostBlocksHack()
	{
		super("NoGhostBlocks");
		setCategory(Category.BLOCKS);
		addSetting(breaking);
		addSetting(placing);
	}

	@Override
	protected void onEnable()
	{
		pending.clear();
		EVENTS.add(PacketInputListener.class, this);
		EVENTS.add(UpdateListener.class, this);
	}

	@Override
	protected void onDisable()
	{
		EVENTS.remove(PacketInputListener.class, this);
		EVENTS.remove(UpdateListener.class, this);
		pending.clear();
	}

	@Override
	public void onReceivedPacket(PacketInputEvent event)
	{
		Packet<?> packet = event.getPacket();

		if(!(packet instanceof ClientboundBlockUpdatePacket)
			&& !(packet instanceof ClientboundSectionBlocksUpdatePacket))
			return;

		if(pending.size() >= MAX_PENDING)
			pending.poll();

		pending.add(packet);
	}

	@Override
	public void onUpdate()
	{
		if(MC.level == null)
		{
			pending.clear();
			return;
		}

		for(int i = 0; i < MAX_PER_TICK; i++)
		{
			Packet<?> packet = pending.poll();
			if(packet == null)
				return;

			if(packet instanceof ClientboundBlockUpdatePacket blockUpdate)
				refresh(blockUpdate.getPos(), blockUpdate.getBlockState());
			else if(packet instanceof ClientboundSectionBlocksUpdatePacket
				sectionUpdate)
				sectionUpdate.runUpdates(this::refresh);
		}
	}

	private void refresh(BlockPos pos, BlockState state)
	{
		if(!MC.level.isInWorldBounds(pos))
			return;

		if(state.isAir() && !breaking.isChecked())
			return;

		if(!state.isAir() && !placing.isChecked())
			return;

		if(MC.level.getBlockState(pos).equals(state))
			return;

		// Apply the server's state on the client thread. Using flag 3 also
		// notifies neighbours and redraws the affected chunk section.
		MC.level.setBlock(pos, state, 3);
	}
}

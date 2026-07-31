/*
 * Copyright (c) 2014-2025 Wurst-Imperium and contributors.
 *
 * This source code is subject to the terms of the GNU General Public
 * License, version 3. If a copy of the GPL was not distributed with this
 * file, You can obtain one at: https://www.gnu.org/licenses/gpl-3.0.txt
 */
package net.wurstclient.hacks;

import java.util.ArrayList;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.AABB;
import net.wurstclient.Category;
import net.wurstclient.SearchTags;
import net.wurstclient.events.UpdateListener;
import net.wurstclient.hack.Hack;
import net.wurstclient.util.BlockPlacer;
import net.wurstclient.util.BlockUtils;
import net.wurstclient.util.ChatUtils;

@SearchTags({"instant bunker"})
public final class InstantBunkerHack extends Hack implements UpdateListener
{
	private final int[][] template = {{2, 0, 2}, {-2, 0, 2}, {2, 0, -2},
		{-2, 0, -2}, {2, 1, 2}, {-2, 1, 2}, {2, 1, -2}, {-2, 1, -2}, {2, 2, 2},
		{-2, 2, 2}, {2, 2, -2}, {-2, 2, -2}, {1, 2, 2}, {0, 2, 2}, {-1, 2, 2},
		{2, 2, 1}, {2, 2, 0}, {2, 2, -1}, {-2, 2, 1}, {-2, 2, 0}, {-2, 2, -1},
		{1, 2, -2}, {0, 2, -2}, {-1, 2, -2}, {1, 0, 2}, {0, 0, 2}, {-1, 0, 2},
		{2, 0, 1}, {2, 0, 0}, {2, 0, -1}, {-2, 0, 1}, {-2, 0, 0}, {-2, 0, -1},
		{1, 0, -2}, {0, 0, -2}, {-1, 0, -2}, {1, 1, 2}, {0, 1, 2}, {-1, 1, 2},
		{2, 1, 1}, {2, 1, 0}, {2, 1, -1}, {-2, 1, 1}, {-2, 1, 0}, {-2, 1, -1},
		{1, 1, -2}, {0, 1, -2}, {-1, 1, -2}, {1, 2, 1}, {-1, 2, 1}, {1, 2, -1},
		{-1, 2, -1}, {0, 2, 1}, {1, 2, 0}, {-1, 2, 0}, {0, 2, -1}, {0, 2, 0}};
	private final ArrayList<BlockPos> positions = new ArrayList<>();
	
	private int startTimer;
	
	public InstantBunkerHack()
	{
		super("InstantBunker");
		setCategory(Category.BLOCKS);
	}
	
	@Override
	protected void onEnable()
	{
		WURST.getHax().tunnellerHack.setEnabled(false);
		
		if(!MC.player.onGround())
		{
			ChatUtils.error("无法在空中建造。");
			setEnabled(false);
			return;
		}
		
		ItemStack stack = MC.player.getInventory().getSelected();
		
		if(!(stack.getItem() instanceof BlockItem))
		{
			ChatUtils.error("主手必须持有方块。");
			setEnabled(false);
			return;
		}
		
		if(stack.getCount() < 57 && !MC.player.isCreative())
			ChatUtils.warning("方块不足，堡垒可能不完整。");
		
		// get start pos and facings
		BlockPos startPos = BlockPos.containing(MC.player.position());
		Direction facing = MC.player.getDirection();
		Direction facing2 = facing.getCounterClockWise();
		
		// set positions
		positions.clear();
		for(int[] pos : template)
			positions.add(startPos.above(pos[1]).relative(facing, pos[2])
				.relative(facing2, pos[0]));
		
		startTimer = 2;
		MC.player.jumpFromGround();
		
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
		if(startTimer > 0)
		{
			startTimer--;
			return;
		}
		
		// build instantly
		if(startTimer <= 0)
		{
			for(BlockPos pos : positions)
				if(BlockUtils.getState(pos).canBeReplaced()
					&& !MC.player.getBoundingBox().intersects(new AABB(pos)))
					BlockPlacer.place(pos, false, false);
			MC.player.swing(InteractionHand.MAIN_HAND);

			if(MC.player.onGround())
				setEnabled(false);
		}
	}

}

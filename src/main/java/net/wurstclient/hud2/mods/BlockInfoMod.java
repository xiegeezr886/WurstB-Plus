/*
 * Copyright (c) 2026 WurstB+ Plus contributors
 *
 * This source code is subject to the terms of the GNU Affero General Public
 * License, version 3. If a copy of the AGPL was not distributed with this
 * file, You can obtain one at: https://www.gnu.org/licenses/agpl-3.0.txt
 */
// Ported from Soar Client v7.1.2 (LGPL-3.0).
package net.wurstclient.hud2.mods;

import net.minecraft.core.BlockPos;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.wurstclient.hud2.HUDMod;
import net.wurstclient.hud2.compat.HudItemIcons;
import net.wurstclient.hud2.nanovg.HudFonts;

/**
 * Soar-style block info: an 80x80 card with the block rendered as a 3D icon
 * and its name above it. The card stays for a second after looking away.
 */
public final class BlockInfoMod extends HUDMod
{
	private static final long HOLD_MS = 1000;

	private Block lastBlock;
	private ItemStack lastStack;
	private String lastName;
	private long lastSelection;

	public BlockInfoMod()
	{
		super("BlockInfo", "Block Info",
			"Shows the block the player is looking at.");
		setTranslationKey("block_info");
		setDefaultPosition(180, 10);
	}

	@Override
	protected void draw()
	{
		Block block = null;
		if(isEditing())
			block = Blocks.GRASS_BLOCK;
		else if(MC.hitResult != null
			&& MC.hitResult.getType() == HitResult.Type.BLOCK
			&& MC.level != null)
		{
			BlockPos pos = ((BlockHitResult)MC.hitResult).getBlockPos();
			BlockState state = MC.level.getBlockState(pos);
			block = state.getBlock();
		}

		if(block != null && block != Blocks.NETHER_PORTAL
			&& block != Blocks.END_PORTAL)
		{
			lastBlock = block;
			lastStack = new ItemStack(block);
			lastName = block.getName().getString();
			lastSelection = System.currentTimeMillis();
		}else if(lastBlock == null
			|| System.currentTimeMillis() - lastSelection > HOLD_MS)
		{
			if(!isEditing())
			{
				setSize(0, 0);
				return;
			}
		}

		if(lastBlock == null || lastStack == null)
		{
			setSize(0, 0);
			return;
		}

		drawBackground(80, 80);
		drawCenteredText(lastName, 40, 6, 9, HudFonts.REGULAR);
		drawImage(HudItemIcons.get(lastStack), 24, 24, 32, 32);
		setSize(80, 80);
	}
}

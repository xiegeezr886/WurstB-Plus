/* Adapted from Meteor Client. Copyright (c) Meteor Development. SPDX-License-Identifier: GPL-3.0 */
package net.wurstclient.hacks;

import java.util.HashSet;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.wurstclient.Category;
import net.wurstclient.SearchTags;
import net.wurstclient.events.RightClickListener;
import net.wurstclient.hack.Hack;
import net.wurstclient.mixinterface.IMinecraftClient;

@SearchTags({"ghost hand", "containers through walls", "隔墙容器"})
public final class GhostHandHack extends Hack implements RightClickListener
{
	public GhostHandHack()
	{
		super("GhostHand");
		setCategory(Category.BLOCKS);
	}

	@Override
	protected void onEnable()
	{
		EVENTS.add(RightClickListener.class, this);
	}

	@Override
	protected void onDisable()
	{
		EVENTS.remove(RightClickListener.class, this);
	}

	@Override
	public void onRightClick(RightClickEvent event)
	{
		if(event.isCancelled() || MC.player == null || MC.level == null
			|| MC.gameMode == null || !MC.options.keyUse.isDown()
			|| MC.player.isShiftKeyDown() || MC.player.isSpectator())
			return;
		double range = MC.player.blockInteractionRange();
		HitResult direct = MC.player.pick(range, 1, false);
		if(direct instanceof BlockHitResult hit
			&& direct.getType() == HitResult.Type.BLOCK
			&& MC.level.getBlockState(hit.getBlockPos()).hasBlockEntity())
			return;

		Vec3 eyes = MC.player.getEyePosition(1);
		Vec3 direction = MC.player.getViewVector(1).scale(0.1);
		Set<BlockPos> visited = new HashSet<>();
		for(int i = 1; i < range * 10; i++)
		{
			BlockPos pos = BlockPos.containing(eyes.add(direction.scale(i)));
			if(!visited.add(pos)
				|| !MC.level.getBlockState(pos).hasBlockEntity())
				continue;
			for(InteractionHand hand : InteractionHand.values())
			{
				InteractionResult result = MC.gameMode.useItemOn(MC.player,
					hand, new BlockHitResult(Vec3.atCenterOf(pos), Direction.UP,
						pos, true));
				if(!result.consumesAction() && result != InteractionResult.FAIL)
					continue;
				MC.player.swing(hand);
				((IMinecraftClient)(Object)MC).setRightClickDelay(4);
				event.cancel();
				return;
			}
		}
	}
}

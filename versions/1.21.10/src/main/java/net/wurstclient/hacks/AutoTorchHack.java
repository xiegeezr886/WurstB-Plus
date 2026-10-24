/*
 * Copyright (c) 2026 WurstB+ Plus contributors
 *
 * This source code is subject to the terms of the GNU General Public
 * License, version 3. If a copy of the GPL was not distributed with this
 * file, You can obtain one at: https://www.gnu.org/licenses/gpl-3.0.txt
 *
 * Behavior reference: LeavesHack (AGPL-3.0); independently implemented.
 */
package net.wurstclient.hacks;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.TorchBlock;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.wurstclient.Category;
import net.wurstclient.SearchTags;
import net.wurstclient.events.UpdateListener;
import net.wurstclient.hack.Hack;
import net.wurstclient.settings.CheckboxSetting;
import net.wurstclient.settings.SliderSetting;
import net.wurstclient.settings.SliderSetting.ValueDisplay;
import net.wurstclient.util.BlockUtils;

@SearchTags({"auto torch", "torch", "light", "AutoTorch"})
public final class AutoTorchHack extends Hack implements UpdateListener
{
	private static final double PLACE_RANGE = 4.5;

	private final SliderSetting radius =
		new SliderSetting("Radius", "How far to search for dark spots.", 5, 1, 6,
			1, ValueDisplay.INTEGER);

	private final SliderSetting lightThreshold = new SliderSetting(
		"Light threshold",
		"Places torches where the block light level is at or below this value.",
		7, 0, 15, 1, ValueDisplay.INTEGER);

	private final SliderSetting delay = new SliderSetting("Delay",
		"Delay between placement attempts in ticks.", 5, 0, 40, 1,
		ValueDisplay.INTEGER);

	private final CheckboxSetting selectTorch = new CheckboxSetting(
		"Select torch", "Automatically selects a torch from the hotbar.", true);

	private int timer;

	public AutoTorchHack()
	{
		super("AutoTorch");
		setCategory(Category.BLOCKS);
		addSetting(radius);
		addSetting(lightThreshold);
		addSetting(delay);
		addSetting(selectTorch);
	}

	@Override
	protected void onEnable()
	{
		timer = 0;
		EVENTS.add(UpdateListener.class, this);
	}

	@Override
	protected void onDisable()
	{
		EVENTS.remove(UpdateListener.class, this);
		timer = 0;
	}

	@Override
	public void onUpdate()
	{
		if(MC.player == null || MC.level == null)
			return;

		if(timer > 0)
		{
			timer--;
			return;
		}

		timer = delay.getValueI();
		BlockPos pos = findTorchPos();
		if(pos != null)
			placeTorch(pos);
	}

	private BlockPos findTorchPos()
	{
		BlockPos center = BlockPos.containing(MC.player.position());
		int r = radius.getValueI();
		double rangeSq = radius.getValueSq();
		BlockPos best = null;
		double bestDistanceSq = Double.MAX_VALUE;

		for(int x = -r; x <= r; x++)
			for(int y = -r; y <= r; y++)
				for(int z = -r; z <= r; z++)
				{
					BlockPos pos = center.offset(x, y, z);
					double distanceSq =
						MC.player.distanceToSqr(Vec3.atCenterOf(pos));

					if(distanceSq > rangeSq || distanceSq >= bestDistanceSq)
						continue;

					if(!isValidTorchPos(pos))
						continue;

					best = pos;
					bestDistanceSq = distanceSq;
				}

		return best;
	}

	private boolean isValidTorchPos(BlockPos pos)
	{
		if(!MC.level.isInWorldBounds(pos))
			return false;

		// only empty spots are considered
		if(!BlockUtils.getState(pos).isAir())
			return false;

		if(MC.level.getBrightness(LightLayer.BLOCK,
			pos) > lightThreshold.getValueI())
			return false;

		// the torch must be able to stand on the block below it
		if(!Blocks.TORCH.defaultBlockState().canSurvive(MC.level, pos))
			return false;

		// don't place torches inside entities or the player
		if(!MC.level.getEntities((Entity)null, new AABB(pos)).isEmpty())
			return false;

		// the hit point must be within reach and visible
		Vec3 hitVec = Vec3.atBottomCenterOf(pos);
		Vec3 eyesPos = MC.player.getEyePosition();
		if(eyesPos.distanceToSqr(hitVec) > PLACE_RANGE * PLACE_RANGE)
			return false;

		BlockHitResult hit = BlockUtils.raycast(eyesPos, hitVec);
		return hit.getType() == HitResult.Type.MISS
			|| hit.getBlockPos().equals(pos.below())
				&& hit.getDirection() == Direction.UP;
	}

	private void placeTorch(BlockPos pos)
	{
		int slot = findTorchSlot();
		if(slot == -1)
			return;

		int oldSlot = MC.player.getInventory().getSelectedSlot();
		if(selectTorch.isChecked())
			MC.player.getInventory().setSelectedSlot(slot);
		else if(!isTorch(MC.player.getMainHandItem()))
			return;

		if(isTorch(MC.player.getMainHandItem()))
			placeOnFloor(pos);

		if(MC.player.getInventory().getSelectedSlot() != oldSlot)
			MC.player.getInventory().setSelectedSlot(oldSlot);
	}

	private void placeOnFloor(BlockPos pos)
	{
		BlockPos support = pos.below();
		Vec3 hitVec = Vec3.atBottomCenterOf(pos);

		WURST.getRotationFaker().faceVectorPacket(hitVec);
		IMC.getInteractionManager().rightClickBlock(support, Direction.UP,
			hitVec);
		MC.player.swing(InteractionHand.MAIN_HAND);
	}

	private int findTorchSlot()
	{
		for(int i = 0; i < 9; i++)
		{
			ItemStack stack = MC.player.getInventory().getItem(i);
			if(isTorch(stack))
				return i;
		}

		return -1;
	}

	private boolean isTorch(ItemStack stack)
	{
		if(stack.isEmpty() || !(stack.getItem() instanceof BlockItem))
			return false;

		Block block = Block.byItem(stack.getItem());
		return block instanceof TorchBlock;
	}
}

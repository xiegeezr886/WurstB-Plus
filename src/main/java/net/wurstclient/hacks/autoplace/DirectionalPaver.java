/* AutoPlaceBlock behavior adapted from LeavesHack. SPDX-License-Identifier: AGPL-3.0 */
package net.wurstclient.hacks.autoplace;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.wurstclient.WurstClient;
import net.wurstclient.settings.EnumSetting;
import net.wurstclient.settings.Setting;
import net.wurstclient.settings.SliderSetting;
import net.wurstclient.settings.SliderSetting.ValueDisplay;
import net.wurstclient.settings.SwingHandSetting.SwingHand;
import net.wurstclient.util.BlockUtils;
import net.wurstclient.util.InventoryUtils;
import net.wurstclient.util.RenderUtils;

public final class DirectionalPaver
{
	private static final Minecraft MC = WurstClient.MC;
	private final SliderSetting delay = new SliderSetting("Paving delay",
		"铺设批次之间的毫秒数。", 50, 0, 10000, 10, ValueDisplay.INTEGER);
	private final SliderSetting perTick = new SliderSetting("Paving blocks per tick", 1, 0, 4, 1, ValueDisplay.INTEGER);
	private final EnumSetting<Support> support = new EnumSetting<>("Paving support",
		"选择空位上方、下方或水平方向的支撑方块。", Support.values(), Support.UP);
	private final EnumSetting<SlabHalf> slabHalf = new EnumSetting<>("Paving slab half",
		"水平放置半砖时选择上半砖或下半砖。", SlabHalf.values(), SlabHalf.TOP);
	private final List<BlockPos> recent = new ArrayList<>();
	private ClientLevel world;
	private long lastAction;

	public Setting[] settings() {return new Setting[]{delay, perTick, support, slabHalf};}

	public void reset()
	{
		recent.clear(); world = MC.level; lastAction = 0;
	}

	public void tick(Block block, double range)
	{
		if(world != MC.level) reset();
		if(MC.level == null || MC.player == null || MC.gameMode == null
			|| MC.screen != null || perTick.getValueI() <= 0
			|| System.nanoTime() - lastAction < delay.getValueI() * 1_000_000L)
			return;
		int slot = InventoryUtils.indexOf(block.asItem(), 9);
		if(slot < 0) return;
		Vec3 eyes = MC.player.getEyePosition();
		List<BlockPos> candidates = BlockUtils.getAllInBoxStream(MC.player.blockPosition(), (int)Math.ceil(range))
			.filter(p -> MC.level.isInWorldBounds(p) && MC.level.hasChunkAt(p))
			.filter(p -> eyes.distanceToSqr(Vec3.atCenterOf(p)) <= range * range)
			.filter(p -> MC.level.getBlockState(p).canBeReplaced())
			.filter(p -> MC.level.getEntities((Entity)null, new AABB(p)).isEmpty())
			.sorted(Comparator.comparingDouble(p -> eyes.distanceToSqr(Vec3.atCenterOf(p))))
			.map(BlockPos::immutable).toList();
		int oldSlot = MC.player.getInventory().selected;
		recent.clear();
		try
		{
			MC.player.getInventory().selected = slot;
			for(BlockPos pos : candidates)
			{
				if(recent.size() >= perTick.getValueI() || !MC.player.getMainHandItem().is(block.asItem())) break;
				for(Direction direction : Direction.values())
				{
					if(!support.getSelected().accepts(direction)) continue;
					BlockPos neighbor = pos.relative(direction);
					if(!MC.level.hasChunkAt(neighbor) || !BlockUtils.canBeClicked(neighbor)
						|| MC.level.getBlockState(neighbor).canBeReplaced()
						|| MC.level.getBlockState(neighbor).getBlock() == block) continue;
					Direction face = direction.getOpposite();
					Vec3 point = hitPoint(neighbor, face, block instanceof SlabBlock, slabHalf.getSelected() == SlabHalf.TOP);
					if(eyes.distanceToSqr(point) > range * range) continue;
					WurstClient.INSTANCE.getRotationFaker().faceVectorPacket(point);
					MC.gameMode.useItemOn(MC.player, InteractionHand.MAIN_HAND, new BlockHitResult(point, face, neighbor, false));
					SwingHand.CLIENT.swing(InteractionHand.MAIN_HAND);
					recent.add(pos);
					break; // A position consumes one action even when several support faces exist.
				}
			}
		}finally
		{
			MC.player.getInventory().selected = oldSlot;
		}
		if(!recent.isEmpty()) lastAction = System.nanoTime();
	}

	public static Vec3 hitPoint(BlockPos support, Direction face, boolean slab, boolean upper)
	{
		Vec3 center = Vec3.atCenterOf(support).add(face.getStepX() * .5, face.getStepY() * .5, face.getStepZ() * .5);
		return slab && face.getAxis().isHorizontal() ? center.add(0, upper ? .1 : -.1, 0) : center;
	}

	public void render(PoseStack matrices)
	{
		if(MC.level != world || System.nanoTime() - lastAction > 250_000_000L) return;
		for(BlockPos pos : recent)
		{
			RenderUtils.drawSolidBox(matrices, new AABB(pos), 0x50FFFFFF, false);
			RenderUtils.drawOutlinedBox(matrices, new AABB(pos), 0x50FFFFFF, false);
		}
	}

	private enum SlabHalf {TOP, BOTTOM}
	private enum Support
	{
		UP, DOWN, HORIZONTAL;
		boolean accepts(Direction direction)
		{
			return this == HORIZONTAL ? direction.getAxis().isHorizontal()
				: direction == (this == UP ? Direction.UP : Direction.DOWN);
		}
	}
}

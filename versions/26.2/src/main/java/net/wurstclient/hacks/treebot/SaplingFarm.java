/*
 * AutoTree selection/planting behavior adapted from LeavesHack (AGPL-3.0).
 * SPDX-License-Identifier: AGPL-3.0
 */
package net.wurstclient.hacks.treebot;

import java.util.LinkedHashSet;
import java.util.Set;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.SaplingBlock;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.wurstclient.WurstClient;
import net.wurstclient.settings.CheckboxSetting;
import net.wurstclient.settings.ColorSetting;
import net.wurstclient.settings.EnumSetting;
import net.wurstclient.settings.Setting;
import net.wurstclient.settings.SliderSetting;
import net.wurstclient.settings.SliderSetting.ValueDisplay;
import net.wurstclient.util.RenderUtils;

public final class SaplingFarm
{
	private static final net.minecraft.client.Minecraft MC = WurstClient.MC;
	private final SliderSetting delay = new SliderSetting("Plant delay",
		"两批种植或施肥之间的毫秒数。", 50, 0, 1000, 10, ValueDisplay.INTEGER);
	private final SliderSetting blocksPerTick = new SliderSetting("Plants per tick", 1, 0, 4, 1, ValueDisplay.INTEGER);
	private final CheckboxSetting boneMeal = new CheckboxSetting("Use bone meal", true);
	private final EnumSetting<Shape> shape = new EnumSetting<>("Farm shape",
		"种植点的高亮方式。", Shape.values(), Shape.BOTH);
	private final ColorSetting lines = new ColorSetting("Farm line color", new java.awt.Color(255, 255, 255));
	private final ColorSetting sides = new ColorSetting("Farm fill color", new java.awt.Color(255, 255, 255));
	private final SliderSetting lineAlpha = new SliderSetting("Farm line opacity", 255, 0, 255, 1, ValueDisplay.INTEGER);
	private final SliderSetting fillAlpha = new SliderSetting("Farm fill opacity", 50, 0, 255, 1, ValueDisplay.INTEGER);
	private final Set<BlockPos> positions = new LinkedHashSet<>();
	private ClientLevel world;
	private long lastAction;

	public Setting[] settings()
	{
		return new Setting[]{delay, blocksPerTick, boneMeal, shape, lines, sides, lineAlpha, fillAlpha};
	}

	public void reset()
	{
		positions.clear();
		world = MC.level;
		lastAction = 0;
	}

	public boolean select()
	{
		if(MC.level == null || !(MC.hitResult instanceof BlockHitResult hit)
			|| hit.getType() != HitResult.Type.BLOCK)
			return false;
		if(world != MC.level)
			reset();
		BlockPos pos = hit.getBlockPos().immutable();
		if(MC.level.getBlockState(pos).getDestroySpeed(MC.level, pos) < 0)
			return false;
		if(!positions.remove(pos))
			positions.add(pos);
		return true;
	}

	public void tick(double range)
	{
		if(world != MC.level)
			reset();
		if(MC.player == null || MC.level == null || MC.gameMode == null
			|| MC.gui.screen() != null || positions.isEmpty()
			|| System.nanoTime() - lastAction < delay.getValueI() * 1_000_000L)
			return;
		if(!(MC.player.getMainHandItem().getItem() instanceof BlockItem item)
			|| !(item.getBlock() instanceof SaplingBlock))
			return;
		int oldSlot = MC.player.getInventory().getSelectedSlot();
		int boneSlot = -1;
		for(int slot = 0; slot < 9; slot++)
			if(MC.player.getInventory().getItem(slot).is(Items.BONE_MEAL))
			{
				boneSlot = slot;
				break;
			}
		if(boneMeal.isChecked() && boneSlot < 0)
			return;
		int used = 0;
		try
		{
			for(BlockPos base : positions)
			{
				if(used >= blocksPerTick.getValueI())
					break;
				BlockPos plant = base.above();
				if(!MC.level.hasChunkAt(base))
					continue;
				var state = MC.level.getBlockState(plant);
				boolean grow = state.getBlock() instanceof SaplingBlock && boneMeal.isChecked();
				if(!grow && !state.canBeReplaced())
					continue;
				BlockPos clicked = grow ? plant : base;
				Vec3 point = Vec3.atCenterOf(clicked).add(0, 0.5, 0);
				if(MC.player.getEyePosition().distanceToSqr(point) > range * range)
					continue;
				MC.player.getInventory().setSelectedSlot(grow ? boneSlot : oldSlot);
				WurstClient.INSTANCE.getRotationFaker().faceVectorPacket(point);
				MC.gameMode.useItemOn(MC.player, InteractionHand.MAIN_HAND,
					new BlockHitResult(point, Direction.UP, clicked, false));
				net.wurstclient.settings.SwingHandSetting.SwingHand.CLIENT.swing(InteractionHand.MAIN_HAND);
				used++;
			}
		}finally
		{
			MC.player.getInventory().setSelectedSlot(oldSlot);
		}
		if(used > 0)
			lastAction = System.nanoTime();
	}

	public void render(PoseStack matrices)
	{
		if(world != MC.level || MC.level == null)
			return;
		for(BlockPos pos : positions)
		{
			AABB box = new AABB(pos);
			if(shape.getSelected() != Shape.LINES)
				RenderUtils.drawSolidBox(matrices, box, fillAlpha.getValueI() << 24 | sides.getColorI() & 0xFFFFFF, false);
			if(shape.getSelected() != Shape.SIDES)
				RenderUtils.drawOutlinedBox(matrices, box, lineAlpha.getValueI() << 24 | lines.getColorI() & 0xFFFFFF, false);
		}
	}

	private enum Shape {LINES, SIDES, BOTH}
}

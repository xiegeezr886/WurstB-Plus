/*
 * Copyright (c) 2025 Penguin
 *
 * This source code is subject to the terms of the GNU General Public
 * License, version 3. If a copy of the GPL was not distributed with this
 * file, You can obtain one at: https://www.gnu.org/licenses/gpl-3.0.txt
 */
package net.wurstclient.hacks;

import net.minecraft.core.BlockPos;
import net.minecraft.network.protocol.game.ServerboundMovePlayerPacket;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.EmptyBlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.wurstclient.Category;
import net.wurstclient.SearchTags;
import net.wurstclient.events.UpdateListener;
import net.wurstclient.hack.Hack;
import net.wurstclient.settings.CheckboxSetting;
import net.wurstclient.settings.EnumSetting;
import net.wurstclient.settings.SwingHandSetting.SwingHand;
import net.wurstclient.util.BlockPlacer;
import net.wurstclient.util.BlockPlacer.BlockPlacingParams;
import net.wurstclient.util.BlockUtils;
import net.wurstclient.util.InteractionSimulator;
import net.wurstclient.util.RotationUtils;

@SearchTags({"burrow", "InstantBurrow", "block", "ClipIntoBlock"})
public final class BurrowHack extends Hack implements UpdateListener
{
	private final EnumSetting<Mode> mode = new EnumSetting<>("Mode",
		"\u00a7lInstant\u00a7r - Teleports you into a block instantly.\n"
			+ "\u00a7lSmooth\u00a7r - Smoothly clips into the block.\n"
			+ "\u00a7lRubberband\u00a7r - Uses rubberband to clip in.",
		Mode.values(), Mode.INSTANT);

	private final CheckboxSetting preferObsidian = new CheckboxSetting(
		"Prefer obsidian", "Prefers obsidian over other blocks.", true);

	private final CheckboxSetting disableAfter = new CheckboxSetting(
		"Disable after", "Disables after burrowing.", true);

	private int stage;

	public BurrowHack()
	{
		super("Burrow");
		setCategory(Category.COMBAT);
		addSetting(mode);
		addSetting(preferObsidian);
		addSetting(disableAfter);
	}

	@Override
	public String getRenderName()
	{
		return getName() + " [" + mode.getSelected() + "]";
	}

	@Override
	protected void onEnable()
	{
		stage = 0;
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
		BlockPos playerPos = BlockPos.containing(MC.player.position());

		if(!BlockUtils.getState(playerPos).canBeReplaced())
		{
			// 脚下方块已经不可替换：要么已经埋好了，要么本来就站在方块里。
			// 关掉 "Disable after" 时保持开启，等被挖出来之后再埋一次
			if(disableAfter.isChecked())
				setEnabled(false);
			return;
		}

		int slot = findBlockSlot();
		if(slot == -1)
		{
			setEnabled(false);
			return;
		}

		switch(mode.getSelected())
		{
			case INSTANT:
			doInstantBurrow(playerPos, slot);
			break;

			case SMOOTH:
			doSmoothBurrow(playerPos, slot);
			break;

			case RUBBERBAND:
			doRubberbandBurrow(playerPos, slot);
			break;
		}
	}

	private void doInstantBurrow(BlockPos pos, int slot)
	{
		placeBurrowBlock(pos, slot);

		if(disableAfter.isChecked())
			setEnabled(false);
	}

	private void doSmoothBurrow(BlockPos pos, int slot)
	{
		stage++;

		if(stage == 1)
		{
			double x = MC.player.getX();
			double z = MC.player.getZ();
			MC.player.connection.send(
				new ServerboundMovePlayerPacket.Pos(
					x, MC.player.getY() + 0.42, z, true));
		}
		else if(stage == 2)
		{
			placeBurrowBlock(pos, slot);
		}
		else if(stage >= 3)
		{
			if(disableAfter.isChecked())
				setEnabled(false);
		}
	}

	private void doRubberbandBurrow(BlockPos pos, int slot)
	{
		if(stage == 0)
		{
			MC.player.setPos(MC.player.getX(),
				MC.player.getY() + 0.42, MC.player.getZ());
			stage++;
			return;
		}

		placeBurrowBlock(pos, slot);

		if(disableAfter.isChecked())
			setEnabled(false);
	}

	/**
	 * 对着 pos 放一个方块。
	 *
	 * <p>
	 * 三个模式原来各自手写了一遍「遍历六个方向、拿邻居中心加 0.5 当命中点」。
	 * 对满方块那套算法和这里的结果一样，但台阶、雪层、箱子这类非满方块会被算出
	 * 一个落在方块外面的命中点，而且完全不看视线。共享的
	 * {@link BlockPlacer#getBlockPlacingParams(BlockPos)} 本来就是按方块的实际
	 * 形状算命中点、再挑有视线的面，直接用它。
	 */
	private void placeBurrowBlock(BlockPos pos, int slot)
	{
		BlockPlacingParams params = BlockPlacer.getBlockPlacingParams(pos);
		if(params == null)
			return;

		int oldSlot = MC.player.getInventory().selected;
		MC.player.getInventory().selected = slot;

		RotationUtils.getNeededRotations(params.hitVec()).sendPlayerLookPacket();
		InteractionSimulator.rightClickBlock(params.toHitResult(),
			SwingHand.CLIENT);

		MC.player.getInventory().selected = oldSlot;
	}

	private int findBlockSlot()
	{
		int bestNonObsidian = -1;
		for(int i = 0; i < 9; i++)
		{
			ItemStack stack = MC.player.getInventory().getItem(i);
			if(stack.isEmpty() || !(stack.getItem() instanceof BlockItem))
				continue;

			Block block = Block.byItem(stack.getItem());
			if(block == Blocks.TNT || block == Blocks.RESPAWN_ANCHOR
				|| block == Blocks.COBWEB || block == Blocks.AIR)
				continue;

			// 必须是能围住玩家的满方块。火把、台阶、雪层、栅栏之类放在脚下方块
			// 里挡不住人，原来的过滤却会照拿不误：白耗一个物品，hack 还以为自己
			// 埋成功了
			BlockState state = block.defaultBlockState();
			if(!state.isCollisionShapeFullBlock(EmptyBlockGetter.INSTANCE,
				BlockPos.ZERO))
				continue;

			if(preferObsidian.isChecked()
				&& (block == Blocks.OBSIDIAN
					|| block == Blocks.CRYING_OBSIDIAN))
				return i;

			if(bestNonObsidian == -1)
				bestNonObsidian = i;
		}
		return bestNonObsidian;
	}

	private enum Mode
	{
		INSTANT("Instant"),
		SMOOTH("Smooth"),
		RUBBERBAND("Rubberband");

		private final String name;

		Mode(String name)
		{
			this.name = name;
		}

		@Override
		public String toString()
		{
			return name;
		}
	}
}

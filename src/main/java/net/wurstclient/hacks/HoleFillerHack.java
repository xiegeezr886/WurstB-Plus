/*
 * Copyright (c) 2025 Penguin
 */
package net.wurstclient.hacks;

import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.wurstclient.Category;
import net.wurstclient.SearchTags;
import net.wurstclient.events.UpdateListener;
import net.wurstclient.hack.Hack;
import net.wurstclient.settings.CheckboxSetting;
import net.wurstclient.settings.SliderSetting;
import net.wurstclient.settings.SliderSetting.ValueDisplay;
import net.wurstclient.settings.SwingHandSetting;
import net.wurstclient.settings.SwingHandSetting.SwingHand;
import net.wurstclient.util.BlockPlacer;
import net.wurstclient.util.BlockUtils;
import net.wurstclient.util.HoleFillPolicy;

import java.util.ArrayList;
import java.util.Comparator;

@SearchTags({"hole filler", "HoleFiller", "fill holes"})
public final class HoleFillerHack extends Hack implements UpdateListener
{
	private final SliderSetting range = new SliderSetting("Range",
		5, 1, 8, 0.5, ValueDisplay.DECIMAL);

	private final CheckboxSetting onlyObsidian = new CheckboxSetting(
		"Only obsidian", "Only uses obsidian.", true);

	private final SwingHandSetting swingHand =
		new SwingHandSetting(this, SwingHand.CLIENT);

	private ArrayList<BlockPos> holes = new ArrayList<>();
	private int scanTimer;

	public HoleFillerHack()
	{
		super("HoleFiller");
		setCategory(Category.COMBAT);
		addSetting(range);
		addSetting(onlyObsidian);
		addSetting(swingHand);
	}

	@Override
	protected void onEnable()
	{
		holes.clear();
		scanTimer = 0;
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
		scanTimer++;
		if(scanTimer >= 10)
		{
			scanTimer = 0;
			scanHoles();
		}

		if(holes.isEmpty())
		{
			setEnabled(false);
			return;
		}

		holes.removeIf(p -> !BlockUtils.getState(p).canBeReplaced());

		if(holes.isEmpty())
		{
			setEnabled(false);
			return;
		}

		BlockPos closest = holes.stream()
			.filter(p -> !isOccupied(p))
			.min(Comparator.comparingDouble(
				p -> MC.player.distanceToSqr(Vec3.atCenterOf(p))))
			.orElse(null);

		if(closest == null)
			return;

		int slot = findBlockSlot();
		if(slot == -1)
		{
			setEnabled(false);
			return;
		}

		int oldSlot = MC.player.getInventory().selected;
		MC.player.getInventory().selected = slot;

		boolean placed = BlockPlacer.place(closest, false, false);
		if(placed)
			swingHand.swing(InteractionHand.MAIN_HAND);

		MC.player.getInventory().selected = oldSlot;
	}

	private void scanHoles()
	{
		holes.clear();
		int r = range.getValueI();
		BlockPos pp = BlockPos.containing(MC.player.position());
		HoleFillPolicy.BlockLookup blocks = this::blockKind;
		for(int x = -r; x <= r; x++)
			for(int z = -r; z <= r; z++)
				for(int y = -2; y <= 2; y++)
				{
					BlockPos pos = pp.offset(x, y, z);
					if(!HoleFillPolicy.isHole(blocks, pos.getX(), pos.getY(),
						pos.getZ()))
						continue;
					holes.add(pos);
				}
	}

	/**
	 * 参考 AutoHoleFill 的 getHoleInfos()：洞里已经有实体时不要填，玩家
	 * 自己占着的洞更要跳过。本项目的 SelfTrap/AutoWeb/AutoTrap/InstantBunker
	 * 在放置前也有同样一条判断；否则按距离排序后离玩家最近的那个洞往往
	 * 就是自己脚下这根，每次重试的都是这个填不进去的位置。
	 */
	private boolean isOccupied(BlockPos pos)
	{
		AABB playerBox = MC.player.getBoundingBox();
		if(HoleFillPolicy.isOccupiedBy(playerBox.minX, playerBox.minY,
			playerBox.minZ, playerBox.maxX, playerBox.maxY, playerBox.maxZ,
			pos.getX(), pos.getY(), pos.getZ()))
			return true;

		AABB holeBox = new AABB(pos.getX(), pos.getY(), pos.getZ(),
			pos.getX() + 1, pos.getY() + 2, pos.getZ() + 1);
		return !MC.level.getEntities(MC.player, holeBox).isEmpty();
	}

	/**
	 * 把方块的 {@code canBeReplaced()} 与是否为空气归纳成三态，供
	 * {@link HoleFillPolicy} 判定洞时使用。
	 */
	private HoleFillPolicy.BlockKind blockKind(int x, int y, int z)
	{
		BlockState state = BlockUtils.getState(new BlockPos(x, y, z));
		if(!state.canBeReplaced())
			return HoleFillPolicy.BlockKind.SOLID;
		return state.isAir() ? HoleFillPolicy.BlockKind.AIR
			: HoleFillPolicy.BlockKind.REPLACEABLE;
	}

	private int findBlockSlot()
	{
		for(int i = 0; i < 9; i++)
		{
			ItemStack s = MC.player.getInventory().getItem(i);
			if(s.isEmpty() || !(s.getItem() instanceof BlockItem))
				continue;
			Block b = Block.byItem(s.getItem());
			if(onlyObsidian.isChecked()
				&& b != Blocks.OBSIDIAN && b != Blocks.CRYING_OBSIDIAN
				&& b != Blocks.BEDROCK)
				continue;
			if(b == Blocks.TNT || b == Blocks.RESPAWN_ANCHOR)
				continue;
			return i;
		}
		return -1;
	}
}

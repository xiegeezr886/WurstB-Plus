package net.wurstclient.hacks;

import java.util.ArrayList;
import java.util.ArrayDeque;
import java.util.HashSet;
import java.util.List;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.wurstclient.Category;
import net.wurstclient.SearchTags;
import net.wurstclient.events.UpdateListener;
import net.wurstclient.hack.DontSaveState;
import net.wurstclient.hack.Hack;
import net.wurstclient.settings.CheckboxSetting;
import net.wurstclient.settings.SliderSetting;
import net.wurstclient.settings.SliderSetting.ValueDisplay;
import net.wurstclient.util.BaritoneUtils;
import net.wurstclient.util.BlockUtils;

@SearchTags({"baritone tree", "tree bot baritone", "tree feller",
	"auto tree"})
@DontSaveState
public final class BaritoneTreeBotHack extends Hack implements UpdateListener
{
	private final SliderSetting range = new SliderSetting("Range",
		"How far to search for trees.", 50, 10, 200, 1, ValueDisplay.INTEGER);

	private final CheckboxSetting replant = new CheckboxSetting("Replant",
		"Replants saplings after cutting a tree.\n"
			+ "Requires saplings in your inventory.",
		false);

	private TreeTarget currentTree;
	private int searchCooldown;

	public BaritoneTreeBotHack()
	{
		super("BaritoneTreeBot");
		setCategory(Category.BLOCKS);
		addSetting(range);
		addSetting(replant);
	}

	@Override
	protected boolean canEnable()
	{
		if(!BaritoneUtils.IS_AVAILABLE)
			return false;
		return MC.player != null;
	}

	@Override
	protected void onEnable()
	{
		EVENTS.add(UpdateListener.class, this);
		searchCooldown = 0;
		currentTree = null;
	}

	@Override
	protected void onDisable()
	{
		EVENTS.remove(UpdateListener.class, this);
		BaritoneUtils.stop();
		currentTree = null;
	}

	@Override
	public void onUpdate()
	{
		if(!BaritoneUtils.IS_AVAILABLE)
		{
			setEnabled(false);
			return;
		}

		if(currentTree != null && !currentTree.hasLogs())
		{
			if(replant.isChecked())
				replantSapling(currentTree.basePos);
			currentTree = null;
			BaritoneUtils.stop();
		}

		if(BaritoneUtils.isPathing())
			return;

		if(currentTree != null && currentTree.hasLogs())
		{
			mineTreeLogs(currentTree);
			return;
		}

		if(searchCooldown > 0)
		{
			searchCooldown--;
			return;
		}

		currentTree = findTree();
		if(currentTree != null)
		{
			BlockPos targetPos = currentTree.logs.get(0);
			BaritoneUtils.walkTo(targetPos);
		}else
			searchCooldown = 20;
	}

	private TreeTarget findTree()
	{
		if(MC.player == null || MC.level == null)
			return null;

		BlockPos playerPos = MC.player.blockPosition();
		int radius = range.getValueI();

		for(int x = -radius; x <= radius; x++)
			for(int z = -radius; z <= radius; z++)
				for(int y = -5; y <= 10; y++)
				{
					BlockPos pos = playerPos.offset(x, y, z);
					Block block = BlockUtils.getBlock(pos);

					if(isLog(block) && isTreeBase(pos))
					{
						TreeTarget tree = new TreeTarget(pos);
						collectTreeLogs(tree);
						if(!tree.logs.isEmpty())
							return tree;
					}
				}

		return null;
	}

	private boolean isLog(Block block)
	{
		return block == Blocks.OAK_LOG || block == Blocks.SPRUCE_LOG
			|| block == Blocks.BIRCH_LOG || block == Blocks.JUNGLE_LOG
			|| block == Blocks.ACACIA_LOG || block == Blocks.DARK_OAK_LOG
			|| block == Blocks.MANGROVE_LOG
			|| block == Blocks.CHERRY_LOG
			|| block == Blocks.CRIMSON_STEM || block == Blocks.WARPED_STEM;
	}

	private boolean isTreeBase(BlockPos pos)
	{
		Block below = BlockUtils.getBlock(pos.below());
		return !isLog(below)
			|| below == Blocks.DIRT || below == Blocks.GRASS_BLOCK
			|| below == Blocks.PODZOL || below == Blocks.COARSE_DIRT
			|| below == Blocks.ROOTED_DIRT || below == Blocks.MYCELIUM
			|| below == Blocks.NETHERRACK || below == Blocks.CRIMSON_NYLIUM
			|| below == Blocks.WARPED_NYLIUM;
	}

	private void collectTreeLogs(TreeTarget tree)
	{
		HashSet<BlockPos> visited = new HashSet<>();
		ArrayDeque<BlockPos> queue = new ArrayDeque<>();
		visited.add(tree.basePos);
		queue.add(tree.basePos);

		while(!queue.isEmpty() && tree.logs.size() < 512)
		{
			BlockPos current = queue.pollFirst();
			tree.logs.add(current);

			for(int dx = -1; dx <= 1; dx++)
				for(int dy = -1; dy <= 1; dy++)
					for(int dz = -1; dz <= 1; dz++)
					{
						if(dx == 0 && dy == 0 && dz == 0)
							continue;
						BlockPos neighbor = current.offset(dx, dy, dz);
						if(!visited.contains(neighbor)
							&& isLog(BlockUtils.getBlock(neighbor)))
						{
							visited.add(neighbor);
							queue.add(neighbor);
						}
					}
		}
	}

	private void mineTreeLogs(TreeTarget tree)
	{
		List<Block> logBlocks = new ArrayList<>();
		for(BlockPos pos : tree.logs)
		{
			Block block = BlockUtils.getBlock(pos);
			if(isLog(block))
				logBlocks.add(block);
		}

		if(!logBlocks.isEmpty())
			BaritoneUtils.startMining(
				logBlocks.toArray(new Block[0]));
	}

	private void replantSapling(BlockPos pos)
	{
		if(MC.player == null)
			return;

		Block block = BlockUtils.getBlock(pos);
		Block sapling = getSaplingForLog(block);

		if(sapling == null)
			return;

		int saplingSlot = findSaplingInInventory(sapling);
		if(saplingSlot == -1)
			return;

		int prevSlot = MC.player.getInventory().getSelectedSlot();
		MC.player.getInventory().setSelectedSlot(saplingSlot);

		net.minecraft.world.InteractionHand hand =
			net.minecraft.world.InteractionHand.MAIN_HAND;
		net.minecraft.world.phys.BlockHitResult hitResult =
			new net.minecraft.world.phys.BlockHitResult(
				net.minecraft.world.phys.Vec3.atCenterOf(pos.below()),
				net.minecraft.core.Direction.UP, pos.below(), false);

		MC.gameMode.useItemOn(MC.player, hand, hitResult);

		MC.player.getInventory().setSelectedSlot(prevSlot);
	}

	private Block getSaplingForLog(Block log)
	{
		if(log == Blocks.OAK_LOG)
			return Blocks.OAK_SAPLING;
		if(log == Blocks.SPRUCE_LOG)
			return Blocks.SPRUCE_SAPLING;
		if(log == Blocks.BIRCH_LOG)
			return Blocks.BIRCH_SAPLING;
		if(log == Blocks.JUNGLE_LOG)
			return Blocks.JUNGLE_SAPLING;
		if(log == Blocks.ACACIA_LOG)
			return Blocks.ACACIA_SAPLING;
		if(log == Blocks.DARK_OAK_LOG)
			return Blocks.DARK_OAK_SAPLING;
		if(log == Blocks.MANGROVE_LOG)
			return Blocks.MANGROVE_PROPAGULE;
		if(log == Blocks.CHERRY_LOG)
			return Blocks.CHERRY_SAPLING;
		return null;
	}

	private int findSaplingInInventory(Block sapling)
	{
		for(int i = 0; i < 36; i++)
		{
			net.minecraft.world.item.ItemStack stack =
				MC.player.getInventory().getItem(i);
			if(stack.getItem() == sapling.asItem())
				return i;
		}
		return -1;
	}

	@Override
	public String getRenderName()
	{
		if(isEnabled() && BaritoneUtils.isPathing())
		{
			if(currentTree != null && !currentTree.logs.isEmpty())
				return getName() + " [Chopping]";
			return getName() + " [Going]";
		}
		if(isEnabled() && currentTree == null)
			return getName() + " [Searching]";
		return getName();
	}

	private static class TreeTarget
	{
		final BlockPos basePos;
		final ArrayList<BlockPos> logs = new ArrayList<>();

		TreeTarget(BlockPos basePos)
		{
			this.basePos = basePos;
		}

		boolean hasLogs()
		{
			logs.removeIf(pos -> {
				Block block = BlockUtils.getBlock(pos);
				return block == Blocks.AIR || block == Blocks.CAVE_AIR
					|| block == Blocks.VOID_AIR;
			});
			return !logs.isEmpty();
		}
	}
}

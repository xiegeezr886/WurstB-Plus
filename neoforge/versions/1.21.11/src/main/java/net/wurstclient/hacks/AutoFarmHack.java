/*
 * Copyright (c) 2014-2025 Wurst-Imperium and contributors.
 *
 * This source code is subject to the terms of the GNU General Public
 * License, version 3. If a copy of the GPL was not distributed with this
 * file, You can obtain one at: https://www.gnu.org/licenses/gpl-3.0.txt
 */
package net.wurstclient.hacks;

import com.mojang.blaze3d.vertex.PoseStack;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import net.minecraft.core.BlockPos;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.BambooStalkBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CactusBlock;
import net.minecraft.world.level.block.CocoaBlock;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.block.FarmBlock;
// FarmBlock removed in MC 26.1.2
import net.minecraft.world.level.block.KelpPlantBlock;
import net.minecraft.world.level.block.NetherWartBlock;
import net.minecraft.world.level.block.SoulSandBlock;
import net.minecraft.world.level.block.SugarCaneBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.wurstclient.Category;
import net.wurstclient.SearchTags;
import net.wurstclient.events.RenderListener;
import net.wurstclient.events.UpdateListener;
import net.wurstclient.hack.Hack;
import net.wurstclient.hacks.autofarm.AutoFarmRenderer;
import net.wurstclient.settings.CheckboxSetting;
import net.wurstclient.settings.SliderSetting;
import net.wurstclient.settings.SliderSetting.ValueDisplay;
import net.wurstclient.settings.SwingHandSetting.SwingHand;
import net.wurstclient.util.BlockBreaker;
import net.wurstclient.util.BlockBreakingCache;
import net.wurstclient.util.BlockPlacer;
import net.wurstclient.util.BlockPlacer.BlockPlacingParams;
import net.wurstclient.util.BlockUtils;
import net.wurstclient.util.InventoryUtils;
import net.wurstclient.util.OverlayRenderer;
import net.wurstclient.util.RotationUtils;

@SearchTags({"auto farm", "AutoHarvest", "auto harvest"})
public final class AutoFarmHack extends Hack
	implements UpdateListener, RenderListener
{
	private final SliderSetting range =
		new SliderSetting("Range", 5, 1, 6, 0.05, ValueDisplay.DECIMAL);
	
	private final CheckboxSetting replant =
		new CheckboxSetting("Replant", true);
	
	private final HashMap<Block, Item> seeds = new HashMap<>();
	{
		seeds.put(Blocks.WHEAT, Items.WHEAT_SEEDS);
		seeds.put(Blocks.CARROTS, Items.CARROT);
		seeds.put(Blocks.POTATOES, Items.POTATO);
		seeds.put(Blocks.BEETROOTS, Items.BEETROOT_SEEDS);
		seeds.put(Blocks.PUMPKIN_STEM, Items.PUMPKIN_SEEDS);
		seeds.put(Blocks.MELON_STEM, Items.MELON_SEEDS);
		seeds.put(Blocks.NETHER_WART, Items.NETHER_WART);
		seeds.put(Blocks.COCOA, Items.COCOA_BEANS);
	}
	
	private final HashMap<BlockPos, Item> plants = new HashMap<>();
	private final BlockBreakingCache cache = new BlockBreakingCache();
	private BlockPos currentlyHarvesting;
	
	private final AutoFarmRenderer renderer = new AutoFarmRenderer();
	private final OverlayRenderer overlay = new OverlayRenderer();
	
	private boolean busy;
	
	public AutoFarmHack()
	{
		super("AutoFarm");
		
		setCategory(Category.BLOCKS);
		addSetting(range);
		addSetting(replant);
	}
	
	@Override
	protected void onEnable()
	{
		plants.clear();
		
		EVENTS.add(UpdateListener.class, this);
		EVENTS.add(RenderListener.class, this);
	}
	
	@Override
	protected void onDisable()
	{
		EVENTS.remove(UpdateListener.class, this);
		EVENTS.remove(RenderListener.class, this);
		
		if(currentlyHarvesting != null)
		{
			// TODO: 26.1.2 - isDestroying is now private
			MC.gameMode.isDestroying = true;
			MC.gameMode.stopDestroyBlock();
			currentlyHarvesting = null;
		}
		
		cache.reset();
		overlay.resetProgress();
		busy = false;
		
		renderer.reset();
	}
	
	@Override
	public void onUpdate()
	{
		currentlyHarvesting = null;
		Vec3 eyesVec = RotationUtils.getEyesPos();
		BlockPos eyesBlock = BlockPos.containing(eyesVec);
		double rangeSq = range.getValueSq();
		int blockRange = range.getValueCeil();
		
		// get nearby, non-empty blocks
		ArrayList<BlockPos> blocks =
			BlockUtils.getAllInBoxStream(eyesBlock, blockRange)
				.filter(pos -> pos.distToCenterSqr(eyesVec) <= rangeSq)
				.filter(BlockUtils::canBeClicked)
				.collect(Collectors.toCollection(ArrayList::new));
		
		// check for any new plants and add them to the map
		updatePlants(blocks);
		
		ArrayList<BlockPos> blocksToHarvest = new ArrayList<>();
		ArrayList<BlockPos> blocksToReplant = new ArrayList<>();
		
		// don't place or break any blocks while Freecam is enabled
		if(!WURST.getHax().freecamHack.isEnabled())
		{
			// check which of the nearby blocks can be harvested
			blocksToHarvest = getBlocksToHarvest(eyesVec, blocks);
			
			// do a new search to find empty blocks that can be replanted
			if(replant.isChecked())
				blocksToReplant =
					getBlocksToReplant(eyesVec, eyesBlock, rangeSq, blockRange);
		}
		
		// first, try to replant
		boolean replanting = replant(blocksToReplant);
		
		// if we can't replant, harvest instead
		if(!replanting)
			harvest(blocksToHarvest.stream());
		
		// update busy state
		busy = replanting || currentlyHarvesting != null;
		
		// update renderer
		renderer.updateVertexBuffers(blocksToHarvest, plants.keySet(),
			blocksToReplant);
	}
	
	@Override
	public void onRender(PoseStack matrixStack, float partialTicks)
	{
		renderer.render(matrixStack);
		overlay.render(matrixStack, partialTicks, currentlyHarvesting);
	}
	
	/**
	 * Returns true if AutoFarm is currently harvesting or replanting something.
	 */
	public boolean isBusy()
	{
		return busy;
	}
	
	private void updatePlants(List<BlockPos> blocks)
	{
		for(BlockPos pos : blocks)
		{
			Item seed = seeds.get(BlockUtils.getBlock(pos));
			if(seed == null)
				continue;
			
			plants.put(pos, seed);
		}
	}
	
	private ArrayList<BlockPos> getBlocksToHarvest(Vec3 eyesVec,
		ArrayList<BlockPos> blocks)
	{
		return blocks.parallelStream().filter(this::shouldBeHarvested)
			.sorted(Comparator
				.comparingDouble(pos -> pos.distToCenterSqr(eyesVec)))
			.collect(Collectors.toCollection(ArrayList::new));
	}
	
	private boolean shouldBeHarvested(BlockPos pos)
	{
		Block block = BlockUtils.getBlock(pos);
		BlockState state = BlockUtils.getState(pos);
		
		if(block instanceof CropBlock)
			return ((CropBlock)block).isMaxAge(state);
		
		if(block instanceof NetherWartBlock)
			return state.getValue(NetherWartBlock.AGE) >= 3;
		
		if(block instanceof CocoaBlock)
			return state.getValue(CocoaBlock.AGE) >= 2;
		
		if(block == Blocks.MELON || block == Blocks.PUMPKIN)
			return true;
		
		if(block instanceof SugarCaneBlock)
			return BlockUtils.getBlock(pos.below()) instanceof SugarCaneBlock
				&& !(BlockUtils
					.getBlock(pos.below(2)) instanceof SugarCaneBlock);
		
		if(block instanceof CactusBlock)
			return BlockUtils.getBlock(pos.below()) instanceof CactusBlock
				&& !(BlockUtils.getBlock(pos.below(2)) instanceof CactusBlock);
		
		if(block instanceof KelpPlantBlock)
			return BlockUtils.getBlock(pos.below()) instanceof KelpPlantBlock
				&& !(BlockUtils
					.getBlock(pos.below(2)) instanceof KelpPlantBlock);
		
		if(block instanceof BambooStalkBlock)
			return BlockUtils.getBlock(pos.below()) instanceof BambooStalkBlock
				&& !(BlockUtils.getBlock(pos.below(2)) instanceof BambooStalkBlock);
		
		return false;
	}
	
	private ArrayList<BlockPos> getBlocksToReplant(Vec3 eyesVec,
		BlockPos eyesBlock, double rangeSq, int blockRange)
	{
		return BlockUtils.getAllInBoxStream(eyesBlock, blockRange)
			.filter(pos -> pos.distToCenterSqr(eyesVec) <= rangeSq)
			.filter(pos -> BlockUtils.getState(pos).canBeReplaced())
			.filter(pos -> plants.containsKey(pos)).filter(this::canBeReplanted)
			.sorted(Comparator
				.comparingDouble(pos -> pos.distToCenterSqr(eyesVec)))
			.collect(Collectors.toCollection(ArrayList::new));
	}
	
	private boolean canBeReplanted(BlockPos pos)
	{
		Item item = plants.get(pos);
		
		if(item == Items.WHEAT_SEEDS || item == Items.CARROT
			|| item == Items.POTATO || item == Items.BEETROOT_SEEDS
			|| item == Items.PUMPKIN_SEEDS || item == Items.MELON_SEEDS)
			return BlockUtils.getBlock(pos.below()) instanceof FarmBlock;
		
		if(item == Items.NETHER_WART)
			return BlockUtils.getBlock(pos.below()) instanceof SoulSandBlock;
		
		if(item == Items.COCOA_BEANS)
			return BlockUtils.getState(pos.north()).is(BlockTags.JUNGLE_LOGS)
				|| BlockUtils.getState(pos.east()).is(BlockTags.JUNGLE_LOGS)
				|| BlockUtils.getState(pos.south()).is(BlockTags.JUNGLE_LOGS)
				|| BlockUtils.getState(pos.west()).is(BlockTags.JUNGLE_LOGS);
		
		return false;
	}
	
	private boolean replant(List<BlockPos> blocksToReplant)
	{
		// TODO: 26.1.2 - rightClickDelay is private in Forge
		// if(false) // TODO: 26.1.2 - rightClickDelay is private
		// 	return false;
		
		// check if already holding one of the seeds needed for blocksToReplant
		Optional<Item> heldSeed = blocksToReplant.stream().map(plants::get)
			.distinct().filter(item -> MC.player.isHolding(item)).findFirst();
		
		// if so, try to replant the blocks that need that seed
		if(heldSeed.isPresent())
		{
			// get the seed and the hand that is holding it
			Item item = heldSeed.get();
			InteractionHand hand = MC.player.getMainHandItem().is(item) ? InteractionHand.MAIN_HAND
				: InteractionHand.OFF_HAND;
			
			// filter out blocks that need a different seed
			ArrayList<BlockPos> blocksToReplantWithHeldSeed =
				blocksToReplant.stream().filter(pos -> plants.get(pos) == item)
					.collect(Collectors.toCollection(ArrayList::new));
			
			for(BlockPos pos : blocksToReplantWithHeldSeed)
			{
				// skip over blocks that we can't reach
				BlockPlacingParams params =
					BlockPlacer.getBlockPlacingParams(pos);
				if(params == null || params.distanceSq() > range.getValueSq())
					continue;
				
				// face block
				WURST.getRotationFaker().faceVectorPacket(params.hitVec());
				
				// place seed
				InteractionResult result = MC.gameMode
					.useItemOn(MC.player, hand, params.toHitResult());
				
				// swing arm
				if(result.consumesAction())
					SwingHand.SERVER.swing(hand);
				
				// reset cooldown
				// TODO: 26.1.2 - rightClickDelay is private in Forge
				// // MC.rightClickDelay = 4; // TODO: 26.1.2 - rightClickDelay is private
				return true;
			}
		}
		
		// otherwise, find a block that we can reach and have seeds for
		for(BlockPos pos : blocksToReplant)
		{
			// skip over blocks that we can't reach
			BlockPlacingParams params = BlockPlacer.getBlockPlacingParams(pos);
			if(params == null || params.distanceSq() > range.getValueSq())
				continue;
			
			// try to select the seed (returns false if we don't have it)
			Item item = plants.get(pos);
			if(InventoryUtils.selectItem(item))
				return true;
		}
		
		// if we couldn't replant anything, return false
		return false;
	}
	
	private void harvest(Stream<BlockPos> stream)
	{
		// Break all blocks in creative mode
		if(MC.player.getAbilities().instabuild)
		{
			MC.gameMode.stopDestroyBlock();
			overlay.resetProgress();
			
			ArrayList<BlockPos> blocks = cache.filterOutRecentBlocks(stream);
			if(blocks.isEmpty())
				return;
			
			currentlyHarvesting = blocks.get(0);
			BlockBreaker.breakBlocksWithPacketSpam(blocks);
			return;
		}
		
		// Break the first valid block in survival mode
		currentlyHarvesting =
			stream.filter(BlockBreaker::breakOneBlock).findFirst().orElse(null);
		
		if(currentlyHarvesting == null)
		{
			MC.gameMode.stopDestroyBlock();
			overlay.resetProgress();
			return;
		}
		
		overlay.updateProgress();
	}
}

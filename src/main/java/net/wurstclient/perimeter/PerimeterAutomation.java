/*
 * Copyright (c) 2014-2025 Wurst-Imperium and contributors.
 *
 * This source code is subject to the terms of the GNU General Public
 * License, version 3. If a copy of the GPL was not distributed with this
 * file, You can obtain one at: https://www.gnu.org/licenses/gpl-3.0.txt
 */
package net.wurstclient.perimeter;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import baritone.api.IBaritone;
import baritone.api.pathing.goals.Goal;
import baritone.api.pathing.goals.GoalBlock;
import baritone.api.pathing.goals.GoalComposite;
import baritone.api.utils.input.Input;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.wurstclient.WurstClient;
import net.wurstclient.perimeter.config.PerimeterAdvancedConfig;
import net.wurstclient.perimeter.config.PerimeterConfig;
import net.wurstclient.perimeter.config.PerimeterPosition;
import net.wurstclient.perimeter.config.PerimeterUnloadingPoint;

/**
 * The perimeter automation itself: it drives a mining batch, collects the
 * drops, eats, keeps the tools and the Elytra serviceable, travels to the
 * unloading shaft, the supply chests, the repair furnaces and the bed, and
 * remembers what it has been doing.
 *
 * <p>
 * The state machine, the batch rules and the flow order are ported from the
 * reference mod. Where the reference relies on its own Baritone fork (liquid
 * aware breaking, the area mine process, placeholders for its area status), the
 * port uses the schematic based mining job in this package instead.
 */
public final class PerimeterAutomation
{
	private static final int MONITOR_INTERVAL_TICKS = 10;
	private static final int UNLOAD_SETTLE_TICKS = 5;
	private static final int ITEM_COLLECTION_SETTLE_TICKS = 20;
	private static final int MAX_RETURN_ATTEMPTS = 3;
	private static final double ARRIVAL_DISTANCE_SQUARED = 2.25;
	
	private final PerimeterMiningJob job = new PerimeterMiningJob();
	private final PerimeterNavigation navigation = new PerimeterNavigation();
	private final PerimeterInteractionPositions positions =
		new PerimeterInteractionPositions(2, 9.0);
	private final PerimeterStateHistory history = new PerimeterStateHistory();
	
	private PerimeterConfig config = new PerimeterConfig();
	private PerimeterColumnArea area;
	private PerimeterLiquidPolicy liquidPolicy =
		PerimeterLiquidPolicy.SEAL_BOUNDARY;
	private List<net.minecraft.world.level.block.Block> sealingBlocks =
		List.of();
	private Set<Item> foods = Set.of();
	private Set<Item> whitelist = Set.of();
	
	private boolean running;
	private PerimeterAutomationState state = PerimeterAutomationState.IDLE;
	private PerimeterAutomationState stateBeforePause =
		PerimeterAutomationState.IDLE;
	private PerimeterAutomationState stateBeforeEating =
		PerimeterAutomationState.IDLE;
	private String detail = "stopped";
	private int tickCounter;
	private int monitorTicks;
	
	private String unloadPointName;
	private PerimeterUnloadingPoint unloadPoint;
	private List<BlockPos> unloadCandidates = List.of();
	private int unloadCandidateIndex;
	private Vec3 unloadEdgePosition;
	private int unloadSettleTicks;
	private boolean miningCompletePending;
	private int stableInventoryScans;
	private int lastInventoryItemCount;
	
	private BlockPos miningReturnPoint;
	private String miningReturnDimension;
	private int returnAttempts;
	
	private boolean supplyDurability;
	private PerimeterPosition supplyPoint;
	private int supplyFlightAttempts;
	private int interactionTicks;
	private boolean sleeping;
	private int sleepTicks;
	private int repairTicks;
	private int furnaceIndex;
	
	private BlockPos watchdogTarget;
	private Vec3 watchdogPosition;
	private int watchdogStationaryScans;
	private int watchdogRetries;
	
	// ------------------------------------------------------------------
	// lifecycle
	// ------------------------------------------------------------------
	
	/**
	 * @return the problems that stop the automation, empty when it can start.
	 */
	public List<String> start(PerimeterConfig config)
	{
		List<String> problems = validate(config);
		
		if(!problems.isEmpty())
			return problems;
		
		stopProcesses();
		this.config = config;
		liquidPolicy = config.liquidPolicyValue();
		sealingBlocks = blockList(config);
		foods = itemSet(config.foods);
		whitelist = itemSet(config.unloadingWhitelist);
		area = buildArea(config);
		
		if(!navigation.bind())
			return List.of("Baritone is not available.");
		
		history.clear();
		stableInventoryScans = 0;
		lastInventoryItemCount = inventoryItemCount();
		miningCompletePending = false;
		returnAttempts = 0;
		running = true;
		tickCounter = 0;
		monitorTicks = 0;
		miningReturnPoint = null;
		miningReturnDimension = null;
		startMiningCycle();
		return List.of();
	}
	
	public void pause()
	{
		if(!running || state == PerimeterAutomationState.PAUSED)
			return;
		
		stateBeforePause = state;
		
		if(state == PerimeterAutomationState.MINING && job.isActive())
			job.pause();
		
		navigation.stop();
		transition(PerimeterAutomationState.PAUSED, "paused");
	}
	
	public void resume()
	{
		if(!running || state != PerimeterAutomationState.PAUSED)
			return;
		
		PerimeterAutomationState target =
			stateBeforePause == PerimeterAutomationState.IDLE
				? PerimeterAutomationState.MINING : stateBeforePause;
		
		if(target == PerimeterAutomationState.MINING && job.isActive())
		{
			job.resume();
			transition(PerimeterAutomationState.MINING, "resumed");
			return;
		}
		
		if(target == PerimeterAutomationState.MINING)
		{
			startMiningCycle();
			return;
		}
		
		transition(target, "resumed");
	}
	
	public void stop()
	{
		stopProcesses();
		running = false;
		transition(PerimeterAutomationState.IDLE, "stopped");
	}
	
	/**
	 * Called when the client joins another world or leaves one.
	 */
	public void onWorldChanged()
	{
		stopProcesses();
		navigation.unbind();
		running = false;
		miningReturnPoint = null;
		miningReturnDimension = null;
		stateBeforePause = PerimeterAutomationState.IDLE;
		stateBeforeEating = PerimeterAutomationState.IDLE;
		transition(PerimeterAutomationState.IDLE, "world changed");
	}
	
	public void onTick()
	{
		if(!running || WurstClient.MC.player == null
			|| WurstClient.MC.level == null)
			return;
		
		tickCounter++;
		
		try
		{
			switch(state)
			{
				case MINING -> tickMining();
				case COLLECTING_DROPS -> tickDropCollection();
				case EATING -> tickEating();
				case NAVIGATING_TO_MINE -> tickNavigateToMine();
				case NAVIGATING_TO_UNLOAD -> tickNavigateToUnload();
				case APPROACHING_UNLOAD -> tickApproachingUnload();
				case POSITIONING_FOR_UNLOAD -> tickPositioningForUnload();
				case UNLOADING -> tickUnloading();
				case RETURNING_TO_MINE -> tickReturningToMine();
				case NAVIGATING_TO_RESUPPLY, NAVIGATING_TO_DURABILITY_SUPPLY ->
					tickSupplyNavigation();
				case RESUPPLYING, SWAPPING_DURABILITY_AT_SUPPLY ->
					tickSupplyInteraction();
				case NAVIGATING_TO_BED -> tickBedNavigation();
				case SLEEPING -> tickSleeping();
				case NAVIGATING_TO_PERIMETER_PORTAL,
					NAVIGATING_TO_REPAIR_PORTAL, ENTERING_PERIMETER_PORTAL,
					ENTERING_REPAIR_PORTAL, CLEARING_REPAIR_PORTAL,
					NAVIGATING_TO_REPAIR_MACHINE ->
					tickRepairNavigation();
				case REPAIRING -> tickRepairing();
				case PAUSED -> tickPaused();
				default -> {}
			}
			
			if(++monitorTicks >= MONITOR_INTERVAL_TICKS)
			{
				monitorTicks = 0;
				monitor();
			}
		}catch(Exception e)
		{
			fail(e);
		}
	}
	
	private void tickPaused()
	{
		if(job.isActive() && !job.isPaused())
			job.pause();
		
		navigation.stop();
	}
	
	private void fail(Exception e)
	{
		e.printStackTrace();
		stopProcesses();
		transition(PerimeterAutomationState.ERROR,
			PerimeterText.get("error: %s", e.getClass().getSimpleName()
				+ (e.getMessage() == null ? "" : ": " + e.getMessage())));
	}
	
	// ------------------------------------------------------------------
	// mining
	// ------------------------------------------------------------------
	
	private void startMiningCycle()
	{
		long limit = PerimeterInventoryPolicy.batchLimit(emptyInventorySlots(),
			config.advanced.inventoryReservedSlots,
			config.advanced.miningBlocksPerEmptySlot);
		
		navigation.enablePlacementInFluids();
		navigation.disablePlacement();
		
		if(!job.start(baritone(), area, liquidPolicy, sealingBlocks,
			disallowedBlocks(), worldView(), limit))
		{
			transition(PerimeterAutomationState.ERROR,
				"could not start the mining batch");
			return;
		}
		
		miningCompletePending = false;
		transition(PerimeterAutomationState.MINING,
			"mining batch of %d blocks", limit);
	}
	
	private void tickMining()
	{
		if(!job.isActive())
		{
			beginDropCollection(true);
			return;
		}
		
		job.tick(feet(), worldView());
		
		if(!job.isActive())
		{
			beginDropCollection(true);
			return;
		}
		
		if(job.isLimitReached())
		{
			beginDropCollection(false);
			return;
		}
		
		synchronize(PerimeterAutomationState.MINING,
			"mined %d/%d this batch", job.getMinedBlocks(),
				job.getBlockLimit());
	}
	
	private void beginDropCollection(boolean miningComplete)
	{
		miningCompletePending = miningComplete;
		job.cancel();
		
		if(!config.functions.collectDrops)
		{
			navigation.stopWalking();
			continueAfterCollection();
			return;
		}
		
		navigation.disablePlacement();
		stableInventoryScans = 0;
		lastInventoryItemCount = inventoryItemCount();
		transition(PerimeterAutomationState.COLLECTING_DROPS,
			"collecting drops within %d blocks",
				config.advanced.dropCollectionRadius);
		tickDropCollection();
	}
	
	private void tickDropCollection()
	{
		if(emptyInventorySlots() == 0)
		{
			continueAfterCollection();
			return;
		}
		
		int itemCount = inventoryItemCount();
		
		if(itemCount > lastInventoryItemCount)
			stableInventoryScans = 0;
		else
			stableInventoryScans++;
		
		lastInventoryItemCount = itemCount;
		
		if(stableInventoryScans >= dropStableScanLimit())
		{
			navigation.stopWalking();
			continueAfterCollection();
			return;
		}
		
		List<Goal> goals = nearbyDropGoals();
		
		if(goals.isEmpty())
		{
			navigation.stopWalking();
			synchronize(PerimeterAutomationState.COLLECTING_DROPS,
				"waiting for the inventory to settle (%d items)",
					itemCount);
			return;
		}
		
		navigation.walk(new GoalComposite(goals.toArray(new Goal[0])));
		synchronize(PerimeterAutomationState.COLLECTING_DROPS,
			"walking to %d drop(s)", goals.size());
	}
	
	private void continueAfterCollection()
	{
		navigation.restoreDestinationSettings();
		
		if(!miningCompletePending && emptyInventorySlots() > 0)
		{
			startMiningCycle();
			return;
		}
		
		if(config.functions.unload && !config.unloadingPoints.isEmpty())
		{
			requestUnload();
			return;
		}
		
		if(miningCompletePending)
			transition(PerimeterAutomationState.COMPLETE,
				"mining complete");
		else
			startMiningCycle();
	}
	
	private List<Goal> nearbyDropGoals()
	{
		List<Goal> goals = new ArrayList<>();
		double radius = config.advanced.dropCollectionRadius;
		double radiusSquared = radius * radius;
		Player player = WurstClient.MC.player;
		AABB box = player.getBoundingBox().inflate(radius);
		
		for(ItemEntity entity : WurstClient.MC.level
			.getEntitiesOfClass(ItemEntity.class, box))
		{
			if(!entity.isAlive())
				continue;
			
			double dx = entity.getX() - player.getX();
			double dz = entity.getZ() - player.getZ();
			
			if(dx * dx + dz * dz > radiusSquared)
				continue;
			
			goals.add(new GoalBlock(BlockPos.containing(entity.getX(),
				entity.getY() + 0.1, entity.getZ())));
		}
		
		return goals;
	}
	
	private int dropStableScanLimit()
	{
		return monitorScans(config.advanced.dropCollectionStableSeconds);
	}
	
	// ------------------------------------------------------------------
	// unloading
	// ------------------------------------------------------------------
	
	private void requestUnload()
	{
		PerimeterUnloadingPoint best = null;
		String bestName = null;
		long bestDistance = Long.MAX_VALUE;
		BlockPos player = feet();
		
		for(var entry : config.unloadingPoints.entrySet())
		{
			PerimeterUnloadingPoint point = entry.getValue();
			long dx = (long)player.getX() - point.x;
			long dz = (long)player.getZ() - point.z;
			long distance = dx * dx + dz * dz;
			
			if(distance < bestDistance)
			{
				bestDistance = distance;
				best = point;
				bestName = entry.getKey();
			}
		}
		
		if(best == null)
		{
			continueAfterCollection();
			return;
		}
		
		miningReturnPoint = player;
		miningReturnDimension = dimensionId();
		unloadPoint = best;
		unloadPointName = bestName;
		unloadCandidates = findUnloadCandidates(best);
		unloadCandidateIndex = 0;
		unloadEdgePosition = null;
		unloadSettleTicks = 0;
		
		navigation.disablePlacement();
		
		if(unloadCandidates.isEmpty())
		{
			transition(PerimeterAutomationState.ERROR,
				"no safe standing position near unloading point %s",
				unloadPointName);
			return;
		}
		
		transition(PerimeterAutomationState.NAVIGATING_TO_UNLOAD,
			"travelling to unloading point %s",
			unloadPointName);
		pathToUnloadCandidate();
	}
	
	private List<BlockPos> findUnloadCandidates(
		PerimeterUnloadingPoint point)
	{
		List<BlockPos> candidates = new ArrayList<>();
		int radius = config.advanced.unloadLandingSearchRadius;
		int preferredY = feet().getY();
		int minY = WurstClient.MC.level.getMinBuildHeight() + 1;
		int maxY = WurstClient.MC.level.getMaxBuildHeight() - 2;
		BlockPos player = feet();
		
		for(int dx = -radius; dx <= radius; dx++)
			for(int dz = -radius; dz <= radius; dz++)
			{
				int horizontalSquared = dx * dx + dz * dz;
				
				if(horizontalSquared == 0
					|| horizontalSquared > radius * radius)
					continue;
				
				BlockPos position = PerimeterInteractionPositions
					.closestSafeStandingPosition(WurstClient.MC.level,
						point.x + dx, point.z + dz, preferredY, minY, maxY);
				
				if(position != null)
					candidates.add(position);
			}
		
		candidates.sort(Comparator
			.comparingInt((BlockPos candidate) -> horizontalDistanceSquared(
				player, candidate))
			.thenComparingInt(candidate -> Math
				.abs(candidate.getY() - preferredY))
			.thenComparingInt(candidate -> -candidate.getY())
			.thenComparingDouble(candidate -> candidate.distSqr(player)));
		
		return candidates;
	}
	
	private static int horizontalDistanceSquared(BlockPos first,
		BlockPos second)
	{
		int dx = first.getX() - second.getX();
		int dz = first.getZ() - second.getZ();
		return dx * dx + dz * dz;
	}
	
	private void pathToUnloadCandidate()
	{
		if(unloadCandidateIndex >= unloadCandidates.size())
		{
			transition(PerimeterAutomationState.ERROR,
				"could not reach unloading point %s",
				unloadPointName);
			return;
		}
		
		navigation.walk(
			new GoalBlock(unloadCandidates.get(unloadCandidateIndex)));
	}
	
	private void tickNavigateToUnload()
	{
		BlockPos target = unloadCandidates.get(unloadCandidateIndex);
		BlockPos player = feet();
		
		if(horizontalDistanceSquared(player, target) <= 4
			&& Math.abs(player.getY() - target.getY()) <= 2)
		{
			beginUnloadApproach(target);
			return;
		}
		
		if(!navigation.isWalking())
		{
			unloadCandidateIndex++;
			pathToUnloadCandidate();
			return;
		}
		
		if(config.functions.elytraNavigation && canFly()
			&& horizontalDistanceSquared(player, target) > 32 * 32)
		{
			navigation.fly(target);
			return;
		}
		
		synchronize(PerimeterAutomationState.NAVIGATING_TO_UNLOAD,
			"travelling to unloading point %s",
			unloadPointName);
	}
	
	private void beginUnloadApproach(BlockPos standing)
	{
		unloadEdgePosition = PerimeterInteractionPositions.unloadEdgePosition(
			standing, unloadPoint.x, unloadPoint.z,
			config.advanced.unloadEdgeInset);
		navigation.stopWalking();
		transition(PerimeterAutomationState.APPROACHING_UNLOAD,
			"approaching the edge of %s", unloadPointName);
	}
	
	private void tickApproachingUnload()
	{
		if(unloadEdgePosition == null)
		{
			beginUnloadApproach(unloadCandidates.get(unloadCandidateIndex));
			return;
		}
		
		Vec3 playerPosition = WurstClient.MC.player.position();
		double dx = unloadEdgePosition.x - playerPosition.x;
		double dz = unloadEdgePosition.z - playerPosition.z;
		
		if(dx * dx + dz * dz <= 0.04)
		{
			forceInput(Input.MOVE_FORWARD, false);
			unloadSettleTicks = UNLOAD_SETTLE_TICKS;
			transition(PerimeterAutomationState.POSITIONING_FOR_UNLOAD,
				PerimeterText.get("facing the shaft at %s",
			unloadPointName));
			return;
		}
		
		forceInput(Input.SNEAK, true);
		lookAt(new Vec3(unloadEdgePosition.x,
			WurstClient.MC.player.getEyeY(), unloadEdgePosition.z));
		forceInput(Input.MOVE_FORWARD, true);
		synchronize(PerimeterAutomationState.APPROACHING_UNLOAD,
			"positioning at %s", unloadPointName);
	}
	
	private void tickPositioningForUnload()
	{
		forceInput(Input.SNEAK, true);
		forceInput(Input.MOVE_FORWARD, false);
		lookAt(new Vec3(unloadPoint.x + 0.5, unloadPoint.minY + 0.5,
			unloadPoint.z + 0.5));
		WurstClient.MC.player.setXRot(90.0F);
		
		if(unloadSettleTicks > 0)
		{
			unloadSettleTicks--;
			return;
		}
		
		transition(PerimeterAutomationState.UNLOADING,
			"dropping products into %s", unloadPointName);
	}
	
	private void tickUnloading()
	{
		forceInput(Input.SNEAK, true);
		forceInput(Input.MOVE_FORWARD, false);
		lookAt(new Vec3(unloadPoint.x + 0.5, unloadPoint.minY + 0.5,
			unloadPoint.z + 0.5));
		WurstClient.MC.player.setXRot(90.0F);
		
		if(unloadSettleTicks > 0)
		{
			unloadSettleTicks--;
			return;
		}
		
		int disposable = firstDisposableSlot();
		
		if(disposable >= 0)
		{
			throwSlot(disposable);
			unloadSettleTicks = 1;
			synchronize(PerimeterAutomationState.UNLOADING,
				"dropping products into %s", unloadPointName);
			return;
		}
		
		forceInput(Input.SNEAK, false);
		unloadPoint = null;
		unloadPointName = null;
		unloadEdgePosition = null;
		
		if(miningCompletePending)
			transition(PerimeterAutomationState.COMPLETE,
				"mining and unloading complete");
		else
			beginReturnToMine();
	}
	
	private void beginReturnToMine()
	{
		if(miningReturnPoint == null
			|| !dimensionId().equals(miningReturnDimension))
		{
			startMiningCycle();
			return;
		}
		
		if(returnAttempts >= MAX_RETURN_ATTEMPTS)
		{
			returnAttempts = 0;
			startMiningCycle();
			return;
		}
		
		returnAttempts++;
		transition(PerimeterAutomationState.RETURNING_TO_MINE,
			"returning to the mining area");
		navigation.walk(new GoalBlock(miningReturnPoint));
	}
	
	private void tickReturningToMine()
	{
		BlockPos player = feet();
		
		if(miningReturnPoint == null
			|| horizontalDistanceSquared(player, miningReturnPoint) <= 16)
		{
			watchdogTarget = null;
			watchdogStationaryScans = 0;
			startMiningCycle();
			return;
		}
		
		if(!navigation.isWalking())
			navigation.walk(new GoalBlock(miningReturnPoint));
		
		synchronize(PerimeterAutomationState.RETURNING_TO_MINE,
			"returning to the mining area");
	}
	
	private void tickNavigateToMine()
	{
		if(miningReturnPoint == null)
		{
			startMiningCycle();
			return;
		}
		
		startMiningCycle();
	}
	
	// ------------------------------------------------------------------
	// eating
	// ------------------------------------------------------------------
	
	private boolean beginEating()
	{
		int foodSlot = PerimeterConsumables.findFoodSlot(WurstClient.MC.player,
			foods);
		
		if(foodSlot < 0)
			return false;
		
		stateBeforeEating = state;
		job.pause();
		navigation.stop();
		
		int hotbarSlot = foodSlot < 9 ? foodSlot : 7;
		
		if(foodSlot >= 9)
			swapIntoHotbar(foodSlot, hotbarSlot);
		
		WurstClient.MC.player.getInventory().selected = hotbarSlot;
		WurstClient.MC.options.keyUse.setDown(true);
		transition(PerimeterAutomationState.EATING, "eating");
		return true;
	}
	
	private void tickEating()
	{
		if(!PerimeterConsumables.shouldEat(WurstClient.MC.player,
			config.advanced))
		{
			WurstClient.MC.options.keyUse.setDown(false);
			
			PerimeterAutomationState target =
				stateBeforeEating == PerimeterAutomationState.IDLE
					? PerimeterAutomationState.MINING : stateBeforeEating;
			
			if(target == PerimeterAutomationState.MINING && job.isActive())
			{
				job.resume();
				transition(PerimeterAutomationState.MINING,
					"finished eating, mining on");
				return;
			}
			
			transition(target, "finished eating");
			
			if(target == PerimeterAutomationState.MINING)
				startMiningCycle();
			
			return;
		}
		
		WurstClient.MC.options.keyUse.setDown(true);
		synchronize(PerimeterAutomationState.EATING, "eating");
	}
	
	// ------------------------------------------------------------------
	// monitoring: durability, supplies, sleep
	// ------------------------------------------------------------------
	
	private void monitor()
	{
		if(state == PerimeterAutomationState.EATING
			|| state == PerimeterAutomationState.COMPLETE
			|| state == PerimeterAutomationState.ERROR)
			return;
		
		if(PerimeterConsumables.shouldEat(WurstClient.MC.player,
			config.advanced) && beginEating())
			return;
		
		if(config.functions.durabilityRecovery)
		{
			PerimeterGear.Check check = PerimeterGear.inspect(
				WurstClient.MC.player, config.functions.elytraNavigation,
				config.advanced);
			
			if(check.repairRequired())
			{
				if(check.replacement() != null)
				{
					performReplacement(check.replacement());
					return;
				}
				
				if(config.functions.crossDimensionRepair
					&& hasRepairConfiguration())
				{
					beginRepairFlow();
					return;
				}
				
				if(config.functions.resupply && config.durabilitySupplyPoint
					.isSet())
				{
					beginSupply(true);
					return;
				}
			}
		}
		
		if(config.functions.resupply && config.consumableSupplyPoint.isSet()
			&& needsConsumableResupply())
		{
			beginSupply(false);
			return;
		}
		
		if(config.functions.sleep && config.bedPoint.isSet()
			&& state == PerimeterAutomationState.MINING && !sleeping
			&& isNight())
			beginSleepFlow();
	}
	
	private boolean needsConsumableResupply()
	{
		if(!config.functions.elytraNavigation)
			return PerimeterConsumables.needsFoodResupply(
				PerimeterConsumables.countFood(WurstClient.MC.player, foods),
				config.advanced);
		
		return PerimeterConsumables.needsFoodResupply(
			PerimeterConsumables.countFood(WurstClient.MC.player, foods),
			config.advanced)
			|| PerimeterConsumables
				.needsFireworkResupply(PerimeterConsumables
					.countFireworks(WurstClient.MC.player), config.advanced);
	}
	
	private void performReplacement(PerimeterGear.Replacement replacement)
	{
		swapIntoHotbar(replacement.healthy().inventorySlot(),
			replacement.low().inventorySlot() < 9
				? replacement.low().inventorySlot() : 7);
		
		synchronize(state, "replaced a worn item with a healthy one");
	}
	
	// ------------------------------------------------------------------
	// supply
	// ------------------------------------------------------------------
	
	private void beginSupply(boolean durability)
	{
		supplyDurability = durability;
		supplyPoint = durability ? config.durabilitySupplyPoint
			: config.consumableSupplyPoint;
		supplyFlightAttempts = 0;
		interactionTicks = 0;
		
		if(!supplyPoint.isSet())
		{
			synchronize(state, "no supply point is configured");
			return;
		}
		
		job.pause();
		navigation.disablePlacement();
		transition(durability
			? PerimeterAutomationState.NAVIGATING_TO_DURABILITY_SUPPLY
			: PerimeterAutomationState.NAVIGATING_TO_RESUPPLY,
			"travelling to the supply point");
		tickSupplyNavigation();
	}
	
	private void tickSupplyNavigation()
	{
		if(supplyPoint == null || !supplyPoint.isSet())
		{
			finishSupply();
			return;
		}
		
		BlockPos target = supplyStandingPosition();
		
		if(target == null)
		{
			synchronize(state, "looking for a place to reach the supply");
			return;
		}
		
		BlockPos player = feet();
		
		if(player.distSqr(target) <= ARRIVAL_DISTANCE_SQUARED)
		{
			navigation.stop();
			transition(supplyDurability
				? PerimeterAutomationState.SWAPPING_DURABILITY_AT_SUPPLY
				: PerimeterAutomationState.RESUPPLYING,
				"using the supply point");
			return;
		}
		
		if(config.functions.elytraNavigation && canFly()
			&& supplyFlightAttempts < config.advanced.flightRetryCount
			&& player.distSqr(target) > 1024)
		{
			supplyFlightAttempts++;
			navigation.fly(target);
			return;
		}
		
		if(!navigation.isWalking() || tickCounter % 40 == 0)
			navigation.walk(new GoalBlock(target));
		
		synchronize(state, "travelling to the supply point");
	}
	
	private BlockPos supplyStandingPosition()
	{
		BlockPos point = new BlockPos(supplyPoint.x, supplyPoint.y,
			supplyPoint.z);
		return positions.closest(WurstClient.MC.level, point, feet())
			.orElse(point);
	}
	
	private void tickSupplyInteraction()
	{
		interactionTicks++;
		BlockPos point = new BlockPos(supplyPoint.x, supplyPoint.y,
			supplyPoint.z);
		
		if(!positions.canReach(WurstClient.MC.player, point))
		{
			if(interactionTicks > config.advanced.supplyInteractionTimeoutSeconds
				* 20)
			{
				finishSupply();
				return;
			}
			
			lookAt(Vec3.atCenterOf(point));
			synchronize(state, "looking for the supply container");
			return;
		}
		
		lookAt(Vec3.atCenterOf(point));
		
		if(!isContainerOpen())
		{
			openContainer(point);
			return;
		}
		
		if(supplyDurability)
			transferDurability();
		else
			transferConsumables();
		
		boolean done = supplyDurability
			? !PerimeterGear
				.inspect(WurstClient.MC.player,
					config.functions.elytraNavigation, config.advanced)
				.repairRequired()
			: !needsConsumableResupply();
		
		if(done)
			finishSupply();
	}
	
	private void transferConsumables()
	{
		transferMatching(stack -> foods.contains(stack.getItem())
			|| stack.is(net.minecraft.world.item.Items.FIREWORK_ROCKET));
	}
	
	private void transferDurability()
	{
		transferMatching(stack -> stack.isDamageableItem() || PerimeterGear
			.keepWhenUnloading(stack, whitelist));
	}
	
	/**
	 * Shift-clicks matching stacks out of the open container, as the reference
	 * does through container clicks.
	 */
	private void transferMatching(java.util.function.Predicate<ItemStack> filter)
	{
		var menu = WurstClient.MC.player.containerMenu;
		int containerSlots = Math.max(0, menu.slots.size() - 36);
		
		for(int slot = 0; slot < containerSlots; slot++)
		{
			ItemStack stack = menu.getSlot(slot).getItem();
			
			if(stack.isEmpty() || !filter.test(stack))
				continue;
			
			WurstClient.MC.gameMode.handleInventoryMouseClick(menu.containerId,
				slot, 0, ClickType.QUICK_MOVE, WurstClient.MC.player);
			return;
		}
	}
	
	private void finishSupply()
	{
		if(WurstClient.MC.player.containerMenu != WurstClient.MC.player.inventoryMenu)
			WurstClient.MC.player.closeContainer();
		
		supplyPoint = null;
		interactionTicks = 0;
		navigation.restoreDestinationSettings();
		navigation.walk(new GoalBlock(
			miningReturnPoint == null ? feet() : miningReturnPoint));
		transition(PerimeterAutomationState.RETURNING_TO_MINE,
			"returning to the mining area");
	}
	
	private void openContainer(BlockPos point)
	{
		BlockHitResult hit = new BlockHitResult(Vec3.atCenterOf(point),
			net.minecraft.core.Direction.UP, point, false);
		WurstClient.MC.gameMode.useItemOn(WurstClient.MC.player,
			net.minecraft.world.InteractionHand.MAIN_HAND, hit);
	}
	
	private boolean isContainerOpen()
	{
		return WurstClient.MC.player.containerMenu != WurstClient.MC.player.inventoryMenu;
	}
	
	// ------------------------------------------------------------------
	// sleep
	// ------------------------------------------------------------------
	
	private void beginSleepFlow()
	{
		sleeping = true;
		sleepTicks = 0;
		job.pause();
		navigation.disablePlacement();
		transition(PerimeterAutomationState.NAVIGATING_TO_BED,
			"travelling to the bed");
		tickBedNavigation();
	}
	
	private void tickBedNavigation()
	{
		if(!config.bedPoint.isSet())
		{
			finishSleep();
			return;
		}
		
		BlockPos bed = new BlockPos(config.bedPoint.x, config.bedPoint.y,
			config.bedPoint.z);
		BlockPos standing = positions.closest(WurstClient.MC.level, bed, feet())
			.orElse(bed);
		
		if(feet().distSqr(standing) <= ARRIVAL_DISTANCE_SQUARED)
		{
			navigation.stop();
			transition(PerimeterAutomationState.SLEEPING, "sleeping");
			return;
		}
		
		if(!navigation.isWalking() || tickCounter % 40 == 0)
			navigation.walk(new GoalBlock(standing));
		
		synchronize(PerimeterAutomationState.NAVIGATING_TO_BED,
			"travelling to the bed");
	}
	
	private void tickSleeping()
	{
		sleepTicks++;
		BlockPos bed = new BlockPos(config.bedPoint.x, config.bedPoint.y,
			config.bedPoint.z);
		lookAt(Vec3.atCenterOf(bed));
		
		if(!isNight() || sleepTicks > 20 * 60)
		{
			finishSleep();
			return;
		}
		
		if(sleepTicks % 20 == 1 && positions.canReach(WurstClient.MC.player, bed))
			openContainer(bed);
		
		if(WurstClient.MC.player.isSleeping())
			synchronize(PerimeterAutomationState.SLEEPING, "sleeping");
	}
	
	private void finishSleep()
	{
		sleeping = false;
		sleepTicks = 0;
		navigation.restoreDestinationSettings();
		
		if(job.isActive())
		{
			job.resume();
			transition(PerimeterAutomationState.MINING, "awake, mining on");
			return;
		}
		
		startMiningCycle();
	}
	
	private boolean isNight()
	{
		if(WurstClient.MC.level == null)
			return false;
		
		long time = WurstClient.MC.level.getDayTime() % 24000L;
		return time >= 13000L && time <= 23000L;
	}
	
	// ------------------------------------------------------------------
	// cross dimensional repair
	// ------------------------------------------------------------------
	
	private boolean hasRepairConfiguration()
	{
		boolean furnaceRow = config.furnaceRowStart.isSet()
			&& config.furnaceRowEnd.isSet();
		boolean portal = config.perimeterPortalNether.isSet()
			|| config.repairPortalNether.isSet();
		return furnaceRow && portal;
	}
	
	private void beginRepairFlow()
	{
		repairTicks = 0;
		furnaceIndex = 0;
		job.pause();
		navigation.disableBreakingAndPlacement();
		
		if(repairReturnPoint == null)
			repairReturnPoint = feet();
		
		startedInNether = currentDimensionIsNether();
		hasEnteredTargetDimension = false;
		
		PerimeterPosition portal = currentDimensionIsNether()
			? config.repairPortalNether : config.perimeterPortalOverworld;
		
		if(!portal.isSet())
			portal = config.repairPortalOverworld;
		
		if(!portal.isSet())
		{
			navigation.restoreDestinationSettings();
			beginSupply(true);
			return;
		}
		
		transition(PerimeterAutomationState.NAVIGATING_TO_PERIMETER_PORTAL,
			"travelling to the perimeter portal");
		navigation.walk(new GoalBlock(
			new BlockPos(portal.x, portal.y, portal.z)));
	}
	
	private void tickRepairNavigation()
	{
		repairTicks++;
		
		if(repairTicks > config.advanced.portalTransitionTimeoutSeconds * 20
			+ config.advanced.portalExitTimeoutSeconds * 20)
		{
			finishRepair();
			return;
		}
		
		switch(state)
		{
			case NAVIGATING_TO_PERIMETER_PORTAL,
				NAVIGATING_TO_REPAIR_PORTAL ->
				tickRepairPortalNavigation();
			case ENTERING_PERIMETER_PORTAL, ENTERING_REPAIR_PORTAL,
				CLEARING_REPAIR_PORTAL ->
				tickRepairPortalEntry();
			case NAVIGATING_TO_REPAIR_MACHINE -> tickRepairMachineNavigation();
			default -> {}
		}
	}
	
	private void tickRepairPortalNavigation()
	{
		PerimeterPosition portal = state == PerimeterAutomationState.NAVIGATING_TO_PERIMETER_PORTAL
			? (currentDimensionIsNether() ? config.repairPortalNether
				: config.perimeterPortalOverworld)
			: config.repairPortalNether;
		
		if(!portal.isSet())
			portal = config.repairPortalOverworld;
		
		if(!portal.isSet())
		{
			finishRepair();
			return;
		}
		
		BlockPos target = new BlockPos(portal.x, portal.y, portal.z);
		
		if(feet().distSqr(target) <= 9)
		{
			navigation.stop();
			transition(state == PerimeterAutomationState.NAVIGATING_TO_PERIMETER_PORTAL
				? PerimeterAutomationState.ENTERING_PERIMETER_PORTAL
				: PerimeterAutomationState.ENTERING_REPAIR_PORTAL,
				"entering the portal");
			return;
		}
		
		if(!navigation.isWalking() || tickCounter % 40 == 0)
			navigation.walk(new GoalBlock(target));
		
		synchronize(state, "travelling to the portal");
	}
	
	private void tickRepairPortalEntry()
	{
		PerimeterPosition target = currentDimensionIsNether()
			? config.repairPortalNether : config.repairPortalOverworld;
		
		if(!target.isSet())
			target = config.repairPortalOverworld;
		
		if(!target.isSet())
		{
			finishRepair();
			return;
		}
		
		BlockPos portal = new BlockPos(target.x, target.y, target.z);
		lookAt(Vec3.atCenterOf(portal));
		forceInput(Input.MOVE_FORWARD, true);
		
		boolean inNether = currentDimensionIsNether();
		
		if(!hasEnteredTargetDimension)
			hasEnteredTargetDimension = inNether != startedInNether;
		
		if(hasEnteredTargetDimension)
		{
			forceInput(Input.MOVE_FORWARD, false);
			transition(PerimeterAutomationState.NAVIGATING_TO_REPAIR_MACHINE,
				"travelling to the repair furnaces");
		}
	}
	
	private boolean hasEnteredTargetDimension;
	private boolean startedInNether;
	
	private void tickRepairMachineNavigation()
	{
		BlockPos start = new BlockPos(config.furnaceRowStart.x,
			config.furnaceRowStart.y, config.furnaceRowStart.z);
		BlockPos target = positions.closest(WurstClient.MC.level, start, feet())
			.orElse(start);
		
		if(feet().distSqr(target) <= ARRIVAL_DISTANCE_SQUARED)
		{
			navigation.stop();
			transition(PerimeterAutomationState.REPAIRING,
				"taking furnace output");
			return;
		}
		
		if(!navigation.isWalking() || tickCounter % 40 == 0)
			navigation.walk(new GoalBlock(target));
		
		synchronize(PerimeterAutomationState.NAVIGATING_TO_REPAIR_MACHINE,
			"travelling to the repair furnaces");
	}
	
	private void tickRepairing()
	{
		repairTicks++;
		
		BlockPos start = new BlockPos(config.furnaceRowStart.x,
			config.furnaceRowStart.y, config.furnaceRowStart.z);
		BlockPos end = new BlockPos(config.furnaceRowEnd.x,
			config.furnaceRowEnd.y, config.furnaceRowEnd.z);
		List<BlockPos> furnaces = furnacePositions(start, end);
		
		if(furnaces.isEmpty())
		{
			finishRepair();
			return;
		}
		
		PerimeterGear.Check check = PerimeterGear.inspect(
			WurstClient.MC.player, config.functions.elytraNavigation,
			config.advanced);
		
		if(!check.repairRequired())
		{
			finishRepair();
			return;
		}
		
		BlockPos furnace = furnaces.get(furnaceIndex % furnaces.size());
		lookAt(Vec3.atCenterOf(furnace));
		
		if(!isContainerOpen())
		{
			if(positions.canReach(WurstClient.MC.player, furnace))
				openContainer(furnace);
			else
			{
				navigation.walk(new GoalBlock(positions
					.closest(WurstClient.MC.level, furnace, feet())
					.orElse(furnace)));
				return;
			}
		}else
			takeFurnaceOutput();
		
		if(repairTicks % (int)Math.max(1.0,
			config.advanced.furnaceInteractionTimeoutSeconds * 20) == 0)
		{
			if(isContainerOpen())
				WurstClient.MC.player.closeContainer();
			
			furnaceIndex++;
			
			if(furnaceIndex >= furnaces.size() * 2)
				finishRepair();
		}
		
		synchronize(PerimeterAutomationState.REPAIRING,
			"taking furnace output");
	}
	
	private void takeFurnaceOutput()
	{
		var menu = WurstClient.MC.player.containerMenu;
		int containerSlots = Math.max(0, menu.slots.size() - 36);
		
		for(int slot = 0; slot < containerSlots; slot++)
		{
			ItemStack stack = menu.getSlot(slot).getItem();
			
			if(stack.isEmpty())
				continue;
			
			WurstClient.MC.gameMode.handleInventoryMouseClick(menu.containerId,
				slot, 0, ClickType.QUICK_MOVE, WurstClient.MC.player);
			return;
		}
	}
	
	private List<BlockPos> furnacePositions(BlockPos start, BlockPos end)
	{
		List<BlockPos> positionsList = new ArrayList<>();
		int steps = Math.max(Math.abs(end.getX() - start.getX()),
			Math.max(Math.abs(end.getY() - start.getY()),
				Math.abs(end.getZ() - start.getZ())));
		
		for(int step = 0; step <= steps; step++)
		{
			int x = start.getX() + (int)Math.round(
				(double)(end.getX() - start.getX()) * step / Math.max(1, steps));
			int y = start.getY() + (int)Math.round(
				(double)(end.getY() - start.getY()) * step / Math.max(1, steps));
			int z = start.getZ() + (int)Math.round(
				(double)(end.getZ() - start.getZ()) * step / Math.max(1, steps));
			positionsList.add(new BlockPos(x, y, z));
		}
		
		return positionsList;
	}
	
	private void finishRepair()
	{
		if(isContainerOpen())
			WurstClient.MC.player.closeContainer();
		
		repairTicks = 0;
		furnaceIndex = 0;
		hasEnteredTargetDimension = false;
		navigation.restoreDestinationSettings();
		
		transactionReturn();
	}
	
	private void transactionReturn()
	{
		if(repairReturnPoint != null
			&& repairReturnPoint.distSqr(feet()) > 16)
		{
			navigation.walk(new GoalBlock(repairReturnPoint));
			transition(PerimeterAutomationState.RETURNING_TO_MINE,
				"returning to the mining area");
			return;
		}
		
		if(job.isActive())
		{
			job.resume();
			transition(PerimeterAutomationState.MINING, "mining on");
			return;
		}
		
		startMiningCycle();
	}
	
	private BlockPos repairReturnPoint;
	
	private boolean currentDimensionIsNether()
	{
		if(WurstClient.MC.level == null)
			return false;
		
		return WurstClient.MC.level.dimension().location().getPath()
			.equals("the_nether");
	}
	
	// ------------------------------------------------------------------
	// watchdog
	// ------------------------------------------------------------------
	
	private boolean canFly()
	{
		if(!config.functions.elytraNavigation || WurstClient.MC.player == null)
			return false;
		
		ItemStack chest =
			WurstClient.MC.player.getItemBySlot(net.minecraft.world.entity.EquipmentSlot.CHEST);
		
		if(!chest.is(net.minecraft.world.item.Items.ELYTRA))
			return false;
		
		return PerimeterGear.remainingDurability(chest) > config.advanced
			.emergencyFlightDurabilityThreshold;
	}
	
	// ------------------------------------------------------------------
	// helpers
	// ------------------------------------------------------------------
	
	private void stopProcesses()
	{
		WurstClient.MC.options.keyUse.setDown(false);
		job.cancel();
		navigation.stop();
		navigation.restoreDestinationSettings();
		clearInputs();
		unloadPoint = null;
		unloadCandidates = List.of();
		unloadEdgePosition = null;
		supplyPoint = null;
		sleeping = false;
		hasEnteredTargetDimension = false;
		watchdogTarget = null;
		watchdogStationaryScans = 0;
	}
	
	private void clearInputs()
	{
		try
		{
			baritone().getInputOverrideHandler().clearAllKeys();
		}catch(Exception | LinkageError e)
		{
			// Baritone is not installed; vanilla keys were used anyway
		}
		
		WurstClient.MC.options.keyUse.setDown(false);
	}
	
	private void forceInput(Input input, boolean down)
	{
		try
		{
			baritone().getInputOverrideHandler().setInputForceState(input, down);
		}catch(Exception | LinkageError e)
		{
			// ignore, the automation keeps running without forced inputs
		}
	}
	
	private IBaritone baritone()
	{
		return net.wurstclient.util.BaritoneUtils.getBaritone();
	}
	
	private void lookAt(Vec3 target)
	{
		Vec3 eye = WurstClient.MC.player.getEyePosition();
		double dx = target.x - eye.x;
		double dy = target.y - eye.y;
		double dz = target.z - eye.z;
		double horizontal = Math.sqrt(dx * dx + dz * dz);
		float yaw = (float)(Math.toDegrees(Math.atan2(dz, dx)) - 90.0);
		float pitch = (float)(-Math.toDegrees(Math.atan2(dy, horizontal)));
		WurstClient.MC.player.setYRot(yaw);
		WurstClient.MC.player.setXRot(pitch);
	}
	
	private void swapIntoHotbar(int inventorySlot, int hotbarSlot)
	{
		int menuSlot = PerimeterGear.menuSlot(inventorySlot);
		WurstClient.MC.gameMode.handleInventoryMouseClick(
			WurstClient.MC.player.inventoryMenu.containerId, menuSlot,
			hotbarSlot, ClickType.SWAP, WurstClient.MC.player);
	}
	
	private void throwSlot(int inventorySlot)
	{
		WurstClient.MC.gameMode.handleInventoryMouseClick(
			WurstClient.MC.player.inventoryMenu.containerId,
			PerimeterGear.menuSlot(inventorySlot), 1, ClickType.THROW,
			WurstClient.MC.player);
	}
	
	private int firstDisposableSlot()
	{
		Inventory inventory = WurstClient.MC.player.getInventory();
		
		for(int slot = 0; slot < PerimeterInventoryPolicy.INVENTORY_SLOTS
			&& slot < inventory.getContainerSize(); slot++)
		{
			ItemStack stack = inventory.getItem(slot);
			
			if(!stack.isEmpty() && !PerimeterGear.keepWhenUnloading(stack,
				whitelist))
				return slot;
		}
		
		return -1;
	}
	
	public int emptyInventorySlots()
	{
		Inventory inventory = WurstClient.MC.player.getInventory();
		int empty = 0;
		
		for(int slot = 0; slot < PerimeterInventoryPolicy.INVENTORY_SLOTS;
			slot++)
			if(inventory.getItem(slot).isEmpty())
				empty++;
		
		return empty;
	}
	
	private int inventoryItemCount()
	{
		Inventory inventory = WurstClient.MC.player.getInventory();
		int count = 0;
		
		for(int slot = 0; slot < PerimeterInventoryPolicy.INVENTORY_SLOTS;
			slot++)
			count += inventory.getItem(slot).getCount();
		
		return count;
	}
	
	private BlockPos feet()
	{
		return WurstClient.MC.player.blockPosition();
	}
	
	private String dimensionId()
	{
		if(WurstClient.MC.level == null)
			return "none";
		
		return WurstClient.MC.level.dimension().location().toString();
	}
	
	private PerimeterWorldView worldView()
	{
		return new PerimeterWorldView(WurstClient.MC.level);
	}
	
	private static List<net.minecraft.world.level.block.Block> blockList(
		PerimeterConfig config)
	{
		List<net.minecraft.world.level.block.Block> blocks = new ArrayList<>();
		
		for(String id : config.sealingBlocks)
		{
			net.minecraft.world.level.block.Block block =
				net.wurstclient.util.BlockUtils.getBlockFromNameOrID(id);
			
			if(block != null)
				blocks.add(block);
		}
		
		return List.copyOf(blocks);
	}
	
	/**
	 * Baritone's own list of blocks that must never be broken, which the
	 * reference passes straight into its mining schematic.
	 */
	private static List<net.minecraft.world.level.block.Block> disallowedBlocks()
	{
		try
		{
			return baritone.api.BaritoneAPI.getSettings().blocksToDisallowBreaking.value;
		}catch(Exception | LinkageError e)
		{
			return List.of();
		}
	}
	
	private static Set<Item> itemSet(List<String> ids)
	{
		java.util.Set<Item> items = new java.util.HashSet<>();
		
		for(String id : ids)
		{
			Item item = net.wurstclient.util.ItemUtils
				.getItemFromNameOrID(id);
			
			if(item != null)
				items.add(item);
		}
		
		return Set.copyOf(items);
	}
	
	private static int monitorScans(double seconds)
	{
		return Math.max(1, (int)Math.ceil(seconds * 20.0
			/ MONITOR_INTERVAL_TICKS));
	}
	
	// ------------------------------------------------------------------
	// validation and status
	// ------------------------------------------------------------------
	
	public List<String> validate(PerimeterConfig config)
	{
		List<String> problems = new ArrayList<>();
		
		if(config == null)
		{
			problems.add("no configuration");
			return problems;
		}
		
		config.normalize();
		
		if(!config.hasDiggingRange())
			problems.add("digging_min_y and digging_max_y must both be set");
		
		if(!config.hasDetectedArea())
			problems.add(
				"no mining area: plan a rectangle or detect a boundary first");
		
		if(!config.liquidPolicyValue().id().equals(config.liquidPolicy))
			problems.add("liquid_policy");
		
		if(config.liquidPolicyValue() != PerimeterLiquidPolicy.AVOID
			&& config.sealingBlocks.isEmpty())
			problems.add("sealing_blocks is empty");
		
		return problems;
	}
	
	private PerimeterColumnArea buildArea(PerimeterConfig config)
	{
		int[] range = config.diggingRange();
		return new PerimeterColumnArea(config.detectedArea, range[0],
			range[1]);
	}
	
	public PerimeterAutomationState getState()
	{
		return state;
	}
	
	public String getDetail()
	{
		return detail;
	}
	
	public boolean isRunning()
	{
		return running;
	}
	
	public boolean isPaused()
	{
		return state == PerimeterAutomationState.PAUSED;
	}
	
	public PerimeterMiningJob getJob()
	{
		return job;
	}
	
	public PerimeterStateHistory getHistory()
	{
		return history;
	}
	
	public PerimeterColumnArea getArea()
	{
		return area;
	}
	
	public PerimeterConfig getConfig()
	{
		return config;
	}
	
	public String describeStatus()
	{
		StringBuilder builder = new StringBuilder();
		builder.append(state.id());
		
		if(!detail.isEmpty())
			builder.append(" - ").append(detail);
		
		if(area != null)
			builder.append(" [").append(area.describe()).append(']');
		
		return builder.toString();
	}
	
	public List<String> describe()
	{
		List<String> lines = new ArrayList<>();
		lines.add("state: " + state.id());
		lines.add("detail: " + detail);
		lines.add("running: " + running);
		
		if(area != null)
		{
			lines.add("area: " + area.describe());
			lines.add("columns: " + area.columnCount() + ", layers: "
				+ area.sizeY());
		}
		
		lines.add("liquid policy: " + liquidPolicy.id());
		lines.add("mining batch: " + job.getMinedBlocks() + "/"
			+ job.getBlockLimit());
		lines.add("unloading points: " + config.unloadingPoints.size());
		lines.add(String.format(Locale.ROOT, "inventory: %d empty slots, %d items",
			emptyInventorySlots(), inventoryItemCount()));
		
		return lines;
	}
	
	private void transition(PerimeterAutomationState next, String nextDetail,
		Object... args)
	{
		String rendered = PerimeterText.get(nextDetail, args);

		if(next == state)
		{
			detail = rendered;
			return;
		}
		
		PerimeterAutomationState previous = state;
		state = next;
		detail = rendered;
		
		Player player = WurstClient.MC.player;
		String position = player == null ? "unknown"
			: player.getBlockX() + " " + player.getBlockY() + " "
				+ player.getBlockZ();
		history.record(previous, next, rendered, dimensionId(), position);
	}
	
	private void synchronize(PerimeterAutomationState next, String nextDetail,
		Object... args)
	{
		state = next;
		detail = PerimeterText.get(nextDetail, args);
	}
}

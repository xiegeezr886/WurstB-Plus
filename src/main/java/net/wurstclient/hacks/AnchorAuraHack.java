/*
 * Copyright (c) 2014-2025 Wurst-Imperium and contributors.
 *
 * This source code is subject to the terms of the GNU General Public
 * License, version 3. If a copy of the GPL was not distributed with this
 * file, You can obtain one at: https://www.gnu.org/licenses/gpl-3.0.txt
 */
package net.wurstclient.hacks;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.RespawnAnchorBlock;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.wurstclient.Category;
import net.wurstclient.SearchTags;
import net.wurstclient.events.UpdateListener;
import net.wurstclient.hack.Hack;
import net.wurstclient.hack.HackConflictGroup;
import net.wurstclient.settings.CheckboxSetting;
import net.wurstclient.settings.EnumSetting;
import net.wurstclient.settings.FacingSetting;
import net.wurstclient.settings.FacingSetting.Facing;
import net.wurstclient.settings.SliderSetting;
import net.wurstclient.settings.SliderSetting.ValueDisplay;
import net.wurstclient.settings.SwingHandSetting;
import net.wurstclient.settings.SwingHandSetting.SwingHand;
import net.wurstclient.settings.filterlists.AnchorAuraFilterList;
import net.wurstclient.settings.filterlists.EntityFilterList;
import net.wurstclient.util.BlockUtils;
import net.wurstclient.util.ChatUtils;
import net.wurstclient.util.DamageUtils;
import net.wurstclient.util.FakePlayerEntity;
import net.wurstclient.util.InventoryUtils;
import net.wurstclient.util.RotationQueue;
import net.wurstclient.util.RotationUtils;

@SearchTags({"anchor aura", "CrystalAura", "crystal aura"})
public final class AnchorAuraHack extends Hack implements UpdateListener
{
	private final SliderSetting range =
		new SliderSetting("Range", "description.wurst.setting.anchoraura.range",
			6, 1, 6, 0.05, ValueDisplay.DECIMAL);
	
	private final CheckboxSetting autoPlace =
		new CheckboxSetting("Auto-place anchors",
			"description.wurst.setting.anchoraura.auto-place_anchors", true);
	
	private final FacingSetting faceBlocks =
		FacingSetting.withPacketSpam("Face anchors",
			"description.wurst.setting.anchoraura.face_anchors", Facing.OFF);
	
	private final CheckboxSetting checkLOS =
		new CheckboxSetting("Check line of sight",
			"description.wurst.setting.anchoraura.check_line_of_sight", false);
	
	private final SwingHandSetting swingHand =
		new SwingHandSetting(this, SwingHand.CLIENT);
	
	private final EnumSetting<TakeItemsFrom> takeItemsFrom =
		new EnumSetting<>("Take items from",
			"description.wurst.setting.anchoraura.take_items_from",
			TakeItemsFrom.values(), TakeItemsFrom.INVENTORY);

	private final SliderSetting placeDelay = new SliderSetting("Place delay",
		"Ticks between new anchor placements.", 2, 0, 10, 1,
		ValueDisplay.INTEGER.withSuffix(" ticks"));

	private final SliderSetting chargeDelay = new SliderSetting("Charge delay",
		"Ticks to wait after an anchor appears before charging it.", 1, 0, 10,
		1, ValueDisplay.INTEGER.withSuffix(" ticks"));

	private final SliderSetting detonateDelay = new SliderSetting(
		"Detonate delay", "Ticks to wait after charging before detonation.", 1,
		0, 10, 1, ValueDisplay.INTEGER.withSuffix(" ticks"));

	private final SliderSetting minDamage = new SliderSetting("Min damage",
		"Minimum damage the anchor must deal to one valid target.", 6, 0, 36,
		0.5, ValueDisplay.DECIMAL);

	private final SliderSetting maxSelfDamage = new SliderSetting(
		"Max self damage", "Maximum allowed damage to the local player.", 8,
		0, 36, 0.5, ValueDisplay.DECIMAL);

	private final CheckboxSetting antiSuicide = new CheckboxSetting(
		"Anti suicide", "Rejects anchors that could kill the local player.",
		true);
	
	private final EntityFilterList entityFilters =
		AnchorAuraFilterList.create();

	private RotationQueue rotationQueue;
	private BlockPos pendingPos;
	private PendingAction pendingAction = PendingAction.NONE;
	private int actionDelay;
	private int pendingTicks;
	private int placeTimer;
	
	public AnchorAuraHack()
	{
		super("AnchorAura");
		
		setCategory(Category.COMBAT);
		addConflictGroup(HackConflictGroup.COMBAT_TARGETING);
		addSetting(range);
		addSetting(autoPlace);
		addSetting(faceBlocks);
		addSetting(checkLOS);
		addSetting(swingHand);
		addSetting(takeItemsFrom);
		addSetting(placeDelay);
		addSetting(chargeDelay);
		addSetting(detonateDelay);
		addSetting(minDamage);
		addSetting(maxSelfDamage);
		addSetting(antiSuicide);
		
		entityFilters.forEach(this::addSetting);
	}
	
	@Override
	protected void onEnable()
	{
		clearPending();
		placeTimer = placeDelay.getValueI();
		rotationQueue =
			new RotationQueue(RotationQueue.Priority.COMBAT);
		rotationQueue.start();
		EVENTS.add(UpdateListener.class, this);
	}

	@Override
	protected void onDisable()
	{
		EVENTS.remove(UpdateListener.class, this);
		clearPending();
		rotationQueue.stop();
		rotationQueue = null;
	}
	
	@Override
	public void onUpdate()
	{
		if(MC.level.dimensionType().respawnAnchorWorks())
		{
			ChatUtils.error("重生锚在此维度不会爆炸。");
			setEnabled(false);
			return;
		}

		if(isSneaking())
			return;

		ArrayList<Entity> targets = getNearbyTargets();
		if(processPending(targets))
			return;
		if(targets.isEmpty())
			return;

		ArrayList<BlockPos> anchors = getNearbyAnchors();

		Map<Boolean, ArrayList<BlockPos>> anchorsByCharge = anchors.stream()
			.collect(Collectors.partitioningBy(this::isChargedAnchor,
				Collectors.toCollection(ArrayList::new)));

		ArrayList<BlockPos> chargedAnchors = anchorsByCharge.get(true);
		ArrayList<BlockPos> unchargedAnchors = anchorsByCharge.get(false);

		BlockPos charged = findBestAnchor(chargedAnchors, targets);
		if(charged != null)
		{
			beginPending(charged, PendingAction.DETONATE,
				detonateDelay.getValueI());
			return;
		}

		int maxInvSlot = takeItemsFrom.getSelected().maxInvSlot;
		BlockPos uncharged = findBestAnchor(unchargedAnchors, targets);
		if(uncharged != null
			&& InventoryUtils.indexOf(Items.GLOWSTONE, maxInvSlot) >= 0)
		{
			beginPending(uncharged, PendingAction.CHARGE,
				chargeDelay.getValueI());
			return;
		}

		if(!autoPlace.isChecked()
			|| InventoryUtils.indexOf(Items.RESPAWN_ANCHOR, maxInvSlot) == -1
			|| InventoryUtils.indexOf(Items.GLOWSTONE, maxInvSlot) == -1)
			return;

		if(placeTimer++ < placeDelay.getValueI())
			return;

		BlockPos placement = findBestPlacement(targets);
		if(placement != null && placeAnchor(placement))
		{
			swingHand.swing(InteractionHand.MAIN_HAND);
			placeTimer = 0;
			beginPending(placement, PendingAction.CHARGE,
				chargeDelay.getValueI());
		}
	}

	private boolean processPending(ArrayList<Entity> targets)
	{
		if(pendingAction == PendingAction.NONE)
			return false;
		if(pendingPos == null || ++pendingTicks > 20
			|| !isDamageSafe(pendingPos, targets))
		{
			clearPending();
			return false;
		}
		if(actionDelay > 0)
		{
			actionDelay--;
			return true;
		}

		if(BlockUtils.getBlock(pendingPos) != Blocks.RESPAWN_ANCHOR)
			return true;

		if(pendingAction == PendingAction.CHARGE)
		{
			if(isChargedAnchor(pendingPos))
			{
				pendingAction = PendingAction.DETONATE;
				actionDelay = detonateDelay.getValueI();
				pendingTicks = 0;
				return true;
			}

			InventoryUtils.selectItem(Items.GLOWSTONE,
				takeItemsFrom.getSelected().maxInvSlot);
			if(!MC.player.getMainHandItem().is(Items.GLOWSTONE))
				return true;
			if(rightClickBlock(pendingPos))
			{
				swingHand.swing(InteractionHand.MAIN_HAND);
				pendingAction = PendingAction.DETONATE;
				actionDelay = detonateDelay.getValueI();
				pendingTicks = 0;
			}
			return true;
		}

		if(!isChargedAnchor(pendingPos))
			return true;
		InventoryUtils.selectItem(stack -> !stack.is(Items.GLOWSTONE),
			takeItemsFrom.getSelected().maxInvSlot);
		if(MC.player.getMainHandItem().is(Items.GLOWSTONE))
			return true;
		if(rightClickBlock(pendingPos))
			swingHand.swing(InteractionHand.MAIN_HAND);
		clearPending();
		return true;
	}

	private void beginPending(BlockPos pos, PendingAction action, int delay)
	{
		pendingPos = pos.immutable();
		pendingAction = action;
		actionDelay = Math.max(0, delay);
		pendingTicks = 0;
	}

	private void clearPending()
	{
		pendingPos = null;
		pendingAction = PendingAction.NONE;
		actionDelay = 0;
		pendingTicks = 0;
	}

	private BlockPos findBestAnchor(ArrayList<BlockPos> anchors,
		ArrayList<Entity> targets)
	{
		return anchors.stream().filter(pos -> isDamageSafe(pos, targets))
			.max(Comparator.comparingDouble(pos -> getTargetDamage(pos, targets)))
			.orElse(null);
	}

	private BlockPos findBestPlacement(ArrayList<Entity> targets)
	{
		HashSet<BlockPos> candidates = new HashSet<>();
		for(Entity target : targets)
			candidates.addAll(getFreeBlocksNear(target));

		return candidates.stream().filter(pos -> isDamageSafe(pos, targets))
			.max(Comparator.comparingDouble(pos -> getTargetDamage(pos, targets)
				- DamageUtils.calculateDamage(Vec3.atCenterOf(pos), MC.player, 5)
					* 0.25F)).orElse(null);
	}

	private boolean isDamageSafe(BlockPos pos, ArrayList<Entity> targets)
	{
		float targetDamage = getTargetDamage(pos, targets);
		float selfDamage =
			DamageUtils.calculateDamage(Vec3.atCenterOf(pos), MC.player, 5);
		if(!DamageUtils.isDamageWorthwhile(targetDamage, selfDamage,
			minDamage.getValueF(), maxSelfDamage.getValueF()))
			return false;
		return !antiSuicide.isChecked() || selfDamage
			< MC.player.getHealth() + MC.player.getAbsorptionAmount();
	}

	private float getTargetDamage(BlockPos pos, ArrayList<Entity> targets)
	{
		float best = 0;
		Vec3 explosionPos = Vec3.atCenterOf(pos);
		for(Entity target : targets)
			if(target instanceof LivingEntity living)
				best = Math.max(best,
					DamageUtils.calculateDamage(explosionPos, living, 5));
		return best;
	}
	
	private boolean rightClickBlock(BlockPos pos)
	{
		Vec3 eyesPos = RotationUtils.getEyesPos();
		Vec3 posVec = Vec3.atCenterOf(pos);
		double distanceSqPosVec = eyesPos.distanceToSqr(posVec);
		double rangeSq = range.getValueSq();
		
		for(Direction side : Direction.values())
		{
			Vec3 hitVec = posVec.add(Vec3.atLowerCornerOf(side.getNormal()).scale(0.5));
			double distanceSqHitVec = eyesPos.distanceToSqr(hitVec);
			
			if(distanceSqHitVec > rangeSq)
				continue;
			
			// check if side is facing towards player
			if(distanceSqHitVec >= distanceSqPosVec)
				continue;
			
			if(checkLOS.isChecked()
				&& !BlockUtils.hasLineOfSight(eyesPos, hitVec))
				continue;
			
			face(hitVec);

			// place block
			IMC.getInteractionManager().rightClickBlock(pos, side, hitVec);
			
			return true;
		}
		
		return false;
	}
	
	private boolean placeAnchor(BlockPos pos)
	{
		Vec3 eyesPos = RotationUtils.getEyesPos();
		double rangeSq = range.getValueSq();
		Vec3 posVec = Vec3.atCenterOf(pos);
		double distanceSqPosVec = eyesPos.distanceToSqr(posVec);
		
		for(Direction side : Direction.values())
		{
			BlockPos neighbor = pos.relative(side);
			
			// check if neighbor can be right clicked
			if(!isClickableNeighbor(neighbor))
				continue;
			
			Vec3 dirVec = Vec3.atLowerCornerOf(side.getNormal());
			Vec3 hitVec = posVec.add(dirVec.scale(0.5));
			
			// check if hitVec is within range
			if(eyesPos.distanceToSqr(hitVec) > rangeSq)
				continue;
			
			// check if side is visible (facing away from player)
			if(distanceSqPosVec > eyesPos.distanceToSqr(posVec.add(dirVec)))
				continue;
			
			if(checkLOS.isChecked()
				&& !BlockUtils.hasLineOfSight(eyesPos, hitVec))
				continue;
			
			InventoryUtils.selectItem(Items.RESPAWN_ANCHOR,
				takeItemsFrom.getSelected().maxInvSlot);
			if(!MC.player.isHolding(Items.RESPAWN_ANCHOR))
				return false;
			
			face(hitVec);

			// place block
			IMC.getInteractionManager().rightClickBlock(neighbor,
				side.getOpposite(), hitVec);
			
			return true;
		}
		
		return false;
	}
	
	private ArrayList<BlockPos> getNearbyAnchors()
	{
		Vec3 eyesVec = RotationUtils.getEyesPos().subtract(0.5, 0.5, 0.5);
		BlockPos center = BlockPos.containing(RotationUtils.getEyesPos());
		int rangeI = range.getValueCeil();
		double rangeSq = Mth.square(range.getValue() + 0.5);
		
		Comparator<BlockPos> furthestFromPlayer =
			Comparator.<BlockPos> comparingDouble(
				pos -> eyesVec.distanceToSqr(Vec3.atLowerCornerOf(pos))).reversed();
		
		return BlockUtils.getAllInBoxStream(center, rangeI)
			.filter(pos -> eyesVec.distanceToSqr(Vec3.atLowerCornerOf(pos)) <= rangeSq)
			.filter(pos -> BlockUtils.getBlock(pos) == Blocks.RESPAWN_ANCHOR)
			.sorted(furthestFromPlayer)
			.collect(Collectors.toCollection(ArrayList::new));
	}
	
	private ArrayList<Entity> getNearbyTargets()
	{
		double rangeSq = range.getValueSq();
		
		Comparator<Entity> furthestFromPlayer = Comparator
			.<Entity> comparingDouble(e -> MC.player.distanceToSqr(e))
			.reversed();
		
		Stream<Entity> stream =
			StreamSupport.stream(MC.level.entitiesForRendering().spliterator(), false)
				.filter(e -> !e.isRemoved())
				.filter(e -> e instanceof LivingEntity
					&& ((LivingEntity)e).getHealth() > 0)
				.filter(e -> e != MC.player)
				.filter(e -> !(e instanceof FakePlayerEntity))
				.filter(e -> !WURST.getFriends().contains(e.getScoreboardName()))
				.filter(e -> MC.player.distanceToSqr(e) <= rangeSq);
		
		stream = entityFilters.applyTo(stream);
		
		return stream.sorted(furthestFromPlayer)
			.collect(Collectors.toCollection(ArrayList::new));
	}
	
	private ArrayList<BlockPos> getFreeBlocksNear(Entity target)
	{
		Vec3 eyesVec = RotationUtils.getEyesPos().subtract(0.5, 0.5, 0.5);
		double rangeSq = Mth.square(range.getValue() + 0.5);
		
		BlockPos center = target.blockPosition();
		int rangeI = 2;
		
		AABB targetBB = target.getBoundingBox();
		Vec3 targetEyesVec =
			target.position().add(0, target.getEyeHeight(target.getPose()), 0);
		
		Comparator<BlockPos> closestToTarget =
			Comparator.<BlockPos> comparingDouble(
				pos -> targetEyesVec.distanceToSqr(Vec3.atCenterOf(pos)));
		
		return BlockUtils.getAllInBoxStream(center, rangeI)
			.filter(pos -> eyesVec.distanceToSqr(Vec3.atLowerCornerOf(pos)) <= rangeSq)
			.filter(this::isReplaceable).filter(this::hasClickableNeighbor)
			.filter(pos -> !targetBB.intersects(new AABB(pos)))
			.sorted(closestToTarget)
			.collect(Collectors.toCollection(ArrayList::new));
	}
	
	private boolean isReplaceable(BlockPos pos)
	{
		return BlockUtils.getState(pos).canBeReplaced();
	}
	
	private boolean hasClickableNeighbor(BlockPos pos)
	{
		return isClickableNeighbor(pos.above()) || isClickableNeighbor(pos.below())
			|| isClickableNeighbor(pos.north())
			|| isClickableNeighbor(pos.east())
			|| isClickableNeighbor(pos.south())
			|| isClickableNeighbor(pos.west());
	}
	
	private boolean isClickableNeighbor(BlockPos pos)
	{
		return BlockUtils.canBeClicked(pos)
			&& !BlockUtils.getState(pos).canBeReplaced();
	}
	
	private boolean isChargedAnchor(BlockPos pos)
	{
		return BlockUtils.getState(pos).getOptionalValue(RespawnAnchorBlock.CHARGE)
			.orElse(0) > 0;
	}
	
	private boolean isSneaking()
	{
		return MC.player.isShiftKeyDown() || WURST.getHax().sneakHack.isEnabled();
	}

	private void face(Vec3 vec)
	{
		Facing mode = faceBlocks.getSelected();
		if(mode == Facing.SERVER)
			rotationQueue.setRotation(
				RotationUtils.getNeededRotations(vec));
		else
			mode.face(vec);
	}

	private enum PendingAction
	{
		NONE,
		CHARGE,
		DETONATE
	}

	private enum TakeItemsFrom
	{
		HOTBAR("Hotbar", 9),
		
		INVENTORY("Inventory", 36);
		
		private final String name;
		private final int maxInvSlot;
		
		private TakeItemsFrom(String name, int maxInvSlot)
		{
			this.name = name;
			this.maxInvSlot = maxInvSlot;
		}
		
		@Override
		public String toString()
		{
			return name;
		}
	}
}

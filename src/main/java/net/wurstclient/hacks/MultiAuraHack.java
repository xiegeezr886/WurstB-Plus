/*
 * This file contains a Forge/Mojmap adaptation of FDPClient's KillAura
 * multi-target mode and LiquidBounce Nextgen's click scheduler.
 *
 * Copyright (c) 2014-2025 Wurst-Imperium and contributors.
 * Copyright (c) 2015-2026 CCBlueX
 * Copyright (c) 2025-2026 Penguin
 *
 * This source code is subject to the terms of the GNU General Public
 * License, version 3. If a copy of the GPL was not distributed with this
 * file, You can obtain one at: https://www.gnu.org/licenses/gpl-3.0.txt
 */
package net.wurstclient.hacks;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Random;

import com.mojang.blaze3d.vertex.PoseStack;

import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.ItemUseAnimation;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.protocol.game.ServerboundContainerClosePacket;
import net.minecraft.network.protocol.game.ServerboundMovePlayerPacket.PosRot;
import net.minecraft.network.protocol.game.ServerboundPlayerActionPacket;
import net.minecraft.network.protocol.game.ServerboundPlayerCommandPacket;
import net.minecraft.network.protocol.game.ServerboundPlayerCommandPacket.Action;
import net.minecraft.network.protocol.game.ServerboundSetCarriedItemPacket;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.item.AxeItem;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
// MilkBucketItem removed in MC 26.1.2
import net.minecraft.world.item.PotionItem;
// SwordItem removed in MC 26.1.2
// UseAnim removed in MC 26.1.2
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.wurstclient.Category;
import net.wurstclient.SearchTags;
import net.wurstclient.events.HandleInputListener;
import net.wurstclient.events.RenderListener;
import net.wurstclient.events.UpdateListener;
import net.wurstclient.hack.Hack;
import net.wurstclient.hack.HackConflictGroup;
import net.wurstclient.mixin.LivingEntityAccessor;
import net.wurstclient.mixin.PlayerEntityAccessor;
import net.wurstclient.mixinterface.IClientPlayerInteractionManager;
import net.wurstclient.mixinterface.IMinecraftClient;
import net.wurstclient.settings.AimAtSetting;
import net.wurstclient.settings.CheckboxSetting;
import net.wurstclient.settings.EnumSetting;
import net.wurstclient.settings.SliderSetting;
import net.wurstclient.settings.SliderSetting.ValueDisplay;
import net.wurstclient.settings.SwingHandSetting;
import net.wurstclient.settings.SwingHandSetting.SwingHand;
import net.wurstclient.settings.filterlists.EntityFilterList;
import net.wurstclient.util.BlockUtils;
import net.wurstclient.util.ClickPattern;
import net.wurstclient.util.CombatAimPointPlanner;
import net.wurstclient.util.CombatAimPointPlanner.AimPoint;
import net.wurstclient.util.CombatActionPolicy;
import net.wurstclient.util.CombatClickScheduler;
import net.wurstclient.util.CombatTargetUtils;
import net.wurstclient.util.MultiTargetAttackPlanner;
import net.wurstclient.util.Rotation;
import net.wurstclient.util.RotationQueue;
import net.wurstclient.util.RotationSmoothing;
import net.wurstclient.util.RotationUtils;
import net.wurstclient.util.render.AuraRangeRenderer;

@SearchTags({"multi aura", "ForceField", "force field"})
public final class MultiAuraHack extends Hack
	implements UpdateListener, HandleInputListener, RenderListener
{
	private final SliderSetting range = new SliderSetting("Range", 3.7, 1,
		8, 0.05, ValueDisplay.DECIMAL.withSuffix(" blocks"));
	private final SliderSetting scanRange = new SliderSetting("Scan range", 2,
		0, 10, 0.1, ValueDisplay.DECIMAL.withSuffix(" blocks"));
	private final SliderSetting throughWallsRange = new SliderSetting(
		"Through walls range", 3, 0, 8, 0.05,
		ValueDisplay.DECIMAL.withSuffix(" blocks"));
	private final SliderSetting sprintRangeReduction = new SliderSetting(
		"Sprint range reduction", 0, 0, 0.4, 0.05,
		ValueDisplay.DECIMAL.withSuffix(" blocks"));
	private final SliderSetting rangeChance = new SliderSetting("Range chance",
		100, 1, 100, 1, ValueDisplay.PERCENTAGE);

	private final SliderSetting minCps = new SliderSetting("Minimum CPS", 5,
		1, 60, 1, ValueDisplay.INTEGER.withSuffix(" clicks"));
	private final SliderSetting maxCps = new SliderSetting("Maximum CPS", 8,
		1, 60, 1, ValueDisplay.INTEGER.withSuffix(" clicks"));
	private final EnumSetting<ClickPattern> clickPattern = new EnumSetting<>(
		"Click technique", ClickPattern.values(), ClickPattern.STABILIZED);
	private final CheckboxSetting attackCooldown = new CheckboxSetting(
		"Attack cooldown", true);
	private final SliderSetting minimumCooldown = new SliderSetting(
		"Minimum item cooldown", 1, 0, 2, 0.05, ValueDisplay.PERCENTAGE);
	private final SliderSetting maximumCooldown = new SliderSetting(
		"Maximum item cooldown", 1, 0, 2, 0.05, ValueDisplay.PERCENTAGE);
	private final CheckboxSetting ignoreCooldownWhenExitingRange =
		new CheckboxSetting("Ignore cooldown when exiting range", true);

	private final SliderSetting hurtTime = new SliderSetting("Hurt time", 10,
		0, 10, 1, ValueDisplay.INTEGER.withSuffix(" ticks"));
	private final SliderSetting maxTargets = new SliderSetting("Target limit",
		0, 0, 50, 1, ValueDisplay.INTEGER.withLabel(0, "unlimited"));
	private final EnumSetting<TargetPriority> priority = new EnumSetting<>(
		"Priority", TargetPriority.values(), TargetPriority.ARMOR);
	private final SliderSetting fov = new SliderSetting("FOV", 180, 0, 180,
		5, ValueDisplay.DEGREES);
	private final AimAtSetting aimAt = new AimAtSetting(
		"Preferred point in the primary target's predicted hitbox.");
	private final SliderSetting targetPrediction = new SliderSetting(
		"Target prediction", 1, 0, 3, 0.1,
		ValueDisplay.DECIMAL.withSuffix(" ticks"));
	private final SliderSetting selfPrediction = new SliderSetting(
		"Self prediction", 1, 0, 5, 1,
		ValueDisplay.INTEGER.withSuffix(" ticks"));
	private final CheckboxSetting checkLOS = new CheckboxSetting(
		"Check line of sight", true);

	private final EnumSetting<RaycastMode> raycastMode = new EnumSetting<>(
		"Raycast", RaycastMode.values(), RaycastMode.ENEMY);
	private final CheckboxSetting raycastIgnored = new CheckboxSetting(
		"Raycast ignored", false)
			.visibleWhen(() -> raycastMode.getSelected() != RaycastMode.NONE);
	private final CheckboxSetting livingRaycast = new CheckboxSetting(
		"Living raycast", true)
			.visibleWhen(() -> raycastMode.getSelected() != RaycastMode.NONE);
	private final EnumSetting<RaytraceMode> raytraceMode = new EnumSetting<>(
		"Raytrace mode", RaytraceMode.values(), RaytraceMode.NORMAL)
			.visibleWhen(() -> raycastMode.getSelected() != RaycastMode.NONE);
	private final EnumSetting<RotationMode> rotationMode = new EnumSetting<>(
		"Rotation mode", RotationMode.values(), RotationMode.SILENT);
	private final EnumSetting<RotationTiming> rotationTiming = new EnumSetting<>(
		"Rotation timing", RotationTiming.values(), RotationTiming.NORMAL)
			.visibleWhen(() -> rotationMode.getSelected() != RotationMode.NONE);
	private final SliderSetting rotationSmooth = new SliderSetting(
		"Rotation smooth", 0.5, 0, 1, 0.05, ValueDisplay.PERCENTAGE)
			.visibleWhen(() -> rotationMode.getSelected() != RotationMode.NONE);
	private final EnumSetting<RotationSmoothing> rotationCurve =
		new EnumSetting<>("Rotation smoothing", RotationSmoothing.values(),
			RotationSmoothing.EASE_IN_OUT)
				.visibleWhen(() -> rotationMode.getSelected() != RotationMode.NONE);

	private final CheckboxSetting activationSlot = new CheckboxSetting(
		"Activation slot", false);
	private final SliderSetting preferredSlot = new SliderSetting(
		"Preferred slot", 1, 1, 9, 1, ValueDisplay.INTEGER)
			.visibleWhen(activationSlot::isChecked);
	private final CheckboxSetting clickOnly = new CheckboxSetting("Click only",
		false);
	private final CheckboxSetting onSwording = new CheckboxSetting(
		"Require sword", true);
	private final CheckboxSetting onScaffold = new CheckboxSetting(
		"Allow on scaffold", false);
	private final CheckboxSetting onDestroyBlock = new CheckboxSetting(
		"Allow while breaking", false);
	private final CheckboxSetting noScaffold = new CheckboxSetting(
		"Disable on scaffold", false);
	private final CheckboxSetting noFly = new CheckboxSetting("Disable on fly",
		false);
	private final CheckboxSetting noEat = new CheckboxSetting(
		"Disable while eating", false);
	private final CheckboxSetting noBlocking = new CheckboxSetting(
		"Disable while using blocks", false);
	private final CheckboxSetting blinkCheck = new CheckboxSetting(
		"Disable on blink", false);
	private final CheckboxSetting attackWhileUsing = new CheckboxSetting(
		"Attack while using items", false);
	private final CheckboxSetting ignoreOpenInventory = new CheckboxSetting(
		"Ignore open inventory", true);
	private final CheckboxSetting simulateInventoryClosing = new CheckboxSetting(
		"Simulate inventory closing", true);

	private final CheckboxSetting keepSprint = new CheckboxSetting(
		"Keep sprint", true);
	private final SwingHandSetting swingHand = new SwingHandSetting(
		SwingHandSetting.genericCombatDescription(this), SwingHand.CLIENT);
	private final CheckboxSetting failSwing = new CheckboxSetting("Fail swing",
		false);
	private final CheckboxSetting rangeAura = new CheckboxSetting("Range aura",
		"Shows the configured attack distance as a theme-colored ring.", true)
			.aliases("range indicator", "attack range display", "范围光环");

	private final EnumSetting<AutoBlockMode> autoBlock = new EnumSetting<>(
		"Auto block", AutoBlockMode.values(), AutoBlockMode.PACKET);
	private final SliderSetting blockMaxRange = new SliderSetting(
		"Block max range", 3, 0, 8, 0.05,
		ValueDisplay.DECIMAL.withSuffix(" blocks"))
			.visibleWhen(() -> autoBlock.getSelected() == AutoBlockMode.PACKET);
	private final EnumSetting<UnblockMode> unblockMode = new EnumSetting<>(
		"Unblock mode", UnblockMode.values(), UnblockMode.STOP)
			.visibleWhen(() -> autoBlock.getSelected() == AutoBlockMode.PACKET);
	private final CheckboxSetting releaseAutoBlock = new CheckboxSetting(
		"Release before attack", true)
			.visibleWhen(() -> autoBlock.getSelected() == AutoBlockMode.PACKET);
	private final CheckboxSetting ignoreTickRule = new CheckboxSetting(
		"Ignore tick rule", false)
			.visibleWhen(() -> autoBlock.getSelected() == AutoBlockMode.PACKET);
	private final SliderSetting blockRate = new SliderSetting("Block rate", 100,
		1, 100, 1, ValueDisplay.PERCENTAGE)
			.visibleWhen(() -> autoBlock.getSelected() == AutoBlockMode.PACKET);
	private final CheckboxSetting interactAutoBlock = new CheckboxSetting(
		"Interact auto block", true)
			.visibleWhen(() -> autoBlock.getSelected() == AutoBlockMode.PACKET);
	private final CheckboxSetting smartAutoBlock = new CheckboxSetting(
		"Smart auto block", false)
			.visibleWhen(() -> autoBlock.getSelected() == AutoBlockMode.PACKET);
	private final CheckboxSetting forceBlockWhenStill = new CheckboxSetting(
		"Force block when still", true).visibleWhen(smartAutoBlock::isChecked);
	private final CheckboxSetting checkEnemyWeapon = new CheckboxSetting(
		"Check enemy weapon", true).visibleWhen(smartAutoBlock::isChecked);
	private final SliderSetting blockRange = new SliderSetting("Block range",
		3.7, 1, 8, 0.05, ValueDisplay.DECIMAL.withSuffix(" blocks"))
			.visibleWhen(smartAutoBlock::isChecked);
	private final SliderSetting maxOwnHurtTime = new SliderSetting(
		"Maximum own hurt time", 3, 0, 10, 1,
		ValueDisplay.INTEGER.withSuffix(" ticks"))
			.visibleWhen(smartAutoBlock::isChecked);
	private final SliderSetting maxOpponentDirectionDiff = new SliderSetting(
		"Opponent direction difference", 60, 30, 180, 5,
		ValueDisplay.DEGREES).visibleWhen(smartAutoBlock::isChecked);
	private final SliderSetting maxOpponentSwingProgress = new SliderSetting(
		"Opponent swing progress", 1, 0, 5, 1, ValueDisplay.INTEGER)
			.visibleWhen(smartAutoBlock::isChecked);

	private final EntityFilterList entityFilters = EntityFilterList.genericCombat();
	private final CombatClickScheduler clickScheduler =
		new CombatClickScheduler();
	private final Random random = new Random();
	private List<Entity> targets = List.of();
	private Entity currentTarget;
	private Entity pendingPrimary;
	private Rotation plannedRotation;
	private RotationQueue rotationQueue;
	private boolean pendingFailSwing;
	private boolean blockVisual;
	private InteractionHand blockingHand;
	private float rolledRange = -1;
	private int rangeRollCounter;

	public MultiAuraHack()
	{
		super("MultiAura");
		setCategory(Category.COMBAT);
		addConflictGroup(HackConflictGroup.COMBAT_TARGETING);
		addSetting(range);
		addSetting(scanRange);
		addSetting(throughWallsRange);
		addSetting(sprintRangeReduction);
		addSetting(rangeChance);
		addSetting(minCps);
		addSetting(maxCps);
		addSetting(clickPattern);
		addSetting(attackCooldown);
		addSetting(minimumCooldown);
		addSetting(maximumCooldown);
		addSetting(ignoreCooldownWhenExitingRange);
		addSetting(hurtTime);
		addSetting(maxTargets);
		addSetting(priority);
		addSetting(fov);
		addSetting(aimAt);
		addSetting(targetPrediction);
		addSetting(selfPrediction);
		addSetting(checkLOS);
		addSetting(raycastMode);
		addSetting(raycastIgnored);
		addSetting(livingRaycast);
		addSetting(raytraceMode);
		addSetting(rotationMode);
		addSetting(rotationTiming);
		addSetting(rotationSmooth);
		addSetting(rotationCurve);
		addSetting(activationSlot);
		addSetting(preferredSlot);
		addSetting(clickOnly);
		addSetting(onSwording);
		addSetting(onScaffold);
		addSetting(onDestroyBlock);
		addSetting(noScaffold);
		addSetting(noFly);
		addSetting(noEat);
		addSetting(noBlocking);
		addSetting(blinkCheck);
		addSetting(attackWhileUsing);
		addSetting(ignoreOpenInventory);
		addSetting(simulateInventoryClosing);
		addSetting(keepSprint);
		addSetting(swingHand);
		addSetting(failSwing);
		addSetting(rangeAura);
		addSetting(autoBlock);
		addSetting(blockMaxRange);
		addSetting(unblockMode);
		addSetting(releaseAutoBlock);
		addSetting(ignoreTickRule);
		addSetting(blockRate);
		addSetting(interactAutoBlock);
		addSetting(smartAutoBlock);
		addSetting(forceBlockWhenStill);
		addSetting(checkEnemyWeapon);
		addSetting(blockRange);
		addSetting(maxOwnHurtTime);
		addSetting(maxOpponentDirectionDiff);
		addSetting(maxOpponentSwingProgress);
		entityFilters.forEach(this::addSetting);
	}

	@Override
	protected void onEnable()
	{
		clearTargets();
		clickScheduler.reset(minCps.getValueI(), maxCps.getValueI(),
			clickPattern.getSelected(), minimumCooldown.getValueF(),
			maximumCooldown.getValueF(), System.currentTimeMillis());
		rotationQueue = new RotationQueue(RotationQueue.Priority.COMBAT);
		rotationQueue.start();
		EVENTS.add(UpdateListener.class, this);
		EVENTS.add(HandleInputListener.class, this);
		EVENTS.add(RenderListener.class, this);
	}

	@Override
	protected void onDisable()
	{
		EVENTS.remove(UpdateListener.class, this);
		EVENTS.remove(HandleInputListener.class, this);
		EVENTS.remove(RenderListener.class, this);
		stopBlocking(true);
		if(rotationQueue != null)
		{
			rotationQueue.stop();
			rotationQueue = null;
		}
		clearTargets();
	}

	@Override
	public void onUpdate()
	{
		configureClicker();
		clickScheduler.advanceTick();
		pendingPrimary = null;
		pendingFailSwing = false;
		if(shouldCancel())
		{
			stopBlocking(false);
			clearTargets();
			return;
		}
		if(autoBlock.getSelected() != AutoBlockMode.PACKET
			&& blockingHand != null)
			stopBlocking(false);
		if(autoBlock.getSelected() == AutoBlockMode.OFF)
			blockVisual = false;

		currentTarget = selectPrimaryTarget();
		if(currentTarget == null)
		{
			targets = List.of();
			plannedRotation = null;
			blockVisual = false;
			rotationQueue.clear();
			stopBlocking(false);
			return;
		}

		WURST.getHax().autoSwordHack.setSlot(currentTarget);
		TargetPlan scanPlan = createPlan(currentTarget, getMaximumRange());
		if(scanPlan == null)
		{
			clearTargets();
			return;
		}
		updateRotation(scanPlan.aimPoint().point(), isClickTick());
		targets = collectAttackTargets();

		TargetPlan attackPlan = createPlan(currentTarget, rolledRangeFor());
		boolean hittable = attackPlan != null
			&& isLookingAtPrimary(currentTarget, rotationForAttack(attackPlan));
		if(isClickTick() && hittable)
			pendingPrimary = currentTarget;
		else if(isClickTick() && failSwing.isChecked())
			pendingFailSwing = true;
		else
			startBlocking(currentTarget);
	}

	@Override
	public void onHandleInput()
	{
		Entity primary = pendingPrimary;
		boolean fakeSwing = pendingFailSwing;
		pendingPrimary = null;
		pendingFailSwing = false;
		if(shouldCancel())
			return;
		if(primary != null)
			performClickCycles(primary);
		else if(fakeSwing)
			performFailSwing();
	}

	private void performClickCycles(Entity primary)
	{
		long now = System.currentTimeMillis();
		int clicks = clickScheduler.getClickAmount(this::isItemCooldownPassed,
			now);
		clickScheduler.beginClickTick();
		for(int click = 0; click < clicks; click++)
		{
			if(attackCooldown.isChecked()
				&& CombatActionPolicy.isAttackMissCooldownActive(0))
				continue;
			if(!isItemCooldownPassed(0))
				continue;

			TargetPlan refreshed = createPlan(primary, rolledRangeFor());
			if(refreshed == null)
				continue;
			Rotation attackRotation = rotationForAttack(refreshed);
			Entity raycastTarget = resolveRaycastTarget(primary, attackRotation);
			if(raycastMode.getSelected() != RaycastMode.NONE
				&& raycastTarget != primary)
			{
				if(raycastIgnored.isChecked() && raycastTarget != null
					&& isValidScanTarget(raycastTarget))
				{
					primary = raycastTarget;
					currentTarget = raycastTarget;
					refreshed = createPlan(primary, rolledRangeFor());
					if(refreshed == null)
						continue;
					attackRotation = rotationForAttack(refreshed);
				}else if(raytraceMode.getSelected() == RaytraceMode.STRICT)
					continue;
			}
			if(!isLookingAtPrimary(primary, attackRotation))
				continue;

			if(isBlocking() && releaseAutoBlock.isChecked()
				&& autoBlock.getSelected() == AutoBlockMode.PACKET)
			{
				boolean stopped = stopBlocking(false);
				if(stopped && !ignoreTickRule.isChecked())
					continue;
			}
			if(MC.player.isUsingItem() && !attackWhileUsing.isChecked()
				&& !isBlocking())
				continue;

			if(shouldSimulateInventoryClose())
				MC.player.connection.send(new ServerboundContainerClosePacket(
					MC.player.containerMenu.containerId));
			boolean onTick = rotationMode.getSelected() != RotationMode.NONE
				&& rotationTiming.getSelected() == RotationTiming.ON_TICK;
			if(onTick)
				sendFullRotation(attackRotation);

			List<Entity> freshTargets = collectAttackTargets();
			boolean attacked = false;
			for(Entity entity : freshTargets)
			{
				if(!isValidAttackTarget(entity))
					continue;
				boolean wasSprinting = MC.player.isSprinting();
				swingHand.swing(InteractionHand.MAIN_HAND);
				MC.gameMode.attack(MC.player, entity);
				if(keepSprint.isChecked() && wasSprinting)
					restoreSprint();
				attacked = true;
			}

			if(onTick)
				sendFullRotation(
					new Rotation(MC.player.getYRot(), MC.player.getXRot()));
			if(attacked)
			{
				clickScheduler.recordSuccessfulClick(now);
				startBlocking(primary);
			}
		}
	}

	private void performFailSwing()
	{
		long now = System.currentTimeMillis();
		int clicks = clickScheduler.getClickAmount(this::isItemCooldownPassed,
			now);
		clickScheduler.beginClickTick();
		for(int click = 0; click < clicks; click++)
		{
			if(!isItemCooldownPassed(0) || MC.hitResult == null
				|| MC.hitResult.getType() != HitResult.Type.MISS)
				continue;
			if(attackCooldown.isChecked()
				&& CombatActionPolicy.isAttackMissCooldownActive(0))
				continue;
			// TODO: 26.1.2 - setMissTime() removed
			// if(attackCooldown.isChecked())
			// 	((IMinecraftClient)MC).setMissTime(10);
			swingHand.swing(InteractionHand.MAIN_HAND);
			clickScheduler.recordSuccessfulClick(now);
		}
	}

	private Entity selectPrimaryTarget()
	{
		List<Entity> candidates = CombatTargetUtils.getList(getMaximumRange(),
			fov.getValue() * 2, this::getPredictedAimPoint, entityFilters, false,
			net.wurstclient.util.CombatTargetUtils.Priority.DISTANCE,
			Integer.MAX_VALUE);
		Comparator<Entity> comparator = Comparator
			.comparingInt(this::getTargetTypeWeight)
			.thenComparing(priority.getSelected().comparator(this))
			.thenComparingDouble(CombatTargetUtils::distanceToBoxSqr)
			.thenComparingInt(Entity::getId);
		for(Entity entity : candidates.stream()
			.filter(LivingEntity.class::isInstance).sorted(comparator).toList())
			if(createPlan(entity, getMaximumRange()) != null)
				return entity;
		return null;
	}

	private List<Entity> collectAttackTargets()
	{
		if(MC.level == null)
			return List.of();
		List<Entity> worldOrder = new ArrayList<>();
		for(Entity entity : MC.level.entitiesForRendering())
			if(entity instanceof LivingEntity)
				worldOrder.add(entity);
		return MultiTargetAttackPlanner.plan(worldOrder,
			this::isValidAttackTarget, this::getHurtTime, hurtTime.getValueI(),
			maxTargets.getValueI());
	}

	private boolean isValidScanTarget(Entity entity)
	{
		return CombatTargetUtils.isValid(entity, getMaximumRange(),
			fov.getValue() * 2, this::getPredictedAimPoint, entityFilters, false)
			&& getHurtTime(entity) <= hurtTime.getValueI();
	}

	private boolean isValidAttackTarget(Entity entity)
	{
		if(!isValidScanTarget(entity))
			return false;
		double distanceSq = CombatTargetUtils.distanceToBoxSqr(entity);
		double attackRange = getMultiAttackRange(entity);
		if(distanceSq > attackRange * attackRange)
			return false;
		if(!checkLOS.isChecked())
			return true;
		Vec3 point = getPredictedAimPoint(entity);
		return BlockUtils.hasLineOfSight(point)
			|| distanceSq <= throughWallsRange.getValueSq();
	}

	private TargetPlan createPlan(Entity entity, double planRange)
	{
		if(entity == null || !isValidScanTarget(entity))
			return null;
		AABB box = entity.getBoundingBox().move(
			entity.getDeltaMovement().scale(targetPrediction.getValue()));
		Vec3 futureEyes = RotationUtils.getEyesPos().add(
			MC.player.getDeltaMovement().scale(selfPrediction.getValue()));
		AimPoint point = CombatAimPointPlanner.find(box, futureEyes,
			getPredictedAimPoint(entity), planRange,
			Math.min(planRange, throughWallsRange.getValue()),
			candidate -> BlockUtils.hasLineOfSight(RotationUtils.getEyesPos(),
				candidate),
			candidate -> currentServerRotation().getAngleTo(
				RotationUtils.getNeededRotations(candidate)));
		return point == null ? null : new TargetPlan(entity, point);
	}

	private Vec3 getPredictedAimPoint(Entity entity)
	{
		return aimAt.getAimPoint(entity).add(
			entity.getDeltaMovement().scale(targetPrediction.getValue()));
	}

	private int getHurtTime(Entity entity)
	{
		return entity instanceof LivingEntity living ? living.hurtTime : 0;
	}

	private double rolledRangeFor()
	{
		if(rangeChance.getValueI() >= 100)
			return range.getValue();
		if(rolledRange < 0 || --rangeRollCounter <= 0)
		{
			rolledRange = random.nextInt(100) < rangeChance.getValueI()
				? range.getValueF() : Math.min(range.getValueF(), 3);
			rangeRollCounter = 10;
		}
		return Math.min(rolledRange, range.getValue());
	}

	private double getMaximumRange()
	{
		return Math.max(range.getValue() + scanRange.getValue(),
			throughWallsRange.getValue());
	}

	private double getMultiAttackRange(Entity entity)
	{
		double distance = Math.sqrt(CombatTargetUtils.distanceToBoxSqr(entity));
		double value = distance >= throughWallsRange.getValue()
			? range.getValue() + scanRange.getValue()
			: throughWallsRange.getValue();
		if(MC.player.isSprinting())
			value -= sprintRangeReduction.getValue();
		return Math.max(0, value);
	}

	private boolean shouldCancel()
	{
		if(MC.player == null || MC.level == null || MC.gameMode == null
			|| MC.player.isDeadOrDying() || MC.player.isSpectator())
			return true;
		if(activationSlot.isChecked()
			&& MC.player.getInventory().getSelectedSlot() != preferredSlot.getValueI() - 1)
			return true;
		if(clickOnly.isChecked() && !MC.options.keyAttack.isDown())
			return true;
		if(onSwording.isChecked()
			&& !MC.player.getMainHandItem().is(net.minecraft.tags.ItemTags.SWORDS))
			return true;
		if(WURST.getHax().scaffoldWalkHack.isEnabled()
			&& (!onScaffold.isChecked() || noScaffold.isChecked()))
			return true;
		if(!onDestroyBlock.isChecked() && MC.gameMode.isDestroying())
			return true;
		if(noFly.isChecked() && WURST.getHax().flightHack.isEnabled())
			return true;
		if(blinkCheck.isChecked() && WURST.getHax().blinkHack.isEnabled())
			return true;
		if(noEat.isChecked() && isConsumingItem())
			return true;
		if(noBlocking.isChecked() && MC.player.isUsingItem()
			&& MC.player.getUseItem().getItem() instanceof BlockItem)
			return true;
		if(isInventoryOpen() && !ignoreOpenInventory.isChecked())
			return true;
		return MC.player.isUsingItem() && !attackWhileUsing.isChecked()
			&& !isBlocking();
	}

	private boolean isConsumingItem()
	{
		if(!MC.player.isUsingItem())
			return false;
		ItemStack stack = MC.player.getUseItem();
		return stack.has(DataComponents.FOOD)
			|| stack.is(net.minecraft.world.item.Items.MILK_BUCKET)
			|| stack.getItem() instanceof PotionItem;
	}

	private boolean isInventoryOpen()
	{
		return MC.screen instanceof AbstractContainerScreen;
	}

	private boolean shouldSimulateInventoryClose()
	{
		return simulateInventoryClosing.isChecked() && isInventoryOpen();
	}

	private void configureClicker()
	{
		clickScheduler.configure(minCps.getValueI(), maxCps.getValueI(),
			clickPattern.getSelected(), minimumCooldown.getValueF(),
			maximumCooldown.getValueF());
	}

	private boolean isClickTick()
	{
		return clickScheduler.willClickAt(this::isItemCooldownPassed,
			System.currentTimeMillis(), 0);
	}

	private boolean isItemCooldownPassed(int ticks)
	{
		PlayerEntityAccessor player = (PlayerEntityAccessor)MC.player;
		LivingEntityAccessor living = (LivingEntityAccessor)MC.player;
		float delay = player.wurst_getCurrentItemAttackStrengthDelay();
		float progress = delay <= 0 ? Float.POSITIVE_INFINITY
			: (living.wurst_getAttackStrengthTicker() + ticks) / delay;
		return clickScheduler.isCooldownPassed(progress)
			|| ignoreCooldownWhenExitingRange.isChecked()
				&& predictExitingRange(1 + ticks);
	}

	private boolean predictExitingRange(double ticks)
	{
		if(currentTarget == null)
			return false;
		Vec3 futureEyes = RotationUtils.getEyesPos().add(
			MC.player.getDeltaMovement().scale(ticks));
		AABB futureBox = currentTarget.getBoundingBox().move(
			currentTarget.getDeltaMovement().scale(ticks));
		Vec3 nearest = new Vec3(
			Math.max(futureBox.minX, Math.min(futureEyes.x, futureBox.maxX)),
			Math.max(futureBox.minY, Math.min(futureEyes.y, futureBox.maxY)),
			Math.max(futureBox.minZ, Math.min(futureEyes.z, futureBox.maxZ)));
		return futureEyes.distanceToSqr(nearest)
			> getMultiAttackRange(currentTarget) * getMultiAttackRange(currentTarget);
	}

	private void updateRotation(Vec3 point, boolean clickReady)
	{
		RotationMode mode = rotationMode.getSelected();
		if(mode == RotationMode.NONE)
		{
			plannedRotation = null;
			rotationQueue.clear();
			return;
		}
		Rotation start = plannedRotation != null ? plannedRotation
			: currentServerRotation();
		Rotation needed = RotationUtils.getNeededRotations(point);
		float maxChange = 180 - rotationSmooth.getValueF() * 175;
		int ticks = Math.max(1,
			(int)Math.ceil(start.getAngleTo(needed) / maxChange));
		if(rotationTiming.getSelected() == RotationTiming.SNAP && !clickReady
			&& !clickScheduler.willClickAt(this::isItemCooldownPassed,
				System.currentTimeMillis(), ticks))
			return;
		if(rotationTiming.getSelected() == RotationTiming.ON_TICK && ticks <= 1)
		{
			plannedRotation = needed;
			rotationQueue.clear();
			return;
		}
		plannedRotation = RotationSmoothing.smooth(start, needed, maxChange,
			rotationCurve.getSelected());
		if(mode == RotationMode.SILENT)
			rotationQueue.setRotation(plannedRotation);
		else
		{
			rotationQueue.clear();
			MC.player.setYRot(plannedRotation.yaw());
			MC.player.setXRot(plannedRotation.pitch());
		}
	}

	private Rotation rotationForAttack(TargetPlan plan)
	{
		if(rotationMode.getSelected() == RotationMode.NONE)
			return new Rotation(MC.player.getYRot(), MC.player.getXRot());
		if(rotationTiming.getSelected() == RotationTiming.ON_TICK)
			return RotationUtils.getNeededRotations(plan.aimPoint().point());
		return currentServerRotation();
	}

	private Rotation currentServerRotation()
	{
		Vec3 look = RotationUtils.getServerLookVec();
		return RotationUtils.getNeededRotations(
			RotationUtils.getEyesPos().add(look));
	}

	private Entity resolveRaycastTarget(Entity selected, Rotation rotation)
	{
		if(raycastMode.getSelected() == RaycastMode.NONE)
			return selected;
		double reach = rolledRangeFor();
		Vec3 start = RotationUtils.getEyesPos();
		Vec3 look = rotation.toLookVec();
		Vec3 end = start.add(look.scale(reach));
		AABB search = MC.player.getBoundingBox()
			.expandTowards(look.scale(reach)).inflate(1);
		EntityHitResult hit = ProjectileUtil.getEntityHitResult(MC.player, start,
			end, search, entity -> !entity.isSpectator() && entity.isPickable()
				&& (!livingRaycast.isChecked()
					|| entity instanceof LivingEntity)
				&& (raycastMode.getSelected() == RaycastMode.ALL
					|| isValidScanTarget(entity)), reach * reach);
		return hit == null ? null : hit.getEntity();
	}

	private boolean isLookingAtPrimary(Entity entity, Rotation rotation)
	{
		if(rotationMode.getSelected() == RotationMode.NONE)
			return CombatTargetUtils.distanceToBoxSqr(entity)
				<= rolledRangeFor() * rolledRangeFor();
		Vec3 start = RotationUtils.getEyesPos();
		Vec3 end = start.add(rotation.toLookVec().scale(rolledRangeFor()));
		Vec3 hit = entity.getBoundingBox().inflate(entity.getPickRadius())
			.clip(start, end).orElse(null);
		if(hit == null)
			return false;
		double distanceSq = start.distanceToSqr(hit);
		if(distanceSq <= throughWallsRange.getValueSq())
			return true;
		return BlockUtils.raycast(start, hit).getType() == HitResult.Type.MISS;
	}

	private boolean startBlocking(Entity target)
	{
		AutoBlockMode mode = autoBlock.getSelected();
		if(mode == AutoBlockMode.OFF || target == null)
			return false;
		blockVisual = true;
		if(mode == AutoBlockMode.FAKE)
			return false;
		if(!canBlock(target) || random.nextInt(100) > blockRate.getValueI())
			return false;
		if(blockingHand != null || isBlocking())
			return false;
		InteractionHand hand = findBlockableHand();
		if(hand == null)
			return false;
		if(interactAutoBlock.isChecked())
		{
			EntityHitResult hit = new EntityHitResult(target,
				target.getBoundingBox().getCenter());
			MC.gameMode.interact(MC.player, target, hit, hand);
		}
		((IClientPlayerInteractionManager)MC.gameMode)
			.sendPlayerUseItemPacket(hand);
		blockingHand = hand;
		return true;
	}

	private boolean canBlock(Entity target)
	{
		if(CombatTargetUtils.distanceToBoxSqr(target)
			> blockMaxRange.getValueSq())
			return false;
		if(!smartAutoBlock.isChecked())
			return true;
		boolean moving = MC.player.getDeltaMovement().horizontalDistanceSqr()
			>= 1.0E-4;
		if(moving && forceBlockWhenStill.isChecked())
			return false;
		if(checkEnemyWeapon.isChecked() && target instanceof LivingEntity living)
		{
			ItemStack held = living.getMainHandItem();
			if(!held.is(net.minecraft.tags.ItemTags.SWORDS)
				&& !held.is(net.minecraft.tags.ItemTags.AXES))
				return false;
		}
		if(MC.player.hurtTime > maxOwnHurtTime.getValueI()
			|| CombatTargetUtils.distanceToBoxSqr(target)
				> Math.pow(Math.min(blockRange.getValue(), range.getValue()), 2))
			return false;
		if(target instanceof LivingEntity living
			&& ((LivingEntityAccessor)living).wurst_getSwingTime()
				> maxOpponentSwingProgress.getValueI())
			return false;
		Vec3 toPlayer = MC.player.getEyePosition()
			.subtract(target.getEyePosition()).normalize();
		double angle = Math.toDegrees(Math.acos(Math.max(-1,
			Math.min(1, target.getLookAngle().dot(toPlayer)))));
		return angle <= maxOpponentDirectionDiff.getValue();
	}

	private boolean stopBlocking(boolean force)
	{
		if(!force)
			blockVisual = false;
		if(blockingHand == null)
			return false;
		switch(unblockMode.getSelected())
		{
			case STOP -> sendReleaseUsingItemPacket();
			case SWITCH -> {
				int selected = MC.player.getInventory().getSelectedSlot();
				MC.player.connection.send(
					new ServerboundSetCarriedItemPacket((selected + 1) % 9));
				MC.player.connection.send(
					new ServerboundSetCarriedItemPacket(selected));
			}
			case EMPTY -> {
				int empty = findEmptyHotbarSlot();
				if(empty < 0)
					sendReleaseUsingItemPacket();
				else
				{
					int selected = MC.player.getInventory().getSelectedSlot();
					MC.player.connection.send(
						new ServerboundSetCarriedItemPacket(empty));
					MC.player.connection.send(
						new ServerboundSetCarriedItemPacket(selected));
				}
			}
		}
		blockingHand = null;
		return true;
	}

	private void sendReleaseUsingItemPacket()
	{
		((IClientPlayerInteractionManager)MC.gameMode)
			.sendPlayerActionC2SPacket(
				ServerboundPlayerActionPacket.Action.RELEASE_USE_ITEM,
				BlockPos.ZERO, Direction.DOWN);
	}

	private int findEmptyHotbarSlot()
	{
		for(int slot = 0; slot < 9; slot++)
			if(MC.player.getInventory().getItem(slot).isEmpty())
				return slot;
		return -1;
	}

	private InteractionHand findBlockableHand()
	{
		for(InteractionHand hand : InteractionHand.values())
		{
			ItemStack stack = MC.player.getItemInHand(hand);
			if(stack.getUseAnimation() == ItemUseAnimation.BLOCK
				&& !MC.player.getCooldowns().isOnCooldown(stack))
				return hand;
		}
		return null;
	}

	private boolean isBlocking()
	{
		return blockingHand != null || MC.player.isUsingItem()
			&& MC.player.getUseItem().getUseAnimation() == ItemUseAnimation.BLOCK;
	}

	public boolean shouldRenderFakeBlock()
	{
		return isEnabled() && autoBlock.getSelected() != AutoBlockMode.OFF
			&& blockVisual && (blockingHand != null || findBlockableHand() != null);
	}

	public InteractionHand getFakeBlockingHand()
	{
		if(!shouldRenderFakeBlock())
			return null;
		return blockingHand != null ? blockingHand : findBlockableHand();
	}

	private int getTargetTypeWeight(Entity entity)
	{
		return entity instanceof Player ? 0 : 1;
	}

	private double getArmorScore(Entity entity)
	{
		if(!(entity instanceof LivingEntity living))
			return Double.MAX_VALUE;
		return living.getArmorValue();
	}

	private void restoreSprint()
	{
		MC.player.setSprinting(true);
		MC.player.connection.send(new ServerboundPlayerCommandPacket(MC.player,
			Action.START_SPRINTING));
	}

	private void sendFullRotation(Rotation rotation)
	{
		MC.player.connection.send(new PosRot(MC.player.getX(), MC.player.getY(),
			MC.player.getZ(), rotation.yaw(), rotation.pitch(),
			MC.player.onGround(), MC.player.horizontalCollision));
	}

	private void clearTargets()
	{
		targets = List.of();
		currentTarget = null;
		pendingPrimary = null;
		pendingFailSwing = false;
		plannedRotation = null;
		blockVisual = false;
		rolledRange = -1;
		rangeRollCounter = 0;
		if(rotationQueue != null)
			rotationQueue.clear();
	}

	public Entity getCurrentTarget()
	{
		return currentTarget;
	}

	@Override
	public void onRender(PoseStack PoseStack, float partialTicks)
	{
		if(MC.player == null || !rangeAura.isChecked())
			return;
		AuraRangeRenderer.render(PoseStack, MC.player, partialTicks,
			getMaximumRange(), WURST.getGui().getTheme().accent(1),
			currentTarget != null);
	}

	private record TargetPlan(Entity entity, AimPoint aimPoint)
	{
	}

	private enum RaycastMode
	{
		NONE("None"), ENEMY("Enemy"), ALL("All");
		private final String name;
		RaycastMode(String name)
		{
			this.name = name;
		}
		@Override
		public String toString()
		{
			return name;
		}
	}

	private enum RotationMode
	{
		SILENT("Silent"), VISIBLE("Visible"), NONE("None");
		private final String name;
		RotationMode(String name)
		{
			this.name = name;
		}
		@Override
		public String toString()
		{
			return name;
		}
	}

	private enum RaytraceMode
	{
		NORMAL("Normal"), STRICT("Strict");
		private final String name;
		RaytraceMode(String name)
		{
			this.name = name;
		}
		@Override
		public String toString()
		{
			return name;
		}
	}

	private enum RotationTiming
	{
		NORMAL("Normal"), SNAP("Snap"), ON_TICK("OnTick");
		private final String name;
		RotationTiming(String name)
		{
			this.name = name;
		}
		@Override
		public String toString()
		{
			return name;
		}
	}

	private enum AutoBlockMode
	{
		OFF("Off"), PACKET("Packet"), FAKE("Fake");
		private final String name;
		AutoBlockMode(String name)
		{
			this.name = name;
		}
		@Override
		public String toString()
		{
			return name;
		}
	}

	private enum UnblockMode
	{
		STOP("Stop"), SWITCH("Switch"), EMPTY("Empty");
		private final String name;
		UnblockMode(String name)
		{
			this.name = name;
		}
		@Override
		public String toString()
		{
			return name;
		}
	}

	private enum TargetPriority
	{
		HEALTH("Health"), DISTANCE("Distance"), DIRECTION("Direction"),
		LIVING_TIME("Living time"), ARMOR("Armor"),
		HURT_RESISTANCE("Hurt resistance"), HURT_TIME("Hurt time"),
		HEALTH_ABSORPTION("Health absorption"),
		REGEN_AMPLIFIER("Regeneration amplifier"), ON_LADDER("On ladder"),
		IN_LIQUID("In liquid"), IN_WEB("In web");

		private final String name;
		TargetPriority(String name)
		{
			this.name = name;
		}

		private Comparator<Entity> comparator(MultiAuraHack aura)
		{
			return switch(this)
			{
				case HEALTH -> Comparator.comparingDouble(entity ->
					entity instanceof LivingEntity living ? living.getHealth()
						: Double.MAX_VALUE);
				case DISTANCE -> Comparator.comparingDouble(
					CombatTargetUtils::distanceToBoxSqr);
				case DIRECTION -> Comparator.comparingDouble(entity ->
					RotationUtils.getAngleToLookVec(aura.getPredictedAimPoint(entity)));
				case LIVING_TIME -> Comparator.comparingInt(
					(Entity entity) -> entity.tickCount)
					.reversed();
				case ARMOR -> Comparator.comparingDouble(aura::getArmorScore);
				case HURT_RESISTANCE -> Comparator.comparingInt(entity ->
					entity instanceof LivingEntity living ? living.invulnerableTime : 0);
				case HURT_TIME -> Comparator.comparingInt(aura::getHurtTime);
				case HEALTH_ABSORPTION -> Comparator.comparingDouble(entity ->
					entity instanceof LivingEntity living ? living.getHealth()
						+ living.getAbsorptionAmount() : Double.MAX_VALUE);
				case REGEN_AMPLIFIER -> Comparator.comparingInt(entity -> {
					if(!(entity instanceof LivingEntity living))
						return Integer.MAX_VALUE;
					MobEffectInstance effect = living.getEffect(MobEffects.REGENERATION);
					return effect == null ? -1 : effect.getAmplifier();
				});
				case ON_LADDER -> Comparator.comparingInt(entity ->
					entity instanceof LivingEntity living && living.onClimbable() ? 0 : 1);
				case IN_LIQUID -> Comparator.comparingInt(entity ->
					entity instanceof LivingEntity living
						&& (living.isInWaterOrSwimmable() || living.isInLava()) ? 0 : 1);
				case IN_WEB -> Comparator.comparingInt(entity ->
					aura.MC.level.getBlockState(entity.blockPosition())
						.is(Blocks.COBWEB) ? 0 : 1);
			};
		}

		@Override
		public String toString()
		{
			return name;
		}
	}
}

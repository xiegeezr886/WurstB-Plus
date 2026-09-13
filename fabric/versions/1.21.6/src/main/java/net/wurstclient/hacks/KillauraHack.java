/*
 * This file contains a Forge/Mojmap adaptation of LiquidBounce's KillAura.
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

import java.util.Comparator;
import java.util.List;
import java.util.Random;

import com.mojang.blaze3d.vertex.PoseStack;

import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.protocol.game.ServerboundContainerClosePacket;
import net.minecraft.network.protocol.game.ServerboundMovePlayerPacket.PosRot;
import net.minecraft.network.protocol.game.ServerboundPlayerActionPacket;
import net.minecraft.network.protocol.game.ServerboundPlayerCommandPacket;
import net.minecraft.network.protocol.game.ServerboundPlayerCommandPacket.Action;
import net.minecraft.network.protocol.game.ServerboundSetCarriedItemPacket;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.ItemUseAnimation;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.item.AxeItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
// SwordItem removed in MC 26.1.2
import net.minecraft.world.item.TridentItem;
// UseAnim removed in MC 26.1.2
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
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
import net.wurstclient.util.CombatTargetUtils.Priority;
import net.wurstclient.util.EntityUtils;
import net.wurstclient.util.EnchantmentUtils;
import net.wurstclient.util.RenderUtils;
import net.wurstclient.util.Rotation;
import net.wurstclient.util.RotationQueue;
import net.wurstclient.util.RotationSmoothing;
import net.wurstclient.util.RotationUtils;
import net.wurstclient.util.render.AuraRangeRenderer;

@SearchTags({"kill aura", "ForceField", "force field", "CrystalAura",
	"crystal aura", "AutoCrystal", "auto crystal"})
public final class KillauraHack extends Hack
	implements UpdateListener, HandleInputListener, RenderListener
{
	private final SliderSetting rangeIncrease = new SliderSetting(
		"Range increase", "Added to Minecraft 1.20.1's 3 block interaction range.",
		1.2, 0, 5, 0.05, ValueDisplay.DECIMAL.withSuffix(" blocks"));
	private final SliderSetting throughWallsRange = new SliderSetting(
		"Through walls range", "Interaction range when a block obstructs the hit.",
		3, 0, 8, 0.05, ValueDisplay.DECIMAL.withSuffix(" blocks"));
	private final SliderSetting scanRangeMin = new SliderSetting(
		"Minimum scan increase", 2, 0, 7, 0.1,
		ValueDisplay.DECIMAL.withSuffix(" blocks"));
	private final SliderSetting scanRangeMax = new SliderSetting(
		"Maximum scan increase", 3, 0, 7, 0.1,
		ValueDisplay.DECIMAL.withSuffix(" blocks"));

	private final SliderSetting minCps = new SliderSetting("Minimum CPS", 5,
		1, 60, 1, ValueDisplay.INTEGER.withSuffix(" clicks"));
	private final SliderSetting maxCps = new SliderSetting("Maximum CPS", 8,
		1, 60, 1, ValueDisplay.INTEGER.withSuffix(" clicks"));
	private final EnumSetting<ClickPattern> clickPattern = new EnumSetting<>(
		"Click technique", ClickPattern.values(), ClickPattern.STABILIZED);
	private final CheckboxSetting attackCooldown = new CheckboxSetting(
		"Attack cooldown", "Respects Minecraft's cooldown after a missed attack.",
		true);
	private final SliderSetting minimumCooldown = new SliderSetting(
		"Minimum item cooldown", 1, 0, 2, 0.05, ValueDisplay.PERCENTAGE);
	private final SliderSetting maximumCooldown = new SliderSetting(
		"Maximum item cooldown", 1, 0, 2, 0.05, ValueDisplay.PERCENTAGE);
	private final CheckboxSetting ignoreCooldownOnShieldBreak =
		new CheckboxSetting("Ignore cooldown on shield break", true);
	private final CheckboxSetting ignoreCooldownWhenExitingRange =
		new CheckboxSetting("Ignore cooldown when exiting range", true);

	private final CheckboxSetting prioritizeType = new CheckboxSetting(
		"Prioritize target type", "Orders players before hostile and neutral mobs.",
		true);
	private final EnumSetting<Priority> priority = new EnumSetting<>("Priority",
		Priority.values(), Priority.HEALTH);
	private final SliderSetting fov = new SliderSetting("FOV", 180, 0, 180,
		5, ValueDisplay.DEGREES);
	private final SliderSetting hurtTime = new SliderSetting("Hurt time", 10,
		0, 10, 1, ValueDisplay.INTEGER.withSuffix(" ticks"));
	private final AimAtSetting aimAt = new AimAtSetting(
		"Preferred point used by the sampled hit-box point tracker.");
	private final SliderSetting targetPrediction = new SliderSetting(
		"Target prediction", "Ticks of target movement predicted for aiming.",
		1.5, -1, 3, 0.1, ValueDisplay.DECIMAL.withSuffix(" ticks"));
	private final SliderSetting selfPrediction = new SliderSetting(
		"Self prediction", "Ticks of local movement predicted for range checks.",
		2, 0, 5, 1, ValueDisplay.INTEGER.withSuffix(" ticks"));
	private final CheckboxSetting checkLOS = new CheckboxSetting(
		"Check line of sight", true);
	private final CheckboxSetting ignoreShield = new CheckboxSetting(
		"Ignore target shield", true);

	private final EnumSetting<RaycastMode> raycastMode = new EnumSetting<>(
		"Raycast", RaycastMode.values(), RaycastMode.ALL);
	private final EnumSetting<CriticalsSelectionMode> criticalsSelection =
		new EnumSetting<>("Criticals", CriticalsSelectionMode.values(),
			CriticalsSelectionMode.SMART);
	private final CheckboxSetting keepSprint = new CheckboxSetting(
		"Keep sprint", true);

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

	private final CheckboxSetting requireAttackKey = new CheckboxSetting(
		"Require click", false);
	private final CheckboxSetting requireWeapon = new CheckboxSetting(
		"Require weapon", false);
	private final CheckboxSetting requireEmptyHand = new CheckboxSetting(
		"Require empty hand", false);
	private final CheckboxSetting requireVanillaName = new CheckboxSetting(
		"Require vanilla name", false);
	private final CheckboxSetting pauseWhileMining = new CheckboxSetting(
		"Require not breaking", true);
	private final CheckboxSetting attackWhileUsing = new CheckboxSetting(
		"Attack while using items", false);
	private final CheckboxSetting ignoreOpenInventory = new CheckboxSetting(
		"Ignore open inventory", true);
	private final CheckboxSetting simulateInventoryClosing = new CheckboxSetting(
		"Simulate inventory closing", true);

	private final CheckboxSetting autoBlock = new CheckboxSetting(
		"Auto block", false);
	private final EnumSetting<BlockMode> blockMode = new EnumSetting<>(
		"Block mode", BlockMode.values(), BlockMode.INTERACT)
			.visibleWhen(autoBlock::isChecked);
	private final CheckboxSetting simulateVanillaUse = new CheckboxSetting(
		"Simulate vanilla use", true).visibleWhen(autoBlock::isChecked);
	private final EnumSetting<UnblockMode> unblockMode = new EnumSetting<>(
		"Unblock mode", UnblockMode.values(), UnblockMode.STOP_USING_ITEM)
			.visibleWhen(autoBlock::isChecked);
	private final SliderSetting reblockTicksMin = new SliderSetting(
		"Minimum reblock delay", 0, 0, 3, 1,
		ValueDisplay.INTEGER.withSuffix(" ticks")).visibleWhen(autoBlock::isChecked);
	private final SliderSetting reblockTicksMax = new SliderSetting(
		"Maximum reblock delay", 0, 0, 3, 1,
		ValueDisplay.INTEGER.withSuffix(" ticks")).visibleWhen(autoBlock::isChecked);
	private final SliderSetting pauseOnUnblockMin = new SliderSetting(
		"Minimum unblock pause", 0, 0, 3, 1,
		ValueDisplay.INTEGER.withSuffix(" ticks")).visibleWhen(autoBlock::isChecked);
	private final SliderSetting pauseOnUnblockMax = new SliderSetting(
		"Maximum unblock pause", 0, 0, 3, 1,
		ValueDisplay.INTEGER.withSuffix(" ticks")).visibleWhen(autoBlock::isChecked);
	private final SliderSetting blockChance = new SliderSetting("Block chance",
		100, 0, 100, 1, ValueDisplay.PERCENTAGE).visibleWhen(autoBlock::isChecked);
	private final CheckboxSetting prioritizeBlocking = new CheckboxSetting(
		"Prioritize blocking", true).visibleWhen(autoBlock::isChecked);
	private final CheckboxSetting blockOnScanRange = new CheckboxSetting(
		"Block on scan range", true).visibleWhen(autoBlock::isChecked);
	private final CheckboxSetting onlyBlockInDanger = new CheckboxSetting(
		"Only block in danger", false).visibleWhen(autoBlock::isChecked);

	private final CheckboxSetting failSwing = new CheckboxSetting("Fail swing",
		false);
	private final SliderSetting failSwingRangeMin = new SliderSetting(
		"Minimum fail range", 2.5, 0, 10, 0.1,
		ValueDisplay.DECIMAL.withSuffix(" blocks")).visibleWhen(failSwing::isChecked);
	private final SliderSetting failSwingRangeMax = new SliderSetting(
		"Maximum fail range", 3, 0, 10, 0.1,
		ValueDisplay.DECIMAL.withSuffix(" blocks")).visibleWhen(failSwing::isChecked);
	private final SwingHandSetting swingHand = new SwingHandSetting(
		SwingHandSetting.genericCombatDescription(this), SwingHand.CLIENT);
	private final CheckboxSetting damageIndicator = new CheckboxSetting(
		"Damage indicator", true);
	private final CheckboxSetting rangeAura = new CheckboxSetting("Range aura",
		"Shows the configured attack distance as a theme-colored ring.", true)
			.aliases("range indicator", "attack range display", "范围光环");
	private final EntityFilterList entityFilters = EntityFilterList.genericCombat();

	private final CombatClickScheduler clickScheduler =
		new CombatClickScheduler();
	private final Random random = new Random();
	private TargetPlan targetPlan;
	private Entity pendingAttack;
	private Entity renderTarget;
	private Rotation plannedRotation;
	private RotationQueue rotationQueue;
	private boolean pendingFailSwing;
	private boolean blockVisual;
	private boolean hasBlockedSinceAttack;
	private boolean isInDanger;
	private InteractionHand enforcedBlockingHand;
	private int waitTicks;
	private int reblockTicks;
	private int pauseOnUnblockTicks;
	private double currentScanRangeAddition;
	private double currentFailSwingRange;
	private long lastAttackKeyTime;

	public KillauraHack()
	{
		super("Killaura");
		setCategory(Category.COMBAT);
		addConflictGroup(HackConflictGroup.COMBAT_TARGETING);

		addSetting(rangeIncrease);
		addSetting(throughWallsRange);
		addSetting(scanRangeMin);
		addSetting(scanRangeMax);
		addSetting(minCps);
		addSetting(maxCps);
		addSetting(clickPattern);
		addSetting(attackCooldown);
		addSetting(minimumCooldown);
		addSetting(maximumCooldown);
		addSetting(ignoreCooldownOnShieldBreak);
		addSetting(ignoreCooldownWhenExitingRange);
		addSetting(prioritizeType);
		addSetting(priority);
		addSetting(fov);
		addSetting(hurtTime);
		addSetting(aimAt);
		addSetting(targetPrediction);
		addSetting(selfPrediction);
		addSetting(checkLOS);
		addSetting(ignoreShield);
		addSetting(raycastMode);
		addSetting(criticalsSelection);
		addSetting(keepSprint);
		addSetting(rotationMode);
		addSetting(rotationTiming);
		addSetting(rotationSmooth);
		addSetting(rotationCurve);
		addSetting(requireAttackKey);
		addSetting(requireWeapon);
		addSetting(requireEmptyHand);
		addSetting(requireVanillaName);
		addSetting(pauseWhileMining);
		addSetting(attackWhileUsing);
		addSetting(ignoreOpenInventory);
		addSetting(simulateInventoryClosing);
		addSetting(autoBlock);
		addSetting(blockMode);
		addSetting(simulateVanillaUse);
		addSetting(unblockMode);
		addSetting(reblockTicksMin);
		addSetting(reblockTicksMax);
		addSetting(pauseOnUnblockMin);
		addSetting(pauseOnUnblockMax);
		addSetting(blockChance);
		addSetting(prioritizeBlocking);
		addSetting(blockOnScanRange);
		addSetting(onlyBlockInDanger);
		addSetting(failSwing);
		addSetting(failSwingRangeMin);
		addSetting(failSwingRangeMax);
		addSetting(swingHand);
		addSetting(damageIndicator);
		addSetting(rangeAura);
		entityFilters.forEach(this::addSetting);
	}

	@Override
	protected void onEnable()
	{
		clearTracking();
		currentScanRangeAddition = randomRange(scanRangeMin, scanRangeMax);
		currentFailSwingRange = randomRange(failSwingRangeMin, failSwingRangeMax);
		reblockTicks = randomTicks(reblockTicksMin, reblockTicksMax);
		pauseOnUnblockTicks = randomTicks(pauseOnUnblockMin, pauseOnUnblockMax);
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
		stopBlocking(false);
		if(rotationQueue != null)
		{
			rotationQueue.stop();
			rotationQueue = null;
		}
		clearTracking();
	}

	@Override
	public void onUpdate()
	{
		configureClicker();
		clickScheduler.advanceTick();
		pendingAttack = null;
		pendingFailSwing = false;
		if(MC.options.keyAttack.isDown())
			lastAttackKeyTime = System.currentTimeMillis();
		if(waitTicks > 0)
			waitTicks--;

		if(shouldResetTarget())
		{
			stopBlocking(false);
			clearTracking();
			return;
		}

		targetPlan = selectTarget();
		renderTarget = targetPlan == null ? null : targetPlan.entity();
		updateDangerState();
		if(targetPlan == null)
		{
			plannedRotation = null;
			rotationQueue.clear();
			boolean unblocked = stopBlocking(false);
			if(!unblocked || pauseOnUnblockTicks == 0)
				pendingFailSwing = canScheduleFailSwing();
			else
				waitTicks = pauseOnUnblockTicks;
			return;
		}

		if(autoBlock.isChecked())
			blockVisual = true;
		WURST.getHax().autoSwordHack.setSlot(targetPlan.entity());
		boolean clickTick = isClickTick();
		updateRotation(targetPlan.aimPoint().point(), clickTick);
		TargetPlan attackPlan = createPlan(targetPlan.entity(), false);
		if(attackPlan == null)
		{
			if(autoBlock.isChecked() && blockOnScanRange.isChecked()
				&& CombatTargetUtils.distanceToBoxSqr(targetPlan.entity())
					<= getMaximumScanRange() * getMaximumScanRange())
			{
				if(clickScheduler.getTicksSinceLastClick() >= reblockTicks)
					startBlocking();
				return;
			}

			boolean unblocked = stopBlocking(false);
			if(unblocked && pauseOnUnblockTicks > 0)
				waitTicks = pauseOnUnblockTicks;
			else
				pendingFailSwing = canScheduleFailSwing();
			return;
		}

		if(clickTick && canAttackNow(targetPlan.entity())
			&& !isPrioritizingBlocking() && waitTicks == 0)
			pendingAttack = targetPlan.entity();
		else if(clickScheduler.getTicksSinceLastClick() >= reblockTicks)
			startBlocking();
	}

	@Override
	public void onHandleInput()
	{
		Entity selected = pendingAttack;
		boolean fakeSwing = pendingFailSwing;
		pendingAttack = null;
		pendingFailSwing = false;
		if(shouldResetTarget() || waitTicks > 0)
			return;

		if(selected != null)
			performScheduledAttacks(selected);
		else if(fakeSwing)
			performFailSwing();
	}

	private void performScheduledAttacks(Entity selected)
	{
		long now = System.currentTimeMillis();
		int clicks = clickScheduler.getClickAmount(this::isItemCooldownPassed,
			now);
		clickScheduler.beginClickTick();
		for(int i = 0; i < clicks; i++)
		{
			if(attackCooldown.isChecked()
				&& CombatActionPolicy.isAttackMissCooldownActive(0))
				continue;
			if(!isItemCooldownPassed(0) || !canAttackNow(selected))
				continue;

			TargetPlan refreshed = createPlan(selected, false);
			if(refreshed == null)
			{
				performFailSwingAttempt(now);
				continue;
			}

			Rotation attackRotation = rotationForAttack(refreshed);
			Entity attackTarget = resolveRaycastTarget(selected, attackRotation);
			boolean traceAllTarget = raycastMode.getSelected() == RaycastMode.ALL
				&& attackTarget != selected;
			if(attackTarget instanceof LivingEntity && attackTarget != selected
				&& isValidScanTarget(attackTarget))
				targetPlan = createPlan(attackTarget, true);
			if(attackTarget == null
				|| (!traceAllTarget && !isValidAttackTarget(attackTarget))
				|| !isLookingAt(attackTarget, attackRotation))
			{
				performFailSwingAttempt(now);
				continue;
			}

			if(!prepareForAttack(attackRotation))
				return;
			if(!canAttackNow(attackTarget)
				|| (!traceAllTarget && !isValidAttackTarget(attackTarget)))
			{
				finishAttackPreparation(attackRotation, false);
				continue;
			}

			boolean wasSprinting = MC.player.isSprinting();
			swingHand.swing(InteractionHand.MAIN_HAND);
			MC.gameMode.attack(MC.player, attackTarget);
			if(keepSprint.isChecked() && wasSprinting
				&& !shouldBlockSprinting())
				restoreSprint();
			clickScheduler.recordSuccessfulClick(now);
			currentScanRangeAddition = randomRange(scanRangeMin, scanRangeMax);
			currentFailSwingRange = randomRange(failSwingRangeMin,
				failSwingRangeMax);
			hasBlockedSinceAttack = false;
			finishAttackPreparation(attackRotation, true);
		}
	}

	private boolean prepareForAttack(Rotation rotation)
	{
		if(isBlocking())
		{
			if(!autoBlock.isChecked() && !attackWhileUsing.isChecked())
				return false;
			if(autoBlock.isChecked() && unblockMode.getSelected() != UnblockMode.NONE
				&& stopBlocking(true) && pauseOnUnblockTicks > 0)
			{
				waitTicks = pauseOnUnblockTicks;
				return false;
			}
		}else if(MC.player.isUsingItem() && !attackWhileUsing.isChecked())
			return false;

		if(shouldSimulateInventoryClose())
			MC.player.connection.send(new ServerboundContainerClosePacket(
				MC.player.containerMenu.containerId));
		if(rotationMode.getSelected() != RotationMode.NONE
			&& rotationTiming.getSelected() == RotationTiming.ON_TICK)
			sendFullRotation(rotation);
		return true;
	}

	private void finishAttackPreparation(Rotation attackRotation,
		boolean attacked)
	{
		if(rotationMode.getSelected() != RotationMode.NONE
			&& rotationTiming.getSelected() == RotationTiming.ON_TICK)
		{
			Rotation real = new Rotation(MC.player.getYRot(), MC.player.getXRot());
			sendFullRotation(real);
		}
		if(attacked && reblockTicks == 0)
			startBlocking();
	}

	private void performFailSwing()
	{
		long now = System.currentTimeMillis();
		int clicks = clickScheduler.getClickAmount(this::isItemCooldownPassed,
			now);
		clickScheduler.beginClickTick();
		for(int i = 0; i < clicks; i++)
			performFailSwingAttempt(now);
	}

	private void performFailSwingAttempt(long now)
	{
		if(!failSwing.isChecked() || !isItemCooldownPassed(0)
			|| MC.hitResult == null || MC.hitResult.getType() != HitResult.Type.MISS)
			return;
		if(targetPlan != null)
		{
			double maximum = getInteractionRange() + currentFailSwingRange;
			if(CombatTargetUtils.distanceToBoxSqr(targetPlan.entity())
				> maximum * maximum)
				return;
		}
		if(attackCooldown.isChecked()
			&& CombatActionPolicy.isAttackMissCooldownActive(0))
			return;
		if(!prepareForAttack(null))
			return;

		// TODO: 26.1.2 - setMissTime() removed
		// if(attackCooldown.isChecked())
		// 	((IMinecraftClient)MC).setMissTime(10);
		swingHand.swing(InteractionHand.MAIN_HAND);
		clickScheduler.recordSuccessfulClick(now);
		finishAttackPreparation(null, true);
	}

	private boolean shouldResetTarget()
	{
		if(MC.player == null || MC.level == null || MC.gameMode == null
			|| MC.player.isDeadOrDying() || MC.player.isSpectator())
			return true;
		if(!requirementsMet())
			return true;
		return isInventoryOpen() && !ignoreOpenInventory.isChecked();
	}

	private boolean requirementsMet()
	{
		if(requireAttackKey.isChecked() && !MC.options.keyAttack.isDown()
			&& System.currentTimeMillis() - lastAttackKeyTime > 250)
			return false;
		ItemStack stack = MC.player.getMainHandItem();
		if(requireWeapon.isChecked() && !isWeapon(stack))
			return false;
		if(requireEmptyHand.isChecked() && !stack.isEmpty())
			return false;
		if(requireVanillaName.isChecked()
			&& stack.has(net.minecraft.core.component.DataComponents.CUSTOM_NAME))
			return false;
		return !pauseWhileMining.isChecked() || !MC.gameMode.isDestroying();
	}

	private boolean canAttackNow(Entity target)
	{
		if(target == null || !criticalsSelection.getSelected().allowsAttack(target))
			return false;
		return !isInventoryOpen() || ignoreOpenInventory.isChecked()
			|| simulateInventoryClosing.isChecked();
	}

	private boolean shouldSimulateInventoryClose()
	{
		return simulateInventoryClosing.isChecked() && isInventoryOpen();
	}

	private boolean isInventoryOpen()
	{
		return MC.screen instanceof AbstractContainerScreen;
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
		if(clickScheduler.isCooldownPassed(progress))
			return true;
		if(ignoreCooldownOnShieldBreak.isChecked() && targetPlan != null
			&& targetWouldBlock(targetPlan.entity())
			&& MC.player.getMainHandItem().getItem() instanceof AxeItem)
			return true;
		return ignoreCooldownWhenExitingRange.isChecked()
			&& predictExitingRange(1 + ticks);
	}

	private boolean predictExitingRange(double ticks)
	{
		if(targetPlan == null || ticks <= 0)
			return false;
		Entity target = targetPlan.entity();
		if(target instanceof LivingEntity living && living.hurtTime > 7)
			return false;
		Vec3 futureEyes = RotationUtils.getEyesPos()
			.add(MC.player.getDeltaMovement().scale(ticks));
		AABB futureBox = target.getBoundingBox()
			.move(target.getDeltaMovement().scale(ticks));
		return CombatAimPointPlanner.find(futureBox, futureEyes,
			futureBox.getCenter(), getInteractionRange(), getWallRange(),
			point -> BlockUtils.hasLineOfSight(futureEyes, point), point -> 0) == null;
	}

	private TargetPlan selectTarget()
	{
		List<Entity> candidates = CombatTargetUtils.getList(getMaximumScanRange(),
			fov.getValue() * 2, this::getPredictedPreferredPoint, entityFilters,
			false, priority.getSelected(), Integer.MAX_VALUE);
		Comparator<Entity> comparator = priority.getSelected()
			.getComparator(this::getPredictedPreferredPoint);
		if(prioritizeType.isChecked())
			comparator = Comparator.comparingInt(this::getTargetTypeWeight)
				.thenComparing(comparator);
		candidates = candidates.stream().sorted(comparator).toList();

		double normalRangeSq = getInteractionRange() * getInteractionRange();
		candidates = candidates.stream().sorted(Comparator.comparingInt(entity ->
			CombatTargetUtils.distanceToBoxSqr(entity) <= normalRangeSq ? 0 : 1))
			.toList();
		for(Entity candidate : candidates)
		{
			TargetPlan plan = createPlan(candidate, true);
			if(plan != null)
				return plan;
		}
		return null;
	}

	private TargetPlan createPlan(Entity entity, boolean scanning)
	{
		if(!isValidScanTarget(entity))
			return null;
		double ticks = targetPrediction.getValue();
		AABB predictedBox = entity.getBoundingBox()
			.move(entity.getDeltaMovement().scale(ticks));
		Vec3 preferred = getPredictedPreferredPoint(entity);
		Vec3 futureEyes = RotationUtils.getEyesPos()
			.add(MC.player.getDeltaMovement().scale(selfPrediction.getValue()));
		double visibleRange = scanning ? getMaximumScanRange()
			: getInteractionRange();
		Rotation reference = currentServerRotation();
		AimPoint point = CombatAimPointPlanner.find(predictedBox, futureEyes,
			preferred, visibleRange, getWallRange(),
			candidate -> BlockUtils.hasLineOfSight(RotationUtils.getEyesPos(),
				candidate),
			candidate -> reference.getAngleTo(
				RotationUtils.getNeededRotations(candidate)));
		return point == null ? null : new TargetPlan(entity, point);
	}

	private boolean isValidScanTarget(Entity entity)
	{
		if(!CombatTargetUtils.isValid(entity, getMaximumScanRange(),
			fov.getValue() * 2, this::getPredictedPreferredPoint, entityFilters,
			false))
			return false;
		if(entity instanceof LivingEntity living
			&& living.hurtTime > hurtTime.getValueI())
			return false;
		return ignoreShield.isChecked() || !targetWouldBlock(entity)
			|| MC.player.getMainHandItem().getItem() instanceof AxeItem;
	}

	private boolean isValidAttackTarget(Entity entity)
	{
		return createPlan(entity, false) != null;
	}

	private boolean targetWouldBlock(Entity entity)
	{
		return entity instanceof Player player && player.isUsingItem()
			&& player.getUseItem().getUseAnimation() == ItemUseAnimation.BLOCK;
	}

	private double getInteractionRange()
	{
		return 3 + rangeIncrease.getValue();
	}

	private double getWallRange()
	{
		return checkLOS.isChecked()
			? Math.min(getInteractionRange(), throughWallsRange.getValue())
			: getInteractionRange();
	}

	private double getMaximumScanRange()
	{
		return Math.max(getInteractionRange(), getWallRange())
			+ currentScanRangeAddition;
	}

	private Vec3 getPredictedPreferredPoint(Entity entity)
	{
		return aimAt.getAimPoint(entity)
			.add(entity.getDeltaMovement().scale(targetPrediction.getValue()));
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
		float maxChange = getMaximumRotationChange();
		int rotationTicks = Math.max(1,
			(int)Math.ceil(start.getAngleTo(needed) / maxChange));
		RotationTiming timing = rotationTiming.getSelected();
		if(timing == RotationTiming.SNAP && !clickReady
			&& !clickScheduler.willClickAt(this::isItemCooldownPassed,
				System.currentTimeMillis(), rotationTicks))
			return;
		if(timing == RotationTiming.ON_TICK && rotationTicks <= 1)
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

	private float getMaximumRotationChange()
	{
		return 180 - rotationSmooth.getValueF() * 175;
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
		RaycastMode mode = raycastMode.getSelected();
		if(mode == RaycastMode.NONE)
			return selected;
		Vec3 start = RotationUtils.getEyesPos();
		Vec3 look = rotation.toLookVec();
		Vec3 end = start.add(look.scale(getInteractionRange()));
		AABB searchBox = MC.player.getBoundingBox()
			.expandTowards(look.scale(getInteractionRange())).inflate(1);
		EntityHitResult hit = ProjectileUtil.getEntityHitResult(MC.player, start,
			end, searchBox, entity -> !entity.isSpectator() && entity.isPickable()
				&& (mode == RaycastMode.ALL || isValidScanTarget(entity)),
			getInteractionRange() * getInteractionRange());
		return hit == null ? selected : hit.getEntity();
	}

	private boolean isLookingAt(Entity entity, Rotation rotation)
	{
		Vec3 start = RotationUtils.getEyesPos();
		Vec3 end = start.add(rotation.toLookVec().scale(getInteractionRange()));
		Vec3 hit = entity.getBoundingBox().inflate(entity.getPickRadius())
			.clip(start, end).orElse(null);
		if(hit == null)
			return false;
		double distanceSq = start.distanceToSqr(hit);
		if(distanceSq <= getWallRange() * getWallRange())
			return true;
		if(distanceSq > getInteractionRange() * getInteractionRange())
			return false;
		BlockHitResult blockHit = BlockUtils.raycast(start, hit);
		return blockHit.getType() == HitResult.Type.MISS;
	}

	private boolean canScheduleFailSwing()
	{
		if(!failSwing.isChecked() || !isClickTick() || waitTicks > 0
			|| MC.hitResult == null || MC.hitResult.getType() != HitResult.Type.MISS)
			return false;
		double range = getInteractionRange() + currentFailSwingRange;
		return CombatTargetUtils.get(range, fov.getValue() * 2,
			this::getPredictedPreferredPoint, entityFilters, false,
			priority.getSelected()) != null;
	}

	private boolean startBlocking()
	{
		if(!autoBlock.isChecked()
			|| random.nextInt(100) > blockChance.getValueI())
			return false;
		if(onlyBlockInDanger.isChecked() && !isInDanger)
		{
			stopBlocking(false);
			return false;
		}
		if(MC.player.isUsingItem())
		{
			hasBlockedSinceAttack = true;
			return false;
		}

		InteractionHand hand = findBlockableHand();
		if(hand == null)
			return false;
		if(blockMode.getSelected() == BlockMode.FAKE)
		{
			blockVisual = true;
			return false;
		}

		boolean blocked = false;
		if(blockMode.getSelected() == BlockMode.INTERACT)
			blocked = interactWithFacing(hand);
		if(!blocked)
		{
			InteractionResult result = MC.gameMode.useItem(MC.player, hand);
			blocked = result.consumesAction();
		}
		if(!blocked)
			return false;

		enforcedBlockingHand = hand;
		blockVisual = true;
		hasBlockedSinceAttack = true;
		reblockTicks = randomTicks(reblockTicksMin, reblockTicksMax);
		return true;
	}

	private boolean interactWithFacing(InteractionHand hand)
	{
		if(MC.hitResult instanceof EntityHitResult entityHit)
		{
			InteractionResult result = MC.gameMode.interact(MC.player,
				entityHit.getEntity(), hand);
			if(result.consumesAction())
				return true;
		}
		if(MC.hitResult instanceof BlockHitResult blockHit)
		{
			InteractionResult result = MC.gameMode.useItemOn(MC.player, hand,
				blockHit);
			if(result.consumesAction())
				return true;
		}
		return !simulateVanillaUse.isChecked()
			&& MC.gameMode.useItem(MC.player, hand).consumesAction();
	}

	private boolean stopBlocking(boolean pauses)
	{
		if(!pauses)
			blockVisual = false;
		if(enforcedBlockingHand == null && (!pauses || !autoBlock.isChecked()))
			return false;
		if(!pauses && MC.options.keyUse.isDown())
			return false;
		if(!isBlocking())
			return false;
		pauseOnUnblockTicks = randomTicks(pauseOnUnblockMin, pauseOnUnblockMax);
		boolean stopped = switch(unblockMode.getSelected())
		{
			case STOP_USING_ITEM -> {
				MC.gameMode.releaseUsingItem(MC.player);
				yield true;
			}
			case CHANGE_SLOT -> {
				int current = MC.player.getInventory().getSelectedSlot();
				MC.player.connection.send(
					new ServerboundSetCarriedItemPacket((current + 1) % 9));
				MC.player.connection.send(new ServerboundSetCarriedItemPacket(current));
				yield enforcedBlockingHand == InteractionHand.MAIN_HAND;
			}
			case SWAP_HAND -> {
				MC.player.connection.send(new ServerboundPlayerActionPacket(
					ServerboundPlayerActionPacket.Action.SWAP_ITEM_WITH_OFFHAND,
					BlockPos.ZERO, Direction.DOWN));
				MC.player.connection.send(new ServerboundPlayerActionPacket(
					ServerboundPlayerActionPacket.Action.SWAP_ITEM_WITH_OFFHAND,
					BlockPos.ZERO, Direction.DOWN));
				yield true;
			}
			case NONE -> {
				if(pauses)
					yield false;
				MC.gameMode.releaseUsingItem(MC.player);
				yield true;
			}
		};
		if(stopped)
			enforcedBlockingHand = null;
		return stopped;
	}

	private boolean isBlocking()
	{
		return MC.player != null && MC.player.isUsingItem()
			&& MC.player.getUseItem().getUseAnimation() == ItemUseAnimation.BLOCK;
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

	public boolean shouldRenderFakeBlock()
	{
		return isEnabled() && autoBlock.isChecked() && blockVisual
			&& blockMode.getSelected() == BlockMode.FAKE
			&& findBlockableHand() != null;
	}

	public InteractionHand getFakeBlockingHand()
	{
		return shouldRenderFakeBlock() ? findBlockableHand() : null;
	}

	private boolean isPrioritizingBlocking()
	{
		if(MC.player.isUsingItem())
			hasBlockedSinceAttack = true;
		return autoBlock.isChecked() && prioritizeBlocking.isChecked()
			&& blockMode.getSelected() != BlockMode.FAKE
			&& findBlockableHand() != null && !hasBlockedSinceAttack
			&& (!onlyBlockInDanger.isChecked() || isInDanger);
	}

	private void updateDangerState()
	{
		isInDanger = false;
		if(!autoBlock.isChecked() || !onlyBlockInDanger.isChecked())
			return;
		for(Entity entity : CombatTargetUtils.getList(getMaximumScanRange(), 360,
			this::getPredictedPreferredPoint, entityFilters, false,
			Priority.DISTANCE, Integer.MAX_VALUE))
			if(entity instanceof LivingEntity
				&& CombatTargetUtils.distanceToBoxSqr(entity)
					<= getInteractionRange() * getInteractionRange()
				&& entity.getLookAngle().dot(MC.player.getEyePosition()
					.subtract(entity.getEyePosition()).normalize()) > 0.95)
			{
				isInDanger = true;
				return;
			}
	}

	private boolean shouldBlockSprinting()
	{
		return criticalsSelection.getSelected() != CriticalsSelectionMode.IGNORE
			&& !MC.player.onGround()
			&& clickScheduler.willClickAt(this::isItemCooldownPassed,
				System.currentTimeMillis(), 1);
	}

	private int getTargetTypeWeight(Entity entity)
	{
		if(entity instanceof Player)
			return 0;
		if(entity.getType().getCategory().isFriendly())
			return 2;
		return 1;
	}

	private boolean isWeapon(ItemStack stack)
	{
		return stack.is(net.minecraft.tags.ItemTags.SWORDS)
			|| stack.is(net.minecraft.tags.ItemTags.AXES)
			|| stack.is(net.minecraft.tags.ItemTags.TRIDENT_ENCHANTABLE)
			|| EnchantmentUtils.getLevel(Enchantments.KNOCKBACK, stack) > 0;
	}

	private int randomTicks(SliderSetting first, SliderSetting second)
	{
		int minimum = Math.min(first.getValueI(), second.getValueI());
		int maximum = Math.max(first.getValueI(), second.getValueI());
		return minimum == maximum ? minimum : random.nextInt(minimum, maximum + 1);
	}

	private double randomRange(SliderSetting first, SliderSetting second)
	{
		double minimum = Math.min(first.getValue(), second.getValue());
		double maximum = Math.max(first.getValue(), second.getValue());
		return minimum == maximum ? minimum
			: minimum + random.nextDouble() * (maximum - minimum);
	}

	private void restoreSprint()
	{
		MC.player.setSprinting(true);
		MC.player.connection.send(new ServerboundPlayerCommandPacket(MC.player,
			Action.START_SPRINTING));
	}

	private void sendFullRotation(Rotation rotation)
	{
		if(rotation == null)
			return;
		MC.player.connection.send(new PosRot(MC.player.getX(), MC.player.getY(),
			MC.player.getZ(), rotation.yaw(), rotation.pitch(),
			MC.player.onGround(), MC.player.horizontalCollision));
	}

	private void clearTracking()
	{
		targetPlan = null;
		pendingAttack = null;
		pendingFailSwing = false;
		renderTarget = null;
		plannedRotation = null;
		hasBlockedSinceAttack = false;
		blockVisual = false;
		isInDanger = false;
		waitTicks = 0;
		if(rotationQueue != null)
			rotationQueue.clear();
	}

	public Entity getCurrentTarget()
	{
		return targetPlan == null ? null : targetPlan.entity();
	}

	@Override
	public void onRender(PoseStack matrixStack, float partialTicks)
	{
		if(MC.player != null && rangeAura.isChecked())
			AuraRangeRenderer.render(matrixStack, MC.player, partialTicks,
				getInteractionRange(), WURST.getGui().getTheme().accent(1),
				renderTarget != null);
		if(renderTarget == null || !damageIndicator.isChecked())
			return;
		float progress = 1;
		if(renderTarget instanceof LivingEntity living
			&& living.getMaxHealth() > 1e-5)
			progress = 1 - living.getHealth() / living.getMaxHealth();
		float[] rgb = {progress * 2, 2 - progress * 2, 0};
		int quadColor = RenderUtils.toIntColor(rgb, 0.25F);
		int lineColor = RenderUtils.toIntColor(rgb, 0.5F);
		AABB box = EntityUtils.getLerpedBox(renderTarget, partialTicks);
		if(progress < 1)
		{
			double factor = progress < 0.01 ? 0.495 : (1 - progress) * 0.5;
			box = box.deflate(factor * box.getXsize(), factor * box.getYsize(),
				factor * box.getZsize());
		}
		RenderUtils.drawSolidBox(matrixStack, box, quadColor, false);
		RenderUtils.drawOutlinedBox(matrixStack, box, lineColor, false);
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

	private enum CriticalsSelectionMode
	{
		SMART("Smart"), IGNORE("Ignore"), ALWAYS("Always");
		private final String name;
		CriticalsSelectionMode(String name)
		{
			this.name = name;
		}
		private boolean allowsAttack(Entity target)
		{
			if(this != ALWAYS || target == null)
				return true;
			return !MC.player.onGround() && MC.player.fallDistance > 0
				&& !MC.player.onClimbable() && !MC.player.isInWater()
				&& !MC.player.isInLava() && !MC.player.isPassenger()
				&& !MC.player.isSprinting()
				&& !MC.player.hasEffect(MobEffects.BLINDNESS);
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

	private enum BlockMode
	{
		BASIC("Basic"), INTERACT("Interact"), FAKE("Fake");
		private final String name;
		BlockMode(String name)
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
		STOP_USING_ITEM("Stop using item"), CHANGE_SLOT("Change slot"),
		SWAP_HAND("Swap hand"), NONE("None");
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
}

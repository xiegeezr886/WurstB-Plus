/*
 * Copyright (c) 2014-2025 Wurst-Imperium and contributors.
 * Copyright (c) 2015-2026 CCBlueX
 *
 * This source code is subject to the terms of the GNU General Public
 * License, version 3. If a copy of the GPL was not distributed with this
 * file, You can obtain one at: https://www.gnu.org/licenses/gpl-3.0.txt
 */
package net.wurstclient.hacks;

import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import net.wurstclient.Category;
import net.wurstclient.events.MouseUpdateListener;
import net.wurstclient.events.UpdateListener;
import net.wurstclient.hack.Hack;
import net.wurstclient.hack.HackConflictGroup;
import net.wurstclient.settings.AimAtSetting;
import net.wurstclient.settings.CheckboxSetting;
import net.wurstclient.settings.EnumSetting;
import net.wurstclient.settings.SliderSetting;
import net.wurstclient.settings.SliderSetting.ValueDisplay;
import net.wurstclient.settings.filterlists.EntityFilterList;
import net.wurstclient.settings.filters.*;
import net.wurstclient.util.BlockUtils;
import net.wurstclient.util.CombatTargetUtils;
import net.wurstclient.util.CombatTargetUtils.Priority;
import net.wurstclient.util.Rotation;
import net.wurstclient.util.RotationSmoothing;
import net.wurstclient.util.RotationUtils;
import net.wurstclient.util.TargetTracker;

public final class AimAssistHack extends Hack
	implements UpdateListener, MouseUpdateListener
{
	private final SliderSetting range =
		new SliderSetting("Range", 4.5, 1, 6, 0.05, ValueDisplay.DECIMAL);
	
	private final SliderSetting rotationSpeed =
		new SliderSetting("Rotation Speed", 600, 10, 3600, 10,
			ValueDisplay.DEGREES.withSuffix("/s"));
	
	private final SliderSetting fov =
		new SliderSetting("FOV", "description.wurst.setting.aimassist.fov", 120,
			30, 360, 10, ValueDisplay.DEGREES);

	private final EnumSetting<Priority> priority = new EnumSetting<>("Priority",
		Priority.values(), Priority.ANGLE);

	private final EnumSetting<RotationSmoothing> smoothing = new EnumSetting<>(
		"Angle smoothing", RotationSmoothing.values(),
		RotationSmoothing.EASE_IN_OUT);

	private final CheckboxSetting horizontalAxis = new CheckboxSetting(
		"Horizontal axis", "Allows AimAssist to adjust yaw.", true);

	private final CheckboxSetting verticalAxis = new CheckboxSetting(
		"Vertical axis", "Allows AimAssist to adjust pitch.", true);

	private final CheckboxSetting stickyTarget = new CheckboxSetting(
		"Sticky target", "Keeps a valid target instead of switching every tick.",
		true);

	private final SliderSetting switchDelay = new SliderSetting("Switch delay",
		"Ticks to wait before switching targets.", 3, 0, 20, 1,
		ValueDisplay.INTEGER.withSuffix(" ticks"));

	private final SliderSetting switchAdvantage = new SliderSetting(
		"Switch advantage",
		"How much better another target must score before switching.", 10, 0,
		100, 1, ValueDisplay.PERCENTAGE);
	
	private final AimAtSetting aimAt = new AimAtSetting(
		"What point in the target's hitbox AimAssist should aim at.");
	
	private final SliderSetting ignoreMouseInput =
		new SliderSetting("Ignore mouse input",
			"description.wurst.setting.aimassist.ignore_mouse_input", 0, 0, 1,
			0.01, ValueDisplay.PERCENTAGE);
	
	private final CheckboxSetting checkLOS =
		new CheckboxSetting("Check line of sight",
			"description.wurst.setting.aimassist.check_line_of_sight", true);
	
	private final CheckboxSetting aimWhileBlocking =
		new CheckboxSetting("Aim while blocking",
			"description.wurst.setting.aimassist.aim_while_blocking", false);
	
	private final EntityFilterList entityFilters =
		new EntityFilterList(FilterPlayersSetting.genericCombat(false),
			FilterSleepingSetting.genericCombat(false),
			FilterFlyingSetting.genericCombat(0),
			FilterHostileSetting.genericCombat(false),
			FilterNeutralSetting
				.genericCombat(AttackDetectingEntityFilter.Mode.OFF),
			FilterPassiveSetting.genericCombat(true),
			FilterPassiveWaterSetting.genericCombat(true),
			FilterBabiesSetting.genericCombat(true),
			FilterBatsSetting.genericCombat(true),
			FilterSlimesSetting.genericCombat(true),
			FilterPetsSetting.genericCombat(true),
			FilterVillagersSetting.genericCombat(true),
			FilterZombieVillagersSetting.genericCombat(true),
			FilterGolemsSetting.genericCombat(false),
			FilterPiglinsSetting
				.genericCombat(AttackDetectingEntityFilter.Mode.OFF),
			FilterZombiePiglinsSetting
				.genericCombat(AttackDetectingEntityFilter.Mode.OFF),
			FilterEndermenSetting
				.genericCombat(AttackDetectingEntityFilter.Mode.OFF),
			FilterShulkersSetting.genericCombat(false),
			FilterInvisibleSetting.genericCombat(true),
			FilterNamedSetting.genericCombat(false),
			FilterShulkerBulletSetting.genericCombat(false),
			FilterArmorStandsSetting.genericCombat(true),
			FilterCrystalsSetting.genericCombat(true));
	
	private Entity target;
	private float nextYaw;
	private float nextPitch;
	private final TargetTracker<Entity> targetTracker = new TargetTracker<>();
	
	public AimAssistHack()
	{
		super("AimAssist");
		setCategory(Category.COMBAT);
		addConflictGroup(HackConflictGroup.COMBAT_TARGETING);
		
		addSetting(range);
		addSetting(rotationSpeed);
		addSetting(fov);
		addSetting(priority);
		addSetting(smoothing);
		addSetting(horizontalAxis);
		addSetting(verticalAxis);
		addSetting(stickyTarget);
		addSetting(switchDelay);
		addSetting(switchAdvantage);
		addSetting(aimAt);
		addSetting(ignoreMouseInput);
		addSetting(checkLOS);
		addSetting(aimWhileBlocking);
		
		entityFilters.forEach(this::addSetting);
	}
	
	@Override
	protected void onEnable()
	{
		// disable incompatible hacks
		WURST.getHax().autoFishHack.setEnabled(false);
		
		EVENTS.add(UpdateListener.class, this);
		EVENTS.add(MouseUpdateListener.class, this);
	}
	
	@Override
	protected void onDisable()
	{
		EVENTS.remove(UpdateListener.class, this);
		EVENTS.remove(MouseUpdateListener.class, this);
		target = null;
		targetTracker.reset();
	}
	
	@Override
	public void onUpdate()
	{
		// don't aim when a container/inventory screen is open
		if(MC.screen instanceof AbstractContainerScreen)
		{
			target = null;
			targetTracker.reset();
			return;
		}
		
		if(!aimWhileBlocking.isChecked() && MC.player.isUsingItem())
		{
			target = null;
			return;
		}
		
		targetTracker.tick();
		chooseTarget();
		if(target == null)
			return;
		
		Vec3 hitVec = aimAt.getAimPoint(target);
		if(checkLOS.isChecked() && !BlockUtils.hasLineOfSight(hitVec))
		{
			target = null;
			return;
		}
		
		WURST.getHax().autoSwordHack.setSlot(target);
		
		// get needed rotation
		Rotation needed = RotationUtils.getNeededRotations(hitVec);
		
		// turn towards center of boundingBox
		Rotation current =
			new Rotation(MC.player.getYRot(), MC.player.getXRot());
		Rotation next = RotationSmoothing.smooth(current, needed,
			rotationSpeed.getValueI() / 20F, smoothing.getSelected());
		nextYaw = next.yaw();
		nextPitch = next.pitch();
	}
	
	private void chooseTarget()
	{
		Entity candidate = CombatTargetUtils.get(range.getValue(), fov.getValue(),
			aimAt::getAimPoint, entityFilters, checkLOS.isChecked(),
			priority.getSelected());
		target = targetTracker.update(candidate, this::isValidTarget,
			entity -> CombatTargetUtils.getScore(entity, priority.getSelected(),
				aimAt::getAimPoint), stickyTarget.isChecked(),
			switchDelay.getValueI(), switchAdvantage.getValue());
	}

	private boolean isValidTarget(Entity entity)
	{
		return CombatTargetUtils.isValid(entity, range.getValue(), fov.getValue(),
			aimAt::getAimPoint, entityFilters, checkLOS.isChecked());
	}
	
	@Override
	public void onMouseUpdate(MouseUpdateEvent event)
	{
		if(target == null || MC.player == null)
			return;
		
		float curYaw = MC.player.getYRot();
		float curPitch = MC.player.getXRot();
		int diffYaw = (int)(nextYaw - curYaw);
		int diffPitch = (int)(nextPitch - curPitch);
		
		// If we are <1 degree off but still missing the hitbox,
		// slightly exaggerate the difference to fix that.
		if(diffYaw == 0 && diffPitch == 0 && !RotationUtils
			.isFacingBox(target.getBoundingBox(), range.getValue()))
		{
			diffYaw = nextYaw < curYaw ? -1 : 1;
			diffPitch = nextPitch < curPitch ? -1 : 1;
		}
		
		double inputFactor = 1 - ignoreMouseInput.getValue();
		int mouseInputX = (int)(event.getDefaultDeltaX() * inputFactor);
		int mouseInputY = (int)(event.getDefaultDeltaY() * inputFactor);
		
		event.setDeltaX(mouseInputX + (horizontalAxis.isChecked() ? diffYaw : 0));
		event.setDeltaY(mouseInputY + (verticalAxis.isChecked() ? diffPitch : 0));
	}
}

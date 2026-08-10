/*
 * Copyright (c) 2014-2025 Wurst-Imperium and contributors.
 * Copyright (c) 2015-2026 CCBlueX
 *
 * This source code is subject to the terms of the GNU General Public
 * License, version 3. If a copy of the GPL was not distributed with this
 * file, You can obtain one at: https://www.gnu.org/licenses/gpl-3.0.txt
 */
package net.wurstclient.hacks;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.AABB;
import net.wurstclient.Category;
import net.wurstclient.events.HandleInputListener;
import net.wurstclient.events.MouseUpdateListener;
import net.wurstclient.events.RenderListener;
import net.wurstclient.events.UpdateListener;
import net.wurstclient.hack.Hack;
import net.wurstclient.hack.HackConflictGroup;
import net.wurstclient.settings.AttackSpeedSliderSetting;
import net.wurstclient.settings.CheckboxSetting;
import net.wurstclient.settings.EnumSetting;
import net.wurstclient.settings.SliderSetting;
import net.wurstclient.settings.SliderSetting.ValueDisplay;
import net.wurstclient.settings.SwingHandSetting;
import net.wurstclient.settings.SwingHandSetting.SwingHand;
import net.wurstclient.settings.filterlists.EntityFilterList;
import net.wurstclient.settings.filters.*;
import net.wurstclient.util.CombatTargetSession;
import net.wurstclient.util.CombatTargetUtils;
import net.wurstclient.util.CombatTargetUtils.Priority;
import net.wurstclient.util.EntityUtils;
import net.wurstclient.util.RenderUtils;
import net.wurstclient.util.Rotation;
import net.wurstclient.util.RotationSmoothing;
import net.wurstclient.util.RotationUtils;

public final class KillauraLegitHack extends Hack implements UpdateListener,
	HandleInputListener, MouseUpdateListener, RenderListener
{
	private final SliderSetting range =
		new SliderSetting("Range", 4.25, 1, 4.25, 0.05, ValueDisplay.DECIMAL);
	
	private final AttackSpeedSliderSetting speed =
		new AttackSpeedSliderSetting();
	
	private final SliderSetting speedRandMS =
		new SliderSetting("Speed randomization",
			"Helps you bypass anti-cheat plugins by varying the delay between"
				+ " attacks.\n\n" + "\u00b1100ms is recommended for Vulcan.\n\n"
				+ "0 (off) is fine for NoCheat+, AAC, Grim, Verus, Spartan, and"
				+ " vanilla servers.",
			100, 0, 1000, 50, ValueDisplay.INTEGER.withPrefix("\u00b1")
				.withSuffix("ms").withLabel(0, "off"));
	
	private final SliderSetting rotationSpeed =
		new SliderSetting("Rotation Speed", 600, 10, 3600, 10,
			ValueDisplay.DEGREES.withSuffix("/s"));
	
	private final EnumSetting<Priority> priority = new EnumSetting<>("Priority",
		"Determines which entity will be attacked first.\n"
			+ "\u00a7lDistance\u00a7r - Attacks the closest entity.\n"
			+ "\u00a7lAngle\u00a7r - Attacks the entity that requires the least head movement.\n"
			+ "\u00a7lHealth\u00a7r - Attacks the weakest entity.",
		Priority.values(), Priority.ANGLE);
	
	private final SliderSetting fov = new SliderSetting("FOV",
		"Field Of View - how far away from your crosshair an entity can be before it's ignored.\n"
			+ "360\u00b0 = entities can be attacked all around you.",
		360, 30, 360, 10, ValueDisplay.DEGREES);

	private final CheckboxSetting stickyTarget = new CheckboxSetting(
		"Sticky target", "Keeps a valid target instead of switching every tick.",
		true);

	private final SliderSetting switchDelay = new SliderSetting("Switch delay",
		"Ticks to wait before switching to another valid target.", 4, 0, 20,
		1, ValueDisplay.INTEGER.withSuffix(" ticks"));

	private final SliderSetting switchAdvantage = new SliderSetting(
		"Switch advantage",
		"How much better another target must score before switching.", 10, 0,
		100, 1, ValueDisplay.PERCENTAGE);

	private final CheckboxSetting checkLOS = new CheckboxSetting(
		"Check line of sight", "Requires a visible point inside the target.",
		true);

	private final CheckboxSetting attackWhileUsing = new CheckboxSetting(
		"Attack while using items", "Allows attacks while using an item.",
		false);

	private final CheckboxSetting pauseWhileMining = new CheckboxSetting(
		"Pause while mining", "Does not attack while breaking a block.", true);
	
	private final SwingHandSetting swingHand =
		SwingHandSetting.withoutOffOption(
			SwingHandSetting.genericCombatDescription(this), SwingHand.CLIENT);
	
	private final CheckboxSetting damageIndicator = new CheckboxSetting(
		"Damage indicator",
		"Renders a colored box within the target, inversely proportional to its remaining health.",
		true);
	
	// same filters as in Killaura, but with stricter defaults
	private final EntityFilterList entityFilters =
		new EntityFilterList(FilterPlayersSetting.genericCombat(false),
			FilterSleepingSetting.genericCombat(true),
			FilterFlyingSetting.genericCombat(0.5),
			FilterHostileSetting.genericCombat(false),
			FilterNeutralSetting
				.genericCombat(AttackDetectingEntityFilter.Mode.OFF),
			FilterPassiveSetting.genericCombat(false),
			FilterPassiveWaterSetting.genericCombat(false),
			FilterBabiesSetting.genericCombat(false),
			FilterBatsSetting.genericCombat(false),
			FilterSlimesSetting.genericCombat(false),
			FilterPetsSetting.genericCombat(false),
			FilterVillagersSetting.genericCombat(false),
			FilterZombieVillagersSetting.genericCombat(false),
			FilterGolemsSetting.genericCombat(false),
			FilterPiglinsSetting
				.genericCombat(AttackDetectingEntityFilter.Mode.OFF),
			FilterZombiePiglinsSetting
				.genericCombat(AttackDetectingEntityFilter.Mode.OFF),
			FilterEndermenSetting
				.genericCombat(AttackDetectingEntityFilter.Mode.OFF),
			FilterShulkersSetting.genericCombat(false),
			FilterAllaysSetting.genericCombat(false),
			FilterInvisibleSetting.genericCombat(true),
			FilterNamedSetting.genericCombat(false),
			FilterShulkerBulletSetting.genericCombat(false),
			FilterArmorStandsSetting.genericCombat(false),
			FilterCrystalsSetting.genericCombat(false));
	
	private float nextYaw;
	private float nextPitch;
	private Rotation rotationDelta = new Rotation(0, 0);
	private final CombatTargetSession<Entity> targetSession =
		new CombatTargetSession<>();
	
	public KillauraLegitHack()
	{
		super("KillauraLegit");
		setCategory(Category.COMBAT);
		addConflictGroup(HackConflictGroup.COMBAT_TARGETING);
		
		addSetting(range);
		addSetting(speed);
		addSetting(speedRandMS);
		addSetting(rotationSpeed);
		addSetting(priority);
		addSetting(fov);
		addSetting(stickyTarget);
		addSetting(switchDelay);
		addSetting(switchAdvantage);
		addSetting(checkLOS);
		addSetting(attackWhileUsing);
		addSetting(pauseWhileMining);
		addSetting(swingHand);
		addSetting(damageIndicator);
		
		entityFilters.forEach(this::addSetting);
	}
	
	@Override
	protected void onEnable()
	{
		speed.resetTimer(speedRandMS.getValue());
		EVENTS.add(UpdateListener.class, this);
		EVENTS.add(HandleInputListener.class, this);
		EVENTS.add(MouseUpdateListener.class, this);
		EVENTS.add(RenderListener.class, this);
	}
	
	@Override
	protected void onDisable()
	{
		EVENTS.remove(UpdateListener.class, this);
		EVENTS.remove(HandleInputListener.class, this);
		EVENTS.remove(MouseUpdateListener.class, this);
		EVENTS.remove(RenderListener.class, this);
		clearTarget();
	}
	
	@Override
	public void onUpdate()
	{
		if(MC.player == null || MC.gameMode == null)
		{
			clearTarget();
			return;
		}

		// don't attack when a container/inventory screen is open
		if(MC.screen instanceof AbstractContainerScreen)
		{
			clearTarget();
			return;
		}
		if(!attackWhileUsing.isChecked() && MC.player.isUsingItem())
		{
			clearTarget();
			return;
		}
		if(pauseWhileMining.isChecked() && MC.gameMode.isDestroying())
		{
			clearTarget();
			return;
		}

		targetSession.tick();
		Entity candidate = CombatTargetUtils.get(range.getValue(), fov.getValue(),
			entity -> entity.getBoundingBox().getCenter(), entityFilters,
			checkLOS.isChecked(), priority.getSelected());
		CombatTargetSession.Selection<Entity> selection = targetSession.update(
			candidate, this::isValidTarget,
			entity -> CombatTargetUtils.getScore(entity, priority.getSelected(),
				value -> value.getBoundingBox().getCenter()),
			stickyTarget.isChecked(), switchDelay.getValueI(),
			switchAdvantage.getValue());
		Entity target = selection.current();
		if(target == null)
		{
			rotationDelta = new Rotation(0, 0);
			return;
		}
		if(selection.changed())
		{
			rotationDelta = new Rotation(0, 0);
			speed.resetTimer(speedRandMS.getValue());
		}
		
		// face entity
		WURST.getHax().autoSwordHack.setSlot(target);
		faceEntityClient(target);
	}

	private boolean isValidTarget(Entity entity)
	{
		return CombatTargetUtils.isValid(entity, range.getValue(), fov.getValue(),
			value -> value.getBoundingBox().getCenter(), entityFilters,
			checkLOS.isChecked());
	}
	
	@Override
	public void onHandleInput()
	{
		Entity target = targetSession.getTarget();
		if(target == null || MC.player == null || MC.gameMode == null
			|| MC.screen instanceof AbstractContainerScreen
			|| !attackWhileUsing.isChecked() && MC.player.isUsingItem()
			|| pauseWhileMining.isChecked() && MC.gameMode.isDestroying()
			|| !isValidTarget(target))
			return;
		
		speed.updateTimer();
		if(!speed.isTimeToAttack())
			return;
		
		if(!RotationUtils.isFacingBox(target.getBoundingBox(),
			range.getValue()))
			return;
		
		// attack entity
		MC.gameMode.attack(MC.player, target);
		swingHand.swing(InteractionHand.MAIN_HAND);
		speed.resetTimer(speedRandMS.getValue());
	}
	
	private boolean faceEntityClient(Entity entity)
	{
		// get needed rotation
		AABB box = entity.getBoundingBox();
		Rotation needed = RotationUtils.getNeededRotations(box.getCenter());
		
		// turn towards center of boundingBox
		Rotation current =
			new Rotation(MC.player.getYRot(), MC.player.getXRot());
		float maxChange = rotationSpeed.getValueI() / 20F;
		RotationSmoothing.Step step = RotationSmoothing.smoothWithAcceleration(
			current, needed, rotationDelta, maxChange,
			Math.max(1, maxChange * 0.35F), RotationSmoothing.LINEAR);
		rotationDelta = step.delta();
		nextYaw = step.rotation().yaw();
		nextPitch = step.rotation().pitch();
		
		// check if facing center
		if(RotationUtils.isAlreadyFacing(needed))
			return true;
		
		// if not facing center, check if facing anything in boundingBox
		return RotationUtils.isFacingBox(box, range.getValue());
	}

	private void clearTarget()
	{
		rotationDelta = new Rotation(0, 0);
		targetSession.clear();
	}
	
	@Override
	public void onMouseUpdate(MouseUpdateEvent event)
	{
		Entity target = targetSession.getTarget();
		if(target == null || MC.player == null)
			return;
		
		int diffYaw = (int)(nextYaw - MC.player.getYRot());
		int diffPitch = (int)(nextPitch - MC.player.getXRot());
		if(Mth.abs(diffYaw) < 1 && Mth.abs(diffPitch) < 1)
			return;
		
		event.setDeltaX(event.getDefaultDeltaX() + diffYaw);
		event.setDeltaY(event.getDefaultDeltaY() + diffPitch);
	}
	
	@Override
	public void onRender(PoseStack matrixStack, float partialTicks)
	{
		Entity target = targetSession.getTarget();
		if(target == null || !damageIndicator.isChecked())
			return;
		
		float p = 1;
		if(target instanceof LivingEntity le)
			p = (le.getMaxHealth() - le.getHealth()) / le.getMaxHealth();
		float red = p * 2F;
		float green = 2 - red;
		float[] rgb = {red, green, 0};
		int quadColor = RenderUtils.toIntColor(rgb, 0.25F);
		int lineColor = RenderUtils.toIntColor(rgb, 0.5F);
		
		AABB box = EntityUtils.getLerpedBox(target, partialTicks);
		if(p < 1)
			box = box.deflate((1 - p) * 0.5 * box.getXsize(),
				(1 - p) * 0.5 * box.getYsize(),
				(1 - p) * 0.5 * box.getZsize());
		
		RenderUtils.drawSolidBox(matrixStack, box, quadColor, false);
		RenderUtils.drawOutlinedBox(matrixStack, box, lineColor, false);
	}
	
}

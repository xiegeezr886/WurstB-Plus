/*
 * Copyright (c) 2014-2025 Wurst-Imperium and contributors.
 *
 * This source code is subject to the terms of the GNU General Public
 * License, version 3. If a copy of the GPL was not distributed with this
 * file, You can obtain one at: https://www.gnu.org/licenses/gpl-3.0.txt
 */
package net.wurstclient.hacks;

import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.wurstclient.Category;
import net.wurstclient.SearchTags;
import net.wurstclient.events.LeftClickListener;
import net.wurstclient.events.UpdateListener;
import net.wurstclient.hack.Hack;
import net.wurstclient.hack.HackConflictGroup;
import net.wurstclient.settings.AimAtSetting;
import net.wurstclient.settings.AttackSpeedSliderSetting;
import net.wurstclient.settings.CheckboxSetting;
import net.wurstclient.settings.EnumSetting;
import net.wurstclient.settings.PauseAttackOnContainersSetting;
import net.wurstclient.settings.SliderSetting;
import net.wurstclient.settings.SliderSetting.ValueDisplay;
import net.wurstclient.settings.SwingHandSetting;
import net.wurstclient.settings.SwingHandSetting.SwingHand;
import net.wurstclient.settings.filterlists.EntityFilterList;
import net.wurstclient.util.CombatTargetUtils;
import net.wurstclient.util.CombatTargetUtils.Priority;
import net.wurstclient.util.RotationUtils;

@SearchTags({"click aura", "ClickAimbot", "click aimbot"})
public final class ClickAuraHack extends Hack
	implements UpdateListener, LeftClickListener
{
	private final SliderSetting range =
		new SliderSetting("Range", 5, 1, 12, 0.05, ValueDisplay.DECIMAL);
	
	private final AttackSpeedSliderSetting speed =
		new AttackSpeedSliderSetting();
	
	private final EnumSetting<Priority> priority = new EnumSetting<>("Priority",
		"Determines which entity will be attacked first.\n"
			+ "\u00a7lDistance\u00a7r - Attacks the closest entity.\n"
			+ "\u00a7lAngle\u00a7r - Attacks the entity that requires the least head movement.\n"
			+ "\u00a7lHealth\u00a7r - Attacks the weakest entity.",
		Priority.values(), Priority.ANGLE);
	
	private final SliderSetting fov =
		new SliderSetting("FOV", 360, 30, 360, 10, ValueDisplay.DEGREES);

	private final AimAtSetting aimAt = new AimAtSetting(
		"What point in the target's hitbox ClickAura should aim at.");

	private final CheckboxSetting checkLOS = new CheckboxSetting(
		"Check line of sight",
		"Skips targets when the selected aim point is behind a block.", false);

	private final SwingHandSetting swingHand = new SwingHandSetting(
		SwingHandSetting.genericCombatDescription(this), SwingHand.CLIENT);

	private final PauseAttackOnContainersSetting pauseOnContainers =
		new PauseAttackOnContainersSetting(true);
	
	private final EntityFilterList entityFilters =
		EntityFilterList.genericCombat();
	
	public ClickAuraHack()
	{
		super("ClickAura");
		
		setCategory(Category.COMBAT);
		addConflictGroup(HackConflictGroup.COMBAT_TARGETING);
		addSetting(range);
		addSetting(speed);
		addSetting(priority);
		addSetting(fov);
		addSetting(aimAt);
		addSetting(checkLOS);
		addSetting(swingHand);
		addSetting(pauseOnContainers);
		
		entityFilters.forEach(this::addSetting);
	}
	
	@Override
	protected void onEnable()
	{
		speed.resetTimer();
		EVENTS.add(UpdateListener.class, this);
		EVENTS.add(LeftClickListener.class, this);
	}
	
	@Override
	protected void onDisable()
	{
		EVENTS.remove(UpdateListener.class, this);
		EVENTS.remove(LeftClickListener.class, this);
	}
	
	@Override
	public void onUpdate()
	{
		if(!MC.options.keyAttack.isDown())
			return;

		attack();
	}
	
	@Override
	public void onLeftClick(LeftClickEvent event)
	{
		if(attack())
			event.cancel();
	}
	
	private boolean attack()
	{
		if(!speed.isTimeToAttack() || pauseOnContainers.shouldPause())
			return false;

		Entity target = CombatTargetUtils.get(range.getValue(), fov.getValue(),
			aimAt::getAimPoint, entityFilters, checkLOS.isChecked(),
			priority.getSelected());
		if(target == null)
			return false;
		
		WURST.getHax().autoSwordHack.setSlot(target);
		
		RotationUtils.getNeededRotations(aimAt.getAimPoint(target))
			.sendPlayerLookPacket();
		MC.gameMode.attack(MC.player, target);
		swingHand.swing(InteractionHand.MAIN_HAND);
		speed.resetTimer();
		return true;
	}
}

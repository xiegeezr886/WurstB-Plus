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
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.AxeItem;
// SwordItem removed in MC 26.1.2
import net.minecraft.world.phys.EntityHitResult;
import net.wurstclient.Category;
import net.wurstclient.SearchTags;
import net.wurstclient.events.HandleInputListener;
import net.wurstclient.events.PreMotionListener;
import net.wurstclient.hack.Hack;
import net.wurstclient.hack.HackConflictGroup;
import net.wurstclient.mixinterface.IKeyBinding;
import net.wurstclient.settings.AttackSpeedSliderSetting;
import net.wurstclient.settings.CheckboxSetting;
import net.wurstclient.settings.EnumSetting;
import net.wurstclient.settings.SliderSetting;
import net.wurstclient.settings.SliderSetting.ValueDisplay;
import net.wurstclient.settings.SwingHandSetting;
import net.wurstclient.settings.SwingHandSetting.SwingHand;
import net.wurstclient.settings.filterlists.EntityFilterList;
import net.wurstclient.util.CombatTargetUtils;

@SearchTags({"trigger bot", "AutoAttack", "auto attack", "AutoClicker",
	"auto clicker"})
public final class TriggerBotHack extends Hack
	implements PreMotionListener, HandleInputListener
{
	private final SliderSetting range =
		new SliderSetting("Range", 4.25, 1, 6, 0.05, ValueDisplay.DECIMAL);
	
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
	
	private final SwingHandSetting swingHand =
		new SwingHandSetting(this, SwingHand.CLIENT);
	
	private final EnumSetting<ItemUseMode> onItemUse = new EnumSetting<>(
		"On item use", ItemUseMode.values(), ItemUseMode.WAIT);

	private final CheckboxSetting requireAttackKey = new CheckboxSetting(
		"Require attack key", "Only runs while the attack key is held.", false);

	private final CheckboxSetting requireWeapon = new CheckboxSetting(
		"Require weapon", "Only attacks while holding a sword or axe.", true);

	private final CheckboxSetting pauseWhileMining = new CheckboxSetting(
		"Pause while mining", "Does not attack while breaking a block.", true);

	private final SliderSetting minCooldown = new SliderSetting(
		"Minimum cooldown", "Required vanilla attack strength.", 0.9, 0, 1,
		0.05, ValueDisplay.PERCENTAGE);
	
	private final CheckboxSetting simulateMouseClick = new CheckboxSetting(
		"Simulate mouse click",
		"Simulates an actual mouse click (or key press) when attacking. Can be"
			+ " used to trick CPS measuring tools into thinking that you're"
			+ " attacking manually.\n\n"
			+ "\u00a7c\u00a7lWARNING:\u00a7r Simulating mouse clicks can lead"
			+ " to unexpected behavior, like in-game menus clicking themselves."
			+ " Also, the \"Swing hand\" and \"Attack while blocking\" settings"
			+ " will not work while this option is enabled.",
		false);
	
	private final EntityFilterList entityFilters =
		EntityFilterList.genericCombat();
	
	private boolean simulatingMouseClick;
	
	public TriggerBotHack()
	{
		super("TriggerBot");
		setCategory(Category.COMBAT);
		addConflictGroup(HackConflictGroup.COMBAT_TARGETING);
		
		addSetting(range);
		addSetting(speed);
		addSetting(speedRandMS);
		addSetting(swingHand);
		addSetting(onItemUse);
		addSetting(requireAttackKey);
		addSetting(requireWeapon);
		addSetting(pauseWhileMining);
		addSetting(minCooldown);
		addSetting(simulateMouseClick);
		swingHand.visibleWhen(() -> !simulateMouseClick.isChecked());
		
		entityFilters.forEach(this::addSetting);
	}
	
	@Override
	protected void onEnable()
	{
		speed.resetTimer(speedRandMS.getValue());
		EVENTS.add(PreMotionListener.class, this);
		EVENTS.add(HandleInputListener.class, this);
	}
	
	@Override
	protected void onDisable()
	{
		if(simulatingMouseClick)
		{
			IKeyBinding.get(MC.options.keyAttack).simulatePress(false);
			simulatingMouseClick = false;
		}
		
		EVENTS.remove(PreMotionListener.class, this);
		EVENTS.remove(HandleInputListener.class, this);
	}
	
	@Override
	public void onPreMotion()
	{
		if(!simulatingMouseClick)
			return;
		
		IKeyBinding.get(MC.options.keyAttack).simulatePress(false);
		simulatingMouseClick = false;
	}
	
	@Override
	public void onHandleInput()
	{
		speed.updateTimer();
		if(!speed.isTimeToAttack())
			return;
		if(requireAttackKey.isChecked() && !MC.options.keyAttack.isDown())
			return;
		if(pauseWhileMining.isChecked() && MC.gameMode.isDestroying())
			return;
		
		// don't attack when a container/inventory screen is open
		if(MC.screen instanceof AbstractContainerScreen)
			return;
		
		LocalPlayer player = MC.player;
		if(requireWeapon.isChecked()
			&& !player.getMainHandItem().is(net.minecraft.tags.ItemTags.SWORDS)
			&& !player.getMainHandItem().is(net.minecraft.tags.ItemTags.AXES))
			return;
		if(player.getAttackStrengthScale(0.5F) < minCooldown.getValueF())
			return;
		if(player.isUsingItem())
		{
			if(onItemUse.getSelected() == ItemUseMode.WAIT)
				return;
			if(onItemUse.getSelected() == ItemUseMode.STOP)
				MC.gameMode.releaseUsingItem(player);
		}
		
		if(MC.hitResult == null
			|| !(MC.hitResult instanceof EntityHitResult eResult))
			return;
		
		Entity target = eResult.getEntity();
		if(!CombatTargetUtils.isValid(target, range.getValue(), 360,
			entity -> entity.getBoundingBox().getCenter(), entityFilters, false))
			return;
		
		WURST.getHax().autoSwordHack.setSlot(target);
		
		if(simulateMouseClick.isChecked())
		{
			IKeyBinding.get(MC.options.keyAttack).simulatePress(true);
			simulatingMouseClick = true;
			
		}else
		{
			MC.gameMode.attack(player, target);
			swingHand.swing(InteractionHand.MAIN_HAND);
		}
		
		speed.resetTimer(speedRandMS.getValue());
	}

	private enum ItemUseMode
	{
		WAIT("Wait"),
		STOP("Stop"),
		IGNORE("Ignore");

		private final String name;

		ItemUseMode(String name)
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

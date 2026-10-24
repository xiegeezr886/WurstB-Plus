/*
 * Copyright (c) 2026 WurstB+ Plus contributors
 *
 * This source code is subject to the terms of the GNU Affero General Public
 * License, version 3. If a copy of the AGPL was not distributed with this
 * file, You can obtain one at: https://www.gnu.org/licenses/agpl-3.0.txt
 */
// Ported from FO (CC0-1.0).
package net.wurstclient.hacks;

import java.util.List;
import java.util.Set;

import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.AxeItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.MaceItem;
import net.minecraft.world.item.TridentItem;
import net.minecraft.world.phys.EntityHitResult;
import net.wurstclient.Category;
import net.wurstclient.SearchTags;
import net.wurstclient.events.UpdateListener;
import net.wurstclient.hack.Hack;
import net.wurstclient.hack.HackConflictGroup;
import net.wurstclient.settings.CheckboxSetting;
import net.wurstclient.settings.EnumSetting;
import net.wurstclient.settings.SliderSetting;
import net.wurstclient.settings.SliderSetting.ValueDisplay;
import net.wurstclient.settings.filterlists.EntityFilterList;
import net.wurstclient.util.CombatTargetUtils;
import net.wurstclient.util.CombatTargetUtils.Priority;
import net.wurstclient.util.EntityUtils;
import net.wurstclient.util.InventoryUtils;
import net.wurstclient.util.RotationQueue;
import net.wurstclient.util.RotationUtils;

/**
 * FO's multi-target kill aura.
 *
 * <p>
 * The target/filter/weapon/swap-back core is ported. FO's TPS sync, lag pause,
 * crystal aura pause, packet-only attacks, shield breaking and Baritone pause
 * are not reproduced; this port uses Wurst's rotation queue and ordinary
 * attacks with the vanilla cooldown.
 */
@SearchTags({"fo kill aura", "killaura", "kill aura", "force field", "aura"})
public final class FoKillAuraHack extends Hack implements UpdateListener
{
	private final EnumSetting<Weapon> weapon = new EnumSetting<>("Weapon",
		"Only attacks while holding the selected weapon type.", Weapon.values(),
		Weapon.SWORD);

	private final EnumSetting<RotationMode> rotation = new EnumSetting<>(
		"Rotation", "When to rotate towards the target.", RotationMode.values(),
		RotationMode.ON_HIT);

	private final CheckboxSetting autoSwitch = new CheckboxSetting(
		"Auto switch", "Switches to a matching weapon in the hotbar.", true);

	private final CheckboxSetting swapBack = new CheckboxSetting(
		"Swap back", "Restores the previously selected hotbar slot when the"
			+ " attack session ends.",
		false);

	private final CheckboxSetting onlyOnClick = new CheckboxSetting(
		"Only on click", "Only attacks while the attack key is held.", false);

	private final CheckboxSetting onlyOnLook = new CheckboxSetting(
		"Only on look", "Only attacks the entity you are looking at.", false);

	private final CheckboxSetting pauseOnUse = new CheckboxSetting(
		"Pause on use", "Stops attacking while using an item or breaking a"
			+ " block.",
		true);

	private final SliderSetting range = new SliderSetting("Range",
		"Maximum attack distance.", 3.1, 3, 7, 0.05,
		ValueDisplay.DECIMAL.withSuffix(" blocks"));

	private final SliderSetting maxTargets = new SliderSetting("Max targets",
		"Maximum number of entities attacked per tick.", 1, 1, 5, 1,
		ValueDisplay.INTEGER);

	private final EnumSetting<Priority> priority = new EnumSetting<>("Priority",
		"Target sorting order.", Priority.values(), Priority.ANGLE);

	private final EntityFilterList entityFilters =
		EntityFilterList.genericCombat();

	private RotationQueue rotationQueue;
	private int previousSlot = -1;
	private boolean swapped;

	public FoKillAuraHack()
	{
		super("FoKillAura");
		setCategory(Category.COMBAT);
		addConflictGroup(HackConflictGroup.COMBAT_TARGETING);
		addSetting(weapon);
		addSetting(rotation);
		addSetting(autoSwitch);
		addSetting(swapBack);
		addSetting(onlyOnClick);
		addSetting(onlyOnLook);
		addSetting(pauseOnUse);
		addSetting(range);
		addSetting(maxTargets);
		addSetting(priority);
		entityFilters.forEach(this::addSetting);
	}

	@Override
	protected void onEnable()
	{
		rotationQueue = new RotationQueue(RotationQueue.Priority.COMBAT);
		rotationQueue.start();
		previousSlot = -1;
		swapped = false;
		EVENTS.add(UpdateListener.class, this);
	}

	@Override
	protected void onDisable()
	{
		EVENTS.remove(UpdateListener.class, this);
		restoreSwapBack();

		if(rotationQueue != null)
		{
			rotationQueue.stop();
			rotationQueue = null;
		}
	}

	@Override
	public void onUpdate()
	{
		if(MC.player == null || MC.level == null || MC.gameMode == null)
			return;

		if(!MC.player.isAlive() || MC.player.isSpectator())
			return;

		if(MC.screen instanceof AbstractContainerScreen)
			return;

		if(pauseOnUse.isChecked()
			&& (MC.player.isUsingItem() || MC.gameMode.isDestroying()))
			return;

		if(onlyOnClick.isChecked() && !MC.options.keyAttack.isDown())
			return;

		List<Entity> targets = findTargets();
		if(targets.isEmpty())
		{
			restoreSwapBack();
			return;
		}

		Entity primary = targets.get(0);

		if(autoSwitch.isChecked() && !isValidWeapon(
			MC.player.getMainHandItem().getItem()))
		{
			int slot = InventoryUtils.indexOf(
				stack -> !stack.isEmpty()
					&& isValidWeapon(stack.getItem()),
				9);

			if(slot == -1)
			{
				restoreSwapBack();
				return;
			}

			if(!swapped)
			{
				previousSlot =
					MC.player.getInventory().selected;
				swapped = true;
			}

			MC.player.getInventory().selected = (slot);
		}

		if(!isValidWeapon(MC.player.getMainHandItem().getItem()))
		{
			restoreSwapBack();
			return;
		}

		if(rotation.getSelected() == RotationMode.ALWAYS && rotationQueue != null)
			rotationQueue.setRotation(
				RotationUtils.getNeededRotations(
					primary.getBoundingBox().getCenter()));

		if(MC.player.getAttackStrengthScale(0.5F) < 1.0F)
			return;

		for(Entity target : targets)
			attack(target);
	}

	private List<Entity> findTargets()
	{
		if(onlyOnLook.isChecked())
		{
			if(!(MC.hitResult instanceof EntityHitResult hit))
				return List.of();

			Entity entity = hit.getEntity();
			if(!EntityUtils.IS_ATTACKABLE.test(entity)
				|| !entityFilters.testOne(entity))
				return List.of();

			if(MC.player.distanceToSqr(entity) > range.getValueSq())
				return List.of();

			return List.of(entity);
		}

		return CombatTargetUtils.getList(range.getValue(), 180,
			entity -> entity.getBoundingBox().getCenter(), entityFilters, false,
			priority.getSelected(), maxTargets.getValueI());
	}

	private void attack(Entity target)
	{
		if(rotation.getSelected() == RotationMode.ON_HIT)
			RotationUtils.getNeededRotations(target.getBoundingBox().getCenter())
				.sendPlayerLookPacket();

		MC.gameMode.attack(MC.player, target);
		MC.player.swing(InteractionHand.MAIN_HAND);
	}

	private boolean isValidWeapon(Item item)
	{
		return switch(weapon.getSelected())
		{
			case SWORD -> isSword(item);
			case AXE -> item instanceof AxeItem;
			case MACE -> item instanceof MaceItem;
			case TRIDENT -> item instanceof TridentItem;
			case ALL -> isSword(item) || item instanceof AxeItem
				|| item instanceof MaceItem || item instanceof TridentItem;
			case ANY -> true;
		};
	}

	private boolean isSword(Item item)
	{
		return Set.of(Items.WOODEN_SWORD, Items.STONE_SWORD, Items.IRON_SWORD,
			Items.GOLDEN_SWORD, Items.DIAMOND_SWORD, Items.NETHERITE_SWORD)
			.contains(item);
	}

	private void restoreSwapBack()
	{
		if(!swapped)
			return;

		if(swapBack.isChecked() && previousSlot != -1 && MC.player != null)
			MC.player.getInventory().selected = (previousSlot);

		previousSlot = -1;
		swapped = false;
	}

	private enum Weapon
	{
		SWORD("Sword"),
		AXE("Axe"),
		MACE("Mace"),
		TRIDENT("Trident"),
		ALL("All weapons"),
		ANY("Any item");

		private final String name;

		private Weapon(String name)
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
		ALWAYS("Always"),
		ON_HIT("On hit"),
		NONE("None");

		private final String name;

		private RotationMode(String name)
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

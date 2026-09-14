/*
 * Copyright (c) 2014-2025 Wurst-Imperium and contributors.
 * Copyright (c) 2015-2026 CCBlueX
 *
 * This source code is subject to the terms of the GNU General Public
 * License, version 3. If a copy of the GPL was not distributed with this
 * file, You can obtain one at: https://www.gnu.org/licenses/gpl-3.0.txt
 */
package net.wurstclient.hacks;

import java.util.ArrayList;
import java.util.Arrays;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.protocol.game.ServerboundContainerClickPacket;
import net.minecraft.world.damagesource.CombatRules;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.Enchantments;
import net.wurstclient.Category;
import net.wurstclient.SearchTags;
import net.wurstclient.events.PacketOutputListener;
import net.wurstclient.events.UpdateListener;
import net.wurstclient.hack.Hack;
import net.wurstclient.settings.CheckboxSetting;
import net.wurstclient.settings.SliderSetting;
import net.wurstclient.settings.SliderSetting.ValueDisplay;
import net.wurstclient.util.ArmorUpgradePlanner;
import net.wurstclient.util.DamageProfile;

@SearchTags({"auto armor"})
public final class AutoArmorHack extends Hack
	implements UpdateListener, PacketOutputListener
{
	private final CheckboxSetting useEnchantments = new CheckboxSetting(
		"Use enchantments",
		"Whether or not to consider the Protection enchantment when calculating armor strength.",
		true);
	
	private final CheckboxSetting swapWhileMoving = new CheckboxSetting(
		"Swap while moving",
		"Whether or not to swap armor pieces while the player is moving.\n\n"
			+ "\u00a7c\u00a7lWARNING:\u00a7r This would not be possible without cheats. It may raise suspicion.",
		false);
	
	private final SliderSetting delay = new SliderSetting("Delay",
		"Amount of ticks to wait before swapping the next piece of armor.", 2,
		0, 20, 1, ValueDisplay.INTEGER);

	private final CheckboxSetting antiBreak = new CheckboxSetting("Anti break",
		"Skips armor that is close to breaking.", true);

	private final SliderSetting minDurability = new SliderSetting(
		"Minimum durability", "Armor at or below this durability is skipped.",
		5, 1, 50, 1, ValueDisplay.PERCENTAGE)
			.visibleWhen(antiBreak::isChecked);

	private final CheckboxSetting keepElytra = new CheckboxSetting(
		"Keep elytra", "Does not replace an equipped elytra.", true);
	
	private int timer;
	
	public AutoArmorHack()
	{
		super("AutoArmor");
		setCategory(Category.COMBAT);
		addSetting(useEnchantments);
		addSetting(swapWhileMoving);
		addSetting(delay);
		addSetting(antiBreak);
		addSetting(minDurability);
		addSetting(keepElytra);
	}
	
	@Override
	protected void onEnable()
	{
		timer = 0;
		EVENTS.add(UpdateListener.class, this);
		EVENTS.add(PacketOutputListener.class, this);
	}
	
	@Override
	protected void onDisable()
	{
		EVENTS.remove(UpdateListener.class, this);
		EVENTS.remove(PacketOutputListener.class, this);
		WURST.getInventoryActionQueue().cancel(this);
	}
	
	@Override
	public void onUpdate()
	{
		if(MC.player == null || MC.player.isSpectator())
			return;

		// wait for timer
		if(timer > 0)
		{
			timer--;
			return;
		}
		
		// check screen
		if(MC.screen instanceof AbstractContainerScreen
			&& !(MC.screen instanceof InventoryScreen))
			return;

		if(WURST.getInventoryActionQueue().hasPending(this))
			return;
		
		LocalPlayer player = MC.player;
		Inventory inventory = player.getInventory();
		
		if(!swapWhileMoving.isChecked() && (player.input.forwardImpulse != 0
			|| player.input.leftImpulse != 0))
			return;
		
		// 比较基准：现在这一套挨一次名义命中会吃多少伤害
		float wornDamage = getDamageTaken(inventory, -1, ItemStack.EMPTY);
		
		// 每个护甲位的现状：有没有东西、能不能动、身上这件剩多少耐久
		ArmorUpgradePlanner.Slot[] armorSlots = new ArmorUpgradePlanner.Slot[4];
		for(int type = 0; type < 4; type++)
			armorSlots[type] = getArmorSlot(inventory, type);
		
		// search inventory for better armor
		ArrayList<ArmorUpgradePlanner.Candidate> candidates = new ArrayList<>();
		for(int slot = 0; slot < 36; slot++)
		{
			ItemStack stack = inventory.getItem(slot);
			
			if(stack.isEmpty() || !(stack.getItem() instanceof ArmorItem item))
				continue;
			if(isLowDurability(stack)
				|| EnchantmentHelper.getItemEnchantmentLevel(
					Enchantments.BINDING_CURSE, stack) > 0)
				continue;
			
			// 候选按「换上它以后这一套少吃多少伤害」打分，不是逐件估分
			int armorType = item.getEquipmentSlot().getIndex();
			candidates.add(new ArmorUpgradePlanner.Candidate(armorType, slot,
				ArmorUpgradePlanner.piece(wornDamage,
					getDamageTaken(inventory, armorType, stack),
					ArmorUpgradePlanner.durabilityScore(stack.getMaxDamage(),
						stack.getDamageValue()))));
		}
		
		// Equip the largest deterministic upgrade first.
		ArmorUpgradePlanner.Choice choice = ArmorUpgradePlanner.choose(
			armorSlots, candidates, inventory.getFreeSlot() != -1);
		if(choice == null)
			return;
		
		int inventorySlot = choice.inventorySlot();
		ItemStack expectedArmor = inventory.getItem(inventorySlot).copy();

		// hotbar fix
		int sourceSlot = inventorySlot < 9 ? inventorySlot + 36 : inventorySlot;

		int armorSlot = 8 - choice.type();
		boolean hasOldArmor = !inventory.getArmor(choice.type()).isEmpty();
		WURST.getInventoryActionQueue().submit(this, 50,
			() -> MC.player != null
				&& MC.player.containerMenu.containerId == 0
				&& ItemStack.isSameItemSameTags(expectedArmor,
					MC.player.getInventory().getItem(inventorySlot)),
			hasOldArmor
				? new Runnable[]{() -> IMC.getInteractionManager()
					.windowClick_QUICK_MOVE(armorSlot),
					() -> IMC.getInteractionManager()
						.windowClick_QUICK_MOVE(sourceSlot)}
				: new Runnable[]{() -> IMC.getInteractionManager()
					.windowClick_QUICK_MOVE(sourceSlot)});
	}
	
	@Override
	public void onSentPacket(PacketOutputEvent event)
	{
		if(event.getPacket() instanceof ServerboundContainerClickPacket)
			timer = delay.getValueI();
	}
	
	/**
	 * 一个护甲位现在的状态：有没有东西、能不能动、身上这件值多少耐久分。
	 *
	 * <p>
	 * 鞘翅位（开了 Keep elytra 时）和带绑定诅咒的护甲标成不可替换，与原来的
	 * {@code Integer.MAX_VALUE} 哨兵等价；空位和非护甲物品都记为「没有可比的可穿
	 * 护甲」，于是任何一件护甲都算提升，空位优先被补上。
	 */
	private ArmorUpgradePlanner.Slot getArmorSlot(Inventory inventory, int type)
	{
		ItemStack stack = inventory.getArmor(type);
		boolean occupied = !stack.isEmpty();
		
		if(type == 2 && keepElytra.isChecked() && stack.is(Items.ELYTRA))
			return new ArmorUpgradePlanner.Slot(occupied, true,
				ArmorUpgradePlanner.worn(0));
		
		if(!(stack.getItem() instanceof ArmorItem))
			return new ArmorUpgradePlanner.Slot(occupied, false,
				ArmorUpgradePlanner.worn(0));
		
		if(EnchantmentHelper.getItemEnchantmentLevel(Enchantments.BINDING_CURSE,
			stack) > 0)
			return new ArmorUpgradePlanner.Slot(occupied, true,
				ArmorUpgradePlanner.worn(0));
		
		return new ArmorUpgradePlanner.Slot(occupied, false,
			ArmorUpgradePlanner.worn(
				ArmorUpgradePlanner.durabilityScore(stack.getMaxDamage(),
					stack.getDamageValue())));
	}
	
	/**
	 * 这一套护甲挨一次名义命中会吃多少伤害：{@code replacedType} 那位用
	 * {@code replacement} 顶上（传 -1 表示就是身上这一套）。护甲与韧性交给原版的
	 * {@code CombatRules.getDamageAfterAbsorb}（韧性是非线性的，不能像原来那样加个
	 * 常数），附魔 EPF 仍由原版 {@code EnchantmentHelper.getDamageProtection} 给出
	 * ——1.20.1 的附魔是数据驱动的，不能抄 1.12.2 的等级表——两者按
	 * {@link DamageProfile} 的公式组合。
	 *
	 * <p>
	 * 参考项目 CakeSlayers/OpenEpsilon 的 {@code util/combat/DamageReduction.kt} 正是
	 * 按整支队伍的护甲列表求和后再算伤害，而不是逐件估分；本方法沿用这个口径。
	 */
	private float getDamageTaken(Inventory inventory, int replacedType,
		ItemStack replacement)
	{
		ItemStack[] armor = new ItemStack[4];
		float armorPoints = 0;
		float toughness = 0;
		
		for(int type = 0; type < 4; type++)
		{
			ItemStack stack =
				type == replacedType ? replacement : inventory.getArmor(type);
			armor[type] = stack;
			
			if(stack.getItem() instanceof ArmorItem item)
			{
				armorPoints += item.getDefense();
				toughness += item.toughness;
			}
		}
		
		int epf = 0;
		if(useEnchantments.isChecked())
		{
			LocalPlayer player = MC.player;
			DamageSource dmgSource =
				player.damageSources().playerAttack(player);
			epf = EnchantmentHelper.getDamageProtection(Arrays.asList(armor),
				dmgSource);
		}
		
		DamageProfile profile = DamageProfile.of(armorPoints, toughness, epf, 0,
			1F);
		float afterAbsorb = CombatRules.getDamageAfterAbsorb(
			ArmorUpgradePlanner.NOMINAL_DAMAGE, armorPoints, toughness);
		return profile.apply(afterAbsorb, false);
	}

	private boolean isLowDurability(ItemStack stack)
	{
		if(!antiBreak.isChecked() || !stack.isDamageableItem())
			return false;

		int remaining = stack.getMaxDamage() - stack.getDamageValue();
		return remaining * 100 <= stack.getMaxDamage()
			* minDurability.getValueI();
	}
}

/*
 * Copyright (c) 2014-2025 Wurst-Imperium and contributors.
 *
 * This source code is subject to the terms of the GNU General Public
 * License, version 3. If a copy of the GPL was not distributed with this
 * file, You can obtain one at: https://www.gnu.org/licenses/gpl-3.0.txt
 */
package net.wurstclient.hacks;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MobType;
import net.minecraft.world.item.DiggerItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.SwordItem;
import net.minecraft.world.item.TieredItem;
import net.minecraft.world.item.TridentItem;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.wurstclient.Category;
import net.wurstclient.SearchTags;
import net.wurstclient.events.UpdateListener;
import net.wurstclient.hack.Hack;
import net.wurstclient.settings.CheckboxSetting;
import net.wurstclient.settings.EnumSetting;
import net.wurstclient.settings.SliderSetting;
import net.wurstclient.settings.SliderSetting.ValueDisplay;
import net.wurstclient.util.AutoSwordWeaponScorer;
import net.wurstclient.util.EntityUtils;
import net.wurstclient.util.ItemUtils;

@SearchTags({"auto sword"})
public final class AutoSwordHack extends Hack implements UpdateListener
{
	private final EnumSetting<Priority> priority =
		new EnumSetting<>("Priority", Priority.values(), Priority.SPEED);
	
	private final CheckboxSetting switchBack = new CheckboxSetting(
		"Switch back", "Switches back to the previously selected slot after"
			+ " \u00a7lRelease time\u00a7r has passed.",
		true);
	
	private final SliderSetting releaseTime = new SliderSetting("Release time",
		"Time until AutoSword will switch back from the weapon to the"
			+ " previously selected slot.\n\n"
			+ "Only works when \u00a7lSwitch back\u00a7r is checked.",
		10, 1, 200, 1,
		ValueDisplay.INTEGER.withSuffix(" ticks").withLabel(1, "1 tick"));
	
	private int oldSlot;
	private int timer;
	private int weaponSlot;
	
	public AutoSwordHack()
	{
		super("AutoSword");
		setCategory(Category.COMBAT);
		
		addSetting(priority);
		addSetting(switchBack);
		addSetting(releaseTime);
	}
	
	@Override
	protected void onEnable()
	{
		oldSlot = -1;
		weaponSlot = -1;
		EVENTS.add(UpdateListener.class, this);
	}
	
	@Override
	protected void onDisable()
	{
		EVENTS.remove(UpdateListener.class, this);
		resetSlot();
	}
	
	@Override
	public void onUpdate()
	{
		if(MC.hitResult != null
			&& MC.hitResult.getType() == HitResult.Type.ENTITY)
		{
			Entity entity = ((EntityHitResult)MC.hitResult).getEntity();
			
			if(entity instanceof LivingEntity
				&& EntityUtils.IS_ATTACKABLE.test(entity))
				setSlot(entity);
		}
		
		// 玩家自己换走了槽位，说明这次接管已经结束：先取消接管，再按正常流程
		// 在计时结束后回到旧槽，避免「切到别的东西又被拽回武器」的一 tick 抖动
		if(weaponSlot != -1 && MC.player.getInventory().selected != weaponSlot)
		{
			weaponSlot = -1;
			timer = 0;
			resetSlot();
			return;
		}
		
		// update timer
		if(timer > 0)
		{
			timer--;
			return;
		}
		
		if(resetSlot())
			return;
	}
	
	public void setSlot(Entity entity)
	{
		// check if active
		if(!isEnabled())
			return;
		
		// wait for AutoEat
		if(WURST.getHax().autoEatHack.isEating())
			return;
		
		// 正在使用物品时不要换。原来只挡了 AutoEat，于是拉弓、举盾、喝药水、
		// 以及 AutoEat 之外的进食都会被换手打断（原版使用中的物品一旦不在
		// 手上就会中断，KillauraHack.java:904 也是按这个前提写的）
		if(shouldPauseForHands())
			return;
		
		// 只用武器评分决定「换到哪一格」，换与不换的时机由上面把关
		int bestSlot = selectBestSlot(entity);
		if(bestSlot < 0)
			return;
		
		// save old slot
		if(oldSlot == -1)
			oldSlot = MC.player.getInventory().selected;
		
		// set slot
		MC.player.getInventory().selected = bestSlot;
		
		// start timer
		timer = releaseTime.getValueI();
		weaponSlot = bestSlot;
	}
	
	/**
	 * 手上正在使用物品（吃 / 喝 / 拉弓 / 举盾 / 投掷）时暂停换手。
	 */
	private boolean shouldPauseForHands()
	{
		return MC.player.isUsingItem();
	}
	
	private int selectBestSlot(Entity entity)
	{
		return AutoSwordWeaponScorer.selectBestHotbarSlot(i ->
		{
			// skip empty slots
			ItemStack stack = MC.player.getInventory().getItem(i);
			if(stack.isEmpty())
				return AutoSwordWeaponScorer.NOT_A_WEAPON;
			
			return getValue(stack, entity);
		});
	}
	
	private float getValue(ItemStack stack, Entity entity)
	{
		Item item = stack.getItem();
		if(!(item instanceof TieredItem || item instanceof TridentItem))
			return Integer.MIN_VALUE;
		
		switch(priority.getSelected())
		{
			case SPEED:
			return ItemUtils.getAttackSpeed(item);
			
			case DAMAGE:
			MobType group = entity instanceof LivingEntity le
				? le.getMobType() : MobType.UNDEFINED;
			float dmg = EnchantmentHelper.getDamageBonus(stack, group);
			if(item instanceof SwordItem sword)
				dmg += sword.getDamage();
			if(item instanceof DiggerItem tool)
				dmg += tool.getAttackDamage();
			if(item instanceof TridentItem)
				dmg += TridentItem.BASE_DAMAGE;
			return dmg;
		}
		
		return Integer.MIN_VALUE;
	}
	
	/**
	 * 收尾：把槽位还给玩家。
	 *
	 * @return 是否因为「手上正在用东西」而推迟，调用方据此在本 tick 直接返回
	 */
	private boolean resetSlot()
	{
		if(weaponSlot == -1)
			return false;
		
		// 换回原槽同样会打断正在进行的吃 / 喝 / 拉弓。玩家停下手之后
		// weaponSlot 仍然记着，下一 tick 会正常收尾
		if(shouldPauseForHands())
			return true;
		
		weaponSlot = -1;
		
		if(!switchBack.isChecked())
		{
			oldSlot = -1;
			return false;
		}
		
		if(oldSlot != -1)
		{
			MC.player.getInventory().selected = oldSlot;
			oldSlot = -1;
		}
		
		return false;
	}
	
	private enum Priority
	{
		SPEED("Speed (swords)"),
		DAMAGE("Damage (axes)");
		
		private final String name;
		
		private Priority(String name)
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

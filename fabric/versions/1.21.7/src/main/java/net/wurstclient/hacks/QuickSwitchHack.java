/*
 * Copyright (c) 2025 Soar Client contributors
 *
 * This source code is subject to the terms of the GNU Affero General Public
 * License, version 3. If a copy of the AGPL was not distributed with this
 * file, You can obtain one at: https://www.gnu.org/licenses/agpl-3.0.txt
 */
// Ported from Soar Client v7.1.2 (LGPL-3.0).
package net.wurstclient.hacks;

import org.lwjgl.glfw.GLFW;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.wurstclient.Category;
import net.wurstclient.SearchTags;
import net.wurstclient.events.KeyPressListener;
import net.wurstclient.hack.Hack;
import net.wurstclient.settings.EnumSetting;

@SearchTags({"quick switch", "hotbar", "sword", "pickaxe", "blocks", "gap"})
public final class QuickSwitchHack extends Hack implements KeyPressListener
{
	private final EnumSetting<TriggerKey> swordKey =
		new EnumSetting<>("Sword key",
			"Key that selects the best sword in your hotbar.", TriggerKey.values(),
			TriggerKey.R);

	private final EnumSetting<TriggerKey> blockKey =
		new EnumSetting<>("Block key",
			"Key that selects the best stack of blocks in your hotbar.",
			TriggerKey.values(), TriggerKey.G);

	private final EnumSetting<TriggerKey> pickaxeKey =
		new EnumSetting<>("Pickaxe key",
			"Key that selects the best pickaxe in your hotbar.",
			TriggerKey.values(), TriggerKey.X);

	private final EnumSetting<TriggerKey> gapKey =
		new EnumSetting<>("Gap key",
			"Key that selects golden apples from your hotbar.",
			TriggerKey.values(), TriggerKey.V);

	public QuickSwitchHack()
	{
		super("QuickSwitch");
		setCategory(Category.ITEMS);
		addSetting(swordKey);
		addSetting(blockKey);
		addSetting(pickaxeKey);
		addSetting(gapKey);
	}

	@Override
	protected void onEnable()
	{
		EVENTS.add(KeyPressListener.class, this);
	}

	@Override
	protected void onDisable()
	{
		EVENTS.remove(KeyPressListener.class, this);
	}

	@Override
	public void onKeyPress(KeyPressEvent event)
	{
		if(event.getAction() != GLFW.GLFW_PRESS)
			return;
		if(MC.player == null || MC.screen != null)
			return;

		if(swordKey.getSelected().matches(event))
			selectBestSword();
		else if(blockKey.getSelected().matches(event))
			selectBestBlock();
		else if(pickaxeKey.getSelected().matches(event))
			selectBestPickaxe();
		else if(gapKey.getSelected().matches(event))
			selectGap();
	}

	private void selectBestSword()
	{
		int bestSlot = -1;
		float bestDamage = Float.NEGATIVE_INFINITY;

		for(int slot = 0; slot < 9; slot++)
		{
			ItemStack stack = MC.player.getInventory().getItem(slot);
			if(!stack.is(ItemTags.SWORDS))
				continue;

			float damage = getAttackDamage(stack);
			if(damage > bestDamage)
			{
				bestDamage = damage;
				bestSlot = slot;
			}
		}

		select(bestSlot);
	}

	private void selectBestPickaxe()
	{
		int bestSlot = -1;
		float bestSpeed = Float.NEGATIVE_INFINITY;

		for(int slot = 0; slot < 9; slot++)
		{
			ItemStack stack = MC.player.getInventory().getItem(slot);
			if(!stack.is(ItemTags.PICKAXES))
				continue;

			float speed =
				stack.getDestroySpeed(Blocks.STONE.defaultBlockState());
			if(speed > bestSpeed)
			{
				bestSpeed = speed;
				bestSlot = slot;
			}
		}

		select(bestSlot);
	}

	private void selectBestBlock()
	{
		int bestSlot = -1;
		int bestCount = -1;

		for(int slot = 0; slot < 9; slot++)
		{
			ItemStack stack = MC.player.getInventory().getItem(slot);
			if(!(stack.getItem() instanceof BlockItem))
				continue;

			if(stack.getCount() > bestCount)
			{
				bestCount = stack.getCount();
				bestSlot = slot;
			}
		}

		select(bestSlot);
	}

	private void selectGap()
	{
		int normalGap = -1;

		for(int slot = 0; slot < 9; slot++)
		{
			ItemStack stack = MC.player.getInventory().getItem(slot);

			if(stack.is(Items.ENCHANTED_GOLDEN_APPLE))
			{
				select(slot);
				return;
			}

			if(normalGap == -1 && stack.is(Items.GOLDEN_APPLE))
				normalGap = slot;
		}

		select(normalGap);
	}

	private void select(int slot)
	{
		if(slot != -1)
			MC.player.getInventory().setSelectedSlot(slot);
	}

	private float getAttackDamage(ItemStack stack)
	{
		float[] damage = {1F};
		stack.forEachModifier(EquipmentSlot.MAINHAND, (attribute, modifier) -> {
			if(attribute.equals(Attributes.ATTACK_DAMAGE))
				damage[0] += (float)modifier.amount();
		});
		return damage[0];
	}

	private enum TriggerKey
	{
		NONE(-1),
		R(GLFW.GLFW_KEY_R),
		G(GLFW.GLFW_KEY_G),
		X(GLFW.GLFW_KEY_X),
		C(GLFW.GLFW_KEY_C),
		V(GLFW.GLFW_KEY_V),
		B(GLFW.GLFW_KEY_B),
		F(GLFW.GLFW_KEY_F),
		H(GLFW.GLFW_KEY_H),
		J(GLFW.GLFW_KEY_J),
		K(GLFW.GLFW_KEY_K),
		L(GLFW.GLFW_KEY_L),
		P(GLFW.GLFW_KEY_P),
		Z(GLFW.GLFW_KEY_Z),
		LEFT_SHIFT(GLFW.GLFW_KEY_LEFT_SHIFT),
		LEFT_CONTROL(GLFW.GLFW_KEY_LEFT_CONTROL),
		LEFT_ALT(GLFW.GLFW_KEY_LEFT_ALT);

		private final int keyCode;

		private TriggerKey(int keyCode)
		{
			this.keyCode = keyCode;
		}

		private boolean matches(KeyPressEvent event)
		{
			return keyCode != -1 && event.getKeyCode() == keyCode;
		}

		@Override
		public String toString()
		{
			return name().replace('_', ' ');
		}
	}
}

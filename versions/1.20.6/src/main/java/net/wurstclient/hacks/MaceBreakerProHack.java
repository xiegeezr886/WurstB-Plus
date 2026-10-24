/*
 * Copyright (c) 2026 WurstB+ Plus contributors
 *
 * This source code is subject to the terms of the GNU Affero General Public
 * License, version 3. If a copy of the AGPL was not distributed with this
 * file, You can obtain one at: https://www.gnu.org/licenses/agpl-3.0.txt
 *
 * Ported from InvincibleMachineGun (GPL-3.0).
 */
package net.wurstclient.hacks;

import net.minecraft.network.protocol.game.ServerboundInteractPacket;
import net.minecraft.network.protocol.game.ServerboundSetCarriedItemPacket;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.wurstclient.Category;
import net.wurstclient.SearchTags;
import net.wurstclient.events.PlayerAttacksEntityListener;
import net.wurstclient.hack.Hack;
import net.wurstclient.settings.CheckboxSetting;
import net.wurstclient.util.InventoryUtils;

@SearchTags({"mace breaker", "shield breaker", "MaceBreakerPro",
	"axe mace swap"})
public final class MaceBreakerProHack extends Hack
	implements PlayerAttacksEntityListener
{
	private final CheckboxSetting swordTrigger = new CheckboxSetting(
		"Sword trigger", "Trigger while holding a sword.", true);

	private final CheckboxSetting axeTrigger = new CheckboxSetting(
		"Axe trigger", "Trigger while holding an axe.", true);

	private final CheckboxSetting maceTrigger = new CheckboxSetting(
		"Mace trigger", "Trigger while holding a mace.", true);

	private final CheckboxSetting onlyOnShield = new CheckboxSetting(
		"Only on shield", "Only trigger against blocking targets.", true);

	private final CheckboxSetting legitMode = new CheckboxSetting(
		"Legit mode", "Sync the client-selected slot with the server.", true);

	private final CheckboxSetting autoReturn = new CheckboxSetting(
		"Return to original",
		"Switch back to the original slot after attacking.", true);

	private boolean performing;

	public MaceBreakerProHack()
	{
		super("MaceBreakerPro");
		setCategory(Category.COMBAT);
		addSetting(swordTrigger);
		addSetting(axeTrigger);
		addSetting(maceTrigger);
		addSetting(onlyOnShield);
		addSetting(legitMode);
		addSetting(autoReturn);
	}

	@Override
	protected void onEnable()
	{
		performing = false;
		EVENTS.add(PlayerAttacksEntityListener.class, this);
	}

	@Override
	protected void onDisable()
	{
		EVENTS.remove(PlayerAttacksEntityListener.class, this);
		performing = false;
	}

	@Override
	public void onPlayerAttacksEntity(Entity target)
	{
		if(performing || target == null || MC.player == null
			|| MC.level == null)
			return;

		if(onlyOnShield.isChecked())
		{
			if(!(target instanceof LivingEntity living) || !living.isBlocking())
				return;
		}

		ItemStack handStack = MC.player.getMainHandItem();
		boolean holdingSword = handStack.is(ItemTags.SWORDS);
		boolean holdingAxe = handStack.is(ItemTags.AXES);
		boolean holdingMace = handStack.is(Items.MACE);

		if(holdingSword && !swordTrigger.isChecked())
			return;
		if(holdingAxe && !axeTrigger.isChecked())
			return;
		if(holdingMace && !maceTrigger.isChecked())
			return;
		if(!holdingSword && !holdingAxe && !holdingMace)
			return;

		int axeSlot = InventoryUtils
			.indexOf(stack -> stack.is(ItemTags.AXES), 9);
		int maceSlot =
			InventoryUtils.indexOf(stack -> stack.is(Items.MACE), 9);
		if(axeSlot == -1 || maceSlot == -1)
			return;

		int originalSlot = MC.player.getInventory().selected;

		performing = true;
		try
		{
			performAttackStep(axeSlot, target);
			performAttackStep(maceSlot, target);

			if(autoReturn.isChecked())
				performSlotSwitch(originalSlot);
			else
				performSlotSwitch(maceSlot);

			MC.player.swing(InteractionHand.MAIN_HAND);
		}finally
		{
			performing = false;
		}
	}

	private void performAttackStep(int slot, Entity target)
	{
		performSlotSwitch(slot);
		MC.player.connection.send(ServerboundInteractPacket
			.createAttackPacket(target, MC.player.isShiftKeyDown()));
	}

	private void performSlotSwitch(int slot)
	{
		if(slot < 0 || slot > 8)
			return;
		if(MC.player.getInventory().selected == slot)
			return;

		MC.player.connection
			.send(new ServerboundSetCarriedItemPacket(slot));

		if(legitMode.isChecked())
			MC.player.getInventory().selected = slot;
	}
}

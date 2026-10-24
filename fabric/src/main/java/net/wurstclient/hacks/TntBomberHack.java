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

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.protocol.game.ServerboundSetCarriedItemPacket;
import net.minecraft.world.item.FireChargeItem;
import net.minecraft.world.item.FlintAndSteelItem;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.Vec3;
import net.wurstclient.Category;
import net.wurstclient.SearchTags;
import net.wurstclient.events.UpdateListener;
import net.wurstclient.hack.Hack;
import net.wurstclient.settings.CheckboxSetting;
import net.wurstclient.settings.SliderSetting;
import net.wurstclient.settings.SliderSetting.ValueDisplay;
import net.wurstclient.util.BlockPlacer;
import net.wurstclient.util.BlockUtils;
import net.wurstclient.util.InventoryUtils;

@SearchTags({"tnt bomber", "bomber", "tnt", "b2"})
public final class TntBomberHack extends Hack implements UpdateListener
{
	private final SliderSetting delay = new SliderSetting("Delay",
		"Ticks between TNT placements.", 4, 0, 20, 1, ValueDisplay.INTEGER);

	private final CheckboxSetting onlyWhenFlying = new CheckboxSetting(
		"Only when flying", "Only places TNT while elytra flying.", true);

	private final CheckboxSetting requireRightClick = new CheckboxSetting(
		"Require right click", "Only places TNT while holding right click.",
		true);

	private int timer;

	public TntBomberHack()
	{
		super("TntBomber");
		setCategory(Category.COMBAT);
		addSetting(delay);
		addSetting(onlyWhenFlying);
		addSetting(requireRightClick);
	}

	@Override
	protected void onEnable()
	{
		timer = 0;
		EVENTS.add(UpdateListener.class, this);
	}

	@Override
	protected void onDisable()
	{
		EVENTS.remove(UpdateListener.class, this);
	}

	@Override
	public void onUpdate()
	{
		if(MC.player == null || MC.level == null)
			return;

		if(timer > 0)
		{
			timer--;
			return;
		}

		boolean rightClick =
			!requireRightClick.isChecked() || MC.options.keyUse.isDown();
		boolean flying = MC.player.isFallFlying();

		if(!rightClick || (onlyWhenFlying.isChecked() && !flying))
			return;

		int tntSlot = InventoryUtils.indexOf(Items.TNT, 9);
		int igniterSlot = InventoryUtils.indexOf(
			stack -> stack.getItem() instanceof FlintAndSteelItem
				|| stack.getItem() instanceof FireChargeItem,
			9);

		if(tntSlot < 0 || igniterSlot < 0)
			return;

		Vec3 behind = MC.player.position()
			.subtract(MC.player.getDeltaMovement().scale(2.5));
		BlockPos placePos = BlockPos.containing(behind.x,
			MC.player.getY() - 1, behind.z);

		if(!BlockUtils.getState(placePos).canBeReplaced())
			return;

		int previousSlot = MC.player.getInventory().selected;

		selectSlot(tntSlot);
		if(!BlockPlacer.place(placePos, true, false))
		{
			selectSlot(previousSlot);
			return;
		}

		selectSlot(igniterSlot);
		Vec3 hitVec = Vec3.atCenterOf(placePos);
		WURST.getRotationFaker().faceVectorPacket(hitVec);
		IMC.getInteractionManager().rightClickBlock(placePos, Direction.UP,
			hitVec);

		selectSlot(previousSlot);
		timer = delay.getValueI();
	}

	private void selectSlot(int slot)
	{
		MC.player.getInventory().selected = slot;
		MC.player.connection
			.send(new ServerboundSetCarriedItemPacket(slot));
	}
}

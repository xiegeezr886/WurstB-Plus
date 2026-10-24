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
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.wurstclient.Category;
import net.wurstclient.SearchTags;
import net.wurstclient.events.UpdateListener;
import net.wurstclient.hack.Hack;
import net.wurstclient.settings.CheckboxSetting;
import net.wurstclient.settings.SliderSetting;
import net.wurstclient.settings.SliderSetting.ValueDisplay;
import net.wurstclient.util.BlockUtils;
import net.wurstclient.util.InventoryUtils;

@SearchTags({"legit no fall", "water bucket", "mlg", "no fall"})
public final class LegitNoFallHack extends Hack implements UpdateListener
{
	private final SliderSetting checkDown = new SliderSetting("Check down",
		"How many blocks below the player to check.", 1, 0, 3, 1,
		ValueDisplay.INTEGER);

	private final CheckboxSetting inventorySwap = new CheckboxSetting(
		"Inventory swap", "Also searches the main inventory for buckets.",
		true);

	private final SliderSetting offSet = new SliderSetting("Offset",
		"Horizontal offset used when searching for a block to place water on.",
		0.3, 0, 1, 0.05, ValueDisplay.DECIMAL);

	private boolean hasPlacedWater;
	private BlockPos lastPos;
	private int previousSlot = -1;

	public LegitNoFallHack()
	{
		super("LegitNoFall");
		setCategory(Category.MOVEMENT);
		addSetting(checkDown);
		addSetting(inventorySwap);
		addSetting(offSet);
	}

	@Override
	protected void onEnable()
	{
		hasPlacedWater = false;
		lastPos = null;
		previousSlot = -1;
		EVENTS.add(UpdateListener.class, this);
	}

	@Override
	protected void onDisable()
	{
		EVENTS.remove(UpdateListener.class, this);
		restoreSlot();
		hasPlacedWater = false;
		lastPos = null;
	}

	@Override
	public void onUpdate()
	{
		if(MC.player == null || MC.level == null)
			return;

		if(MC.level.dimension() == Level.NETHER)
			return;

		if(hasPlacedWater && lastPos != null)
		{
			if(!selectItem(Items.BUCKET))
				return;

			Vec3 aim = new Vec3(lastPos.getX() + 0.5, lastPos.getY() + 0.5,
				lastPos.getZ() + 0.5);
			WURST.getRotationFaker().faceVectorPacket(aim);
			MC.player.swing(InteractionHand.MAIN_HAND, net.minecraft.world.item.component.SwingAnimation.DEFAULT, false);
			IMC.getInteractionManager().rightClickItem();
			restoreSlot();

			hasPlacedWater = false;
			lastPos = null;
			return;
		}

		if(!isFalling())
			return;

		BlockPos base = MC.player.blockPosition().below(checkDown.getValueI());
		double offset = offSet.getValue();

		for(double x : new double[]{offset, -offset})
		{
			for(double z : new double[]{offset, -offset})
			{
				BlockPos pos = BlockPos.containing(MC.player.getX() + x,
					base.getY(), MC.player.getZ() + z);
				BlockState state = MC.level.getBlockState(pos);

				if(state.isAir() || state.canBeReplaced())
					continue;

				Vec3 aim = new Vec3(pos.getX() + 0.5, pos.getY() + 1.0,
					pos.getZ() + 0.5);
				if(!BlockUtils.hasLineOfSight(aim))
					continue;

				if(!selectItem(Items.WATER_BUCKET))
					return;

				WURST.getRotationFaker().faceVectorPacket(aim);
				MC.player.swing(InteractionHand.MAIN_HAND, net.minecraft.world.item.component.SwingAnimation.DEFAULT, false);
				IMC.getInteractionManager().rightClickBlock(pos,
					Direction.UP, aim);
				restoreSlot();

				lastPos = pos.above();
				hasPlacedWater = true;
				return;
			}
		}
	}

	private boolean isFalling()
	{
		return MC.player.fallDistance > 3 && !MC.player.onGround()
			&& !MC.player.isFallFlying();
	}

	private boolean selectItem(Item item)
	{
		int slot = InventoryUtils.indexOf(item,
			inventorySwap.isChecked() ? 36 : 9);
		if(slot < 0)
			return false;

		int selected = MC.player.getInventory().getSelectedSlot();
		if(previousSlot < 0)
			previousSlot = selected;

		if(slot < 9)
			MC.player.getInventory().setSelectedSlot(slot);
		else
			IMC.getInteractionManager().windowClick_SWAP(
				InventoryUtils.toNetworkSlot(slot), selected);

		MC.player.connection.send(new ServerboundSetCarriedItemPacket(
			MC.player.getInventory().getSelectedSlot()));
		return true;
	}

	private void restoreSlot()
	{
		if(previousSlot < 0 || MC.player == null)
			return;

		MC.player.getInventory().setSelectedSlot(previousSlot);
		MC.player.connection
			.send(new ServerboundSetCarriedItemPacket(previousSlot));
		previousSlot = -1;
	}
}

/*
 * Copyright (c) 2014-2025 Wurst-Imperium and contributors.
 *
 * This source code is subject to the terms of the GNU General Public
 * License, version 3. If a copy of the GPL was not distributed with this
 * file, You can obtain one at: https://www.gnu.org/licenses/gpl-3.0.txt
 *
 * Ported from Meteor Client (GPL-3.0).
 */
package net.wurstclient.hacks;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.protocol.game.ServerboundPlayerActionPacket.Action;
import net.minecraft.network.protocol.game.ServerboundSwingPacket;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.wurstclient.Category;
import net.wurstclient.SearchTags;
import net.wurstclient.events.UpdateListener;
import net.wurstclient.hack.Hack;
import net.wurstclient.settings.CheckboxSetting;
import net.wurstclient.util.BlockPlacer;
import net.wurstclient.util.BlockUtils;
import net.wurstclient.util.InventoryUtils;

@SearchTags({"anti bed", "bed aura", "bed trap", "AntiBed"})
public final class AntiBedHack extends Hack implements UpdateListener
{
	private final CheckboxSetting placeStringTop = new CheckboxSetting(
		"Place string top", "Places string above you.", false);

	private final CheckboxSetting placeStringMiddle = new CheckboxSetting(
		"Place string middle", "Places string in your upper hitbox.", true);

	private final CheckboxSetting placeStringBottom = new CheckboxSetting(
		"Place string bottom", "Places string at your feet.", false);

	private final CheckboxSetting onlyInHole = new CheckboxSetting(
		"Only in hole",
		"Only functions when you are standing in a hole.", true);

	private boolean breaking;

	public AntiBedHack()
	{
		super("AntiBed");
		setCategory(Category.COMBAT);
		addSetting(placeStringTop);
		addSetting(placeStringMiddle);
		addSetting(placeStringBottom);
		addSetting(onlyInHole);
	}

	@Override
	protected void onEnable()
	{
		breaking = false;
		EVENTS.add(UpdateListener.class, this);
	}

	@Override
	protected void onDisable()
	{
		EVENTS.remove(UpdateListener.class, this);
		breaking = false;
	}

	@Override
	public void onUpdate()
	{
		if(onlyInHole.isChecked() && !isInHole())
			return;

		BlockPos head = MC.player.blockPosition().above();

		if(BlockUtils.getBlock(head) instanceof BedBlock && !breaking)
		{
			WURST.getRotationFaker()
				.faceVectorPacket(Vec3.atCenterOf(head));
			IMC.getInteractionManager().sendPlayerActionC2SPacket(
				Action.START_DESTROY_BLOCK, head, Direction.UP);
			IMC.getInteractionManager().sendPlayerActionC2SPacket(
				Action.STOP_DESTROY_BLOCK, head, Direction.UP);
			breaking = true;

		}else if(breaking)
		{
			IMC.getInteractionManager().sendPlayerActionC2SPacket(
				Action.ABORT_DESTROY_BLOCK, head, Direction.UP);
			MC.player.connection
				.send(new ServerboundSwingPacket(InteractionHand.MAIN_HAND));
			breaking = false;
		}

		if(placeStringTop.isChecked())
			place(MC.player.blockPosition().above(2));
		if(placeStringMiddle.isChecked())
			place(MC.player.blockPosition().above());
		if(placeStringBottom.isChecked())
			place(MC.player.blockPosition());
	}

	private void place(BlockPos pos)
	{
		if(BlockUtils.getBlock(pos).asItem() == Items.STRING)
			return;

		int slot = InventoryUtils.indexOf(Items.STRING, 9);
		if(slot == -1)
			return;

		int prevSlot = MC.player.getInventory().selected;
		InventoryUtils.selectItem(slot);

		if(!MC.player.isHolding(Items.STRING))
		{
			MC.player.getInventory().selected = prevSlot;
			return;
		}

		BlockPlacer.BlockPlacingParams params =
			BlockPlacer.getBlockPlacingParams(pos);

		if(params != null)
		{
			WURST.getRotationFaker().faceVectorPacket(params.hitVec());
			IMC.getInteractionManager().rightClickBlock(params.neighbor(),
				params.side(), params.hitVec());
			MC.player.swing(InteractionHand.MAIN_HAND);
		}

		MC.player.getInventory().selected = prevSlot;
	}

	private boolean isInHole()
	{
		BlockPos pos = MC.player.blockPosition();
		int air = 0;

		for(Direction dir : Direction.values())
		{
			if(dir == Direction.UP)
				continue;

			BlockState state = BlockUtils.getState(pos.relative(dir));

			if(state.getBlock().getExplosionResistance() < 600)
			{
				if(dir == Direction.DOWN)
					return false;

				air++;

				for(Direction dir2 : Direction.values())
				{
					if(dir2 == dir.getOpposite() || dir2 == Direction.UP)
						continue;

					BlockState state2 =
						BlockUtils.getState(pos.relative(dir).relative(dir2));

					if(state2.getBlock().getExplosionResistance() < 600)
						return false;
				}
			}
		}

		return air < 2;
	}
}

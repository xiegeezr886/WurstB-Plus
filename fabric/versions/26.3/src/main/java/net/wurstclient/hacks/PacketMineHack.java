/*
 * Copyright (c) 2014-2026 Wurst-Imperium and contributors.
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
import net.minecraft.network.protocol.game.ServerboundPlayerActionPacket;
import net.minecraft.network.protocol.game.ServerboundPlayerActionPacket.Action;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.wurstclient.Category;
import net.wurstclient.SearchTags;
import net.wurstclient.events.UpdateListener;
import net.wurstclient.hack.Hack;
import net.wurstclient.hack.HackConflictGroup;
import net.wurstclient.settings.CheckboxSetting;
import net.wurstclient.settings.SliderSetting;
import net.wurstclient.settings.SliderSetting.ValueDisplay;
import net.wurstclient.util.BlockUtils;

/**
 * Simplified port of Meteor's PacketMine module.
 *
 * <p>
 * Sends START_DESTROY_BLOCK and STOP_DESTROY_BLOCK packets for the block the
 * player is looking at, with a configurable tick delay between them. Unlike
 * Meteor's version there is no render overlay or queue of multiple blocks.
 */
@SearchTags({"packet mine", "packetmine", "fast mine", "no animation mining",
	"packet mining"})
public final class PacketMineHack extends Hack implements UpdateListener
{
	private final SliderSetting delay = new SliderSetting("Delay",
		"Ticks between the start and stop packets.", 2, 1, 20, 1,
		ValueDisplay.INTEGER.withSuffix(" ticks"));

	private final CheckboxSetting rotate = new CheckboxSetting("Rotate",
		"Sends rotation packets towards the block being mined.", true);

	private final CheckboxSetting autoTool = new CheckboxSetting("Auto tool",
		"Switches to the best tool while mining.", false);

	private final CheckboxSetting pauseOnUse = new CheckboxSetting(
		"Pause while using item", "Does not mine while an item is in use.",
		true);

	private BlockPos target;
	private Direction side;
	private int timer;
	private boolean mining;

	public PacketMineHack()
	{
		super("PacketMine");
		setCategory(Category.BLOCKS);
		addConflictGroup(HackConflictGroup.BLOCK_BREAKING_AUTOMATION);
		addSetting(delay);
		addSetting(rotate);
		addSetting(autoTool);
		addSetting(pauseOnUse);
	}

	@Override
	protected void onEnable()
	{
		EVENTS.add(UpdateListener.class, this);
		target = null;
		side = null;
		timer = 0;
		mining = false;
	}

	@Override
	protected void onDisable()
	{
		EVENTS.remove(UpdateListener.class, this);
		abortCurrent();
	}

	@Override
	public void onUpdate()
	{
		if(MC.player == null || MC.level == null || MC.gameMode == null)
			return;

		if(MC.gui.screen() != null || pauseOnUse.isChecked()
			&& MC.player.isUsingItem())
		{
			abortCurrent();
			return;
		}

		if(mining && target != null)
		{
			BlockState state = MC.level.getBlockState(target);
			if(state.isAir())
			{
				abortCurrent();
				return;
			}

			timer++;
			if(timer < delay.getValueI())
				return;

			if(rotate.isChecked())
				WURST.getRotationFaker()
					.faceVectorPacket(Vec3.atCenterOf(target));

			MC.getConnection().send(new ServerboundPlayerActionPacket(
				Action.STOP_DESTROY_BLOCK, target, side));

			target = null;
			side = null;
			timer = 0;
			mining = false;
			return;
		}

		HitResult hitResult = MC.hitResult;
		if(hitResult == null || hitResult.getType() != HitResult.Type.BLOCK
			|| !(hitResult instanceof BlockHitResult bHitResult))
			return;

		BlockPos pos = bHitResult.getBlockPos();
		BlockState state = MC.level.getBlockState(pos);

		if(state.isAir() || !BlockUtils.canBeClicked(pos)
			|| BlockUtils.isUnbreakable(pos))
			return;

		if(autoTool.isChecked())
			WURST.getHax().autoToolHack.equipIfEnabled(pos);

		if(rotate.isChecked())
			WURST.getRotationFaker().faceVectorPacket(Vec3.atCenterOf(pos));

		side = bHitResult.getDirection();
		target = pos;
		timer = 0;
		mining = true;

		MC.getConnection().send(new ServerboundPlayerActionPacket(
			Action.START_DESTROY_BLOCK, pos, side));
	}

	private void abortCurrent()
	{
		if(mining && target != null)
			MC.getConnection().send(new ServerboundPlayerActionPacket(
				Action.ABORT_DESTROY_BLOCK, target, side));

		target = null;
		side = null;
		timer = 0;
		mining = false;
	}

	@Override
	public String getRenderName()
	{
		return isEnabled() && mining ? getName() + " [Mining]" : getName();
	}
}

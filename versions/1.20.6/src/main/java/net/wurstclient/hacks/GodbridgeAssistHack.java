/*
 * Copyright (c) 2025 Soar Client contributors
 *
 * This source code is subject to the terms of the GNU Affero General Public
 * License, version 3. If a copy of the AGPL was not distributed with this
 * file, You can obtain one at: https://www.gnu.org/licenses/agpl-3.0.txt
 */
// Ported from Soar Client v7.1.2 (LGPL-3.0).
package net.wurstclient.hacks;

import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.phys.Vec3;
import net.wurstclient.Category;
import net.wurstclient.SearchTags;
import net.wurstclient.events.UpdateListener;
import net.wurstclient.hack.Hack;
import net.wurstclient.settings.CheckboxSetting;
import net.wurstclient.settings.SliderSetting;
import net.wurstclient.settings.SliderSetting.ValueDisplay;
import net.wurstclient.util.BlockPlacer;
import net.wurstclient.util.BlockPlacer.BlockPlacingParams;
import net.wurstclient.util.BlockUtils;
import net.wurstclient.util.Rotation;
import net.wurstclient.util.RotationUtils;

@SearchTags({"god bridge", "GodBridge", "bridging", "auto bridge", "scaffold"})
public final class GodbridgeAssistHack extends Hack implements UpdateListener
{
	private final SliderSetting placeDelay = new SliderSetting("Place delay",
		"Delay between block placements in ticks.\nLower = faster.", 1, 0, 5, 1,
		ValueDisplay.INTEGER.withSuffix(" ticks"));

	private final SliderSetting minPitch = new SliderSetting("Min pitch",
		"How far down the player has to look before blocks are placed.",
		60, 0, 90, 1, ValueDisplay.DEGREES);

	private final SliderSetting range = new SliderSetting("Range",
		"How far to reach when placing blocks.", 4.5, 1, 6, 0.05,
		ValueDisplay.DECIMAL);

	private final CheckboxSetting checkLineOfSight = new CheckboxSetting(
		"Check line of sight",
		"Makes sure that you don't reach through walls when placing blocks.",
		false);

	private int placementCooldown;

	public GodbridgeAssistHack()
	{
		super("GodbridgeAssist");
		setCategory(Category.BLOCKS);
		addSetting(placeDelay);
		addSetting(minPitch);
		addSetting(range);
		addSetting(checkLineOfSight);
	}

	@Override
	protected void onEnable()
	{
		placementCooldown = 0;
		EVENTS.add(UpdateListener.class, this);
	}

	@Override
	protected void onDisable()
	{
		EVENTS.remove(UpdateListener.class, this);
		placementCooldown = 0;
	}

	@Override
	public void onUpdate()
	{
		if(MC.player == null || MC.level == null || MC.gameMode == null)
			return;

		if(placementCooldown > 0)
		{
			placementCooldown--;
			return;
		}

		// must be bridging: holding right click with a block and looking down
		if(!MC.options.keyUse.isDown())
			return;
		if(MC.player.getXRot() < minPitch.getValueF())
			return;
		if(!(MC.player.getMainHandItem().getItem() instanceof BlockItem))
			return;
		if(!MC.player.onGround())
			return;

		BlockPos target = findTarget();
		if(target == null)
			return;

		BlockPlacingParams params = BlockPlacer.getBlockPlacingParams(target);
		if(params == null || params.distanceSq() > range.getValueSq())
			return;
		if(checkLineOfSight.isChecked() && !params.lineOfSight())
			return;

		Rotation rotation = RotationUtils.getNeededRotations(params.hitVec());
		rotation.applyToClientPlayer();
		rotation.sendPlayerLookPacket();

		InteractionResult result = IMC.getInteractionManager()
			.rightClickBlock(params.neighbor(), params.side(), params.hitVec());
		if(!result.consumesAction())
			return;

		((net.wurstclient.mixin.MinecraftAccessor)(Object)MC)
			.setRightClickDelay(4);
		placementCooldown = placeDelay.getValueI();
	}

	/**
	 * Finds the block that should be placed next. The player's movement is
	 * used to look a tick or two ahead, so that blocks get placed under the
	 * position the player is walking onto rather than under their feet.
	 */
	private BlockPos findTarget()
	{
		Vec3 velocity = MC.player.getDeltaMovement();

		for(int ticks = 1; ticks <= 2; ticks++)
		{
			BlockPos pos = BlockPos
				.containing(MC.player.position()
					.add(velocity.x * ticks, 0, velocity.z * ticks))
				.below();
			if(canPlaceAt(pos))
				return pos;
		}

		BlockPos below = BlockPos.containing(MC.player.position()).below();
		return canPlaceAt(below) ? below : null;
	}

	private boolean canPlaceAt(BlockPos pos)
	{
		return MC.level.isInWorldBounds(pos)
			&& BlockUtils.getState(pos).canBeReplaced();
	}
}

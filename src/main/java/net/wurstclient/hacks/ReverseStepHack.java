/*
 * Copyright (c) 2015-2026 CCBlueX
 * Copyright (c) 2025-2026 Penguin
 *
 * This source code is subject to the terms of the GNU General Public
 * License, version 3. If a copy of the GPL was not distributed with this
 * file, You can obtain one at: https://www.gnu.org/licenses/gpl-3.0.txt
 */
package net.wurstclient.hacks;

import java.util.Set;

import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.wurstclient.Category;
import net.wurstclient.SearchTags;
import net.wurstclient.events.UpdateListener;
import net.wurstclient.hack.Hack;
import net.wurstclient.settings.EnumSetting;
import net.wurstclient.settings.SliderSetting;
import net.wurstclient.settings.SliderSetting.ValueDisplay;
import net.wurstclient.util.BlockUtils;

@SearchTags({"reverse step", "fast fall", "down step"})
public final class ReverseStepHack extends Hack implements UpdateListener
{
	private static final Set<Block> DANGEROUS_LANDINGS = Set.of(Blocks.WATER,
		Blocks.COBWEB, Blocks.POWDER_SNOW, Blocks.HAY_BLOCK, Blocks.SLIME_BLOCK);

	private final EnumSetting<Mode> mode =
		new EnumSetting<>("Mode", Mode.values(), Mode.STRICT);
	private final SliderSetting motion = new SliderSetting("Motion", 1, 0.1, 5,
		0.1, ValueDisplay.DECIMAL).visibleWhen(
			() -> mode.getSelected() == Mode.STRICT);
	private final SliderSetting factor = new SliderSetting("Factor", 1.5, 1, 5,
		0.1, ValueDisplay.DECIMAL).visibleWhen(
			() -> mode.getSelected() == Mode.ACCELERATOR);
	private final SliderSetting maximumFallDistance = new SliderSetting(
		"Maximum fall distance", 3, 1, 50, 0.5,
		ValueDisplay.DECIMAL.withSuffix(" blocks"));

	private boolean initiatedJump;

	public ReverseStepHack()
	{
		super("ReverseStep");
		setCategory(Category.MOVEMENT);
		addSetting(mode);
		addSetting(motion);
		addSetting(factor);
		addSetting(maximumFallDistance);
	}

	@Override
	protected void onEnable()
	{
		initiatedJump = MC.player != null
			&& (MC.player.getDeltaMovement().y > 0 || MC.player.input.jumping);
		EVENTS.add(UpdateListener.class, this);
	}

	@Override
	protected void onDisable()
	{
		EVENTS.remove(UpdateListener.class, this);
		initiatedJump = false;
	}

	@Override
	public void onUpdate()
	{
		if(MC.player == null || MC.level == null)
			return;

		if(MC.player.onGround())
		{
			initiatedJump = false;
			return;
		}
		if(MC.player.input.jumping || MC.player.getDeltaMovement().y > 0)
			initiatedJump = true;

		if(initiatedJump || !canAccelerateFall())
			return;

		Vec3 velocity = MC.player.getDeltaMovement();
		double nextY = mode.getSelected() == Mode.STRICT ? -motion.getValue()
			: velocity.y < 0 ? velocity.y * factor.getValue() : velocity.y;
		if(nextY < velocity.y)
			MC.player.setDeltaMovement(velocity.x, nextY, velocity.z);
	}

	private boolean canAccelerateFall()
	{
		if(MC.player.isInWater() || MC.player.isInLava()
			|| MC.player.onClimbable() || MC.player.isFallFlying()
			|| MC.player.isPassenger() || MC.player.isSpectator()
			|| MC.player.getAbilities().flying
			|| MC.player.fallDistance > maximumFallDistance.getValueF())
			return false;

		double distance = maximumFallDistance.getValue();
		AABB sweptBox = MC.player.getBoundingBox().expandTowards(0, -distance, 0);
		if(BlockUtils.getBlockCollisions(sweptBox).findAny().isEmpty())
			return false;

		AABB playerBox = MC.player.getBoundingBox();
		Vec3 start = new Vec3(playerBox.getCenter().x, playerBox.minY,
			playerBox.getCenter().z);
		BlockHitResult landing = BlockUtils.raycast(start,
			start.add(0, -distance - 0.1, 0));
		if(landing.getType() != HitResult.Type.BLOCK)
			return false;

		return !DANGEROUS_LANDINGS.contains(
			MC.level.getBlockState(landing.getBlockPos()).getBlock());
	}

	private enum Mode
	{
		STRICT("Strict"),
		ACCELERATOR("Accelerator");

		private final String name;

		Mode(String name)
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

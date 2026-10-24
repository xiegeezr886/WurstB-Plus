/*
 * Copyright (c) 2014-2025 Wurst-Imperium and contributors.
 *
 * This source code is subject to the terms of the GNU General Public
 * License, version 3. If a copy of the GPL was not distributed with this
 * file, You can obtain one at: https://www.gnu.org/licenses/gpl-3.0.txt
 */
/*
 * Ported from Meteor Client (GPL-3.0).
 */
package net.wurstclient.hacks;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.protocol.game.ServerboundMovePlayerPacket;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemUseAnimation;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.wurstclient.Category;
import net.wurstclient.SearchTags;
import net.wurstclient.events.UpdateListener;
import net.wurstclient.hack.Hack;
import net.wurstclient.util.RenderUtils;
import net.wurstclient.util.Rotation;

@SearchTags({"click tp", "teleport", "click teleport"})
public final class ClickTPHack extends Hack implements UpdateListener
{
	public ClickTPHack()
	{
		super("ClickTP");
		setCategory(Category.MOVEMENT);
	}

	@Override
	protected void onEnable()
	{
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

		if(MC.player.getInventory().getSelectedItem()
			.getUseAnimation() != ItemUseAnimation.NONE)
			return;

		if(!MC.options.keyUse.isDown())
			return;

		if(MC.hitResult != null)
		{
			if(MC.hitResult.getType() == HitResult.Type.ENTITY)
			{
				EntityHitResult entityHit = (EntityHitResult)MC.hitResult;
				if(MC.player.interactOn(entityHit.getEntity(),
					InteractionHand.MAIN_HAND,
					entityHit.getLocation()) != InteractionResult.PASS)
					return;
			}

			if(MC.hitResult.getType() == HitResult.Type.BLOCK
				&& MC.player.getMainHandItem()
					.getItem() instanceof BlockItem)
				return;
		}

		Vec3 cameraPos = RenderUtils.getCameraPos();
		Rotation cameraRotation = RenderUtils.getCameraRotation();
		Vec3 direction = Vec3.directionFromRotation(cameraRotation.pitch(),
			cameraRotation.yaw()).scale(210);
		Vec3 targetPos = cameraPos.add(direction);

		ClipContext context = new ClipContext(cameraPos, targetPos,
			ClipContext.Block.OUTLINE, ClipContext.Fluid.NONE, MC.player);
		BlockHitResult hitResult = MC.level.clip(context);

		if(hitResult.getType() != HitResult.Type.BLOCK)
			return;

		BlockPos pos = hitResult.getBlockPos();
		Direction side = hitResult.getDirection();

		if(MC.level.getBlockState(pos).useWithoutItem(MC.level, MC.player,
			hitResult) != InteractionResult.PASS)
			return;

		BlockState state = MC.level.getBlockState(pos);
		VoxelShape shape = state.getCollisionShape(MC.level, pos);
		if(shape.isEmpty())
			shape = state.getShape(MC.level, pos);
		double height = shape.isEmpty() ? 1 : shape.max(Direction.Axis.Y);

		Vec3 newPos = new Vec3(pos.getX() + 0.5 + side.getStepX(),
			pos.getY() + height, pos.getZ() + 0.5 + side.getStepZ());
		int packetsRequired =
			(int)Math.ceil(MC.player.position().distanceTo(newPos) / 10) - 1;
		if(packetsRequired > 19)
			packetsRequired = 0;

		for(int packetNumber = 0; packetNumber < packetsRequired;
			packetNumber++)
			MC.player.connection.send(
				new ServerboundMovePlayerPacket.StatusOnly(true, true));

		MC.player.connection.send(new ServerboundMovePlayerPacket.Pos(
			newPos.x, newPos.y, newPos.z, true, true));
		MC.player.setPos(newPos);
	}
}

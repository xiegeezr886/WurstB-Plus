/*
 * Adapted from Meteor Client. Copyright (c) Meteor Development.
 * SPDX-License-Identifier: GPL-3.0
 */
package net.wurstclient.hacks;

import net.minecraft.core.BlockPos;
import net.minecraft.network.protocol.game.ServerboundMovePlayerPacket;
import net.minecraft.network.protocol.game.ServerboundMoveVehiclePacket;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.border.WorldBorder;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.wurstclient.Category;
import net.wurstclient.SearchTags;
import net.wurstclient.events.PacketOutputListener;
import net.wurstclient.hack.Hack;
import net.wurstclient.settings.BlockListSetting;
import net.wurstclient.settings.CheckboxSetting;

@SearchTags({"collisions", "block collision", "unloaded chunks", "方块碰撞"})
public final class CollisionsHack extends Hack implements PacketOutputListener
{
	private final BlockListSetting blocks = new BlockListSetting("Blocks",
		"Adds a full collision box to the selected blocks. Fluids are excluded.");
	private final CheckboxSetting magma = new CheckboxSetting("Magma",
		"Prevents walking onto magma blocks unless sneaking.", false);
	private final CheckboxSetting unloadedChunks = new CheckboxSetting("Unloaded chunks",
		"Stops movement into chunks the client has not loaded.", false);
	private final CheckboxSetting ignoreBorder = new CheckboxSetting("Ignore border",
		"Removes client-side world border collisions.", false);

	public CollisionsHack()
	{
		super("Collisions");
		setCategory(Category.MOVEMENT);
		addSetting(blocks);
		addSetting(magma);
		addSetting(unloadedChunks);
		addSetting(ignoreBorder);
	}

	@Override
	protected void onEnable()
	{
		EVENTS.add(PacketOutputListener.class, this);
	}

	@Override
	protected void onDisable()
	{
		EVENTS.remove(PacketOutputListener.class, this);
	}

	public VoxelShape collisionShape(BlockState state, BlockGetter world,
		BlockPos pos, VoxelShape original)
	{
		if(!isEnabled() || MC.player == null || MC.level == null
			|| world != MC.level || !state.getFluidState().isEmpty())
			return original;
		if(blocks.contains(state.getBlock()) || magma.isChecked()
			&& !MC.player.isShiftKeyDown() && state.isAir()
			&& world.getBlockState(pos.below()).is(Blocks.MAGMA_BLOCK))
			return Shapes.block();
		return original;
	}

	public boolean ignoresBorder(WorldBorder border)
	{
		return isEnabled() && ignoreBorder.isChecked() && MC.player != null
			&& MC.level != null && border == MC.level.getWorldBorder();
	}

	private boolean guardsChunks()
	{
		return isEnabled() && unloadedChunks.isChecked()
			&& MC.player != null && MC.level != null;
	}

	private boolean loaded(double x, double z)
	{
		return MC.level.getChunkSource().hasChunk(Mth.floor(x) >> 4, Mth.floor(z) >> 4);
	}

	public Vec3 limitMovement(Entity entity, Vec3 movement)
	{
		if(!guardsChunks() || entity != MC.player
			&& (entity != MC.player.getVehicle() || entity.getControllingPassenger() != MC.player)
			|| loaded(entity.getX() + movement.x, entity.getZ() + movement.z))
			return movement;
		return new Vec3(0, movement.y, 0);
	}

	@Override
	public void onSentPacket(PacketOutputEvent event)
	{
		if(!guardsChunks())
			return;
		if(event.getPacket() instanceof ServerboundMovePlayerPacket packet)
		{
			if(!loaded(packet.getX(MC.player.getX()), packet.getZ(MC.player.getZ())))
				event.cancel();
		}else if(event.getPacket() instanceof ServerboundMoveVehiclePacket packet)
		{
			Entity vehicle = MC.player.getVehicle();
			if(vehicle != null && !loaded(packet.getX(), packet.getZ()))
			{
				vehicle.setPos(vehicle.xo, vehicle.yo, vehicle.zo);
				event.cancel();
			}
		}
	}
}

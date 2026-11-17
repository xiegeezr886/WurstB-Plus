/* Adapted from Meteor Client. Copyright (c) Meteor Development.
 * SPDX-License-Identifier: GPL-3.0 */
package net.wurstclient.mixin;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.CollisionGetter;
import net.minecraft.world.level.BlockCollisions;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.wurstclient.WurstClient;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(BlockCollisions.class)
public abstract class CollisionsShapeMixin
{
	@WrapOperation(method = "computeNext", at = @At(value = "INVOKE",
		target = "Lnet/minecraft/world/level/block/state/BlockState;getCollisionShape(Lnet/minecraft/world/level/BlockGetter;Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/phys/shapes/CollisionContext;)Lnet/minecraft/world/phys/shapes/VoxelShape;"))
	private VoxelShape wurstCollisionShape(BlockState state, BlockGetter world, BlockPos pos, CollisionContext context, Operation<VoxelShape> original)
	{
		VoxelShape shape = original.call(state, world, pos, context);
		var hax = WurstClient.INSTANCE.getHax();
		return hax == null ? shape : hax.collisionsHack.collisionShape(state, world, pos, shape);
	}
}

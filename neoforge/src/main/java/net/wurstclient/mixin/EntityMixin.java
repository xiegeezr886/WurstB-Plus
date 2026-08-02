/*
 * Copyright (c) 2014-2025 Wurst-Imperium and contributors.
 *
 * This source code is subject to the terms of the GNU General Public
 * License, version 3. If a copy of the GPL was not distributed with this
 * file, You can obtain one at: https://www.gnu.org/licenses/gpl-3.0.txt
 */
package net.wurstclient.mixin;

import org.objectweb.asm.Opcodes;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import com.llamalad7.mixinextras.injector.v2.WrapWithCondition;
import net.minecraft.commands.CommandSource;
import net.minecraft.world.Nameable;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.entity.EntityAccess;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.wurstclient.WurstClient;
import net.wurstclient.event.EventManager;
import net.wurstclient.events.VelocityFromEntityCollisionListener.VelocityFromEntityCollisionEvent;
import net.wurstclient.events.VelocityFromFluidListener.VelocityFromFluidEvent;
import net.wurstclient.util.HitboxExpansionPolicy;

@Mixin(Entity.class)
public abstract class EntityMixin implements Nameable, EntityAccess, CommandSource
{
	@Inject(at = @At("HEAD"),
		method = "makeStuckInBlock(Lnet/minecraft/world/level/block/state/BlockState;Lnet/minecraft/world/phys/Vec3;)V",
		cancellable = true)
	private void onMakeStuckInBlock(BlockState state, Vec3 multiplier,
		CallbackInfo ci)
	{
		Entity self = (Entity)(Object)this;
		if(self != WurstClient.MC.player || WurstClient.INSTANCE.getHax() == null)
			return;
		if(WurstClient.INSTANCE.getHax().noSlowdownHack.isEnabled()
			&& WurstClient.INSTANCE.getHax().noSlowdownHack
				.shouldBypassStuckBlock(state))
			ci.cancel();
	}

	/**
	 * This mixin makes the VelocityFromFluidEvent work, which is used by
	 * AntiWaterPush. Forge can alter this invocation, so the injection remains
	 * optional.
	 */
	@WrapWithCondition(at = @At(value = "INVOKE",
		target = "Lnet/minecraft/world/entity/Entity;setDeltaMovement(Lnet/minecraft/world/phys/Vec3;)V",
		opcode = Opcodes.INVOKEVIRTUAL,
		ordinal = 0),
		method = "updateFluidHeightAndDoFluidPushing(Lnet/minecraft/tags/TagKey;D)Z",
		require = 0)
	private boolean shouldSetVelocity(Entity instance, Vec3 velocity)
	{
		VelocityFromFluidEvent event = new VelocityFromFluidEvent(instance);
		EventManager.fire(event);
		return !event.isCancelled();
	}
	
	@Inject(at = @At("HEAD"),
		method = "Lnet/minecraft/world/entity/Entity;push(Lnet/minecraft/world/entity/Entity;)V",
		cancellable = true)
	private void onPushAwayFrom(Entity entity, CallbackInfo ci)
	{
		VelocityFromEntityCollisionEvent event =
			new VelocityFromEntityCollisionEvent((Entity)(Object)this);
		EventManager.fire(event);
		
		if(event.isCancelled())
			ci.cancel();
	}
	
	/**
	 * Makes invisible entities render as ghosts if TrueSight is enabled.
	 */
	@Inject(at = @At("RETURN"),
		method = "Lnet/minecraft/world/entity/Entity;isInvisibleTo(Lnet/minecraft/world/entity/player/Player;)Z",
		cancellable = true)
	private void onIsInvisibleTo(Player player,
		CallbackInfoReturnable<Boolean> cir)
	{
		// Return early if the entity is not invisible
		if(!cir.getReturnValueZ())
			return;
		
		if(WurstClient.INSTANCE.getHax().trueSightHack
			.shouldBeVisible((Entity)(Object)this))
			cir.setReturnValue(false);
	}

	@Inject(at = @At("RETURN"),
		method = "getBoundingBox()Lnet/minecraft/world/phys/AABB;",
		cancellable = true)
	private void onGetBoundingBox(CallbackInfoReturnable<AABB> cir)
	{
		float extra = WurstClient.INSTANCE.getHax().hitboxesHack.getExtraSize();
		Entity self = (Entity)(Object)this;
		if(!HitboxExpansionPolicy.shouldExpand(self.level().isClientSide,
			self instanceof LivingEntity, self == WurstClient.MC.player, extra))
			return;

		cir.setReturnValue(cir.getReturnValue().inflate(extra));
	}
}

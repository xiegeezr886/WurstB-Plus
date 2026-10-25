/*
 * Copyright (c) 2014-2026 Wurst-Imperium and contributors.
 *
 * This source code is subject to the terms of the GNU General Public
 * License, version 3. If a copy of the GPL was not distributed with this
 * file, You can obtain one at: https://www.gnu.org/licenses/gpl-3.0.txt
 */
package net.wurstclient.mixin;

import net.minecraft.world.phys.Vec3;
import net.wurstclient.hack.HackList;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;

import net.minecraft.commands.CommandSource;
import net.minecraft.world.Nameable;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.entity.EntityAccess;
import net.wurstclient.WurstClient;
import net.wurstclient.event.EventManager;
import net.wurstclient.events.VelocityFromEntityCollisionListener.VelocityFromEntityCollisionEvent;
import net.wurstclient.events.VelocityFromFluidListener.VelocityFromFluidEvent;

@Mixin(Entity.class)
public abstract class EntityMixin
	implements Nameable, EntityAccess, CommandSource
{
	/**
	 * This mixin makes the VelocityFromFluidEvent work, which is used by
	 * AntiWaterPush.
	 */
	@WrapOperation(method = "updateFluidInteraction()Z",
		at = @At(value = "INVOKE",
			target = "Lnet/minecraft/world/entity/Entity;isPushedByFluid()Z",
			ordinal = 0),
		// require=0: NeoForge's patched Entity no longer calls isPushedByFluid()
		// from updateFluidInteraction() (fluid system reworked into
		// EntityFluidInteraction), so the injection has no target there and
		// would crash startup without this. Vanilla/Fabric still call it.
		require = 0)
	private boolean wrapUpdateFluidInteractionIsPushedByFluid(Entity instance,
		Operation<Boolean> original)
	{
		VelocityFromFluidEvent event = new VelocityFromFluidEvent(instance);
		EventManager.fire(event);
		
		if(event.isCancelled())
			return false;
		
		return original.call(instance);
	}
	
	@Inject(method = "push(Lnet/minecraft/world/entity/Entity;)V",
		at = @At("HEAD"),
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
	@Inject(
		method = "isInvisibleTo(Lnet/minecraft/world/entity/player/Player;)Z",
		at = @At("RETURN"),
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

	@Inject(method = "setDeltaMovement(DDD)V", at = @At("HEAD"))
	private void soar$updateDamageTilt(double x, double y, double z,
		CallbackInfo ci)
	{
		net.minecraft.world.entity.Entity self =
			(net.minecraft.world.entity.Entity)(Object)this;
		if(!(self instanceof net.minecraft.world.entity.player.Player player)
			|| player != WurstClient.MC.player)
			return;

		HackList hax = WurstClient.INSTANCE.getHax();
		if(hax == null || !hax.damageTiltHack.isEnabled())
			return;

		Vec3 motion = player.getDeltaMovement();
		float hurtDir = (float)(Math.toDegrees(
			Math.atan2(motion.z - z, motion.x - x)) - player.getYRot());
		if(Float.isFinite(hurtDir))
			player.animateHurt(hurtDir);
	}

}

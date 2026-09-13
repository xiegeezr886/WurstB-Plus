/*
 * Copyright (c) 2014-2025 Wurst-Imperium and contributors.
 *
 * This source code is subject to the terms of the GNU General Public
 * License, version 3. If a copy of the GPL was not distributed with this
 * file, You can obtain one at: https://www.gnu.org/licenses/gpl-3.0.txt
 */
package net.wurstclient.mixin;

import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.multiplayer.MultiPlayerGameMode;
import net.minecraft.client.multiplayer.prediction.PredictiveAction;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.protocol.game.ServerboundPlayerActionPacket;
import net.minecraft.network.protocol.game.ServerboundPlayerActionPacket.Action;
import net.minecraft.network.protocol.game.ServerboundUseItemOnPacket;
import net.minecraft.network.protocol.game.ServerboundUseItemPacket;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.wurstclient.WurstClient;
import net.wurstclient.event.EventManager;
import net.wurstclient.events.BlockBreakingProgressListener.BlockBreakingProgressEvent;
import net.wurstclient.events.PlayerAttacksEntityListener.PlayerAttacksEntityEvent;
import net.wurstclient.events.StopUsingItemListener.StopUsingItemEvent;
import net.wurstclient.mixinterface.IClientPlayerInteractionManager;
import net.wurstclient.mixinterface.IMultiPlayerGameMode;

@Mixin(MultiPlayerGameMode.class)
public abstract class ClientPlayerInteractionManagerMixin
	implements IClientPlayerInteractionManager, IMultiPlayerGameMode
{
	@Shadow
	@Final
	private Minecraft minecraft;
	@Shadow
	private boolean isDestroying;
	@Shadow
	private float destroyProgress;

	@Inject(at = @At("HEAD"),
		method = "attack(Lnet/minecraft/world/entity/player/Player;Lnet/minecraft/world/entity/Entity;)V")
	private void onAttackEntity(Player player, Entity target, CallbackInfo ci)
	{
		if(player == minecraft.player)
			EventManager.fire(new PlayerAttacksEntityEvent(target));
	}
	
	@Inject(at = @At(value = "INVOKE",
		target = "Lnet/minecraft/client/player/LocalPlayer;getId()I",
		ordinal = 0),
		method = "continueDestroyBlock(Lnet/minecraft/core/BlockPos;Lnet/minecraft/core/Direction;)Z")
	private void onPlayerDamageBlock(BlockPos pos, Direction direction,
		CallbackInfoReturnable<Boolean> cir)
	{
		EventManager.fire(new BlockBreakingProgressEvent(pos, direction));
	}
	
	@Inject(at = @At("HEAD"),
		method = "releaseUsingItem(Lnet/minecraft/world/entity/player/Player;)V")
	private void onStopUsingItem(Player player, CallbackInfo ci)
	{
		EventManager.fire(StopUsingItemEvent.INSTANCE);
	}
	
	@Override
	public void windowClick_PICKUP(int slot)
	{
		minecraft.player.containerMenu.clicked(slot, 0, ContainerInput.PICKUP,
			minecraft.player);
	}
	
	@Override
	public void windowClick_QUICK_MOVE(int slot)
	{
		minecraft.player.containerMenu.clicked(slot, 0,
			ContainerInput.QUICK_MOVE,
			minecraft.player);
	}
	
	@Override
	public void windowClick_THROW(int slot)
	{
		minecraft.player.containerMenu.clicked(slot, 1, ContainerInput.THROW,
			minecraft.player);
	}
	
	@Override
	public void windowClick_SWAP(int from, int to)
	{
		minecraft.player.containerMenu.clicked(from, to, ContainerInput.SWAP,
			minecraft.player);
	}
	
	@Override
	public void rightClickItem()
	{
		useItem(minecraft.player, InteractionHand.MAIN_HAND);
	}
	
	@Override
	public InteractionResult rightClickBlock(BlockPos pos, Direction side,
		Vec3 hitVec)
	{
		BlockHitResult hitResult = new BlockHitResult(hitVec, side, pos, false);
		InteractionHand hand = InteractionHand.MAIN_HAND;
		InteractionResult result = useItemOn(minecraft.player, hand, hitResult);
		return result.consumesAction() ? result : useItem(minecraft.player, hand);
	}
	
	@Override
	public float getDestroyProgress()
	{
		return destroyProgress;
	}
	
	@Override
	public void setDestroying(boolean destroying)
	{
		isDestroying = destroying;
	}
	
	@Override
	public void setDestroyProgress(float progress)
	{
		destroyProgress = progress;
	}
	
	@Override
	public void sendPlayerActionC2SPacket(Action action, BlockPos blockPos,
		Direction direction)
	{
		startPrediction(minecraft.level,
			i -> new ServerboundPlayerActionPacket(action, blockPos, direction, i));
	}
	
	@Override
	public void sendPlayerInteractBlockPacket(InteractionHand hand,
		BlockHitResult blockHitResult)
	{
		startPrediction(minecraft.level,
			i -> new ServerboundUseItemOnPacket(hand, blockHitResult, i));
	}

	@Override
	public void sendPlayerUseItemPacket(InteractionHand hand)
	{
		startPrediction(minecraft.level,
			i -> new ServerboundUseItemPacket(hand, i,
				minecraft.player.getYRot(), minecraft.player.getXRot()));
	}
	
	@Shadow
	private void startPrediction(ClientLevel world,
		PredictiveAction packetCreator)
	{
		
	}
	
	@Shadow
	public abstract InteractionResult useItemOn(LocalPlayer player,
		InteractionHand hand, BlockHitResult hitResult);
	
	@Shadow
	public abstract InteractionResult useItem(Player player,
		InteractionHand hand);
}

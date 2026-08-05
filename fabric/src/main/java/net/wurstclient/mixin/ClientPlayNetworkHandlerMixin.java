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
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.toasts.SystemToast;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.network.Connection;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockUpdatePacket;
import net.minecraft.network.protocol.game.ClientboundLevelChunkPacketData;
import net.minecraft.network.protocol.game.ClientboundSectionBlocksUpdatePacket;
import net.minecraft.network.protocol.game.ClientboundServerDataPacket;
import net.wurstclient.WurstClient;
import net.wurstclient.event.EventManager;
import net.wurstclient.events.PacketOutputListener.PacketOutputEvent;
import net.wurstclient.util.ChatUtils;

@Mixin(ClientPacketListener.class)
public abstract class ClientPlayNetworkHandlerMixin
	implements ClientGamePacketListener
{
	@Shadow
	@Final
	private Minecraft minecraft;
	
	@WrapOperation(at = @At(value = "INVOKE",
		target = "Lnet/minecraft/network/Connection;send(Lnet/minecraft/network/protocol/Packet;)V"),
		method = "send(Lnet/minecraft/network/protocol/Packet;)V")
	private void wrapSendPacket(Connection connection, Packet<?> packet,
		Operation<Void> original)
	{
		PacketOutputEvent event = new PacketOutputEvent(packet);
		EventManager.fire(event);
		
		if(!event.isCancelled())
		{
			original.call(connection, event.getPacket());
			event.notifySent();
		}
	}
	
	@Inject(at = @At("TAIL"),
		method = "handleServerData(Lnet/minecraft/network/protocol/game/ClientboundServerDataPacket;)V")
	public void onOnServerMetadata(ClientboundServerDataPacket packet,
		CallbackInfo ci)
	{
		WurstClient wurst = WurstClient.INSTANCE;
		if(!wurst.isEnabled())
			return;
		
		// Remove Mojang's dishonest warning toast on safe servers
		if(!packet.enforcesSecureChat())
		{
			removeUnsecureServerWarningToast();
			return;
		}
		
		// Add an honest warning toast on unsafe servers
		MutableComponent title = Component.literal(ChatUtils.WURST_PREFIX
			+ wurst.translate("toast.wurst.nochatreports.unsafe_server.title"));
		MutableComponent message = Component.literal(
			wurst.translate("toast.wurst.nochatreports.unsafe_server.message"));
		
		SystemToast systemToast = SystemToast.multiline(minecraft,
			SystemToast.SystemToastIds.UNSECURE_SERVER_WARNING, title, message);
		minecraft.getToasts().addToast(systemToast);
	}
	
	@Unique
	private void removeUnsecureServerWarningToast()
	{
		@SuppressWarnings("rawtypes")
		java.util.Deque queued =
			((net.wurstclient.mixin.ToastComponentAccessor)(Object)minecraft
				.getToasts()).getQueued();
		java.util.Iterator<?> it = queued.iterator();
		while(it.hasNext())
		{
			Object instance = it.next();
			try
			{
				java.lang.reflect.Method getToken =
					instance.getClass().getMethod("getToken");
				Object token = getToken.invoke(instance);
				if(token == SystemToast.SystemToastIds.UNSECURE_SERVER_WARNING)
					it.remove();
			}catch(ReflectiveOperationException e)
			{
				// ignore
			}
		}
	}
	
	@Inject(at = @At("TAIL"),
		method = "updateLevelChunk(IILnet/minecraft/network/protocol/game/ClientboundLevelChunkPacketData;)V")
	private void onLoadChunk(int x, int z, ClientboundLevelChunkPacketData chunkData, CallbackInfo ci)
	{
		WurstClient.INSTANCE.getHax().newChunksHack.afterLoadChunk(x, z);
	}
	
	@Inject(at = @At("TAIL"),
		method = "handleBlockUpdate(Lnet/minecraft/network/protocol/game/ClientboundBlockUpdatePacket;)V")
	private void onOnBlockUpdate(ClientboundBlockUpdatePacket packet, CallbackInfo ci)
	{
		WurstClient.INSTANCE.getHax().newChunksHack
			.afterUpdateBlock(packet.getPos());
	}
	
	@Inject(at = @At("TAIL"),
		method = "handleChunkBlocksUpdate(Lnet/minecraft/network/protocol/game/ClientboundSectionBlocksUpdatePacket;)V")
	private void onOnChunkDeltaUpdate(ClientboundSectionBlocksUpdatePacket packet,
		CallbackInfo ci)
	{
		packet.runUpdates(
			(pos, state) -> WurstClient.INSTANCE.getHax().newChunksHack
				.afterUpdateBlock(pos));
	}
}

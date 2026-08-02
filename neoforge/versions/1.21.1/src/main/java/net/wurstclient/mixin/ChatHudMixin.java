/*
 * Copyright (c) 2014-2025 Wurst-Imperium and contributors.
 *
 * This source code is subject to the terms of the GNU General Public
 * License, version 3. If a copy of the GPL was not distributed with this
 * file, You can obtain one at: https://www.gnu.org/licenses/gpl-3.0.txt
 */
package net.wurstclient.mixin;

import java.util.List;

import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import net.minecraft.client.GuiMessage;
import net.minecraft.client.GuiMessageTag;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.ChatComponent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MessageSignature;
import net.wurstclient.WurstClient;
import net.wurstclient.event.EventManager;
import net.wurstclient.events.ChatInputListener.ChatInputEvent;
import net.wurstclient.other_feature.OtfList;

@Mixin(ChatComponent.class)
public class ChatHudMixin
{
	@Shadow
	@Final
	private List<GuiMessage.Line> trimmedMessages;
	@Shadow
	@Final
	private Minecraft minecraft;
	
	@Inject(at = @At("HEAD"),
		method = "addMessage(Lnet/minecraft/network/chat/Component;Lnet/minecraft/network/chat/MessageSignature;Lnet/minecraft/client/GuiMessageTag;)V",
		cancellable = true)
	private void onAddMessage(Component message,
		@Nullable MessageSignature signature,
		@Nullable GuiMessageTag indicator, CallbackInfo ci)
	{
		OtfList otfs = WurstClient.INSTANCE.getOtfs();
		if(otfs == null)
			return;

		ChatInputEvent event = new ChatInputEvent(message, trimmedMessages);
		
		EventManager.fire(event);
		if(event.isCancelled())
		{
			ci.cancel();
			return;
		}
		
		message = event.getComponent();
		indicator = otfs.noChatReportsOtf
			.modifyIndicator(message, signature, indicator);
		
		GuiMessage guiMessage = new GuiMessage(minecraft.gui.getGuiTicks(),
			message, signature, indicator);
		shadow$logChatMessage(guiMessage);
		shadow$addMessageToDisplayQueue(guiMessage);
		shadow$addMessageToQueue(guiMessage);
		
		ci.cancel();
	}
	
	@Shadow
	private void shadow$logChatMessage(GuiMessage message)
	{
		
	}
	
	@Shadow
	private void shadow$addMessageToDisplayQueue(GuiMessage message)
	{
		
	}

	@Shadow
	private void shadow$addMessageToQueue(GuiMessage message)
	{
		
	}
}

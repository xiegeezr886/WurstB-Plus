/*
 * Copyright (c) 2014-2026 Wurst-Imperium and contributors.
 *
 * This source code is subject to the terms of the GNU General Public
 * License, version 3. If a copy of the GPL was not distributed with this
 * file, You can obtain one at: https://www.gnu.org/licenses/gpl-3.0.txt
 */
package net.wurstclient.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.network.chat.LocalChatSession;
import net.minecraft.network.chat.SignedMessageChain;

@Mixin(ClientPacketListener.class)
public interface ClientPacketListenerAccessor
{
	@Accessor("chatSession")
	void setChatSession(LocalChatSession chatSession);
	
	@Accessor("chatSession")
	LocalChatSession getChatSession();
	
	@Accessor("signedMessageEncoder")
	void setSignedMessageEncoder(SignedMessageChain.Encoder encoder);
}

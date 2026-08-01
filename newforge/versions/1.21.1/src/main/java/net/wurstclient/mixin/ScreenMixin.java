/*
 * Copyright (c) 2014-2025 Wurst-Imperium and contributors.
 *
 * This source code is subject to the terms of the GNU General Public
 * License, version 3. If a copy of the GPL was not distributed with this
 * file, You can obtain one at: https://www.gnu.org/licenses/gpl-3.0.txt
 */
package net.wurstclient.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Renderable;
import net.minecraft.client.gui.components.events.AbstractContainerEventHandler;
import net.minecraft.client.gui.screens.Screen;
import net.wurstclient.WurstClient;

@Mixin(Screen.class)
public abstract class ScreenMixin extends AbstractContainerEventHandler
	implements Renderable
{
	@Inject(at = @At("HEAD"),
		method = "renderBackground(Lnet/minecraft/client/gui/GuiGraphics;IIF)V",
		cancellable = true)
	public void onRenderBackground(GuiGraphics context, int mouseX, int mouseY,
		float partialTick, CallbackInfo ci)
	{
		if(WurstClient.INSTANCE.getHax().noBackgroundHack
			.shouldCancelBackground((Screen)(Object)this))
			ci.cancel();
	}
	
	@Inject(at = @At("HEAD"),
		method = "renderBlurredBackground",
		cancellable = true)
	public void onRenderBlurredBackground(float partialTick, CallbackInfo ci)
	{
		Screen screen = (Screen)(Object)this;
		
		if(screen.getMinecraft().level != null
			&& screen.getClass().getName().startsWith("net.wurstclient."))
			ci.cancel();
	}
}

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
import net.minecraft.client.gui.screens.PauseScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.wurstclient.WurstClient;
import net.wurstclient.gui.ButtonStyleRenderer;
import net.wurstclient.gui.MainMenuBackground;

@Mixin(Screen.class)
public abstract class ScreenMixin extends AbstractContainerEventHandler
	implements Renderable
{
	@Inject(at = @At("HEAD"),
		method = "renderBackground(Lnet/minecraft/client/gui/GuiGraphics;)V",
		cancellable = true)
	public void onRenderBackground(GuiGraphics context, CallbackInfo ci)
	{
		Screen screen = (Screen)(Object)this;
		ButtonStyleRenderer.beginScreen(context, screen);

		if(WurstClient.INSTANCE.getHax().noBackgroundHack
			.shouldCancelBackground(screen))
		{
			ci.cancel();
			return;
		}

		if(screen instanceof TitleScreen || screen instanceof PauseScreen
			|| screen instanceof AbstractContainerScreen)
			return;

		MainMenuBackground.render(context, screen);
		ci.cancel();
	}

	@Inject(at = @At("HEAD"),
		method = "renderDirtBackground(Lnet/minecraft/client/gui/GuiGraphics;)V",
		cancellable = true)
	private void wurst$replaceDirtBackground(GuiGraphics graphics,
		CallbackInfo ci)
	{
		Screen screen = (Screen)(Object)this;
		ButtonStyleRenderer.beginScreen(graphics, screen);

		if(WurstClient.INSTANCE.getHax().noBackgroundHack
			.shouldCancelBackground(screen))
		{
			ci.cancel();
			return;
		}

		if(screen instanceof TitleScreen || screen instanceof PauseScreen
			|| screen instanceof AbstractContainerScreen)
			return;

		MainMenuBackground.render(graphics, screen);
		ci.cancel();
	}

	@Inject(at = @At("HEAD"),
		method = "render(Lnet/minecraft/client/gui/GuiGraphics;IIF)V")
	private void wurst$drawStyledButtons(GuiGraphics graphics, int mouseX,
		int mouseY, float partialTicks, CallbackInfo ci)
	{
		ButtonStyleRenderer.beginScreen(graphics, (Screen)(Object)this);
	}

	@Inject(at = @At("RETURN"),
		method = "render(Lnet/minecraft/client/gui/GuiGraphics;IIF)V")
	private void wurst$finishStyledButtons(GuiGraphics graphics, int mouseX,
		int mouseY, float partialTicks, CallbackInfo ci)
	{
		ButtonStyleRenderer.endScreen(graphics);
	}
}

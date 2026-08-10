/*
 * Copyright (c) 2014-2025 Wurst-Imperium and contributors.
 *
 * This source code is subject to the terms of the GNU General Public
 * License, version 3. If a copy of the GPL was not distributed with this
 * file, You can obtain one at: https://www.gnu.org/licenses/gpl-3.0.txt
 */
package net.wurstclient.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Renderable;
import net.minecraft.client.gui.components.events.AbstractContainerEventHandler;
import net.minecraft.client.gui.screens.Screen;
import net.wurstclient.WurstClient;
import net.wurstclient.gui.visual.VisualScreenMotion;

@Mixin(Screen.class)
public abstract class ScreenMixin extends AbstractContainerEventHandler
	implements Renderable
{
	@Shadow
	public int width;

	@Shadow
	public int height;

	@Unique
	private long wurst$openedAtNanos;

	@Unique
	private boolean wurst$motionPosePushed;

	@Inject(at = @At("RETURN"),
		method = "init(Lnet/minecraft/client/Minecraft;II)V")
	private void onInit(Minecraft minecraft, int width, int height,
		CallbackInfo ci)
	{
		wurst$openedAtNanos = System.nanoTime();
	}

	@Inject(at = @At("HEAD"),
		method = "renderWithTooltip(Lnet/minecraft/client/gui/GuiGraphics;IIF)V")
	private void beginScreenMotion(GuiGraphics graphics, int mouseX, int mouseY,
		float partialTicks, CallbackInfo ci)
	{
		Screen screen = (Screen)(Object)this;
		wurst$motionPosePushed = VisualScreenMotion.shouldAnimate(screen);
		if(!wurst$motionPosePushed)
			return;

		float progress = VisualScreenMotion.progress(wurst$openedAtNanos,
			System.nanoTime());
		float scale = VisualScreenMotion.scale(progress);
		graphics.pose().pushPose();
		graphics.pose().translate(width * 0.5F, height * 0.5F, 0);
		graphics.pose().scale(scale, scale, 1);
		graphics.pose().translate(width * -0.5F, height * -0.5F, 0);
	}

	@Inject(at = @At("RETURN"),
		method = "renderWithTooltip(Lnet/minecraft/client/gui/GuiGraphics;IIF)V")
	private void endScreenMotion(GuiGraphics graphics, int mouseX, int mouseY,
		float partialTicks, CallbackInfo ci)
	{
		if(!wurst$motionPosePushed)
			return;

		graphics.pose().popPose();
		wurst$motionPosePushed = false;
		float progress = VisualScreenMotion.progress(wurst$openedAtNanos,
			System.nanoTime());
		int veil = VisualScreenMotion.veilColor(progress);
		if(veil >>> 24 != 0)
			graphics.fill(0, 0, width, height, veil);
	}

	@Inject(at = @At("HEAD"),
		method = "renderBackground(Lnet/minecraft/client/gui/GuiGraphics;)V",
		cancellable = true)
	public void onRenderBackground(GuiGraphics context, CallbackInfo ci)
	{
		if(WurstClient.INSTANCE.getHax().noBackgroundHack
			.shouldCancelBackground((Screen)(Object)this))
			ci.cancel();
	}
}

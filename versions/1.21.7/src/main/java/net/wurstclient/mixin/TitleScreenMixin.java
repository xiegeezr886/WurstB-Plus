/*
 * Copyright (c) 2014-2026 Wurst-Imperium and contributors.
 *
 * This source code is subject to the terms of the GNU General Public
 * License, version 3. If a copy of the GPL was not distributed with this
 * file, You can obtain one at: https://www.gnu.org/licenses/gpl-3.0.txt
 */
package net.wurstclient.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import net.wurstclient.util.render.GuiGraphicsExtractor;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.network.chat.Component;
import net.wurstclient.WurstClient;
import net.wurstclient.gui.title.WurstTitleMenu;

@Mixin(TitleScreen.class)
public abstract class TitleScreenMixin extends Screen
{
	@Unique
	private WurstTitleMenu wurstPenguin$titleMenu;
	@Unique
	private boolean wurstPenguin$customMenu;

	private TitleScreenMixin(Component title)
	{
		super(title);
	}

	@Inject(method = "init()V", at = @At("RETURN"))
	private void onInit(CallbackInfo ci)
	{
		wurstPenguin$customMenu = WurstClient.INSTANCE.isEnabled()
			&& !minecraft.isDemo();
		if(!wurstPenguin$customMenu)
			return;

		clearWidgets();
		wurstPenguin$titleMenu =
			new WurstTitleMenu((TitleScreen)(Object)this);
		wurstPenguin$titleMenu.init(minecraft, width, height,
			this::addRenderableWidget);
	}

	@Inject(
		method = "render(Lnet/minecraft/client/gui/GuiGraphics;IIF)V",
		at = @At("HEAD"),
		cancellable = true)
	private void onRender(GuiGraphics graphics, int mouseX,
		int mouseY, float partialTicks, CallbackInfo ci)
	{
		if(!wurstPenguin$customMenu || wurstPenguin$titleMenu == null)
			return;

		wurstPenguin$titleMenu.render(new GuiGraphicsExtractor(graphics),
			mouseX, mouseY, partialTicks,
			width, height);
		super.render(graphics, mouseX, mouseY, partialTicks);
		ci.cancel();
	}

	@Inject(at = @At("HEAD"),
		method = "getMultiplayerDisabledReason()Lnet/minecraft/network/chat/Component;",
		cancellable = true)
	private void onGetMultiplayerDisabledText(
		CallbackInfoReturnable<Component> cir)
	{
		cir.setReturnValue(null);
	}
}

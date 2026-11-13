/*
 * Copyright (c) 2014-2025 Wurst-Imperium and contributors.
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
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Renderable;
import net.minecraft.client.gui.components.events.AbstractContainerEventHandler;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.client.gui.screens.PauseScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.gui.screens.achievement.StatsScreen;
import net.minecraft.client.gui.screens.advancements.AdvancementsScreen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.inventory.AbstractSignEditScreen;
import net.minecraft.client.gui.screens.inventory.BookEditScreen;
import net.minecraft.client.gui.screens.inventory.BookViewScreen;
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

		if(shouldKeepVanillaBackground(screen))
			return;

		MainMenuBackground.render(context, screen);
		ci.cancel();
	}

	/**
	 * Gameplay and editor screens keep their own background. Painting the
	 * title-menu gradient over an inventory, book, sign or chat screen hides
	 * the world and makes those functional screens harder to use.
	 */
	@Unique
	private static boolean shouldKeepVanillaBackground(Screen screen)
	{
		if(screen instanceof TitleScreen || screen instanceof PauseScreen)
			return true;

		if(screen instanceof AbstractContainerScreen
			|| screen instanceof BookViewScreen
			|| screen instanceof BookEditScreen
			|| screen instanceof AbstractSignEditScreen
			|| screen instanceof ChatScreen || screen instanceof StatsScreen
			|| screen instanceof AdvancementsScreen)
			return true;

		String name = screen.getClass().getName();
		return name.startsWith("net.minecraft.client.gui.screens.inventory.")
			|| name.startsWith("net.wurstclient.clickgui2.screens.");
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
			ci.cancel();

		// Screens that explicitly ask for the dirt background (book offer
		// editor, vanilla menus without a world) keep that background.
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

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
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.scores.Objective;
import net.wurstclient.WurstClient;
import net.wurstclient.hack.HackList;
import net.wurstclient.hud2.HudManager;
import net.wurstclient.hud2.elements.ScoreboardHudElement;

@Mixin(Gui.class)
public class IngameHudMixin
{
	@Inject(at = @At("HEAD"), method = "displayScoreboardSidebar",
		cancellable = true)
	private void replaceScoreboardSidebar(GuiGraphics graphics,
		Objective objective, CallbackInfo ci)
	{
		HudManager hudManager = WurstClient.INSTANCE.getHudManager();
		if(hudManager != null && !WurstClient.MC.options.renderDebug
			&& hudManager.isElementEnabled(ScoreboardHudElement.ID))
			ci.cancel();
	}

	@Inject(at = @At("HEAD"),
		method = "renderTextureOverlay(Lnet/minecraft/client/gui/GuiGraphics;Lnet/minecraft/resources/ResourceLocation;F)V",
		cancellable = true)
	private void onRenderOverlay(GuiGraphics context, ResourceLocation texture,
		float opacity, CallbackInfo ci)
	{
		if(texture == null)
			return;
		
		String path = texture.getPath();
		HackList hax = WurstClient.INSTANCE.getHax();
		
		if("textures/misc/pumpkinblur.png".equals(path)
			&& hax.noPumpkinHack.isEnabled())
			ci.cancel();
		
		if("textures/misc/powder_snow_outline.png".equals(path)
			&& hax.noOverlayHack.isEnabled())
			ci.cancel();
	}
	
	@Inject(at = @At("HEAD"),
		method = "renderVignette",
		cancellable = true)
	private void onRenderVignetteOverlay(GuiGraphics context, Entity entity,
		CallbackInfo ci)
	{
		HackList hax = WurstClient.INSTANCE.getHax();
		if(hax == null || !hax.noVignetteHack.isEnabled())
			return;
		
		ci.cancel();
	}
}

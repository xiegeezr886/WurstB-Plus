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

import net.wurstclient.util.ScreenUtils;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.achievement.StatsScreen;
import net.minecraft.client.gui.screens.achievement.StatsUpdateListener;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.network.chat.Component;
import net.wurstclient.WurstClient;

@Mixin(StatsScreen.class)
public abstract class StatsScreenMixin extends Screen implements StatsUpdateListener
{
	private StatsScreenMixin(WurstClient wurst, Component title)
	{
		super(title);
	}
	
	@Inject(at = @At("TAIL"), method = "initButtons()V")
	private void onCreateButtons(CallbackInfo ci)
	{
		if(WurstClient.INSTANCE.getOtfs().disableOtf.shouldHideEnableButton())
			return;
		
		Button toggleWurstButton =
			Button.builder(Component.literal(""), this::toggleWurst)
				.bounds(width / 2 - 152, height - 28, 150, 20).build();
		
		updateWurstButtonText(toggleWurstButton);
		addRenderableWidget(toggleWurstButton);
		
		for(AbstractWidget button : ScreenUtils.getButtons(this))
		{
			if(!button.getMessage().getString()
				.equals(I18n.get("gui.done")))
				continue;
			
			button.setX(width / 2 + 2);
			button.setWidth(150);
		}
	}
	
	private void toggleWurst(Button button)
	{
		WurstClient wurst = WurstClient.INSTANCE;
		wurst.setEnabled(!wurst.isEnabled());
		
		updateWurstButtonText(button);
	}
	
	private void updateWurstButtonText(Button button)
	{
		WurstClient wurst = WurstClient.INSTANCE;
		String text = (wurst.isEnabled() ? "Disable" : "Enable") + " Wurst";
		button.setMessage(Component.literal(text));
	}
}

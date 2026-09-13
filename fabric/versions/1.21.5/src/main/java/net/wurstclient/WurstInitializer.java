/*
 * Copyright (c) 2014-2026 Wurst-Imperium and contributors.
 *
 * This source code is subject to the terms of the GNU General Public
 * License, version 3. If a copy of the GPL was not distributed with this
 * file, You can obtain one at: https://www.gnu.org/licenses/gpl-3.0.txt
 */
package net.wurstclient;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.wurstclient.event.EventManager;
import net.wurstclient.events.GUIRenderListener.GUIRenderEvent;

public final class WurstInitializer implements ClientModInitializer
{
	public static final String MOD_ID = WurstClient.MOD_ID;
	private static boolean initialized;
	
	@Override
	public void onInitializeClient()
	{
		// 注册 HUD 渲染钩子
		HudRenderCallback.EVENT.register((graphics, tickDelta) ->
		{
			if(!initialized
				|| Minecraft.getInstance().getDebugOverlay()
					.showDebugScreen())
				return;
			float partialTick = tickDelta.getGameTimeDeltaPartialTick(false);
			EventManager.fire(new GUIRenderEvent(graphics, partialTick));
		});
		
		// 初始化 WurstClient
		if(!initialized)
		{
			initialized = true;
			WurstClient.INSTANCE.initialize();
		}
	}
}

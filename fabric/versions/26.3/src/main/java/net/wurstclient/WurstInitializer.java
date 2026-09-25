/*
 * Copyright (c) 2014-2026 Wurst-Imperium and contributors.
 *
 * This source code is subject to the terms of the GNU General Public
 * License, version 3. If a copy of the GPL was not distributed with this
 * file, You can obtain one at: https://www.gnu.org/licenses/gpl-3.0.txt
 */
package net.wurstclient;

import net.fabricmc.api.ClientModInitializer;

public final class WurstInitializer implements ClientModInitializer
{
	public static final String MOD_ID = WurstClient.MOD_ID;
	private static boolean initialized;
	
	@Override
	public void onInitializeClient()
	{
		// GUIRenderEvent/RenderEvent 由 mixin 触发
		// (IngameHudMixin/GuiMixin/LevelRendererMixin)
		
		// 初始化 WurstClient
		if(!initialized)
		{
			initialized = true;
			WurstClient.INSTANCE.initialize();
		}
	}
}

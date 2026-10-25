/*
 * Copyright (c) 2014-2025 Wurst-Imperium and contributors.
 *
 * This source code is subject to the terms of the GNU General Public
 * License, version 3. If a copy of the GPL was not distributed with this
 * file, You can obtain one at: https://www.gnu.org/licenses/gpl-3.0.txt
 */
package net.wurstclient.hud;

import net.minecraft.client.gui.GuiGraphics;
import net.wurstclient.WurstClient;
import net.wurstclient.clickgui2.ClickGui;
import net.wurstclient.events.GUIRenderListener;
import net.wurstclient.hud2.nanovg.NanoVGUI;
import net.wurstclient.util.ScreenRegistry;

public final class IngameHUD implements GUIRenderListener
{
	private TabGui tabGui;
	
	@Override
	public void onRenderGUI(GuiGraphics context, float partialTicks)
	{
		if(!WurstClient.INSTANCE.isEnabled())
			return;
		
		if(tabGui == null)
			tabGui = new TabGui();
		
		ClickGui clickGui = WurstClient.INSTANCE.getGui();

		clickGui.updateColors();

		boolean drawTabGui = tabGui.isVisible();
		boolean drawPinned = !ScreenRegistry.isAny(WurstClient.MC.screen,
			ScreenRegistry.CLICK_GUI, ScreenRegistry.NAVIGATOR)
			&& clickGui.hasVisiblePinnedWindows();
		if(!drawTabGui && !drawPinned)
			return;

		NanoVGUI.pass(context, () -> {
			if(drawTabGui)
				tabGui.render(context, partialTicks);
			if(drawPinned)
				clickGui.renderPinnedWindows(context, partialTicks);
		});
	}
}

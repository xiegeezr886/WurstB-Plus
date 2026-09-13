/*
 * Adapted from BleachHack Window system (GPL-3.0)
 * Copyright (c) 2025 Penguin
 */
package net.wurstclient.clickgui2.window;

import net.minecraft.client.gui.GuiGraphicsExtractor;

public abstract class WindowWidget {

	public void render(GuiGraphicsExtractor drawContext, int wx, int wy,
		int mouseX, int mouseY, float partialTicks) {
		render(drawContext, wx, wy, mouseX, mouseY);
	}

	public void render(GuiGraphicsExtractor drawContext, int wx, int wy,
		int mouseX, int mouseY) {}

	public void mouseClicked(int wx, int wy, int mouseX, int mouseY,
		int button) {}

	public void mouseReleased(int wx, int wy, int mouseX, int mouseY,
		int button) {}

	public boolean shouldRender(int wx1, int wy1, int wx2, int wy2) {
		return true;
	}
}

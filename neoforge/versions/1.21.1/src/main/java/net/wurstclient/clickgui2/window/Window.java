/*
 * Adapted from BleachHack Window system (GPL-3.0)
 * Copyright (c) 2025 Penguin
 */
package net.wurstclient.clickgui2.window;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.world.item.ItemStack;
import net.wurstclient.WurstClient;
import net.wurstclient.clickgui2.FlatRenderer;
import net.wurstclient.clickgui2.theme.FlatTheme;
import net.wurstclient.hud2.nanovg.NanoVGUI;

import java.util.ArrayList;
import java.util.List;

public class Window {

	protected static final Minecraft MC = WurstClient.MC;

	public int x1, y1, x2, y2;
	public String title;
	public ItemStack icon;
	public boolean selected;

	private final List<WindowWidget> widgets = new ArrayList<>();
	protected boolean dragging;
	protected int dragOffX, dragOffY;

	public Window(int x1, int y1, int x2, int y2, String title,
		ItemStack icon) {
		this.x1 = x1;
		this.y1 = y1;
		this.x2 = x2;
		this.y2 = y2;
		this.title = title;
		this.icon = icon;
	}

	public List<WindowWidget> getWidgets() {
		return widgets;
	}

	public void addWidget(WindowWidget widget) {
		widgets.add(widget);
	}

	public void render(GuiGraphics drawContext, int mouseX, int mouseY,
		float partialTicks) {
		if(dragging) {
			x2 = (x2 - x1) + mouseX - dragOffX
				- Math.min(0, mouseX - dragOffX);
			y2 = (y2 - y1) + mouseY - dragOffY
				- Math.min(0, mouseY - dragOffY);
			x1 = Math.max(0, mouseX - dragOffX);
			y1 = Math.max(0, mouseY - dragOffY);
		}

		drawBackground(drawContext, mouseX, mouseY);

		for(WindowWidget w : widgets)
			if(w.shouldRender(x1, y1, x2, y2))
				w.render(drawContext, x1, y1, mouseX, mouseY);

		if(icon != null) {
			drawContext.pose().pushPose();
			drawContext.pose().translate(x1 + 2, y1 + 2, 0);
			drawContext.pose().scale(0.6f, 0.6f, 1f);
			NanoVGUI.item(drawContext, icon, 0, 0, false);
			drawContext.pose().popPose();
		}

		NanoVGUI.text(title, x1 + 14, y1 + 3, 0xFFFFFFFF, 9F);
	}

	protected void drawBackground(GuiGraphics drawContext, int mouseX,
		int mouseY) {
		FlatTheme theme = WurstClient.INSTANCE.getGui().getTheme();
		boolean focused = selected || isOver(mouseX, mouseY);
		FlatRenderer.drawWindowPanel(drawContext, x1, y1, x2, y2, 2,
			theme, focused);
		NanoVGUI.fill(x1 + 4, y1 + 13, x2 - 4, y1 + 14,
			theme.accent(focused ? 0.5F : 0.28F));
	}

	public boolean isOver(int mouseX, int mouseY) {
		return mouseX >= x1 && mouseX <= x2 && mouseY >= y1
			&& mouseY <= y2;
	}

	public void mouseClicked(double mouseX, double mouseY, int button) {
		if(mouseX >= x1 && mouseX <= x2 - 2 && mouseY >= y1
			&& mouseY <= y1 + 11) {
			dragging = true;
			dragOffX = (int)mouseX - x1;
			dragOffY = (int)mouseY - y1;
		}

		if(selected)
			for(WindowWidget w : widgets)
				if(w.shouldRender(x1, y1, x2, y2))
					w.mouseClicked(x1, y1, (int)mouseX, (int)mouseY,
						button);
	}

	public void mouseReleased(double mouseX, double mouseY, int button) {
		dragging = false;
		if(selected)
			for(WindowWidget w : widgets)
				if(w.shouldRender(x1, y1, x2, y2))
					w.mouseReleased(x1, y1, (int)mouseX, (int)mouseY,
						button);
	}

	public static void horizontalGradient(GuiGraphics g, int x1, int y1,
		int x2, int y2, int color1, int color2) {
		NanoVGUI.fillHorizontalGradient(x1, y1, x2, y2, color1, color2);
	}
}

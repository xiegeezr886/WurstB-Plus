/*
 * Adapted from BleachHack Window system (GPL-3.0)
 * Copyright (c) 2025 Penguin
 */
package net.wurstclient.clickgui2.window;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.world.item.ItemStack;
import net.wurstclient.WurstClient;
import net.wurstclient.clickgui2.FlatRenderer;
import net.wurstclient.clickgui2.theme.FlatTheme;

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
			drawContext.renderItem(icon, 0, 0);
			drawContext.pose().popPose();
		}

		drawContext.drawString(MC.font, title, x1 + 14, y1 + 3, -1);
	}

	protected void drawBackground(GuiGraphics drawContext, int mouseX,
		int mouseY) {
		FlatTheme theme = WurstClient.INSTANCE.getGui().getTheme();
		boolean focused = selected || isOver(mouseX, mouseY);
		FlatRenderer.drawWindowPanel(drawContext, x1, y1, x2, y2, 2,
			theme, focused);
		drawContext.fill(x1 + 4, y1 + 13, x2 - 4, y1 + 14,
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
		float a1 = (color1 >> 24 & 255) / 255F;
		float r1 = (color1 >> 16 & 255) / 255F;
		float g1 = (color1 >> 8 & 255) / 255F;
		float b1 = (color1 & 255) / 255F;
		float a2 = (color2 >> 24 & 255) / 255F;
		float r2 = (color2 >> 16 & 255) / 255F;
		float g2 = (color2 >> 8 & 255) / 255F;
		float b2 = (color2 & 255) / 255F;
		RenderSystem.enableBlend();
		RenderSystem.defaultBlendFunc();
		RenderSystem.setShader(GameRenderer::getPositionColorShader);
		BufferBuilder b = Tesselator.getInstance().begin(VertexFormat.Mode.QUADS,
			DefaultVertexFormat.POSITION_COLOR);
		b.addVertex(x1, y1, 0).setColor(r1, g1, b1, a1);
		b.addVertex(x1, y2, 0).setColor(r1, g1, b1, a1);
		b.addVertex(x2, y2, 0).setColor(r2, g2, b2, a2);
		b.addVertex(x2, y1, 0).setColor(r2, g2, b2, a2);
		BufferUploader.drawWithShader(b.buildOrThrow());
		RenderSystem.disableBlend();
	}
}

package net.wurstclient.clickgui2;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.network.chat.Component;

final class ScaledEditBox extends EditBox
{
	private final float scale;

	ScaledEditBox(Font font, int x, int y, int width, int height,
		Component message, float scale)
	{
		super(font, x, y, width, height, message);
		if(scale <= 0 || scale > 1)
			throw new IllegalArgumentException("Scale must be in (0, 1]");
		this.scale = scale;
	}

	@Override
	public void renderWidget(GuiGraphics graphics, int mouseX, int mouseY,
		float partialTicks)
	{
		int physicalWidth = getWidth();
		int physicalHeight = getHeight();
		setWidth((int)Math.ceil(physicalWidth / scale));
		graphics.pose().pushPose();
		graphics.pose().translate(getX(), getY(), 0);
		graphics.pose().scale(scale, scale, 1);
		graphics.pose().translate(-getX(), -getY(), 0);
		try
		{
			int scaledMouseX = scaleCoordinate(mouseX, getX());
			int scaledMouseY = scaleCoordinate(mouseY, getY());
			super.renderWidget(graphics, scaledMouseX, scaledMouseY,
				partialTicks);
		}finally
		{
			graphics.pose().popPose();
			setWidth(physicalWidth);
		}
	}

	@Override
	public void onClick(double mouseX, double mouseY)
	{
		double scaledMouseX = getX() + (mouseX - getX()) / scale;
		double scaledMouseY = getY() + (mouseY - getY()) / scale;
		super.onClick(scaledMouseX, scaledMouseY);
	}

	private int scaleCoordinate(int coordinate, int origin)
	{
		return Math.round(origin + (coordinate - origin) / scale);
	}
}

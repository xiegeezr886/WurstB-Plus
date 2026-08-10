package net.wurstclient.clickgui2;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.input.MouseButtonEvent;
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
	public void extractWidgetRenderState(GuiGraphicsExtractor graphics,
		int mouseX, int mouseY,
		float partialTicks)
	{
		int physicalWidth = getWidth();
		int physicalHeight = getHeight();
		setWidth((int)Math.ceil(physicalWidth / scale));
		setHeight((int)Math.ceil(physicalHeight / scale));
		graphics.pose().pushMatrix();
		graphics.pose().translate(getX(), getY());
		graphics.pose().scale(scale, scale);
		graphics.pose().translate(-getX(), -getY());
		try
		{
			int scaledMouseX = scaleCoordinate(mouseX, getX());
			int scaledMouseY = scaleCoordinate(mouseY, getY());
			super.extractWidgetRenderState(graphics, scaledMouseX, scaledMouseY,
				partialTicks);
		}finally
		{
			graphics.pose().popMatrix();
			setWidth(physicalWidth);
			setHeight(physicalHeight);
		}
	}

	@Override
	public void onClick(MouseButtonEvent event, boolean doubleClick)
	{
		double scaledMouseX = getX() + (event.x() - getX()) / scale;
		double scaledMouseY = getY() + (event.y() - getY()) / scale;
		super.onClick(new MouseButtonEvent(scaledMouseX, scaledMouseY,
			event.buttonInfo()), doubleClick);
	}

	private int scaleCoordinate(int coordinate, int origin)
	{
		return Math.round(origin + (coordinate - origin) / scale);
	}
}

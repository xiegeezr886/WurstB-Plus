package net.wurstclient.clickgui2.component;

import java.awt.Color;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.util.Mth;
import net.wurstclient.clickgui2.FlatRenderer;
import net.wurstclient.clickgui2.GuiIcon;
import net.wurstclient.clickgui2.supersoft.SuperSoftRenderer;
import net.wurstclient.clickgui2.supersoft.EpsilonMd3Theme;
import net.wurstclient.clickgui2.supersoft.SuperSoftTheme;
import net.wurstclient.clickgui2.supersoft.UiTween;
import net.wurstclient.settings.ColorSetting;

public final class ColorComponent extends ValueRowComponent
{
	private static final int HEADER_HEIGHT = 25;
	private static final int HUE_HEIGHT = 22;
	private static final int CHANNEL_HEIGHT = 16;
	private static final int CONTENT_PADDING = 5;
	private static final int CONTENT_HEIGHT = CONTENT_PADDING + HUE_HEIGHT + 4
		+ CHANNEL_HEIGHT * 4 + CONTENT_PADDING;
	private final ColorSetting colorSetting;
	private final UiTween expansionMotion = new UiTween(0, 200);
	private final UiTween arrowMotion = new UiTween(0, 200);
	private final UiTween[] thumbMotions = {new UiTween(1, 150),
		new UiTween(1, 150), new UiTween(1, 150), new UiTween(1, 150),
		new UiTween(1, 150)};
	private boolean expanded;
	private int draggingChannel = -1;

	public ColorComponent(ColorSetting colorSetting)
	{
		super(colorSetting);
		this.colorSetting = colorSetting;
	}

	@Override
	protected void renderSelf(GuiGraphics graphics, int mouseX, int mouseY,
		float partialTicks)
	{
		drawLabel(graphics, mouseX, mouseY);
		int swatchX = (int)(x + getWidth() - 28);
		int swatchY = (int)y + 6;
		FlatRenderer.fillRoundedRect(graphics, swatchX, swatchY,
			swatchX + 16, swatchY + 12, 2, colorSetting.getColor().getRGB());
		FlatRenderer.drawRoundedOutline(graphics, swatchX, swatchY,
			swatchX + 16, swatchY + 12, 2,
			usesSuperSoftTheme() ? SuperSoftTheme.BORDER : VapePalette.BORDER);
		GuiIcon.CHEVRON.drawRotated(graphics, (int)(x + getWidth()) - 11,
			(int)y + 9, 6,
			usesSuperSoftTheme() ? SuperSoftTheme.TEXT_SECONDARY : VapePalette.TEXT,
			arrowMotion.update(expanded ? 90 : 0));
		float expansion = expansionMotion.update(expanded ? 1 : 0);
		if(expansion <= 0.001F)
			return;
		int animatedBottom = (int)y + HEADER_HEIGHT
			+ Math.round(CONTENT_HEIGHT * expansion);
		graphics.enableScissor((int)x, (int)y + HEADER_HEIGHT,
			(int)(x + getWidth()), animatedBottom);
		graphics.fill((int)x, (int)y + HEADER_HEIGHT,
			(int)(x + getWidth()), (int)y + HEADER_HEIGHT + CONTENT_HEIGHT,
			EpsilonMd3Theme.SURFACE_CONTAINER);
		renderHue(graphics, mouseX, mouseY);
		for(int channel = 0; channel < 4; channel++)
			renderChannel(graphics, mouseX, mouseY, channel);
		if(draggingChannel >= 0)
			updateChannel(mouseX, draggingChannel);
		graphics.disableScissor();
	}

	private void renderHue(GuiGraphics graphics, int mouseX, int mouseY)
	{
		int left = (int)x + 5;
		int right = (int)(x + getWidth()) - 5;
		int top = (int)y + HEADER_HEIGHT + CONTENT_PADDING;
		for(int pixel = left; pixel < right; pixel++)
		{
			float hue = (pixel - left) / (float)Math.max(1, right - left - 1);
			graphics.fill(pixel, top, pixel + 1, top + HUE_HEIGHT,
				0xFF000000 | Color.HSBtoRGB(hue, 1, 1) & 0x00FFFFFF);
		}
		Color current = colorSetting.getColor();
		float[] hsb = Color.RGBtoHSB(current.getRed(), current.getGreen(),
			current.getBlue(), null);
		boolean hovered = mouseX >= left && mouseX < right && mouseY >= top
			&& mouseY < top + HUE_HEIGHT;
		float scale = thumbMotions[0]
			.update(hovered || draggingChannel == 0 ? 1.3F : 1);
		int handle = left + Math.round((right - left) * hsb[0]);
		int halfWidth = Math.max(1, Math.round(1.5F * scale));
		FlatRenderer.fillRoundedRect(graphics, handle - halfWidth, top - 1,
			handle + halfWidth, top + HUE_HEIGHT + 1, 2,
			SuperSoftTheme.TEXT);
	}

	private void renderChannel(GuiGraphics graphics, int mouseX, int mouseY,
		int channel)
	{
		Font font = Minecraft.getInstance().font;
		Color color = colorSetting.getColor();
		int[] values = {color.getRed(), color.getGreen(), color.getBlue(),
			color.getAlpha()};
		String[] labels = {"R", "G", "B", "A"};
		int[] colors = {0xFFEC5353, 0xFF53D769, 0xFF5594F0,
			SuperSoftTheme.TEXT};
		int rowTop = (int)y + HEADER_HEIGHT + CONTENT_PADDING + HUE_HEIGHT + 4
			+ channel * CHANNEL_HEIGHT;
		int trackLeft = (int)x + 16;
		int trackRight = (int)(x + getWidth()) - 24;
		int trackY = rowTop + 8;
		boolean hovered = mouseX >= trackLeft - 3 && mouseX < trackRight + 3
			&& mouseY >= rowTop && mouseY < rowTop + CHANNEL_HEIGHT;
		float scale = thumbMotions[channel + 1]
			.update(hovered || draggingChannel == channel + 1 ? 1.3F : 1);

		graphics.drawString(font, labels[channel], (int)x + 5, rowTop + 4,
			colors[channel], false);
		SuperSoftRenderer.slider(graphics, trackLeft, trackRight, trackY,
			colors[channel], values[channel] / 255F, scale);
		String value = Integer.toString(values[channel]);
		graphics.drawString(font, value, (int)(x + getWidth()) - 5
			- font.width(value), rowTop + 4, SuperSoftTheme.TEXT_SECONDARY,
			false);
	}

	@Override
	protected boolean onClick(double mouseX, double mouseY, int button)
	{
		if(button != 0)
			return false;
		if(mouseY < y + HEADER_HEIGHT)
		{
			expanded = !expanded;
			if(!expanded)
				draggingChannel = -1;
			height = getHeight();
			return true;
		}
		if(!expanded)
			return false;
		double localY = mouseY - y - HEADER_HEIGHT;
		if(localY >= CONTENT_PADDING
			&& localY < CONTENT_PADDING + HUE_HEIGHT)
			draggingChannel = 0;
		else
		{
			double channelStart = CONTENT_PADDING + HUE_HEIGHT + 4;
			int channel = (int)((localY - channelStart) / CHANNEL_HEIGHT);
			if(localY < channelStart || channel < 0 || channel >= 4)
				return true;
			draggingChannel = channel + 1;
		}
		updateChannel(mouseX, draggingChannel);
		return true;
	}

	@Override
	protected boolean onDrag(double mouseX, double mouseY, int button)
	{
		if(button != 0 || draggingChannel < 0)
			return false;
		updateChannel(mouseX, draggingChannel);
		return true;
	}

	@Override
	protected boolean onRelease(double mouseX, double mouseY, int button)
	{
		draggingChannel = -1;
		return true;
	}

	private void updateChannel(double mouseX, int channel)
	{
		int left = channel == 0 ? (int)x + 5 : (int)x + 16;
		int right = channel == 0 ? (int)(x + getWidth()) - 5
			: (int)(x + getWidth()) - 24;
		float value = Mth.clamp((float)((mouseX - left)
			/ Math.max(1, right - left)), 0, 1);
		Color current = colorSetting.getColor();
		if(channel == 0)
		{
			int rgb = Color.HSBtoRGB(value, 1, 1);
			colorSetting.setColor(new Color(rgb >> 16 & 0xFF,
				rgb >> 8 & 0xFF, rgb & 0xFF, current.getAlpha()));
			return;
		}
		int updated = Math.round(value * 255);
		int red = channel == 1 ? updated : current.getRed();
		int green = channel == 2 ? updated : current.getGreen();
		int blue = channel == 3 ? updated : current.getBlue();
		int alpha = channel == 4 ? updated : current.getAlpha();
		colorSetting.setColor(new Color(red, green, blue, alpha));
	}

	@Override
	public double getHeight()
	{
		return HEADER_HEIGHT + CONTENT_HEIGHT * expansionMotion.get();
	}

	@Override
	protected double minHeight()
	{
		return HEADER_HEIGHT;
	}
}

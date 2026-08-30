package net.wurstclient.compose;

import net.minecraft.client.gui.GuiGraphics;

/**
 * Compose {@code Box(Modifier.fillMaxSize())} 的等价物：全屏根容器，
 * 支持九个方位的内容对齐（TopEnd / BottomCenter 等）。
 */
public final class UiBox extends UiNode
{
	public enum Alignment
	{
		TOP_START, TOP_CENTER, TOP_END, CENTER_START, CENTER, CENTER_END,
		BOTTOM_START, BOTTOM_CENTER, BOTTOM_END
	}

	private final Alignment alignment;
	private UiNode content;

	public UiBox(Alignment alignment)
	{
		this.alignment = alignment;
	}

	public UiBox content(UiNode content)
	{
		this.content = content;
		return this;
	}

	@Override
	public float measureWidth(float maxWidth)
	{
		return maxWidth;
	}

	@Override
	public float measureHeight(float width)
	{
		return width;
	}

	@Override
	public void layout(float x, float y)
	{
		this.x = x;
		this.y = y;
		width = measureWidth(Float.MAX_VALUE);
		height = measureHeight(width);
		if(content == null)
			return;
		float contentW = content.measureWidth(Float.MAX_VALUE);
		float contentH = content.measureHeight(contentW);
		float contentX;
		float contentY;
		switch(alignment)
		{
			case TOP_START, CENTER_START, BOTTOM_START ->
				contentX = x;
			case TOP_CENTER, CENTER, BOTTOM_CENTER ->
				contentX = x + (width - contentW) / 2F;
			default -> contentX = x + width - contentW;
		}
		switch(alignment)
		{
			case TOP_START, TOP_CENTER, TOP_END -> contentY = y;
			case CENTER_START, CENTER, CENTER_END ->
				contentY = y + (height - contentH) / 2F;
			default -> contentY = y + height - contentH;
		}
		content.layout(contentX, contentY);
	}

	@Override
	public void render(GuiGraphics graphics, int mouseX, int mouseY,
		float partialTicks)
	{
		if(content != null)
			content.render(graphics, mouseX, mouseY, partialTicks);
	}
}

package net.wurstclient.compose;

import net.minecraft.client.gui.GuiGraphics;

/**
 * Compose {@code Spacer} 的等价物：固定尺寸占位。
 */
public final class UiSpacer extends UiNode
{
	private final float spaceWidth;
	private final float spaceHeight;

	public UiSpacer(float width, float height)
	{
		spaceWidth = width;
		spaceHeight = height;
	}

	@Override
	public float measureWidth(float maxWidth)
	{
		return spaceWidth;
	}

	@Override
	public float measureHeight(float width)
	{
		return spaceHeight;
	}

	@Override
	public void layout(float x, float y)
	{
		this.x = x;
		this.y = y;
		width = spaceWidth;
		height = spaceHeight;
	}

	@Override
	public void render(GuiGraphics graphics, int mouseX, int mouseY,
		float partialTicks)
	{}
}

package net.wurstclient.compose;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.client.gui.GuiGraphics;

/**
 * Compose {@code Row} 的等价物：水平排列子组件。
 */
public final class UiRow extends UiNode
{
	private final List<UiNode> children = new ArrayList<>();
	private float spacing;

	public UiRow spacing(float spacing)
	{
		this.spacing = spacing;
		return this;
	}

	public UiRow add(UiNode child)
	{
		children.add(child);
		return this;
	}

	@Override
	public float measureWidth(float maxWidth)
	{
		float total = 0;
		for(int index = 0; index < children.size(); index++)
		{
			total += children.get(index).measureWidth(maxWidth);
			if(index + 1 < children.size())
				total += spacing;
		}
		return total;
	}

	@Override
	public float measureHeight(float width)
	{
		float tallest = 0;
		for(UiNode child : children)
			tallest = Math.max(tallest, child.measureHeight(width));
		return tallest;
	}

	@Override
	public void layout(float x, float y)
	{
		this.x = x;
		this.y = y;
		float cursor = x;
		for(UiNode child : children)
		{
			float childWidth = child.measureWidth(Float.MAX_VALUE);
			child.layout(cursor, y);
			cursor += childWidth + spacing;
		}
		width = cursor - x - (children.isEmpty() ? 0 : spacing);
		height = measureHeight(width);
	}

	@Override
	public void render(GuiGraphics graphics, int mouseX, int mouseY,
		float partialTicks)
	{
		for(UiNode child : children)
			child.render(graphics, mouseX, mouseY, partialTicks);
	}
}

package net.wurstclient.compose;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.client.gui.GuiGraphics;

/**
 * Compose {@code Column} 的等价物：垂直排列子组件。
 */
public final class UiColumn extends UiNode
{
	private final List<UiNode> children = new ArrayList<>();
	private float spacing;

	public UiColumn spacing(float spacing)
	{
		this.spacing = spacing;
		return this;
	}

	public UiColumn add(UiNode child)
	{
		children.add(child);
		return this;
	}

	@Override
	public float measureWidth(float maxWidth)
	{
		float widest = 0;
		for(UiNode child : children)
			widest = Math.max(widest, child.measureWidth(maxWidth));
		return widest;
	}

	@Override
	public float measureHeight(float width)
	{
		float total = 0;
		for(int index = 0; index < children.size(); index++)
		{
			total += children.get(index).measureHeight(width);
			if(index + 1 < children.size())
				total += spacing;
		}
		return total;
	}

	@Override
	public void layout(float x, float y)
	{
		this.x = x;
		this.y = y;
		this.width = measureWidth(Float.MAX_VALUE);
		float cursor = y;
		for(UiNode child : children)
		{
			float childHeight = child.measureHeight(width);
			child.layout(x, cursor);
			cursor += childHeight + spacing;
		}
		height = cursor - y - (children.isEmpty() ? 0 : spacing);
	}

	@Override
	public void render(GuiGraphics graphics, int mouseX, int mouseY,
		float partialTicks)
	{
		for(UiNode child : children)
			child.render(graphics, mouseX, mouseY, partialTicks);
	}
}

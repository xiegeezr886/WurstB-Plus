package net.wurstclient.compose;

import net.minecraft.client.gui.GuiGraphics;

/**
 * Compose 风格声明式 UI 的组件基类（纯 Java 手写实现）。
 *
 * <p>组件树按 measure → layout → render 三阶段工作：先测量得到固有尺寸，
 * 再由父容器放置，最后以屏幕坐标绘制。对应 Compose 的
 * {@code Modifier.measure/layout/draw} 语义。</p>
 */
public abstract class UiNode
{
	protected float x;
	protected float y;
	protected float width;
	protected float height;

	public float getX()
	{
		return x;
	}

	public float getY()
	{
		return y;
	}

	public float getWidth()
	{
		return width;
	}

	public float getHeight()
	{
		return height;
	}

	/**
	 * 测量宽度。返回组件在给定最大宽度下的固有宽度。
	 */
	public abstract float measureWidth(float maxWidth);

	/**
	 * 测量高度。width 为已确定的实际宽度。
	 */
	public abstract float measureHeight(float width);

	/**
	 * 放置组件（含子组件布局）。
	 */
	public abstract void layout(float x, float y);

	/**
	 * 以屏幕坐标渲染。
	 */
	public abstract void render(GuiGraphics graphics, int mouseX, int mouseY,
		float partialTicks);
}

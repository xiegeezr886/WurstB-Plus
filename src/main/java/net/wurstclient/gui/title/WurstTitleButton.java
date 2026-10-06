package net.wurstclient.gui.title;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractButton;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.wurstclient.clickgui2.FlatRenderer;
import net.wurstclient.clickgui2.animation.HoverAnimation;
import net.wurstclient.gui.visual.VisualTheme;

/**
 * 标题界面上的一种按钮，三种形态共用一套悬停动画：
 *
 * <ul>
 * <li>{@link Style#ACTION}：底部动作条上的按钮，深色圆角块，图标 + 文字作为
 * 一个整体居中；</li>
 * <li>{@link Style#ICON}：右上角的齿轮，只有图标；</li>
 * <li>{@link Style#ROW}：齿轮弹出菜单里的一行，左对齐图标 + 文字，背景由菜单
 * 自己画，这里只在悬停时铺一层淡白。</li>
 * </ul>
 */
final class WurstTitleButton extends AbstractButton
{
	private static final int TEXTURE_SIZE = 88;
	/** 参考图上按 82% 黑压出来的按钮底色。 */
	private static final int ACTION_FILL = 0xE6101013;
	private static final int ACTION_FILL_HOVER = 0xF0242429;
	private static final int ICON_FILL = 0x80000000;
	private static final int ICON_FILL_HOVER = 0xB3121215;
	/** 齿轮菜单里悬停那行的淡白底（透明度，不是颜色通道）。 */
	private static final float ROW_HOVER_ALPHA = 0.14F;

	enum Style
	{
		ACTION,
		ICON,
		ROW
	}

	private final ResourceLocation icon;
	private final Runnable action;
	private final Style style;
	private final int radius;
	private final int iconSize;
	private final HoverAnimation hoverAnimation = new HoverAnimation(20);

	/** 带苹方字重的文案，第一次用时才建。 */
	private Component label;

	WurstTitleButton(int x, int y, int width, int height, Component message,
		ResourceLocation icon, Runnable action, Style style, int radius,
		int iconSize)
	{
		super(x, y, width, height, message);
		this.icon = icon;
		this.action = action;
		this.style = style;
		this.radius = radius;
		this.iconSize = iconSize;
	}

	@Override
	public void onPress()
	{
		action.run();
	}

	@Override
	protected void renderWidget(GuiGraphics graphics, int mouseX, int mouseY,
		float partialTicks)
	{
		float hover = hoverAnimation.update(isHoveredOrFocused());
		int x1 = getX();
		int y1 = getY();
		int x2 = x1 + getWidth();
		int y2 = y1 + getHeight();

		switch(style)
		{
			case ACTION -> renderAction(graphics, x1, y1, x2, y2, hover);
			case ICON -> renderIcon(graphics, x1, y1, x2, y2, hover);
			case ROW -> renderRow(graphics, x1, y1, x2, y2, hover);
		}
	}

	private void renderAction(GuiGraphics graphics, int x1, int y1, int x2,
		int y2, float hover)
	{
		FlatRenderer.fillRoundedRect(graphics, x1, y1, x2, y2, radius,
			VisualTheme.mix(ACTION_FILL, ACTION_FILL_HOVER, hover));

		Font font = Minecraft.getInstance().font;
		Component text = getMessage();
		int textWidth = font.width(text);
		int gap = Math.max(3, iconSize / 2);
		// 按钮窄到放不下图标时只留文字，免得两者叠在一起
		boolean showIcon = iconSize > 4
			&& iconSize + gap + textWidth <= getWidth() - 4;
		int content = textWidth + (showIcon ? iconSize + gap : 0);
		int x = x1 + (getWidth() - content) / 2;

		if(showIcon)
		{
			blitIcon(graphics, x, y1 + (getHeight() - iconSize) / 2, iconSize);
			x += iconSize + gap;
		}

		graphics.drawString(font, text, x,
			TitleMenuLayout.centerTextY(y1, getHeight()), VisualTheme.TEXT,
			false);
	}

	private void renderIcon(GuiGraphics graphics, int x1, int y1, int x2,
		int y2, float hover)
	{
		FlatRenderer.fillRoundedRect(graphics, x1, y1, x2, y2, radius,
			VisualTheme.mix(ICON_FILL, ICON_FILL_HOVER, hover));
		blitIcon(graphics, x1 + (getWidth() - iconSize) / 2,
			y1 + (getHeight() - iconSize) / 2, iconSize);
	}

	private void renderRow(GuiGraphics graphics, int x1, int y1, int x2, int y2,
		float hover)
	{
		if(hover > 0.01F)
			FlatRenderer.fillRoundedRect(graphics, x1, y1, x2, y2, radius,
				VisualTheme.withAlpha(0xFFFFFF, ROW_HOVER_ALPHA * hover));

		Font font = Minecraft.getInstance().font;
		int pad = Math.max(3, (getHeight() - iconSize) / 2);
		blitIcon(graphics, x1 + pad, y1 + (getHeight() - iconSize) / 2,
			iconSize);
		graphics.drawString(font, getMessage(), x1 + pad + iconSize + pad,
			TitleMenuLayout.centerTextY(y1, getHeight()),
			VisualTheme.mix(VisualTheme.TEXT_DIMMED, VisualTheme.TEXT, hover),
			false);
	}

	private void blitIcon(GuiGraphics graphics, int x, int y, int size)
	{
		if(size <= 0)
			return;

		graphics.blit(icon, x, y, size, size, 0, 0, TEXTURE_SIZE,
			TEXTURE_SIZE, TEXTURE_SIZE, TEXTURE_SIZE);
	}

	@Override
	protected void updateWidgetNarration(NarrationElementOutput output)
	{
		defaultButtonNarrationText(output);
	}
}

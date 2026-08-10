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
import net.wurstclient.gui.visual.VisualRenderer;
import net.wurstclient.gui.visual.VisualTheme;

final class WurstTitleButton extends AbstractButton
{
	private static final int TEXT_COLOR = VisualTheme.TEXT;
	private static final int ACCENT_COLOR = VisualTheme.ACCENT;
	private static final int DANGER_COLOR = VisualTheme.ERROR;
	private static final int ICON_SIZE = 22;
	private static final int TEXTURE_SIZE = 88;

	private final ResourceLocation icon;
	private final Runnable action;
	private final boolean compact;
	private final boolean dangerous;
	private final HoverAnimation hoverAnimation = new HoverAnimation(20);

	WurstTitleButton(int x, int y, int width, int height, Component message,
		ResourceLocation icon, Runnable action, boolean compact,
		boolean dangerous)
	{
		super(x, y, width, height, message);
		this.icon = icon;
		this.action = action;
		this.compact = compact;
		this.dangerous = dangerous;
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
		int accent = dangerous ? DANGER_COLOR : ACCENT_COLOR;
		int radius = compact ? VisualTheme.RADIUS_MEDIUM
			: VisualTheme.RADIUS_LARGE;
		VisualRenderer.button(graphics, x1, y1, x2, y2, radius, hover,
			false, dangerous);
		int iconArea = compact ? 36 : 46;
		int iconBackground = VisualTheme.mix(
			VisualTheme.ACCENT_SUBTLE_STRONG, accent, hover);
		FlatRenderer.fillRoundedRect(graphics, x1 + 1, y1 + 1,
			x1 + iconArea, y2 - 1, radius - 1, iconBackground);
		graphics.fill(x1 + iconArea - radius, y1 + 1, x1 + iconArea,
			y2 - 1, iconBackground);

		Font font = Minecraft.getInstance().font;
		int iconSize = ICON_SIZE;
		int iconX = x1 + (iconArea - iconSize) / 2;
		int iconY = y1 + (getHeight() - iconSize) / 2;
		graphics.blit(icon, iconX, iconY, iconSize, iconSize, 0, 0,
			TEXTURE_SIZE, TEXTURE_SIZE, TEXTURE_SIZE, TEXTURE_SIZE);

		int color = active ? TEXT_COLOR : VisualTheme.TEXT_DISABLED;
		int textX = iconX + iconSize + (compact ? 7 : 11);
		graphics.drawString(font, getMessage(), textX,
			y1 + (getHeight() - font.lineHeight) / 2 + 1, color, false);

		if(!compact)
			graphics.drawString(font, ">", x2 - 14,
				y1 + (getHeight() - font.lineHeight) / 2 + 1,
				withAlpha(0xFFFFFFFF, 110 + Math.round(hover * 145)), false);
	}

	@Override
	protected void updateWidgetNarration(NarrationElementOutput output)
	{
		defaultButtonNarrationText(output);
	}

	private static int withAlpha(int color, int alpha)
	{
		return Math.max(0, Math.min(255, alpha)) << 24 | color & 0xFFFFFF;
	}
}

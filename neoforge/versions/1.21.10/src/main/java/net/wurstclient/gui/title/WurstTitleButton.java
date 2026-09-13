package net.wurstclient.gui.title;

import net.minecraft.client.gui.GuiGraphics;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.wurstclient.util.render.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractButton;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.input.InputWithModifiers;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.wurstclient.clickgui2.FlatRenderer;
import net.wurstclient.clickgui2.animation.HoverAnimation;

final class WurstTitleButton extends AbstractButton
{
	private static final int TEXT_COLOR = 0xFFF2F6F6;
	private static final int ACCENT_COLOR = 0xFF006366;
	private static final int DANGER_COLOR = 0xFFD95D63;
	private static final int ICON_SIZE = 22;
	private static final int TEXTURE_SIZE = 88;

	private final Identifier icon;
	private final Runnable action;
	private final boolean compact;
	private final boolean dangerous;
	private final HoverAnimation hoverAnimation = new HoverAnimation(20);

	WurstTitleButton(int x, int y, int width, int height, Component message,
		Identifier icon, Runnable action, boolean compact,
		boolean dangerous)
	{
		super(x, y, width, height, message);
		this.icon = icon;
		this.action = action;
		this.compact = compact;
		this.dangerous = dangerous;
	}

	@Override
	public void onPress(InputWithModifiers context)
	{
		action.run();
	}
@Override
	protected void renderContents(GuiGraphics graphics, int mouseX, int mouseY,
		float partialTicks)
	{
		extractContents(new GuiGraphicsExtractor(graphics), mouseX, mouseY, partialTicks);
	}

	public void extractContents(GuiGraphicsExtractor graphics, int mouseX, int mouseY,
		float partialTicks)
	{
		float hover = hoverAnimation.update(isHoveredOrFocused());
		int x1 = getX();
		int y1 = getY();
		int x2 = x1 + getWidth();
		int y2 = y1 + getHeight();
		int accent = dangerous ? DANGER_COLOR : ACCENT_COLOR;
		int fill = mixColor(0xD9141B1C, withAlpha(accent, 230), hover * 0.72F);
		FlatRenderer.fillRoundedRect(graphics, x1, y1, x2, y2,
			compact ? 5 : 7, fill);
		FlatRenderer.drawRoundedOutline(graphics, x1, y1, x2, y2,
			compact ? 5 : 7, withAlpha(0xFFFFFFFF, 22 + Math.round(hover * 48)));

		Font font = Minecraft.getInstance().font;
		int iconSize = ICON_SIZE;
		int iconX = x1 + (compact ? 9 : 12);
		int iconY = y1 + (getHeight() - iconSize) / 2;
		graphics.blit(RenderPipelines.GUI_TEXTURED, icon, iconX, iconY, 0, 0,
			iconSize, iconSize,
			TEXTURE_SIZE, TEXTURE_SIZE, TEXTURE_SIZE, TEXTURE_SIZE);

		int color = active ? TEXT_COLOR : 0xFF707A7B;
		int textX = iconX + iconSize + (compact ? 7 : 11);
		graphics.text(font, getMessage(), textX,
			y1 + (getHeight() - font.lineHeight) / 2 + 1, color, false);

		if(!compact)
			graphics.text(font, ">", x2 - 14,
				y1 + (getHeight() - font.lineHeight) / 2 + 1,
				withAlpha(0xFFFFFFFF, 110 + Math.round(hover * 145)), false);
	}

	@Override
	protected void updateWidgetNarration(NarrationElementOutput output)
	{
		defaultButtonNarrationText(output);
	}

	private static int mixColor(int first, int second, float amount)
	{
		float weight = Math.max(0, Math.min(1, amount));
		float inverse = 1 - weight;
		int alpha = Math.round((first >>> 24) * inverse
			+ (second >>> 24) * weight);
		int red = Math.round((first >> 16 & 0xFF) * inverse
			+ (second >> 16 & 0xFF) * weight);
		int green = Math.round((first >> 8 & 0xFF) * inverse
			+ (second >> 8 & 0xFF) * weight);
		int blue = Math.round((first & 0xFF) * inverse
			+ (second & 0xFF) * weight);
		return alpha << 24 | red << 16 | green << 8 | blue;
	}

	private static int withAlpha(int color, int alpha)
	{
		return Math.max(0, Math.min(255, alpha)) << 24 | color & 0xFFFFFF;
	}
}

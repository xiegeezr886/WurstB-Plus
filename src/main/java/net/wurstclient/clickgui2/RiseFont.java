package net.wurstclient.clickgui2;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Style;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.FormattedCharSequence;

public final class RiseFont
{
	private static final ResourceLocation ID =
		new ResourceLocation("wurst", "rise");

	/**
	 * 客户端 UI 的拉丁字面（{@code wurst:rise} = SF Pro Rounded）。provider 里
	 * 带了 {@code minecraft:default} 回落，所以汉字会落到默认字体上。
	 */
	public static final Style STYLE = Style.EMPTY.withFont(ID);

	private RiseFont()
	{
	}

	public static net.minecraft.network.chat.Component text(String text)
	{
		return net.minecraft.network.chat.Component.literal(text)
			.withStyle(STYLE);
	}

	/**
	 * 字重参数是为了不动原来的调用方：{@code wurst:rise} 只有一个字重，粗体靠
	 * 原版字体的合成加粗。
	 */
	public static net.minecraft.network.chat.Component text(String text,
		Style ignored)
	{
		return text(text);
	}

	public static FormattedCharSequence sequence(String text)
	{
		return FormattedCharSequence.forward(text, STYLE);
	}

	public static int width(Font font, String text)
	{
		return font.width(text(text));
	}

	/** 同 {@link #width(Font, String)}，忽略字重参数。 */
	public static int width(Font font, String text, Style ignored)
	{
		return width(font, text);
	}

	public static String trim(Font font, String text, int maxWidth)
	{
		if(maxWidth <= 0)
			return "";
		int low = 0;
		int high = text.length();
		while(low < high)
		{
			int middle = (low + high + 1) >>> 1;
			if(width(font, text.substring(0, middle)) <= maxWidth)
				low = middle;
			else
				high = middle - 1;
		}
		return text.substring(0, low);
	}

	public static void draw(GuiGraphics graphics, Font font, String text, int x,
		int y, int color)
	{
		graphics.drawString(font, text(text), x, y, color, false);
	}
}

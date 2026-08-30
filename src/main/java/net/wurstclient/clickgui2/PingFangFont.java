package net.wurstclient.clickgui2;

import net.minecraft.client.gui.Font;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.resources.ResourceLocation;

/**
 * 苹方 CJK 字体的多字重回退。
 *
 * <p>三个独立字体 provider（各自拥有独立 256 图集）：
 * {@link #LIGHT_STYLE}（远处歌词行）、{@link #REGULAR_STYLE}（普通行）、
 * {@link #SEMIBOLD_STYLE}（当前高亮行，Apple Music 式粗体强调）。</p>
 */
public final class PingFangFont
{
	private static final ResourceLocation REGULAR_ID =
		new ResourceLocation("wurst", "pingfang");
	private static final ResourceLocation LIGHT_ID =
		new ResourceLocation("wurst", "pingfang_light");
	private static final ResourceLocation SEMIBOLD_ID =
		new ResourceLocation("wurst", "pingfang_semibold");

	/** 常规字重（默认回退）。 */
	public static final Style REGULAR_STYLE =
		Style.EMPTY.withFont(REGULAR_ID);
	/** 细字重（远处歌词行）。 */
	public static final Style LIGHT_STYLE = Style.EMPTY.withFont(LIGHT_ID);
	/** 半粗字重（当前高亮歌词行）。 */
	public static final Style SEMIBOLD_STYLE =
		Style.EMPTY.withFont(SEMIBOLD_ID);

	/** @deprecated 使用 {@link #REGULAR_STYLE}。 */
	@Deprecated
	public static final Style STYLE = REGULAR_STYLE;

	private PingFangFont()
	{}

	/** 常规字重文字。 */
	public static Component text(String text)
	{
		return Component.literal(text).withStyle(REGULAR_STYLE);
	}

	/** 指定字重文字。 */
	public static Component text(String text, Style weight)
	{
		return Component.literal(text).withStyle(weight);
	}

	public static int width(Font font, String text)
	{
		return font.width(text(text));
	}

	/** 指定字重的文字宽度。 */
	public static int width(Font font, String text, Style weight)
	{
		return font.width(text(text, weight));
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
}

package net.wurstclient.clickgui2;

import net.minecraft.client.gui.Font;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.resources.ResourceLocation;

public final class PingFangFont
{
	private static final ResourceLocation ID =
		new ResourceLocation("wurst", "pingfang");
	public static final Style STYLE = Style.EMPTY.withFont(ID);

	private PingFangFont()
	{
	}

	public static Component text(String text)
	{
		return Component.literal(text).withStyle(STYLE);
	}

	public static int width(Font font, String text)
	{
		return font.width(text(text));
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

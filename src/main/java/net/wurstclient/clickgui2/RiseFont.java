package net.wurstclient.clickgui2;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Style;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.FormattedCharSequence;

final class RiseFont
{
	private static final ResourceLocation ID =
		new ResourceLocation("wurst", "rise");
	static final Style STYLE = Style.EMPTY.withFont(ID);

	private RiseFont()
	{
	}

	static net.minecraft.network.chat.Component text(String text)
	{
		return net.minecraft.network.chat.Component.literal(text)
			.withStyle(STYLE);
	}

	static FormattedCharSequence sequence(String text)
	{
		return FormattedCharSequence.forward(text, STYLE);
	}

	static int width(Font font, String text)
	{
		return font.width(text(text));
	}

	static String trim(Font font, String text, int maxWidth)
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

	static void draw(GuiGraphics graphics, Font font, String text, int x, int y,
		int color)
	{
		graphics.drawString(font, text(text), x, y, color, false);
	}
}

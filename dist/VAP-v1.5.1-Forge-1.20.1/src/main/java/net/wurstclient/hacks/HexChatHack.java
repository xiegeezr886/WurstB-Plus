/*
 * Copyright (c) 2014-2026 Wurst-Imperium and contributors.
 *
 * This source code is subject to the terms of the GNU Affero General Public
 * License, version 3. If a copy of the AGPL was not distributed with this
 * file, You can obtain one at: https://www.gnu.org/licenses/agpl-3.0.txt
 */
// Ported from InvincibleMachineGun (GPL-3.0).
package net.wurstclient.hacks;

import java.awt.Color;

import net.wurstclient.Category;
import net.wurstclient.SearchTags;
import net.wurstclient.events.ChatOutputListener;
import net.wurstclient.hack.Hack;
import net.wurstclient.settings.CheckboxSetting;
import net.wurstclient.settings.ColorSetting;
import net.wurstclient.settings.EnumSetting;
import net.wurstclient.settings.SliderSetting;
import net.wurstclient.settings.SliderSetting.ValueDisplay;

@SearchTags({"hex chat", "color chat", "gradient chat", "rainbow chat",
	"chat colors"})
public final class HexChatHack extends Hack implements ChatOutputListener
{
	private final EnumSetting<Mode> mode =
		new EnumSetting<>("Mode", Mode.values(), Mode.QUAD);

	private final ColorSetting color = new ColorSetting("Static color",
		"Color used in Static mode.", Color.RED);

	private final ColorSetting color1 = new ColorSetting("Color 1",
		"First color of the gradient.", new Color(255, 215, 0));

	private final ColorSetting color2 = new ColorSetting("Color 2",
		"Second color of the gradient.", new Color(17, 255, 0));

	private final ColorSetting color3 = new ColorSetting("Color 3",
		"Third color of the four-color gradient.", new Color(0, 191, 255));

	private final ColorSetting color4 = new ColorSetting("Color 4",
		"Fourth color of the four-color gradient.",
		new Color(255, 105, 180));

	private final SliderSetting rainbowSpread = new SliderSetting(
		"Rainbow spread", "How quickly the rainbow hue changes per character.",
		0.1, 0.01, 1, 0.01, ValueDisplay.DECIMAL)
			.visibleWhen(() -> mode.getSelected() == Mode.RAINBOW);

	private final EnumSetting<VanillaColor> vanillaColor =
		new EnumSetting<>("Vanilla color", VanillaColor.values(),
			VanillaColor.WHITE);

	private final CheckboxSetting bold =
		new CheckboxSetting("Bold", "Adds the &l style code.", true);

	private final CheckboxSetting italic =
		new CheckboxSetting("Italic", "Adds the &o style code.", false);

	private final CheckboxSetting underline =
		new CheckboxSetting("Underline", "Adds the &n style code.", false);

	private final CheckboxSetting strikethrough =
		new CheckboxSetting("Strikethrough", "Adds the &m style code.", false);

	private final CheckboxSetting obfuscated =
		new CheckboxSetting("Obfuscated", "Adds the &k style code.", false);

	private final CheckboxSetting ignoreCommands = new CheckboxSetting(
		"Ignore commands", "Leaves messages starting with / unchanged.", true);

	public HexChatHack()
	{
		super("HexChat");
		setCategory(Category.CHAT);
		addSetting(mode);
		addSetting(color);
		addSetting(color1);
		addSetting(color2);
		addSetting(color3);
		addSetting(color4);
		addSetting(rainbowSpread);
		addSetting(vanillaColor);
		addSetting(bold);
		addSetting(italic);
		addSetting(underline);
		addSetting(strikethrough);
		addSetting(obfuscated);
		addSetting(ignoreCommands);
	}

	@Override
	protected void onEnable()
	{
		EVENTS.add(ChatOutputListener.class, this);
	}

	@Override
	protected void onDisable()
	{
		EVENTS.remove(ChatOutputListener.class, this);
	}

	@Override
	public void onSentMessage(ChatOutputEvent event)
	{
		String message = event.getMessage();
		if(ignoreCommands.isChecked() && message.startsWith("/"))
			return;

		String style = getStyleCodes();
		String result = switch(mode.getSelected())
		{
			case STATIC -> getHexCode(color.getColor()) + style + message;
			case GRADIENT -> applyGradient(message, style, false);
			case QUAD -> applyGradient(message, style, true);
			case RAINBOW -> applyRainbow(message, style);
			case VANILLA -> "&" + vanillaColor.getSelected().getCode() + style
				+ message;
		};

		event.setMessage(result);
	}

	private String getStyleCodes()
	{
		StringBuilder builder = new StringBuilder();
		if(bold.isChecked())
			builder.append("&l");
		if(italic.isChecked())
			builder.append("&o");
		if(underline.isChecked())
			builder.append("&n");
		if(strikethrough.isChecked())
			builder.append("&m");
		if(obfuscated.isChecked())
			builder.append("&k");
		return builder.toString();
	}

	private String applyGradient(String text, String style, boolean quad)
	{
		StringBuilder builder = new StringBuilder();
		int length = text.length();

		for(int i = 0; i < length; i++)
		{
			char c = text.charAt(i);
			double progress = length <= 1 ? 0 : (double)i / (length - 1);

			int r;
			int g;
			int b;

			if(!quad)
			{
				r = interpolate(color1.getColor().getRed(),
					color2.getColor().getRed(), progress);
				g = interpolate(color1.getColor().getGreen(),
					color2.getColor().getGreen(), progress);
				b = interpolate(color1.getColor().getBlue(),
					color2.getColor().getBlue(), progress);
			}else if(progress < 0.333)
			{
				double sub = progress * 3;
				r = interpolate(color1.getColor().getRed(),
					color2.getColor().getRed(), sub);
				g = interpolate(color1.getColor().getGreen(),
					color2.getColor().getGreen(), sub);
				b = interpolate(color1.getColor().getBlue(),
					color2.getColor().getBlue(), sub);
			}else if(progress < 0.666)
			{
				double sub = (progress - 0.333) * 3;
				r = interpolate(color2.getColor().getRed(),
					color3.getColor().getRed(), sub);
				g = interpolate(color2.getColor().getGreen(),
					color3.getColor().getGreen(), sub);
				b = interpolate(color2.getColor().getBlue(),
					color3.getColor().getBlue(), sub);
			}else
			{
				double sub = Math.min(1, (progress - 0.666) * 3);
				r = interpolate(color3.getColor().getRed(),
					color4.getColor().getRed(), sub);
				g = interpolate(color3.getColor().getGreen(),
					color4.getColor().getGreen(), sub);
				b = interpolate(color3.getColor().getBlue(),
					color4.getColor().getBlue(), sub);
			}

			builder.append(String.format("&#%02X%02X%02X", r, g, b));
			builder.append(style);
			builder.append(c);
		}

		return builder.toString();
	}

	private String applyRainbow(String text, String style)
	{
		StringBuilder builder = new StringBuilder();
		double spread = rainbowSpread.getValue();

		for(int i = 0; i < text.length(); i++)
		{
			int rgb = Color.HSBtoRGB((float)((i * spread) % 1.0), 1, 1);
			builder.append(String.format("&#%02X%02X%02X",
				(rgb >> 16) & 0xFF, (rgb >> 8) & 0xFF, rgb & 0xFF));
			builder.append(style);
			builder.append(text.charAt(i));
		}

		return builder.toString();
	}

	private static int interpolate(int start, int end, double progress)
	{
		return (int)(start + (end - start) * progress);
	}

	private static String getHexCode(Color color)
	{
		return String.format("&#%02X%02X%02X", color.getRed(), color.getGreen(),
			color.getBlue());
	}

	private enum Mode
	{
		STATIC("Static"),
		GRADIENT("Gradient"),
		QUAD("Quad"),
		RAINBOW("Rainbow"),
		VANILLA("Vanilla");

		private final String name;

		private Mode(String name)
		{
			this.name = name;
		}

		@Override
		public String toString()
		{
			return name;
		}
	}

	private enum VanillaColor
	{
		BLACK("Black", "0"),
		DARK_BLUE("Dark blue", "1"),
		DARK_GREEN("Dark green", "2"),
		DARK_AQUA("Dark aqua", "3"),
		DARK_RED("Dark red", "4"),
		DARK_PURPLE("Dark purple", "5"),
		GOLD("Gold", "6"),
		GRAY("Gray", "7"),
		DARK_GRAY("Dark gray", "8"),
		BLUE("Blue", "9"),
		GREEN("Green", "a"),
		AQUA("Aqua", "b"),
		RED("Red", "c"),
		LIGHT_PURPLE("Light purple", "d"),
		YELLOW("Yellow", "e"),
		WHITE("White", "f");

		private final String name;
		private final String code;

		private VanillaColor(String name, String code)
		{
			this.name = name;
			this.code = code;
		}

		private String getCode()
		{
			return code;
		}

		@Override
		public String toString()
		{
			return name;
		}
	}
}

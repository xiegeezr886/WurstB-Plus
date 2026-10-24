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
import java.util.Optional;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.wurstclient.Category;
import net.wurstclient.SearchTags;
import net.wurstclient.events.ChatInputListener;
import net.wurstclient.hack.Hack;
import net.wurstclient.settings.CheckboxSetting;
import net.wurstclient.settings.ColorSetting;
import net.wurstclient.settings.EnumSetting;
import net.wurstclient.settings.TextFieldSetting;
import net.wurstclient.util.ChatUtils;

/**
 * Replaces the Wurst feedback prefix with a customizable, optionally
 * gradient-colored prefix.
 *
 * <p>
 * The original module rewrote Meteor's static ChatUtils prefix through
 * reflection. Wurst's counterpart is rendered on every feedback message, so
 * this port intercepts those messages and rebuilds the prefix instead.
 */
@SearchTags({"chat prefix", "custom prefix", "prefix", "gradient prefix",
	"chat style"})
public final class ChatPrefixCustomHack extends Hack implements ChatInputListener
{
	private final TextFieldSetting prefixText = new TextFieldSetting(
		"Prefix text", "Text shown inside the brackets.", "WURST");

	private final EnumSetting<ColorMode> colorMode =
		new EnumSetting<>("Color mode", ColorMode.values(), ColorMode.FIXED);

	private final ColorSetting prefixColor = new ColorSetting("Prefix color",
		"Color used in Fixed mode.", new Color(255, 170, 0));

	private final ColorSetting prefixColorEnd = new ColorSetting(
		"Prefix end color", "Second color used in Gradient mode.",
		new Color(255, 0, 255))
			.visibleWhen(() -> colorMode.getSelected() == ColorMode.GRADIENT);

	private final CheckboxSetting bold =
		new CheckboxSetting("Bold", "Renders the prefix in bold.", true);

	private final CheckboxSetting italic =
		new CheckboxSetting("Italic", "Renders the prefix in italics.", false);

	private final CheckboxSetting underline = new CheckboxSetting("Underline",
		"Renders the prefix with an underline.", false);

	private final CheckboxSetting strikethrough = new CheckboxSetting(
		"Strikethrough", "Renders the prefix with a strikethrough.", false);

	public ChatPrefixCustomHack()
	{
		super("ChatPrefixCustom");
		setCategory(Category.CHAT);
		addSetting(prefixText);
		addSetting(colorMode);
		addSetting(prefixColor);
		addSetting(prefixColorEnd);
		addSetting(bold);
		addSetting(italic);
		addSetting(underline);
		addSetting(strikethrough);
	}

	@Override
	protected void onEnable()
	{
		EVENTS.add(ChatInputListener.class, this);
	}

	@Override
	protected void onDisable()
	{
		EVENTS.remove(ChatInputListener.class, this);
	}

	@Override
	public void onReceivedMessage(ChatInputEvent event)
	{
		Component message = event.getComponent();
		String raw = message.getString();
		if(!raw.startsWith(ChatUtils.WURST_PREFIX))
			return;

		Component rest = stripPrefix(message, ChatUtils.WURST_PREFIX.length());
		event.setComponent(buildPrefix().append(rest));
	}

	private MutableComponent buildPrefix()
	{
		MutableComponent prefix =
			Component.literal("[").withStyle(ChatFormatting.GRAY);

		String text = prefixText.getValue();
		if(text.isEmpty())
			text = "WURST";

		for(int i = 0; i < text.length(); i++)
		{
			int color = getColor(i, text.length());
			Style style = Style.EMPTY.withColor(color)
				.withBold(bold.isChecked()).withItalic(italic.isChecked())
				.withUnderlined(underline.isChecked())
				.withStrikethrough(strikethrough.isChecked());

			prefix.append(
				Component.literal(String.valueOf(text.charAt(i))).withStyle(style));
		}

		return prefix.append(
			Component.literal("] ").withStyle(ChatFormatting.GRAY));
	}

	private int getColor(int index, int length)
	{
		Color start = prefixColor.getColor();
		if(colorMode.getSelected() == ColorMode.FIXED || length <= 1)
			return start.getRGB() & 0xFFFFFF;

		Color end = prefixColorEnd.getColor();
		double progress = (double)index / (length - 1);

		int red = interpolate(start.getRed(), end.getRed(), progress);
		int green = interpolate(start.getGreen(), end.getGreen(), progress);
		int blue = interpolate(start.getBlue(), end.getBlue(), progress);
		return (red << 16) | (green << 8) | blue;
	}

	private static int interpolate(int start, int end, double progress)
	{
		return (int)(start + (end - start) * progress);
	}

	private Component stripPrefix(Component component, int skip)
	{
		MutableComponent result = Component.empty();
		int[] remaining = {skip};

		component.visit((style, text) -> {
			if(remaining[0] >= text.length())
			{
				remaining[0] -= text.length();
				return Optional.empty();
			}

			String rest = text.substring(remaining[0]);
			remaining[0] = 0;
			result.append(Component.literal(rest).withStyle(style));
			return Optional.empty();
		}, Style.EMPTY);

		return result;
	}

	private enum ColorMode
	{
		FIXED("Fixed"),
		GRADIENT("Gradient");

		private final String name;

		private ColorMode(String name)
		{
			this.name = name;
		}

		@Override
		public String toString()
		{
			return name;
		}
	}
}

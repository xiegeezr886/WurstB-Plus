/*
 * Copyright (c) 2014-2025 Wurst-Imperium and contributors.
 *
 * This source code is subject to the terms of the GNU General Public
 * License, version 3. If a copy of the GPL was not distributed with this
 * file, You can obtain one at: https://www.gnu.org/licenses/gpl-3.0.txt
 */
package net.wurstclient.util;

import java.util.List;
import java.util.StringJoiner;
import net.minecraft.client.multiplayer.chat.GuiMessage;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.ChatComponent;
import net.wurstclient.mixin.ChatComponentAccessor;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.FormattedText;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.util.FormattedCharSequence;
import net.wurstclient.WurstClient;

public enum ChatUtils
{
	;
	
	private static final Minecraft MC = WurstClient.MC;
	
	public static final String WURST_PREFIX =
		"\u00a7c[\u00a76" + WurstClient.CLIENT_NAME + "\u00a7c]\u00a7r ";
	
	private static boolean enabled = true;
	
	public static void setEnabled(boolean enabled)
	{
		ChatUtils.enabled = enabled;
	}
	
	public static void component(Component component)
	{
		if(!enabled)
			return;
		
		ChatComponent chatHud = MC.gui.getChat();
		MutableComponent prefix = Component.literal(WURST_PREFIX);
		((ChatComponentAccessor)chatHud).wurst$addMessage(prefix.append(component),
			null, null, null);
	}
	
	/**
	 * Shows a chat message, translated into the current language when a
	 * translation exists. The English text doubles as the translation key,
	 * exactly like the setting descriptions do, so callers that already pass
	 * English keep working unchanged.
	 */
	public static void message(String message)
	{
		component(Component.literal(translate(message)));
	}

	/**
	 * Shows a formatted chat message. Use {@code %s}/{@code %d} in the format
	 * string so the translation can reorder or rephrase it.
	 */
	public static void message(String format, Object... args)
	{
		component(Component.literal(translate(format, args)));
	}
	
	public static void warning(String message)
	{
		warningMessage(translate(message));
	}

	public static void warning(String format, Object... args)
	{
		warningMessage(translate(format, args));
	}

	private static void warningMessage(String message)
	{
		if(!enabled)
			return;

		MutableComponent prefix =
			Component.literal(prefix("chat.wurst.prefix.warning", "WARNING"));
		component(prefix.append(Component.literal(message)));
	}
	
	public static void error(String message)
	{
		errorMessage(translate(message));
	}

	public static void error(String format, Object... args)
	{
		errorMessage(translate(format, args));
	}

	private static void errorMessage(String message)
	{
		if(!enabled)
			return;

		MutableComponent prefix =
			Component.literal(prefix("chat.wurst.prefix.error", "ERROR"));
		component(prefix.append(Component.literal(message)));
	}
	
	public static void syntaxError(String message)
	{
		component(Component.literal(
			translate("chat.wurst.prefix.syntax_error", message)));
	}

	public static void syntaxError(String format, Object... args)
	{
		component(Component.literal(
			translate("chat.wurst.prefix.syntax_error", translate(format, args))));
	}

	private static String translate(String key, Object... args)
	{
		return WurstClient.INSTANCE.translate(key, args);
	}

	private static String prefix(String key, String fallback)
	{
		String translated = WurstClient.INSTANCE.translate(key);
		String word = translated.equals(key) ? fallback : translated;
		return "\u00a7c[\u00a76\u00a7l" + word + "\u00a7c]\u00a7r ";
	}
	
	public static String getAsString(GuiMessage.Line visible)
	{
		return getAsString(visible.content());
	}
	
	public static String getAsString(FormattedCharSequence text)
	{
		JustGiveMeTheStringVisitor visitor = new JustGiveMeTheStringVisitor();
		text.accept(visitor);
		return visitor.toString();
	}
	
	public static final String wrapText(String text, int width)
	{
		return wrapText(text, width, Style.EMPTY);
	}
	
	public static final String wrapText(String text, int width, Style style)
	{
		List<FormattedText> lines = MC.font.getSplitter()
			.splitLines(text, width, Style.EMPTY);
		
		StringJoiner joiner = new StringJoiner("\n");
		lines.stream().map(FormattedText::getString)
			.forEach(s -> joiner.add(s));
		
		return joiner.toString();
	}
}

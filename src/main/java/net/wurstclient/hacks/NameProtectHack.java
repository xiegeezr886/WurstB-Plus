/*
 * Copyright (c) 2014-2025 Wurst-Imperium and contributors.
 *
 * This source code is subject to the terms of the GNU General Public
 * License, version 3. If a copy of the GPL was not distributed with this
 * file, You can obtain one at: https://www.gnu.org/licenses/gpl-3.0.txt
 */
package net.wurstclient.hacks;

import java.util.regex.Matcher;
import java.util.regex.Pattern;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.client.player.AbstractClientPlayer;
import net.wurstclient.Category;
import net.wurstclient.SearchTags;
import net.wurstclient.hack.Hack;

@SearchTags({"name protect"})
public final class NameProtectHack extends Hack
{
	public NameProtectHack()
	{
		super("NameProtect");
		setCategory(Category.RENDER);
	}
	
	public String protect(String string)
	{
		if(!isEnabled() || MC.player == null)
			return string;
		
		String me = MC.getUser().getName();
		if(string.contains(me))
			return replaceWholeWord(string, me, "\u00a7oMe\u00a7r");
		
		int i = 0;
		for(PlayerInfo info : MC.player.connection.getOnlinePlayers())
		{
			i++;
			String name =
				info.getProfile().getName().replaceAll("\u00a7\\w", "");
			
			if(string.contains(name))
				return replaceWholeWord(string, name,
					"\u00a7oPlayer" + i + "\u00a7r");
		}
		
		for(AbstractClientPlayer player : MC.level.players())
		{
			i++;
			String name = player.getName().getString();
			
			if(string.contains(name))
				return replaceWholeWord(string, name,
					"\u00a7oPlayer" + i + "\u00a7r");
		}
		
		return string;
	}
	
	/**
	 * 只替换作为"完整单词"出现的玩家名。
	 *
	 * <p>
	 * 旧实现直接 `String.replace(name, ...)`（子串替换）。反例：目标服务器上有玩家叫
	 * {@code Red} 时，聊天/名牌里的 "Reduced" 会被替换成 "§oPlayer1§ruced"；
	 * 3 个字符的玩家名（原版允许 3~16 字符）可以命中大量普通单词。
	 */
	private static String replaceWholeWord(String text, String word,
		String replacement)
	{
		if(word.isEmpty())
			return text;
		
		return text.replaceAll(
			"(?<![A-Za-z0-9_])" + Pattern.quote(word) + "(?![A-Za-z0-9_])",
			Matcher.quoteReplacement(replacement));
	}
}

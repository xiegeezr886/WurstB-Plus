/* SPDX-License-Identifier: GPL-3.0 */
package net.wurstclient.util;

import java.util.HashMap;
import java.util.Map;

/** Exact-level book rules. Invalid input rejects the complete configuration. */
public final class EnchantmentRules
{
	private EnchantmentRules() {}

	public static Map<String, Integer> parse(String text)
	{
		Map<String, Integer> rules = new HashMap<>();
		if(text.isBlank())
			return rules;
		for(String entry : text.split(",", -1))
		{
			String[] parts = entry.trim().split("=", -1);
			if(parts.length != 2)
				throw new IllegalArgumentException("Expected enchantment=level.");
			String id = parts[0].trim();
			if(!id.contains(":"))
				id = "minecraft:" + id;
			if(!id.matches("[a-z0-9_.-]+:[a-z0-9/._-]+"))
				throw new IllegalArgumentException("Invalid enchantment ID.");
			int level = Integer.parseInt(parts[1].trim());
			if(level < 1 || level > 255 || rules.putIfAbsent(id, level) != null)
				throw new IllegalArgumentException("Use one level from 1 to 255 per enchantment.");
		}
		return rules;
	}

	public static boolean matches(Map<String, Integer> rules, Map<String, Integer> enchantments)
	{
		for(var entry : enchantments.entrySet())
			if(entry.getValue().equals(rules.get(entry.getKey())))
				return true;
		return false;
	}
}

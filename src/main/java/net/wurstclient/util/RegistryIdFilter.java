/* SPDX-License-Identifier: GPL-3.0 */
package net.wurstclient.util;

import java.util.HashSet;
import java.util.Set;

/** Exact registry ID matching, with Minecraft as the default namespace. */
public final class RegistryIdFilter
{
	private String previous = "";
	private Set<String> ids = Set.of();

	public boolean contains(String input, String registryId)
	{
		if(!input.equals(previous))
		{
			Set<String> parsed = new HashSet<>();
			for(String token : input.split("[,;\\s]+"))
			{
				if(token.isEmpty())
					continue;
				parsed.add(token.indexOf(':') < 0 ? "minecraft:" + token : token);
			}
			ids = Set.copyOf(parsed);
			previous = input;
		}
		return ids.contains(registryId);
	}
}

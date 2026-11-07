/*
 * Copyright (c) 2026 WurstB+ Plus contributors
 *
 * This source code is subject to the terms of the GNU Affero General Public
 * License, version 3. If a copy of the AGPL was not distributed with this
 * file, You can obtain one at: https://www.gnu.org/licenses/agpl-3.0.txt
 */
// Ported from InvincibleMachineGun v1.9.0 (GPL-3.0).
package net.wurstclient.hacks;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Set;

import net.minecraft.world.phys.Vec3;
import net.wurstclient.Category;
import net.wurstclient.SearchTags;
import net.wurstclient.WurstClient;
import net.wurstclient.hack.Hack;
import net.wurstclient.settings.CheckboxSetting;
import net.wurstclient.settings.TextFieldSetting;
import net.wurstclient.util.ChatUtils;

@SearchTags({"tp server equivalent", "server equiv", "no setback", "防回弹",
	"服务端等价"})
public final class TPServerEquivalentHack extends Hack
{
	private static volatile TPServerEquivalentHack instance;

	private final TextFieldSetting modules = new TextFieldSetting("Modules",
		"Comma-separated names of TP modules that should use the"
			+ " server-equivalent movement path.",
		"GSuperVClip,GTpbot,GTpAura,GAntiLag");

	private final CheckboxSetting logDecisions = new CheckboxSetting(
		"Decision log",
		"Appends every server-equivalent movement decision to"
			+ " gcore-servequiv.log.",
		false);

	private volatile Set<String> registered = Set.of();
	private volatile String cachedRaw = "";

	public TPServerEquivalentHack()
	{
		super("TPServerEquivalent");
		setCategory(Category.MOVEMENT);
		addSetting(modules);
		addSetting(logDecisions);
		instance = this;
	}

	@Override
	protected void onEnable()
	{
		cachedRaw = "";
		ChatUtils.message("TPServerEquivalent: " + modules.getValue());
	}

	public static boolean isEnabledFor(String module)
	{
		TPServerEquivalentHack hack = instance;
		if(hack == null || !hack.isEnabled() || module == null
			|| module.isEmpty())
			return false;
		return hack.resolve().contains(module);
	}

	public static void logDecision(String module, Vec3 from, Vec3 to,
		int packets)
	{
		TPServerEquivalentHack hack = instance;
		if(hack == null || !hack.logDecisions.isChecked()
			|| WurstClient.MC.gameDirectory == null)
			return;

		Path path =
			WurstClient.MC.gameDirectory.toPath().resolve("gcore-servequiv.log");
		String line = String.format(Locale.ROOT,
			"%d %s from=[%.3f,%.3f,%.3f] to=[%.3f,%.3f,%.3f] packets=%d",
			System.currentTimeMillis(), module, from.x, from.y, from.z, to.x,
			to.y, to.z, packets);

		try
		{
			Files.writeString(path, line + System.lineSeparator(),
				StandardCharsets.UTF_8, StandardOpenOption.CREATE,
				StandardOpenOption.APPEND);
		}catch(IOException ignored)
		{
			// Diagnostics must never break the game.
		}
	}

	private Set<String> resolve()
	{
		String raw = modules.getValue();
		if(raw.equals(cachedRaw))
			return registered;

		Set<String> set = new LinkedHashSet<>();
		for(String part : raw.split(","))
		{
			String name = part.trim();
			if(!name.isEmpty())
				set.add(name);
		}

		registered = Set.copyOf(set);
		cachedRaw = raw;
		return registered;
	}
}

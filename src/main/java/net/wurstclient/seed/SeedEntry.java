/*
 * Copyright (c) 2014-2025 Wurst-Imperium and contributors.
 *
 * This source code is subject to the terms of the GNU General Public
 * License, version 3. If a copy of the GPL was not distributed with this
 * file, You can obtain one at: https://www.gnu.org/licenses/gpl-3.0.txt
 */
package net.wurstclient.seed;

/**
 * One remembered world seed together with the Minecraft version it was
 * recorded for, because seed based predictions are only meaningful for the
 * exact version that generated the world.
 */
public final class SeedEntry
{
	public long seed;
	public String version = "";
	public String note = "";
	
	public SeedEntry()
	{
		
	}
	
	public SeedEntry(long seed, String version)
	{
		this.seed = seed;
		this.version = version == null ? "" : version;
	}
}

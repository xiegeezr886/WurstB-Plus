/*
 * Copyright (c) 2014-2025 Wurst-Imperium and contributors.
 *
 * This source code is subject to the terms of the GNU General Public
 * License, version 3. If a copy of the GPL was not distributed with this
 * file, You can obtain one at: https://www.gnu.org/licenses/gpl-3.0.txt
 */
package net.wurstclient.perimeter.config;

import java.math.BigDecimal;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

/**
 * Schema migration for the per-world configuration. Unversioned files are
 * upgraded to schema 1; files written by a newer schema are rejected rather
 * than silently downgraded.
 */
public final class PerimeterConfigMigration
{
	public static final int CURRENT_SCHEMA_VERSION = 1;
	
	private PerimeterConfigMigration()
	{
		
	}
	
	public static Result migrate(JsonObject source)
	{
		JsonObject migrated = source.deepCopy();
		int version = schemaVersion(migrated);
		
		if(version > CURRENT_SCHEMA_VERSION)
			throw new IllegalStateException("Configuration schema " + version
				+ " is newer than supported schema " + CURRENT_SCHEMA_VERSION
				+ ".");
		
		boolean changed = false;
		
		while(version < CURRENT_SCHEMA_VERSION)
		{
			version = migrateOne(migrated, version);
			changed = true;
		}
		
		if(!migrated.has("schemaVersion")
			|| migrated.get("schemaVersion").getAsInt() != CURRENT_SCHEMA_VERSION)
		{
			migrated.addProperty("schemaVersion", CURRENT_SCHEMA_VERSION);
			changed = true;
		}
		
		return new Result(migrated, changed);
	}
	
	private static int schemaVersion(JsonObject source)
	{
		JsonElement element = source.get("schemaVersion");
		
		if(element == null || element.isJsonNull())
			return 0;
		
		try
		{
			int value = new BigDecimal(element.getAsString()).intValueExact();
			
			if(value < 0)
				throw new IllegalStateException(
					"Configuration schema version cannot be negative.");
			
			return value;
			
		}catch(RuntimeException exception)
		{
			throw new IllegalStateException(
				"Configuration schema version must be a non-negative integer.",
				exception);
		}
	}
	
	private static int migrateOne(JsonObject config, int version)
	{
		if(version == 0)
		{
			config.addProperty("schemaVersion", 1);
			return 1;
		}
		
		throw new IllegalStateException(
			"No migration is available from configuration schema " + version
				+ ".");
	}
	
	public record Result(JsonObject config, boolean changed)
	{
	}
}

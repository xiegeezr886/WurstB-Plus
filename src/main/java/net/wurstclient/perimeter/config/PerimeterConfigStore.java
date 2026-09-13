/*
 * Copyright (c) 2014-2025 Wurst-Imperium and contributors.
 *
 * This source code is subject to the terms of the GNU General Public
 * License, version 3. If a copy of the GPL was not distributed with this
 * file, You can obtain one at: https://www.gnu.org/licenses/gpl-3.0.txt
 */
package net.wurstclient.perimeter.config;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Locale;
import java.util.UUID;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.world.level.storage.LevelResource;
import net.minecraftforge.fml.loading.FMLPaths;
import net.wurstclient.WurstClient;

/**
 * Stores the perimeter configuration separately for every server and every
 * singleplayer save, as the reference mod does, and writes it atomically.
 *
 * <p>
 * The file lives in {@code config/perimeter-digger/worlds/<uuid>.json}, where
 * the uuid is derived from the world identity:
 * {@code server:<address>} for multiplayer, {@code singleplayer:<path>} for a
 * local save and {@code local:<dimension>} as a fallback.
 */
public final class PerimeterConfigStore
{
	private static final Gson GSON =
		new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();
	
	private final String identity;
	private final Path file;
	
	public PerimeterConfigStore()
	{
		this(resolveIdentity());
	}
	
	public PerimeterConfigStore(String identity)
	{
		this.identity = identity;
		String name = UUID
			.nameUUIDFromBytes(identity.getBytes(StandardCharsets.UTF_8))
			.toString();
		this.file = FMLPaths.CONFIGDIR.get().resolve("perimeter-digger")
			.resolve("worlds").resolve(name + ".json");
	}
	
	public String identity()
	{
		return identity;
	}
	
	public Path path()
	{
		return file;
	}
	
	/**
	 * @return the stored configuration, or a fresh one with the reference
	 *         defaults when nothing has been stored yet or the file cannot be
	 *         read.
	 */
	public PerimeterConfig load()
	{
		PerimeterConfig config = null;
		
		if(Files.isRegularFile(file))
			try(Reader reader = Files.newBufferedReader(file,
				StandardCharsets.UTF_8))
			{
				JsonObject json = JsonParser.parseReader(reader)
					.getAsJsonObject();
				PerimeterConfigMigration.Result result =
					PerimeterConfigMigration.migrate(json);
				config = GSON.fromJson(result.config(), PerimeterConfig.class);
			}catch(Exception e)
			{
				System.err.println(
					"[Perimeter] could not read " + file + ": " + e);
			}
		
		if(config == null)
			config = new PerimeterConfig();
		
		config.normalize();
		return config;
	}
	
	public boolean save(PerimeterConfig config)
	{
		try
		{
			config.normalize();
			config.schemaVersion =
				PerimeterConfigMigration.CURRENT_SCHEMA_VERSION;
			Files.createDirectories(file.getParent());
			
			Path temporary = file.resolveSibling(file.getFileName() + ".tmp");
			
			try(Writer writer = Files.newBufferedWriter(temporary,
				StandardCharsets.UTF_8))
			{
				GSON.toJson(config, writer);
			}
			
			try
			{
				Files.move(temporary, file,
					StandardCopyOption.REPLACE_EXISTING,
					StandardCopyOption.ATOMIC_MOVE);
			}catch(IOException e)
			{
				Files.move(temporary, file,
					StandardCopyOption.REPLACE_EXISTING);
			}
			
			return true;
		}catch(Exception e)
		{
			System.err.println("[Perimeter] could not write " + file + ": " + e);
			return false;
		}
	}
	
	public boolean delete()
	{
		try
		{
			return Files.deleteIfExists(file);
		}catch(IOException e)
		{
			return false;
		}
	}
	
	/**
	 * Works out which world the player is in. Kept independent of the config
	 * file so that it can be exercised from tests and commands.
	 */
	public static String resolveIdentity()
	{
		Minecraft mc = WurstClient.MC;
		
		if(mc == null)
			return "local:unknown";
		
		ServerData server = mc.getCurrentServer();
		
		if(server != null && server.ip != null && !server.ip.isBlank())
			return "server:" + server.ip.toLowerCase(Locale.ROOT);
		
		if(mc.hasSingleplayerServer() && mc.getSingleplayerServer() != null)
			try
			{
				Path root = mc.getSingleplayerServer()
					.getWorldPath(LevelResource.ROOT).toAbsolutePath()
					.normalize();
				return "singleplayer:" + root.toString()
					.replace('\\', '/').toLowerCase(Locale.ROOT);
			}catch(Exception e)
			{
				// fall through to the dimension based identity
			}
		
		if(mc.level != null)
			return "local:" + mc.level.dimension().location();
		
		return "local:unknown";
	}
}

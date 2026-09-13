/*
 * Copyright (c) 2014-2025 Wurst-Imperium and contributors.
 *
 * This source code is subject to the terms of the GNU General Public
 * License, version 3. If a copy of the GPL was not distributed with this
 * file, You can obtain one at: https://www.gnu.org/licenses/gpl-3.0.txt
 */
package net.wurstclient.seed;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import net.minecraft.client.Minecraft;
import net.minecraft.client.server.IntegratedServer;
import net.minecraftforge.fml.loading.FMLPaths;
import net.wurstclient.perimeter.config.PerimeterConfigStore;

/**
 * Remembers the world seed of every server and every singleplayer save the
 * player has entered, keyed by the same identity that
 * {@link PerimeterConfigStore#resolveIdentity()} produces.
 *
 * <p>
 * Everything lives in a single {@code config/seed-predictor/seeds.json}, which
 * is written atomically, so that a crashed write can never truncate the file.
 * The world identity is the map key, which means the file is shared between
 * worlds while {@link #current()} and {@link #stored()} pick the entry that
 * belongs to the world the player is in right now.
 */
public final class SeedStore
{
	private static final Gson GSON =
		new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();
	private static final int CURRENT_SCHEMA_VERSION = 1;
	
	private static SeedStore instance;
	
	private final String identity;
	private final Path file;
	private final Map<String, SeedEntry> entries = new LinkedHashMap<>();
	private boolean loaded;
	
	public SeedStore()
	{
		this(defaultPath(), PerimeterConfigStore.resolveIdentity());
	}
	
	private SeedStore(Path file, String identity)
	{
		this.file = file;
		this.identity = identity;
	}
	
	/**
	 * Creates a store that reads and writes exactly the given file. Used by
	 * the tests so that they never touch the real config directory, and
	 * available to commands that want to work on a fixed identity.
	 */
	public static SeedStore atPath(Path file, String identity)
	{
		return new SeedStore(file, identity);
	}
	
	/**
	 * @return the shared store for the running game client.
	 */
	public static SeedStore get()
	{
		if(instance == null)
			instance = new SeedStore();
		
		return instance;
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
	 * Reads the file into memory. A missing file is not an error, it simply
	 * means that nothing has been stored yet, but a file that exists and
	 * cannot be parsed is reported as a failure.
	 */
	public boolean load()
	{
		entries.clear();
		loaded = true;
		
		if(!Files.isRegularFile(file))
			return true;
		
		try(Reader reader = Files.newBufferedReader(file,
			StandardCharsets.UTF_8))
		{
			SeedFile parsed = GSON.fromJson(reader, SeedFile.class);
			
			if(parsed == null || parsed.entries == null)
				return true;
			
			parsed.entries.forEach((key, entry) -> {
				if(key == null || entry == null)
					return;
				
				if(entry.version == null)
					entry.version = "";
				
				if(entry.note == null)
					entry.note = "";
				
				entries.put(key, entry);
			});
			
			return true;
		}catch(Exception e)
		{
			System.err.println(
				"[Seed] could not read " + file + ": " + e);
			return false;
		}
	}
	
	/**
	 * Writes the file atomically, exactly like
	 * {@link PerimeterConfigStore#save}.
	 */
	public boolean save()
	{
		try
		{
			SeedFile out = new SeedFile();
			out.schemaVersion = CURRENT_SCHEMA_VERSION;
			out.entries = new LinkedHashMap<>(entries);
			
			Path parent = file.getParent();
			if(parent != null)
				Files.createDirectories(parent);
			
			Path temporary = file.resolveSibling(file.getFileName() + ".tmp");
			
			try(Writer writer = Files.newBufferedWriter(temporary,
				StandardCharsets.UTF_8))
			{
				GSON.toJson(out, writer);
			}
			
			try
			{
				Files.move(temporary, file, StandardCopyOption.REPLACE_EXISTING,
					StandardCopyOption.ATOMIC_MOVE);
			}catch(IOException e)
			{
				Files.move(temporary, file, StandardCopyOption.REPLACE_EXISTING);
			}
			
			loaded = true;
			return true;
		}catch(Exception e)
		{
			System.err.println(
				"[Seed] could not write " + file + ": " + e);
			return false;
		}
	}
	
	public boolean delete()
	{
		try
		{
			entries.clear();
			loaded = true;
			return Files.deleteIfExists(file);
		}catch(IOException e)
		{
			return false;
		}
	}
	
	/**
	 * @return in singleplayer the seed of the running integrated server,
	 *         otherwise the entry stored for this world identity, or
	 *         {@code null} when neither is available.
	 */
	public SeedEntry current()
	{
		Minecraft mc = minecraft();
		
		if(mc != null && mc.hasSingleplayerServer())
			try
			{
				IntegratedServer server = mc.getSingleplayerServer();
				
				if(server != null)
					return new SeedEntry(server.overworld().getSeed(),
						server.getServerVersion());
			}catch(Throwable t)
			{
				// fall through to the stored entry
			}
		
		return stored();
	}
	
	/**
	 * @return only the entry that was stored for this world identity, without
	 *         reading the singleplayer server seed.
	 */
	public SeedEntry stored()
	{
		ensureLoaded();
		return entries.get(identity);
	}
	
	public void set(long seed, String version)
	{
		ensureLoaded();
		entries.put(identity, new SeedEntry(seed, version));
		save();
	}
	
	public void setFromText(String seedText, String version)
	{
		set(parseSeed(seedText), version);
	}
	
	public boolean remove()
	{
		ensureLoaded();
		
		if(entries.remove(identity) == null)
			return false;
		
		save();
		return true;
	}
	
	public Map<String, SeedEntry> all()
	{
		ensureLoaded();
		return Collections.unmodifiableMap(new LinkedHashMap<>(entries));
	}
	
	private void ensureLoaded()
	{
		if(!loaded)
			load();
	}
	
	private static Path defaultPath()
	{
		return FMLPaths.CONFIGDIR.get().resolve("seed-predictor")
			.resolve("seeds.json");
	}
	
	/**
	 * The same rule the vanilla Java Edition uses on the world creation
	 * screen: a valid number is the seed itself, anything else is hashed.
	 */
	public static long parseSeed(String text)
	{
		if(text == null)
			return 0L;
		
		try
		{
			return Long.parseLong(text);
		}catch(NumberFormatException e)
		{
			return text.strip().hashCode();
		}
	}
	
	public static String describe(long seed)
	{
		return String.format(Locale.ROOT, "%d (0x%016X)", seed, seed);
	}
	
	private static Minecraft minecraft()
	{
		try
		{
			return Minecraft.getInstance();
		}catch(Throwable t)
		{
			return null;
		}
	}
	
	private static final class SeedFile
	{
		int schemaVersion = CURRENT_SCHEMA_VERSION;
		Map<String, SeedEntry> entries = new LinkedHashMap<>();
	}
}

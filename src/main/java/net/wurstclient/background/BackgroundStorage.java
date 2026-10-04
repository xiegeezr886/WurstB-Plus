/*
 * Copyright (c) 2014-2025 Wurst-Imperium and contributors.
 *
 * This source code is subject to the terms of the GNU General Public
 * License, version 3. If a copy of the GPL was not distributed with this
 * file, You can obtain one at: https://www.gnu.org/licenses/gpl-3.0.txt
 */
package net.wurstclient.background;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.stream.Stream;

import com.google.gson.JsonObject;

import net.wurstclient.util.json.JsonException;
import net.wurstclient.util.json.JsonUtils;

/**
 * The background library on disk:
 *
 * <pre>
 * &lt;instance&gt;/wurst/backgrounds/&lt;id&gt;/media.&lt;ext&gt;
 *                                     thumbnail.png
 *                                     meta.json
 * </pre>
 *
 * <p>
 * The built-in default is virtual and never stored, so an empty folder is a
 * valid state and the picker simply shows the default alone.
 *
 * <p>
 * Deliberately free of Minecraft types: decoding thumbnails is the caller's
 * job, which is what keeps this class unit testable against a temp folder.
 */
public final class BackgroundStorage
{
	public static final String DEFAULT_ID = "default";
	private static final String META_FILE = "meta.json";
	private static final String THUMBNAIL_FILE = "thumbnail.png";

	/** Longest id we generate, so a folder name stays readable. */
	private static final int MAX_ID_LENGTH = 48;

	private final Path root;

	public BackgroundStorage(Path root)
	{
		this.root = root;
	}

	public Path root()
	{
		return root;
	}

	public Path folder(String id)
	{
		return root.resolve(id);
	}

	public Path metaPath(String id)
	{
		return folder(id).resolve(META_FILE);
	}

	public Path thumbnailPath(String id)
	{
		return folder(id).resolve(THUMBNAIL_FILE);
	}

	/**
	 * @return every stored background, newest first.
	 */
	public List<BackgroundEntry> list()
	{
		List<BackgroundEntry> entries = new ArrayList<>();

		if(!Files.isDirectory(root))
			return entries;

		try(Stream<Path> children = Files.list(root))
		{
			children.filter(Files::isDirectory)
				.map(child -> read(child.getFileName().toString()))
				.filter(entry -> entry != null).forEach(entries::add);
		}catch(IOException e)
		{
			return entries;
		}

		entries.sort(Comparator.comparingLong(BackgroundEntry::importedAtMs)
			.reversed());
		return entries;
	}

	/**
	 * @return the stored background, or null when its folder, metadata or media
	 *         file is missing or unreadable, so a hand-deleted or half-copied
	 *         entry degrades to the default instead of throwing.
	 */
	public BackgroundEntry read(String id)
	{
		if(!isValidId(id))
			return null;

		Path meta = metaPath(id);

		if(!Files.isRegularFile(meta))
			return null;

		try
		{
			JsonObject json = JsonUtils.parseFile(meta).getAsJsonObject();
			String file = json.get("file").getAsString();
			BackgroundKind kind =
				BackgroundKind.valueOf(json.get("kind").getAsString());

			if(mediaPath(id, file) == null)
				return null;

			String title = json.has("title") ? json.get("title").getAsString()
				: id;
			String origin = json.has("origin")
				? json.get("origin").getAsString() : "";
			long imported = json.has("importedAt")
				? json.get("importedAt").getAsLong() : 0L;

			return new BackgroundEntry(id, kind, title, origin, imported);
		}catch(IOException | JsonException | RuntimeException e)
		{
			return null;
		}
	}

	/** The media file of an entry, resolved from the name stored in its
	 * metadata. Never null for a valid id. */
	public Path mediaPath(String id)
	{
		BackgroundEntry entry = read(id);
		if(entry == null)
			return null;

		try
		{
			JsonObject json = JsonUtils.parseFile(metaPath(id))
				.getAsJsonObject();
			return mediaPath(id, json.get("file").getAsString());
		}catch(IOException | JsonException | RuntimeException e)
		{
			return null;
		}
	}

	private Path mediaPath(String id, String file)
	{
		if(file == null || file.isBlank() || file.contains("/")
			|| file.contains("\\"))
			return null;

		Path path = folder(id).resolve(file);
		return Files.isRegularFile(path) ? path : null;
	}

	/**
	 * Copies a media file into the library.
	 *
	 * @param source
	 *            the file to copy in
	 * @param kind
	 *            what the file is
	 * @param title
	 *            the name shown in the picker
	 * @param origin
	 *            where it came from, shown as the card's subtitle
	 * @param thumbnailPng
	 *            a small preview, or null to leave the card blank until the
	 *            caller generates one
	 * @return the id of the new entry, or null when the copy failed
	 */
	public String importFile(Path source, BackgroundKind kind, String title,
		String origin, byte[] thumbnailPng)
	{
		if(source == null || kind == null || !Files.isRegularFile(source))
			return null;

		String id = uniqueId(slugify(title != null && !title.isBlank() ? title
			: source.getFileName().toString()));
		String file = "media" + extensionOf(source, kind);

		try
		{
			Path folder = folder(id);
			Files.createDirectories(folder);
			Files.copy(source, folder.resolve(file),
				StandardCopyOption.REPLACE_EXISTING);

			if(thumbnailPng != null)
				Files.write(thumbnailPath(id), thumbnailPng);

			JsonObject json = new JsonObject();
			json.addProperty("kind", kind.name());
			json.addProperty("file", file);
			json.addProperty("title", title == null ? id : title);
			json.addProperty("origin", origin == null ? "" : origin);
			json.addProperty("importedAt", System.currentTimeMillis());
			JsonUtils.toJson(json, metaPath(id));

			return id;
		}catch(IOException | JsonException | RuntimeException e)
		{
			return null;
		}
	}

	/**
	 * @return whether the entry was removed. The default is never on disk, so
	 *         asking to delete it is a no-op.
	 */
	public boolean delete(String id)
	{
		if(!isValidId(id))
			return false;

		Path folder = folder(id);

		if(!Files.isDirectory(folder))
			return false;

		try(Stream<Path> walk = Files.walk(folder))
		{
			walk.sorted(Comparator.reverseOrder()).forEach(path -> {
				try
				{
					Files.deleteIfExists(path);
				}catch(IOException e)
				{
					// a locked file leaves the folder behind; read() will then
					// drop the entry instead
				}
			});
			return true;
		}catch(IOException e)
		{
			return false;
		}
	}

	/**
	 * Whether an id is safe to use as a folder name. Rejecting separators and
	 * dot segments here is what keeps a stored preference from walking out of
	 * the library. Letters and digits are allowed in any script, so a Chinese
	 * wallpaper title still gives a readable folder name.
	 */
	public static boolean isValidId(String id)
	{
		if(id == null || id.isBlank() || id.length() > MAX_ID_LENGTH
			|| DEFAULT_ID.equals(id) || id.contains(".."))
			return false;

		for(int i = 0; i < id.length(); i++)
		{
			char c = id.charAt(i);

			if(c == '-' || c == '_' || Character.isLetterOrDigit(c))
				continue;

			return false;
		}

		return true;
	}

	/** Turns a display name into a folder-safe id. */
	public static String slugify(String name)
	{
		if(name == null)
			return "background";

		StringBuilder builder = new StringBuilder();
		boolean lastWasDash = true;

		for(int i = 0; i < name.length()
			&& builder.length() < MAX_ID_LENGTH; i++)
		{
			// lowering keeps ASCII names predictable without touching Chinese,
			// which has no case
			char c = Character.toLowerCase(name.charAt(i));

			if(Character.isLetterOrDigit(c) || c == '_')
			{
				builder.append(c);
				lastWasDash = false;
			}else if(!lastWasDash)
			{
				builder.append('-');
				lastWasDash = true;
			}
		}

		while(builder.length() > 0
			&& builder.charAt(builder.length() - 1) == '-')
			builder.setLength(builder.length() - 1);

		return builder.length() == 0 ? "background" : builder.toString();
	}

	private String uniqueId(String base)
	{
		if(!Files.exists(folder(base)))
			return base;

		for(int suffix = 2; suffix < 1000; suffix++)
		{
			String candidate = base + "-" + suffix;

			if(candidate.length() <= MAX_ID_LENGTH
				&& !Files.exists(folder(candidate)))
				return candidate;
		}

		return base + "-" + System.currentTimeMillis();
	}

	private static String extensionOf(Path source, BackgroundKind kind)
	{
		String name = source.getFileName().toString().toLowerCase(Locale.ROOT);
		int dot = name.lastIndexOf('.');

		if(dot >= 0 && dot < name.length() - 1)
			return name.substring(dot);

		return kind.preferredExtension();
	}
}

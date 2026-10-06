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
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Stream;

/**
 * Turns a Wallpaper Engine library into a list of things we can import.
 *
 * <p>
 * Video and image wallpapers import their own media, and a scene imports its
 * {@code scene.pkg} now that {@link WeSceneWallpaper} can draw one. Web and
 * application wallpapers still need Wallpaper Engine's runtime, so they import
 * their preview image instead and carry a note.
 *
 * <p>
 * Takes the folders as arguments rather than finding them, which is what makes
 * it unit testable against a temp folder.
 */
public final class WallpaperEngineImporter
{
	/** Guards against a pathological library producing an endless list. */
	public static final int MAX_CANDIDATES = 500;

	/**
	 * One wallpaper we could import.
	 *
	 * @param media
	 *            the file to copy into the library
	 * @param kind
	 *            what that file is
	 * @param title
	 *            the name to show
	 * @param note
	 *            null when the wallpaper plays as-is, otherwise why it does not
	 * @param thumbnail
	 *            a file the thumbnail can be decoded from, or null when there is
	 *            nothing readable to decode - a scene ships a package rather
	 *            than an image, so it falls back to its preview
	 */
	public record Candidate(Path media, BackgroundKind kind, String title,
		String note, Path thumbnail)
	{
		public boolean playable()
		{
			return note == null;
		}
	}

	private WallpaperEngineImporter()
	{
	}

	/**
	 * @param folders
	 *            workshop content directories and local project directories
	 * @return one candidate per readable wallpaper, playable ones first.
	 */
	public static List<Candidate> scan(List<Path> folders)
	{
		List<Candidate> candidates = new ArrayList<>();

		for(Path folder : folders)
		{
			if(candidates.size() >= MAX_CANDIDATES)
				break;

			scanFolder(folder, candidates);
		}

		candidates.sort(Comparator.comparing(Candidate::playable).reversed()
			.thenComparing(Candidate::title, String.CASE_INSENSITIVE_ORDER));
		return candidates;
	}

	private static void scanFolder(Path folder, List<Candidate> candidates)
	{
		if(folder == null || !Files.isDirectory(folder))
			return;

		try(Stream<Path> children = Files.list(folder))
		{
			children.filter(Files::isDirectory).sorted()
				.forEach(child -> readProject(child, candidates));
		}catch(IOException e)
		{
			// an unreadable library is skipped
		}
	}

	private static void readProject(Path wallpaper, List<Candidate> candidates)
	{
		if(candidates.size() >= MAX_CANDIDATES)
			return;

		Path json = wallpaper.resolve("project.json");

		if(!Files.isRegularFile(json))
			return;

		String title = wallpaper.getFileName().toString();

		try
		{
			ProjectJson project = ProjectJson.parse(Files.readString(json));

			if(project == null)
				return;

			if(!project.title().isBlank())
				title = project.title();

			Path media = resolveMedia(wallpaper, project);

			if(media != null)
			{
				candidates.add(new Candidate(media,
					BackgroundKind.fromFileName(media.getFileName().toString()),
					title, null, resolveThumbnail(wallpaper, project, media)));
				return;
			}

			// no media we can play: fall back to the preview, and say why
			Path preview = resolvePreview(wallpaper, project);

			if(preview != null)
				candidates.add(new Candidate(preview, BackgroundKind.IMAGE,
					title, project.unplayableReason(), preview));
		}catch(IOException e)
		{
			// a wallpaper we cannot read is skipped
		}
	}

	/**
	 * What the thumbnail should be decoded from.
	 *
	 * <p>
	 * An image or GIF is its own thumbnail. Anything else - a scene package, a
	 * video - is not something the decoder can read, so it uses the project's
	 * preview image, and shows a placeholder if it ships none.
	 * </p>
	 */
	private static Path resolveThumbnail(Path wallpaper, ProjectJson project,
		Path media)
	{
		BackgroundKind kind =
			BackgroundKind.fromFileName(media.getFileName().toString());

		if(kind == BackgroundKind.IMAGE || kind == BackgroundKind.GIF)
			return media;

		return resolvePreview(wallpaper, project);
	}

	/** 目录式场景工程里真正可读的那个包。 */
	private static final String SCENE_PACKAGE = "scene.pkg";

	/**
	 * The wallpaper's own media, when we can decode it.
	 *
	 * <p>
	 * 场景类型**优先认同目录的 {@code scene.pkg}**：新版 Wallpaper Engine 的场景
	 * 工程是目录式布局，{@code project.json} 的 {@code "file"} 写的是
	 * {@code scene.json}，而那个文件并不存在（场景散在文件夹里），真正能读的是
	 * 打包好的 {@code scene.pkg}。实测用户库里 83 个场景壁纸**全部**是这种情况，
	 * 只按扩展名判断会把它们整批降级成预览图——表现就是「场景壁纸除了内置那个都
	 * 跑不起来」。
	 * </p>
	 */
	private static Path resolveMedia(Path wallpaper, ProjectJson project)
	{
		if(ProjectJson.TYPE_SCENE.equals(project.type()))
		{
			Path pkg = existingChild(wallpaper, SCENE_PACKAGE);

			if(pkg != null)
				return pkg;
		}

		if(!project.isPlayableMedia())
			return null;

		return existingChild(wallpaper, project.file());
	}

	private static Path resolvePreview(Path wallpaper, ProjectJson project)
	{
		Path declared = existingChild(wallpaper, project.preview());

		if(declared != null)
			return declared;

		// projects that declare no preview usually still ship one of these
		for(String name : new String[]{"preview.jpg", "preview.png",
			"preview.gif"})
		{
			Path candidate = wallpaper.resolve(name);

			if(Files.isRegularFile(candidate))
				return candidate;
		}

		return null;
	}

	/**
	 * Resolves a name from {@code project.json} inside the wallpaper folder,
	 * refusing anything that would escape it.
	 */
	private static Path existingChild(Path wallpaper, String name)
	{
		if(name == null || name.isBlank())
			return null;

		String normalized = name.replace('\\', '/');

		if(normalized.startsWith("/") || normalized.contains(".."))
			return null;

		Path resolved = wallpaper.resolve(normalized).normalize();

		if(!resolved.startsWith(wallpaper.normalize()))
			return null;

		return Files.isRegularFile(resolved) ? resolved : null;
	}
}

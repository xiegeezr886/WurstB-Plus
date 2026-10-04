/*
 * Copyright (c) 2014-2025 Wurst-Imperium and contributors.
 *
 * This source code is subject to the terms of the GNU General Public
 * License, version 3. If a copy of the GPL was not distributed with this
 * file, You can obtain one at: https://www.gnu.org/licenses/gpl-3.0.txt
 */
package net.wurstclient.background;

import java.util.Locale;

import com.google.gson.JsonObject;

import net.wurstclient.util.json.JsonUtils;

/**
 * The {@code project.json} of a Wallpaper Engine wallpaper.
 *
 * <p>
 * Only the fields that decide whether we can show the wallpaper are read. The
 * {@code type} is the important one: {@code video} wallpapers are an mp4 or
 * webm we might be able to play, while {@code scene}, {@code web} and
 * {@code application} wallpapers need Wallpaper Engine's own runtime and can
 * only be represented by their preview image.
 */
public record ProjectJson(String type, String file, String title,
	String preview)
{
	public static final String TYPE_VIDEO = "video";
	public static final String TYPE_SCENE = "scene";
	public static final String TYPE_WEB = "web";
	public static final String TYPE_APPLICATION = "application";

	/**
	 * @return the parsed project, or null when the file is missing or not a
	 *         project we can read.
	 */
	public static ProjectJson parse(String json)
	{
		if(json == null || json.isBlank())
			return null;

		try
		{
			JsonObject object = JsonUtils.GSON.fromJson(json, JsonObject.class);

			if(object == null)
				return null;

			String type = string(object, "type").toLowerCase(Locale.ROOT);
			String file = string(object, "file");
			String title = string(object, "title");
			String preview = string(object, "preview");

			if(type.isEmpty())
				type = file.isEmpty() ? "" : guessType(file);

			return new ProjectJson(type, file, title, preview);
		}catch(RuntimeException e)
		{
			return null;
		}
	}

	/** Wallpaper Engine omits {@code type} on older projects, so infer it. */
	private static String guessType(String file)
	{
		String lower = file.toLowerCase(Locale.ROOT);

		if(lower.endsWith(".mp4") || lower.endsWith(".webm")
			|| lower.endsWith(".m4v"))
			return TYPE_VIDEO;

		if(lower.endsWith(".html") || lower.endsWith(".htm"))
			return TYPE_WEB;

		return TYPE_SCENE;
	}

	private static String string(JsonObject object, String key)
	{
		return object.has(key) && object.get(key).isJsonPrimitive()
			? object.get(key).getAsString() : "";
	}

	/**
	 * Whether the wallpaper's own media can be shown in game.
	 *
	 * <p>
	 * Images, GIFs, mp4s and - since {@link WeSceneWallpaper} learned to draw
	 * one - scene packages are all media we can open. Web and application
	 * wallpapers cannot: they need Wallpaper Engine itself.
	 */
	public boolean isPlayableMedia()
	{
		if(TYPE_VIDEO.equals(type))
			return BackgroundKind.fromFileName(file) != null;

		return BackgroundKind.fromFileName(file) != null;
	}

	/** The reason shown next to an entry we can only preview. */
	public String unplayableReason()
	{
		return switch(type)
		{
			// 场景本来能画，走到这里说明包缺失或读不出来
			case TYPE_SCENE -> "场景包读不出来，已导入预览图";
			case TYPE_WEB -> "网页壁纸需要 Wallpaper Engine 运行，已导入预览图";
			case TYPE_APPLICATION -> "应用壁纸需要 Wallpaper Engine 运行，已导入预览图";
			default -> "该格式无法在游戏内播放，已导入预览图";
		};
	}
}

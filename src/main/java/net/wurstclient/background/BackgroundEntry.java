/*
 * Copyright (c) 2014-2025 Wurst-Imperium and contributors.
 *
 * This source code is subject to the terms of the GNU General Public
 * License, version 3. If a copy of the GPL was not distributed with this
 * file, You can obtain one at: https://www.gnu.org/licenses/gpl-3.0.txt
 */
package net.wurstclient.background;

/**
 * One background on disk.
 *
 * @param id
 *            the folder name, stable across launches
 * @param kind
 *            what the media file is
 * @param title
 *            the name shown in the picker
 * @param origin
 *            where it came from, shown as the card's subtitle: a file name, or
 *            "Wallpaper Engine" for an imported workshop item
 * @param importedAtMs
 *            when it was imported, for ordering
 */
public record BackgroundEntry(String id, BackgroundKind kind, String title,
	String origin, long importedAtMs)
{
	/** The built-in background, which is never on disk. */
	public static final BackgroundEntry DEFAULT = new BackgroundEntry(
		BackgroundStorage.DEFAULT_ID, BackgroundKind.IMAGE, "默认背景", "内置",
		0L);

	public boolean isDefault()
	{
		return BackgroundStorage.DEFAULT_ID.equals(id);
	}
}

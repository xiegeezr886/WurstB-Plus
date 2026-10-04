/*
 * Copyright (c) 2014-2025 Wurst-Imperium and contributors.
 *
 * This source code is subject to the terms of the GNU General Public
 * License, version 3. If a copy of the GPL was not distributed with this
 * file, You can obtain one at: https://www.gnu.org/licenses/gpl-3.0.txt
 */
package net.wurstclient.background;

import java.util.Locale;

/**
 * A kind of background media, decided by the file extension.
 *
 * <p>
 * Deliberately free of Minecraft types so the mapping can be unit tested.
 */
public enum BackgroundKind
{
	/** A single still image. */
	IMAGE,

	/** An animated GIF, played frame by frame. */
	GIF,

	/** An mp4/H.264 video, played frame by frame. */
	VIDEO;

	/**
	 * @return the kind a file name belongs to, or null when the extension is not
	 *         one we can decode.
	 */
	public static BackgroundKind fromFileName(String name)
	{
		if(name == null)
			return null;

		String lower = name.toLowerCase(Locale.ROOT);

		if(lower.endsWith(".gif"))
			return GIF;

		if(lower.endsWith(".mp4") || lower.endsWith(".m4v"))
			return VIDEO;

		if(lower.endsWith(".png") || lower.endsWith(".jpg")
			|| lower.endsWith(".jpeg") || lower.endsWith(".bmp"))
			return IMAGE;

		return null;
	}

	public boolean isAnimated()
	{
		return this != IMAGE;
	}

	/**
	 * Whether this client can actually show that kind.
	 *
	 * <p>
	 * Images and GIFs are decoded with the JDK's own readers; an mp4 needs an
	 * H.264 decoder, which is a dependency this project deliberately does not
	 * ship. Video wallpapers therefore stay preview-only, and the picker says so
	 * instead of letting the selection fail silently.
	 */
	public boolean canPlay()
	{
		return this != VIDEO;
	}

	/** The extension used when the media is copied into the data folder. */
	public String preferredExtension()
	{
		return switch(this)
		{
			case GIF -> ".gif";
			case VIDEO -> ".mp4";
			case IMAGE -> ".png";
		};
	}
}

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
	VIDEO,

	/**
	 * A Wallpaper Engine {@code scene.pkg}, drawn as its own layer tree.
	 *
	 * <p>
	 * Only the static part of a scene is drawn: the image layers, in order, with
	 * their parallax. Particle layers, the clock, and the GLSL post effects
	 * (godrays, blur, film grain, water waves) are not implemented.
	 * </p>
	 */
	SCENE;

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

		if(lower.endsWith(".pkg"))
			return SCENE;

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
	 * Images and GIFs are decoded with the JDK's own readers, a scene is drawn from
	 * its own package ({@link WeSceneWallpaper}), and an mp4 is decoded with JCodec
	 * ({@link BackgroundVideo}). So every kind is playable in principle.
	 *
	 * <p>
	 * Video is the one kind where "in principle" is not enough: JCodec only decodes
	 * H.264, so an HEVC/VP9/AV1 file is imported and shown as a card but cannot be
	 * played. That is a property of the file, not of the kind, so it is decided by
	 * {@link BackgroundVideo#probe(java.nio.file.Path)} - the picker asks before it
	 * lets a video be selected, and {@link BackgroundManager} falls back to the
	 * built-in background when a stored selection turns out not to decode.
	 */
	public boolean canPlay()
	{
		return true;
	}

	/** The extension used when the media is copied into the data folder. */
	public String preferredExtension()
	{
		return switch(this)
		{
			case GIF -> ".gif";
			case VIDEO -> ".mp4";
			case SCENE -> ".pkg";
			case IMAGE -> ".png";
		};
	}
}

/*
 * Copyright (c) 2014-2025 Wurst-Imperium and contributors.
 *
 * This source code is subject to the terms of the GNU General Public
 * License, version 3. If a copy of the GPL was not distributed with this
 * file, You can obtain one at: https://www.gnu.org/licenses/gpl-3.0.txt
 */
package net.wurstclient.twilight;

import java.nio.charset.StandardCharsets;

/**
 * Where the Twilight Echo port talks to NeteaseCloudMusicApiEnhanced.
 *
 * <p>
 * The reference project runs that service next to the player and calls it over
 * {@code http://127.0.0.1:3000}. This class holds the base URL rules and the
 * endpoint paths, and is deliberately free of Minecraft, Skia and HTTP so the
 * URL building can be unit tested. The requests themselves live in
 * {@link TwilightMusicService}.
 *
 * <p>
 * The repository's {@code NeteaseCloudApi} speaks the direct encrypted
 * {@code music.163.com} endpoints instead, so this is a second client rather
 * than a redirected one; the two share song ids and the login cookie. See
 * {@code docs/twilight-echo-port/data-layer.md} for the full endpoint list of
 * the reference and {@code wiring-status.md} for what is wired up.
 */
public final class TwilightApiEndpoint
{
	public static final String DEFAULT_BASE = "http://127.0.0.1:3000";
	
	public static final String QR_KEY = "/login/qr/key";
	public static final String QR_CREATE = "/login/qr/create";
	public static final String QR_CHECK = "/login/qr/check";
	public static final String LOGIN_STATUS = "/login/status";
	public static final String DAILY_SONGS = "/recommend/songs";
	public static final String PERSONAL_FM = "/personal_fm";
	public static final String LIKED_LIST = "/liked/list";
	public static final String LIKED_IDS = "/likelist";
	public static final String SONG_DETAIL = "/song/detail";
	public static final String PLAYLIST_DETAIL = "/playlist/detail";
	public static final String PLAYLIST_TRACKS = "/playlist/track/all";
	public static final String RECOMMEND_PLAYLISTS = "/personalized";
	public static final String SEARCH = "/cloudsearch";
	
	private TwilightApiEndpoint()
	{}
	
	/**
	 * Accepts what a user would actually type ({@code 127.0.0.1:3000},
	 * {@code http://host:3000/}) and returns a usable root, falling back to
	 * {@link #DEFAULT_BASE} for empty input.
	 */
	public static String normalize(String base)
	{
		if(base == null || base.isBlank())
			return DEFAULT_BASE;
		
		String root = base.trim();
		
		if(!root.startsWith("http://") && !root.startsWith("https://"))
			root = "http://" + root;
		
		while(root.endsWith("/"))
			root = root.substring(0, root.length() - 1);
		
		return root.isEmpty() ? DEFAULT_BASE : root;
	}
	
	public static String url(String base, String path)
	{
		String root = normalize(base);
		String route = path == null || path.isBlank() ? "/" : path.trim();
		
		if(!route.startsWith("/"))
			route = "/" + route;
		
		return root + route;
	}
	
	/** Builds {@code url?key=value&...}; null pairs are skipped. */
	public static String url(String base, String path, String... params)
	{
		StringBuilder builder = new StringBuilder(url(base, path));
		boolean first = true;
		
		for(int i = 0; i + 1 < params.length; i += 2)
		{
			String key = params[i];
			String value = params[i + 1];
			
			if(key == null || value == null)
				continue;
			
			builder.append(first ? '?' : '&');
			first = false;
			builder.append(encode(key)).append('=').append(encode(value));
		}
		
		return builder.toString();
	}
	
	/** Percent-encodes one query component as UTF-8. */
	public static String encode(String value)
	{
		StringBuilder builder = new StringBuilder();
		
		for(int i = 0; i < value.length(); i++)
		{
			char c = value.charAt(i);
			
			if(isUnreserved(c))
			{
				builder.append(c);
				continue;
			}
			
			if(c == ' ')
			{
				builder.append("%20");
				continue;
			}
			
			for(byte b : String.valueOf(c).getBytes(StandardCharsets.UTF_8))
				builder.append('%')
					.append(String.format("%02X", b & 0xFF));
		}
		
		return builder.toString();
	}
	
	private static boolean isUnreserved(char c)
	{
		return c >= 'a' && c <= 'z' || c >= 'A' && c <= 'Z'
			|| c >= '0' && c <= '9' || c == '-' || c == '_' || c == '.'
			|| c == '~';
	}
}

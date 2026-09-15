/*
 * Copyright (c) 2014-2025 Wurst-Imperium and contributors.
 *
 * This source code is subject to the terms of the GNU General Public
 * License, version 3. If a copy of the GPL was not distributed with this
 * file, You can obtain one at: https://www.gnu.org/licenses/gpl-3.0.txt
 */
package net.wurstclient.twilight;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

public final class TwilightApiEndpointTest
{
	@Test
	public void normalizesUserInput()
	{
		assertEquals(TwilightApiEndpoint.DEFAULT_BASE,
			TwilightApiEndpoint.normalize(null));
		assertEquals(TwilightApiEndpoint.DEFAULT_BASE,
			TwilightApiEndpoint.normalize("   "));
		assertEquals("http://127.0.0.1:3000",
			TwilightApiEndpoint.normalize("127.0.0.1:3000"));
		assertEquals("http://127.0.0.1:3000",
			TwilightApiEndpoint.normalize("  http://127.0.0.1:3000//  "));
		assertEquals("https://music.example.com",
			TwilightApiEndpoint.normalize("https://music.example.com/"));
	}
	
	@Test
	public void buildsRoutes()
	{
		assertEquals("http://127.0.0.1:3000/search",
			TwilightApiEndpoint.url("127.0.0.1:3000", "search"));
		assertEquals("http://127.0.0.1:3000/login/qr/key",
			TwilightApiEndpoint.url(TwilightApiEndpoint.DEFAULT_BASE,
				TwilightApiEndpoint.QR_KEY));
	}
	
	@Test
	public void buildsQueries()
	{
		String url = TwilightApiEndpoint.url(TwilightApiEndpoint.DEFAULT_BASE,
			TwilightApiEndpoint.SEARCH, "keywords", "周杰伦", "limit", "30",
			"ignored", null);
		
		assertTrue(url.startsWith(
			"http://127.0.0.1:3000/cloudsearch?keywords=%E5%91%A8%E6%9D%B0%E4%BC%A6&limit=30"),
			url);
		assertTrue(!url.contains("ignored"), url);
	}
	
	@Test
	public void encodesQueryComponents()
	{
		assertEquals("a%20b", TwilightApiEndpoint.encode("a b"));
		assertEquals("A-z_0.9~", TwilightApiEndpoint.encode("A-z_0.9~"));
		assertEquals("%26%3D%3F", TwilightApiEndpoint.encode("&=?"));
	}
}

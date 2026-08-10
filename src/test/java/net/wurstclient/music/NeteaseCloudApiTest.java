package net.wurstclient.music;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

final class NeteaseCloudApiTest
{
	@Test
	void keepsOnlyNeteaseSessionCookies()
	{
		String raw = "NMTID=ignored; Path=/, MUSIC_U=user-token; Path=/; "
			+ "__csrf=csrf-token; Max-Age=123, MUSIC_R_T=refresh-token";

		assertEquals(
			"MUSIC_U=user-token; __csrf=csrf-token; MUSIC_R_T=refresh-token",
			NeteaseCloudApi.filterSessionCookie(raw));
	}

	@Test
	void handlesMissingCookies()
	{
		assertEquals("", NeteaseCloudApi.filterSessionCookie(null));
		assertEquals("", NeteaseCloudApi.filterSessionCookie("Path=/"));
	}

}

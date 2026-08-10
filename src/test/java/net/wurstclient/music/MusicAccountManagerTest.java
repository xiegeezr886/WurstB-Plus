package net.wurstclient.music;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

final class MusicAccountManagerTest
{
	@Test
	void acceptsQQMusicSessionCookies()
	{
		MusicAccountManager.ParsedCookie parsed = MusicAccountManager.parseCookie(
			MusicProvider.QQ, "uin=o123456; qqmusic_key=secret; other=value");

		assertTrue(parsed.valid());
		assertEquals("123456", parsed.userId());
	}

	@Test
	void acceptsKugouCompoundCookies()
	{
		MusicAccountManager.ParsedCookie parsed = MusicAccountManager.parseCookie(
			MusicProvider.KUGOU,
			"KuGoo=KugooID=42&KugooPwd=secret; kg_mid=device");

		assertTrue(parsed.valid());
		assertEquals("42", parsed.userId());
	}

	@Test
	void rejectsIncompleteSessions()
	{
		assertFalse(MusicAccountManager
			.parseCookie(MusicProvider.QQ, "uin=123456").valid());
		assertFalse(MusicAccountManager
			.parseCookie(MusicProvider.KUGOU, "userid=42").valid());
	}
}

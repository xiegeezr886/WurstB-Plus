package net.wurstclient.music;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

final class NeteaseRecordsTest
{
	@Test
	void songNormalizesNullFieldsAndNegativeDuration()
	{
		NeteaseSong song = new NeteaseSong(1, null, null, null, null, -12);
		assertEquals("", song.name());
		assertEquals("", song.artist());
		assertEquals("", song.album());
		assertEquals("", song.coverUrl());
		assertEquals(0, song.durationMs());
	}

	@Test
	void playlistNormalizesNullFieldsAndNegativePlayCount()
	{
		NeteasePlaylist playlist =
			new NeteasePlaylist(2, null, null, -99);
		assertEquals("", playlist.name());
		assertEquals("", playlist.coverUrl());
		assertEquals(0, playlist.playCount());
	}

	@Test
	void userProfileNormalizesNullFields()
	{
		NeteaseUserProfile profile = new NeteaseUserProfile(3, null, null);
		assertEquals("", profile.nickname());
		assertEquals("", profile.avatarUrl());
	}

	@Test
	void providersExposeStableShortNames()
	{
		assertEquals("NE", MusicProvider.NETEASE.getShortName());
		assertEquals("QQ", MusicProvider.QQ.getShortName());
		assertEquals("KG", MusicProvider.KUGOU.getShortName());
		assertEquals("网易云", MusicProvider.NETEASE.getDisplayName());
	}
}

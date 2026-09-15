/*
 * Copyright (c) 2014-2025 Wurst-Imperium and contributors.
 *
 * This source code is subject to the terms of the GNU General Public
 * License, version 3. If a copy of the GPL was not distributed with this
 * file, You can obtain one at: https://www.gnu.org/licenses/gpl-3.0.txt
 */
package net.wurstclient.twilight;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.google.gson.JsonObject;

import net.wurstclient.music.NeteasePlaylist;
import net.wurstclient.music.NeteaseSong;

/**
 * Covers the pure half of {@link TwilightMusicService}: the shapes the local
 * NeteaseCloudMusicApiEnhanced service returns, and the defensive parsing that
 * keeps a malformed entry from blanking a whole page. Nothing here touches the
 * network.
 */
public final class TwilightMusicServiceTest
{
	private static final String SONG_JSON = """
		{"id":186016,"name":"晴天","dt":269000,
		 "ar":[{"name":"周杰伦"},{"name":"第二艺人"}],
		 "al":{"name":"叶惠美","picUrl":"http://p1.music.126.net/a.jpg"}}
		""";

	@Test
	public void readsSongsFromEitherEnvelope() throws IOException
	{
		JsonObject daily = TwilightMusicService
			.parse("{\"data\":{\"dailySongs\":[" + SONG_JSON + "]}}");
		assertEquals(1, TwilightMusicService.songsAt(daily, "data.dailySongs")
			.size());

		JsonObject search = TwilightMusicService
			.parse("{\"result\":{\"songs\":[" + SONG_JSON + "]}}");
		assertEquals(1, TwilightMusicService
			.songsAt(search, "data.dailySongs", "result.songs").size());

		JsonObject fm = TwilightMusicService.parse("{\"data\":[" + SONG_JSON
			+ "]}");
		assertEquals(1,
			TwilightMusicService.songsAt(fm, "data.dailySongs", "data").size());

		assertTrue(TwilightMusicService
			.songsAt(TwilightMusicService.parse("{}"), "data.dailySongs")
			.isEmpty());
	}

	@Test
	public void parsesSongFields() throws IOException
	{
		NeteaseSong song = TwilightMusicService
			.song(TwilightMusicService.firstArray(
				TwilightMusicService.parse("{\"songs\":[" + SONG_JSON + "]}"),
				"songs").get(0).getAsJsonObject());

		assertEquals(186016, song.id());
		assertEquals("晴天", song.name());
		assertEquals("周杰伦", song.artist());
		assertEquals("叶惠美", song.album());
		assertEquals("http://p1.music.126.net/a.jpg?param=200y200",
			song.coverUrl());
		assertEquals(269000, song.durationMs());
	}

	@Test
	public void acceptsAlternateFieldNames() throws IOException
	{
		// 老版本服务用 album/artists/duration，且封面已带尺寸参数
		NeteaseSong song = TwilightMusicService.song(TwilightMusicService
			.parse("{\"songs\":[{\"id\":\"7\",\"name\":\"A\",\"duration\":1000,"
				+ "\"artists\":[{\"name\":\"B\"}],\"album\":{\"name\":\"C\","
				+ "\"picUrl\":\"http://x/y.jpg?param=90y90\"}}]}")
			.getAsJsonObject().getAsJsonArray("songs").get(0).getAsJsonObject());

		assertEquals(7, song.id());
		assertEquals("B", song.artist());
		assertEquals("C", song.album());
		assertEquals("http://x/y.jpg?param=90y90", song.coverUrl());
		assertEquals(1000, song.durationMs());
	}

	@Test
	public void skipsMalformedEntriesInsteadOfFailing() throws IOException
	{
		JsonObject root = TwilightMusicService.parse("{\"songs\":["
			+ SONG_JSON + ",{\"name\":\"没有 id\"},\"不是对象\","
			+ "{\"id\":2,\"name\":\"好歌\",\"ar\":\"类型不对\","
			+ "\"al\":null,\"dt\":\"不合法的时长\"}]}");

		List<NeteaseSong> songs = TwilightMusicService.songsAt(root, "songs");

		assertEquals(2, songs.size());
		assertEquals("晴天", songs.get(0).name());
		assertEquals(2, songs.get(1).id());
		assertEquals("", songs.get(1).artist());
		assertEquals("", songs.get(1).album());
		assertEquals(0, songs.get(1).durationMs());
	}

	@Test
	public void parsesPlaylists() throws IOException
	{
		JsonObject root = TwilightMusicService.parse("{\"result\":["
			+ "{\"id\":5,\"name\":\"歌单\",\"picUrl\":\"http://c/d.jpg\","
			+ "\"playCount\":1234},{\"id\":6,\"name\":\"旧字段\","
			+ "\"coverImgUrl\":\"http://c/e.jpg\"}]}");

		List<NeteasePlaylist> playlists = TwilightMusicService
			.playlistsAt(root, "result", "data.result");

		assertEquals(2, playlists.size());
		assertEquals(5, playlists.get(0).id());
		assertEquals("http://c/d.jpg?param=200y200",
			playlists.get(0).coverUrl());
		assertEquals(1234, playlists.get(0).playCount());
		assertEquals("http://c/e.jpg?param=200y200",
			playlists.get(1).coverUrl());
		assertEquals(0, playlists.get(1).playCount());
	}

	@Test
	public void readsLikedIdsAndDropsJunk() throws IOException
	{
		JsonObject root = TwilightMusicService
			.parse("{\"ids\":[1,2,\"3\",null,\"x\",0,{\"id\":9}]}");

		assertArrayEquals(new long[]{1, 2, 3},
			TwilightMusicService.idsAt(root, "ids", "data.ids"));
		assertArrayEquals(new long[0],
			TwilightMusicService.idsAt(root, "data.ids"));
	}

	@Test
	public void readsNestedQrFields() throws IOException
	{
		JsonObject nested = TwilightMusicService.parse(
			"{\"data\":{\"unikey\":\"k1\",\"qrurl\":\"http://u\","
				+ "\"qrimg\":\"data:image/png;base64,AQID\"}}");
		assertEquals("k1",
			TwilightMusicService.firstString(nested, "data.unikey", "unikey"));
		assertEquals("data:image/png;base64,AQID", TwilightMusicService
			.firstString(nested, "data.qrimg", "qrimg"));

		// 扁平信封，且布尔值不能被当成字符串
		JsonObject flat = TwilightMusicService
			.parse("{\"unikey\":\"k2\",\"qrimg\":true}");
		assertEquals("k2",
			TwilightMusicService.firstString(flat, "data.unikey", "unikey"));
		assertEquals("",
			TwilightMusicService.firstString(flat, "data.qrimg", "qrimg"));
	}

	@Test
	public void decodesQrImageDataUrl() throws IOException
	{
		assertArrayEquals(new byte[]{1, 2, 3},
			TwilightMusicService.decodeDataUrl("data:image/png;base64,AQID"));
		assertArrayEquals(new byte[]{1, 2, 3},
			TwilightMusicService.decodeDataUrl("AQID"));
		assertArrayEquals(new byte[0],
			TwilightMusicService.decodeDataUrl("data:image/png;base64,"));
		assertArrayEquals(new byte[0],
			TwilightMusicService.decodeDataUrl("data:image/png;base64"));
		assertArrayEquals(new byte[0],
			TwilightMusicService.decodeDataUrl("data:image/png;base64,!!!!"));
		assertArrayEquals(new byte[0],
			TwilightMusicService.decodeDataUrl(null));
	}

	@Test
	public void rejectsBodiesThatAreNotObjects() throws IOException
	{
		assertThrows(IOException.class, () -> TwilightMusicService.parse(""));
		assertThrows(IOException.class,
			() -> TwilightMusicService.parse("{\"a\":"));
		assertThrows(IOException.class,
			() -> TwilightMusicService.parse("[1,2,3]"));
	}

	@Test
	public void walksDottedPaths() throws IOException
	{
		JsonObject root = TwilightMusicService
			.parse("{\"a\":{\"b\":{\"c\":7}},\"n\":null}");

		assertEquals(7, TwilightMusicService.number(
			TwilightMusicService.at(root, "a.b").getAsJsonObject(), "c"));
		assertTrue(TwilightMusicService.at(root, "a.b.c") != null);
		assertTrue(TwilightMusicService.at(root, "a.x.c") == null);
		assertTrue(TwilightMusicService.at(root, "a.n") == null);
		assertTrue(TwilightMusicService.at(root, "") == null);
	}

	@Test
	public void createsServiceWithoutTouchingNetwork() throws IOException
	{
		try(TwilightMusicService service = new TwilightMusicService())
		{
			assertEquals(TwilightApiEndpoint.DEFAULT_BASE, service.getBase());
			assertFalse(service.isReachable());
			assertEquals("", service.getLoginCookie());
			assertEquals("", service.getLastError());
		}

		try(TwilightMusicService service =
			new TwilightMusicService("127.0.0.1:4000/"))
		{
			assertEquals("http://127.0.0.1:4000", service.getBase());
			service.setBase("https://music.example.com");
			assertEquals("https://music.example.com", service.getBase());
		}
	}

	@Test
	public void qrRecordsDescribeTheirState() throws IOException
	{
		TwilightMusicService.QrSession failed =
			TwilightMusicService.QrSession.failed("  ");
		assertFalse(failed.isUsable());
		assertEquals("无法获取二维码", failed.error());

		TwilightMusicService.QrSession ready = new TwilightMusicService.QrSession(
			"k", "http://u", "data:image/png;base64,AQID", "");
		assertTrue(ready.isUsable());
		assertArrayEquals(new byte[]{1, 2, 3}, ready.imageBytes());

		assertTrue(new TwilightMusicService.QrState(800, "", "").isExpired());
		assertTrue(new TwilightMusicService.QrState(801, "", "").isWaiting());
		assertTrue(new TwilightMusicService.QrState(802, "", "").isScanned());
		assertTrue(new TwilightMusicService.QrState(803, "", "MUSIC_U=x")
			.isSuccess());
		assertFalse(new TwilightMusicService.QrState(0, "", "").isSuccess());
	}
}

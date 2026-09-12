package net.wurstclient.music;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;

import com.google.gson.JsonArray;
import com.google.gson.JsonNull;
import com.google.gson.JsonObject;

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

	@Test
	void parseSongAcceptsSearchAndDetailFieldNames()
	{
		JsonObject searchShape = new JsonObject();
		searchShape.addProperty("id", "186016");
		searchShape.addProperty("name", "海阔天空");
		searchShape.addProperty("duration", 326000);
		JsonObject album = new JsonObject();
		album.addProperty("name", "乐与怒");
		album.addProperty("picUrl", "https://p.music.163.com/cover.jpg");
		searchShape.add("album", album);
		JsonArray artists = new JsonArray();
		JsonObject artist = new JsonObject();
		artist.addProperty("name", "Beyond");
		artists.add(artist);
		searchShape.add("artists", artists);

		NeteaseSong parsed = NeteaseCloudApi.parseSong(searchShape);
		assertEquals(186016, parsed.id());
		assertEquals("海阔天空", parsed.name());
		assertEquals("Beyond", parsed.artist());
		assertEquals("乐与怒", parsed.album());
		assertEquals("https://p.music.163.com/cover.jpg?param=200y200",
			parsed.coverUrl());
		assertEquals(326000, parsed.durationMs());

		JsonObject detailShape = new JsonObject();
		detailShape.addProperty("id", 42);
		detailShape.addProperty("name", "Song");
		detailShape.addProperty("dt", 1234);
		JsonObject al = new JsonObject();
		al.addProperty("name", "Album");
		al.addProperty("picUrl", "https://p.music.163.com/a.jpg?param=300y300");
		detailShape.add("al", al);
		JsonArray ar = new JsonArray();
		JsonObject ar0 = new JsonObject();
		ar0.addProperty("name", "Artist");
		ar.add(ar0);
		detailShape.add("ar", ar);
		NeteaseSong detail = NeteaseCloudApi.parseSong(detailShape);
		assertEquals(42, detail.id());
		assertEquals("Artist", detail.artist());
		assertEquals("https://p.music.163.com/a.jpg?param=300y300",
			detail.coverUrl());
		assertEquals(1234, detail.durationMs());
	}

	@Test
	void parseSongSkipsMalformedArtistsAndNonNumericFields()
	{
		JsonObject song = new JsonObject();
		song.addProperty("id", "not-a-number");
		song.addProperty("name", true);
		song.add("album", new JsonArray());
		JsonArray artists = new JsonArray();
		artists.add(JsonNull.INSTANCE);
		artists.add("string-artist");
		song.add("artists", artists);

		NeteaseSong parsed = NeteaseCloudApi.parseSong(song);
		assertEquals(0, parsed.id());
		assertEquals("true", parsed.name());
		assertEquals("", parsed.artist());
		assertEquals("", parsed.album());
		assertEquals("", parsed.coverUrl());
	}

	@Test
	void imageUrlAddsSizeOnlyWhenMissing()
	{
		assertEquals("", NeteaseCloudApi.imageUrl(null));
		assertEquals("", NeteaseCloudApi.imageUrl("  "));
		assertEquals("https://p.music.163.com/a.jpg?param=200y200",
			NeteaseCloudApi.imageUrl("https://p.music.163.com/a.jpg"));
		assertEquals("https://p.music.163.com/a.jpg?param=300y300",
			NeteaseCloudApi.imageUrl(
				"https://p.music.163.com/a.jpg?param=300y300"));
	}

	@Test
	void jsonAccessorsSkipMalformedValues()
	{
		assertEquals("", NeteaseCloudApi.string(null, "name"));
		assertEquals(0, NeteaseCloudApi.number(null, "id"));
		assertEquals(0, NeteaseCloudApi.object(null, "al").size());
		assertTrue(NeteaseCloudApi.array(null, "ar").isEmpty());
		assertEquals(List.of(), NeteaseCloudApi.objects(null));

		JsonObject root = new JsonObject();
		root.add("name", new JsonArray());
		root.add("album", JsonNull.INSTANCE);
		root.addProperty("id", "12.5");
		root.addProperty("ok", true);
		root.addProperty("count", "7");
		JsonArray mixed = new JsonArray();
		mixed.add(JsonNull.INSTANCE);
		mixed.add("skip");
		JsonObject keep = new JsonObject();
		keep.addProperty("id", 1);
		mixed.add(keep);
		root.add("items", mixed);

		assertEquals("", NeteaseCloudApi.string(root, "name"));
		assertEquals("", NeteaseCloudApi.string(root, "missing"));
		assertEquals(0, NeteaseCloudApi.object(root, "album").size());
		assertEquals(0, NeteaseCloudApi.number(root, "id"));
		assertEquals(0, NeteaseCloudApi.number(root, "ok"));
		assertEquals(7, NeteaseCloudApi.number(root, "count"));
		assertEquals("true", NeteaseCloudApi.string(root, "ok"));
		assertEquals(List.of(keep),
			NeteaseCloudApi.objects(NeteaseCloudApi.array(root, "items")));
	}

	@Test
	void prefersDetailAlbumFieldsThenFallsBackToSearchShape()
	{
		JsonObject song = new JsonObject();
		song.addProperty("id", 9);
		song.addProperty("name", "Both");
		JsonObject al = new JsonObject();
		al.addProperty("name", "DetailAlbum");
		song.add("al", al);
		JsonObject album = new JsonObject();
		album.addProperty("name", "SearchAlbum");
		song.add("album", album);
		assertEquals("DetailAlbum", NeteaseCloudApi.parseSong(song).album());

		song.remove("al");
		assertEquals("SearchAlbum", NeteaseCloudApi.parseSong(song).album());
	}

	@Test
	void normalizesCountryCodes()
	{
		assertEquals("86", NeteaseCloudApi.normalizeCountryCode(null));
		assertEquals("86", NeteaseCloudApi.normalizeCountryCode(""));
		assertEquals("86", NeteaseCloudApi.normalizeCountryCode("abc"));
		assertEquals("86", NeteaseCloudApi.normalizeCountryCode("12345"));
		assertEquals("1", NeteaseCloudApi.normalizeCountryCode("1"));
		assertEquals("852", NeteaseCloudApi.normalizeCountryCode("852"));
	}
}

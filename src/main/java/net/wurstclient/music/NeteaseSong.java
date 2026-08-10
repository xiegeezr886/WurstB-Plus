package net.wurstclient.music;

public record NeteaseSong(long id, String name, String artist, String album,
	String coverUrl, long durationMs)
{
	public NeteaseSong
	{
		name = name == null ? "" : name;
		artist = artist == null ? "" : artist;
		album = album == null ? "" : album;
		coverUrl = coverUrl == null ? "" : coverUrl;
		durationMs = Math.max(0, durationMs);
	}
}

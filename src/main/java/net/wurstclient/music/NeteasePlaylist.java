package net.wurstclient.music;

public record NeteasePlaylist(long id, String name, String coverUrl,
	long playCount)
{
	public NeteasePlaylist
	{
		name = name == null ? "" : name;
		coverUrl = coverUrl == null ? "" : coverUrl;
		playCount = Math.max(0, playCount);
	}
}

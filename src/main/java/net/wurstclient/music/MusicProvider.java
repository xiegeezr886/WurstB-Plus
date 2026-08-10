package net.wurstclient.music;

public enum MusicProvider
{
	NETEASE("NE", "\u7f51\u6613\u4e91"),
	QQ("QQ", "QQ \u97f3\u4e50"),
	KUGOU("KG", "\u9177\u72d7\u97f3\u4e50");

	private final String shortName;
	private final String displayName;

	MusicProvider(String shortName, String displayName)
	{
		this.shortName = shortName;
		this.displayName = displayName;
	}

	public String getShortName()
	{
		return shortName;
	}

	public String getDisplayName()
	{
		return displayName;
	}
}

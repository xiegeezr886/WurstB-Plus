package net.wurstclient.music;

public record NeteaseUserProfile(long userId, String nickname,
	String avatarUrl)
{
	public NeteaseUserProfile
	{
		nickname = nickname == null ? "" : nickname;
		avatarUrl = avatarUrl == null ? "" : avatarUrl;
	}
}

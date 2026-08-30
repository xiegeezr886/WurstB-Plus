package net.wurstclient.music;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;

/**
 * 音乐平台数据与播放抽象接口。
 *
 * <p>每个音乐平台实现一份搜索、歌单、歌词、音源解析与登录能力。当前仅实现
 * 网易云（{@link NeteaseCloudApi}）；QQ/酷狗仍只通过 {@link MusicAccountManager}
 * 存储 Cookie，暂不提供播放实现。</p>
 */
public interface MusicPlatform
{
	String providerName();

	List<NeteaseSong> search(String query, int limit)
		throws IOException, InterruptedException;

	List<NeteaseSong> topNewSongs(int limit)
		throws IOException, InterruptedException;

	List<NeteasePlaylist> recommendedPlaylists(int limit)
		throws IOException, InterruptedException;

	List<NeteaseSong> playlistSongs(long playlistId, int limit, int offset)
		throws IOException, InterruptedException;

	List<NeteaseSong> likedSongs(int limit, int offset)
		throws IOException, InterruptedException;

	NeteaseCloudApi.SongResource resolve(NeteaseSong song)
		throws IOException, InterruptedException;

	List<LyricLine> lyrics(long songId)
		throws IOException, InterruptedException;

	Path download(NeteaseCloudApi.SongResource resource, Path target)
		throws IOException, InterruptedException;

	NeteaseCloudApi.LoginResult sendCaptcha(String phone, String countryCode)
		throws IOException, InterruptedException;

	NeteaseCloudApi.LoginResult loginWithCaptcha(String phone, String captcha,
		String countryCode) throws IOException, InterruptedException;

	NeteaseCloudApi.LoginResult loginWithCookie(String rawCookie)
		throws IOException, InterruptedException;

	NeteaseCloudApi.QrLogin beginQrLogin()
		throws IOException, InterruptedException;

	NeteaseCloudApi.QrCheck checkQrLogin(String key)
		throws IOException, InterruptedException;

	boolean refreshUserProfile() throws IOException, InterruptedException;

	boolean isLoggedIn();

	NeteaseUserProfile getUserProfile();

	void logout();
}

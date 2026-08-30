package net.wurstclient.clickgui2.music;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletionException;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.wurstclient.clickgui2.supersoft.UiTween;
import net.wurstclient.music.NeteaseMusicPlayer;
import net.wurstclient.music.NeteasePlaylist;
import net.wurstclient.music.NeteaseSong;

/**
 * 网易云音乐屏幕的共享状态与导航/异步服务。
 *
 * <p>子组件（各 Page 与播放栏/详情层）通过此对象访问播放器、封面缓存、
 * 动画、导航与异步回调，避免在组件间传递屏幕引用。</p>
 */
public final class MusicContext
{
	public final NeteaseMusicPlayer player;
	public Screen screen;
	public NeteaseImageCache images;

	public final Map<String, UiTween> hoverMotions = new HashMap<>();
	public final UiTween pageMotion = new UiTween(1, 200);
	public final UiTween detailMotion = new UiTween(0, 300);
	public final UiTween queueMotion = new UiTween(0, 220);
	public int accentColor = 0xFF007CFF;
	public int accentTarget = 0xFF007CFF;

	public Page page = Page.HOME;
	public NeteasePlaylist selectedPlaylist;
	public List<NeteaseSong> playlistSongs = List.of();
	public String message = "";
	public boolean contentLoading;
	public boolean detailVisible;
	public boolean queueVisible;
	public int scroll;
	public int playlistScroll;
	public long requestSequence;
	public Runnable onPageSwitched;
	public Runnable onLogout;

	public MusicContext(NeteaseMusicPlayer player)
	{
		this.player = player;
		images = new NeteaseImageCache();
	}

	public UiTween motion(String id)
	{
		return hoverMotions.computeIfAbsent(id,
			ignored -> new UiTween(0, 150));
	}

	public void switchPage(Page target)
	{
		if(page == target && selectedPlaylist == null)
			return;
		page = target;
		selectedPlaylist = null;
		scroll = 0;
		message = "";
		pageMotion.snap(0);
		if(onPageSwitched != null)
			onPageSwitched.run();
	}

	public void openPlaylist(NeteasePlaylist playlist)
	{
		selectedPlaylist = playlist;
		playlistSongs = List.of();
		contentLoading = true;
		scroll = 0;
		pageMotion.snap(0);
		long request = ++requestSequence;
		player.loadPlaylist(playlist).whenComplete((songs, error) ->
			runOnClient(() -> {
				if(request != requestSequence || selectedPlaylist != playlist)
					return;
				contentLoading = false;
				if(error == null)
					playlistSongs = songs;
				else
					message = readableMessage(error);
			}));
	}

	public void runOnClient(Runnable action)
	{
		Minecraft minecraft = Minecraft.getInstance();
		if(minecraft != null)
			minecraft.execute(() -> {
				if(minecraft.screen == screen)
					action.run();
			});
	}

	public String readableMessage(Throwable error)
	{
		Throwable current = error;
		while(current instanceof CompletionException
			&& current.getCause() != null)
			current = current.getCause();
		while(current.getCause() != null)
			current = current.getCause();
		return current.getMessage() == null ? "网易云请求失败"
			: current.getMessage();
	}

	public Bounds bounds()
	{
		int panelWidth = Math.min(MusicRegion.PANEL_WIDTH,
			Math.max(300, screen.width - 24));
		int panelHeight = Math.min(MusicRegion.PANEL_HEIGHT,
			Math.max(220, screen.height - 24));
		int left = (screen.width - panelWidth) / 2;
		int top = (screen.height - panelHeight) / 2;
		return new Bounds(left, top, left + panelWidth, top + panelHeight);
	}

	public enum Page
	{
		HOME,
		SEARCH,
		LIKE,
		LOGIN
	}

	public static final class Bounds
	{
		public final int left;
		public final int top;
		public final int right;
		public final int bottom;

		public Bounds(int left, int top, int right, int bottom)
		{
			this.left = left;
			this.top = top;
			this.right = right;
			this.bottom = bottom;
		}

		public int width()
		{
			return right - left;
		}

		public int height()
		{
			return bottom - top;
		}
	}
}

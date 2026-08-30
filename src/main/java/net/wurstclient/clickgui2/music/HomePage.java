package net.wurstclient.clickgui2.music;

import java.util.List;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.util.Mth;
import net.wurstclient.clickgui2.FlatRenderer;
import net.wurstclient.clickgui2.supersoft.SuperSoftTheme;
import net.wurstclient.music.NeteaseMusicPlayer;
import net.wurstclient.music.NeteasePlaylist;
import net.wurstclient.music.NeteaseSong;

/**
 * 首页：hero 卡、快捷入口、推荐歌单、新歌速递。
 */
public final class HomePage extends MusicRegion
{
	private List<NeteaseSong> homeSongs = List.of();
	private List<NeteasePlaylist> playlists = List.of();
	private boolean homeLoading;
	private boolean playlistLoading;

	public HomePage(MusicContext ctx)
	{
		super(ctx);
	}

	public void init()
	{
		if(homeSongs.isEmpty() && !homeLoading)
			loadHome();
	}

	public void render(GuiGraphics graphics, int left, int top, int right, int bottom,
		int mouseX, int mouseY)
	{
		int x = left + 8;
		int width = right - left - 16;
		int y = top + 8 - ctx.scroll;

		renderHomeHero(graphics, x, y, x + width, y + 84, mouseX, mouseY);
		y += 92;

		drawText(graphics, "快捷入口", 7, x, y, MUTED, width);
		y += 12;
		renderQuickGrid(graphics, x, y, x + width, y + 52, mouseX, mouseY);
		y += 62;

		drawText(graphics, "推荐歌单", 8, x, y, TEXT, width);
		y += 14;
		if(playlistLoading)
			renderLoading(graphics, (left + right) / 2, y + 25);
		else if(playlists.isEmpty())
			drawCenteredText(graphics, "暂无推荐歌单", 7,
				(left + right) / 2, y + 20, MUTED, width - 12);
		else
			for(int index = 0; index < playlists.size(); index++)
			{
				int cardX = x + index * 61 - ctx.playlistScroll;
				if(cardX + 55 <= x || cardX >= right)
					continue;
				renderPlaylistCard(graphics, playlists.get(index), index,
					cardX, y, mouseX, mouseY);
			}
		y += 74;
		drawText(graphics, "新歌速递", 8, x, y, TEXT, width);
		y += 13;
		int songStart = Math.min(5, homeSongs.size());
		int songEnd = Math.min(songStart + 6, homeSongs.size());
		renderSongRows(graphics, homeSongs.subList(songStart, songEnd),
			5, false, x, y, x + width, bottom, mouseX, mouseY);
		renderScrollbar(graphics, right - 3, top + 5, bottom - 5,
			contentHeight(), bottom - top);
	}

	private void renderHomeHero(GuiGraphics graphics, int left, int top,
		int right, int bottom, int mouseX, int mouseY)
	{
		boolean hovered = contains(mouseX, mouseY, left, top, right, bottom);
		float hover = motion("hero").update(hovered ? 1 : 0);
		FlatRenderer.fillRoundedRect(graphics, left, top, right, bottom, 10,
			SuperSoftTheme.mix(0x14181F, withAlpha(accent(), 0.16F), hover));
		FlatRenderer.drawRoundedOutline(graphics, left, top, right, bottom,
			10, SuperSoftTheme.mix(0x24FFFFFF, withAlpha(accent(), 0.55F),
				hover));
		NeteaseSong song = player().getCurrentSong();
		if(song != null)
		{
			drawCover(graphics, song.coverUrl(), left + 6, top + 6,
				left + 78, top + 78, 1);
			FlatRenderer.drawRoundedOutline(graphics, left + 6, top + 6,
				left + 78, top + 78, 8, 0x2AFFFFFF);
			drawText(graphics, song.name(), 11, left + 90, top + 16, TEXT,
				right - left - 130);
			drawText(graphics, song.artist(), 7, left + 90, top + 34, MUTED,
				right - left - 130);
			boolean playing = player().getState()
				== NeteaseMusicPlayer.PlaybackState.PLAYING;
			String state = playing ? "正在播放 · 点击打开详情"
				: "已暂停 · 点击继续播放";
			drawText(graphics, state, 6, left + 90, top + 54,
				withAlpha(accent(), 0.9F), right - left - 130);
			if(hover > 0.01F)
			{
				int cx = right - 34;
				int cy = (top + bottom) / 2;
				FlatRenderer.fillRoundedRect(graphics, cx - 13, cy - 13,
					cx + 13, cy + 13, 13, withAlpha(accent(), hover));
				drawPlay(graphics, cx + 1, cy, withAlpha(TEXT, hover));
			}
		}else
		{
			FlatRenderer.fillRoundedRect(graphics, left + 6, top + 6,
				left + 78, top + 78, 8, 0x12FFFFFF);
			drawText(graphics, "MINERADIO", 12, left + 90, top + 18, TEXT,
				right - left - 130);
			drawText(graphics, "搜索并播放你的网易云音乐", 7, left + 90,
				top + 38, MUTED, right - left - 130);
			if(hover > 0.01F)
			{
				int cx = right - 34;
				int cy = (top + bottom) / 2;
				FlatRenderer.fillRoundedRect(graphics, cx - 13, cy - 13,
					cx + 13, cy + 13, 13, withAlpha(accent(), hover));
				drawPlay(graphics, cx + 1, cy, withAlpha(TEXT, hover));
			}
		}
	}

	private void renderQuickGrid(GuiGraphics graphics, int left, int top,
		int right, int bottom, int mouseX, int mouseY)
	{
		int count = 4;
		int gap = 6;
		int cardWidth = (right - left - gap * (count - 1)) / count;
		String[] labels = { "音乐库", "每日推荐", "最近播放", "搜索" };
		String[] subs = { "我的喜欢", "每日新歌", "播放队列", "搜索歌曲" };
		for(int index = 0; index < count; index++)
		{
			int cardLeft = left + index * (cardWidth + gap);
			boolean hovered = contains(mouseX, mouseY, cardLeft, top,
				cardLeft + cardWidth, bottom);
			float hover = motion("quick-" + index).update(hovered ? 1 : 0);
			FlatRenderer.fillRoundedRect(graphics, cardLeft, top,
				cardLeft + cardWidth, bottom, 8,
				SuperSoftTheme.mix(0x14181F, withAlpha(accent(), 0.18F),
					hover));
			FlatRenderer.drawRoundedOutline(graphics, cardLeft, top,
				cardLeft + cardWidth, bottom, 8,
				SuperSoftTheme.mix(0x16FFFFFF, withAlpha(accent(), 0.5F),
					hover));
			drawCenteredText(graphics, labels[index], 8,
				cardLeft + cardWidth / 2, top + 9, TEXT, cardWidth - 10);
			drawCenteredText(graphics, subs[index], 5,
				cardLeft + cardWidth / 2, top + 24, MUTED, cardWidth - 10);
		}
	}

	private void renderPlaylistCard(GuiGraphics graphics,
		NeteasePlaylist playlist, int index, int left, int top, int mouseX,
		int mouseY)
	{
		boolean hovered = contains(mouseX, mouseY, left, top, left + 52,
			top + 64);
		float hover = motion("playlist-" + playlist.id()).update(hovered ? 1 : 0);
		int inset = Math.round(hover * -1);
		drawCover(graphics, playlist.coverUrl(), left + inset, top + inset,
			left + 50 - inset, top + 50 - inset, 1);
		FlatRenderer.drawRoundedOutline(graphics, left + inset, top + inset,
			left + 50 - inset, top + 50 - inset, 5,
			SuperSoftTheme.mix(0x33171C24, accent(), hover));
		String count = formatCount(playlist.playCount());
		graphics.fill(left + 28, top + 2, left + 48, top + 11, 0x99000000);
		drawText(graphics, count, 4, left + 30, top + 3, TEXT, 17);
		drawWrappedCenteredText(graphics, playlist.name(), 5, left + 25,
			top + 53, TEXT, 54, 2);
	}

	public boolean click(double mouseX, double mouseY, MusicContext.Bounds b)
	{
		int left = b.left + SIDEBAR_WIDTH + 8;
		int right = b.right - 8;
		int y = b.top + 8 - ctx.scroll;
		if(contains(mouseX, mouseY, left, y, right, y + 84))
		{
			if(player().getCurrentSong() != null)
			{
				ctx.queueVisible = false;
				ctx.detailVisible = true;
			}
			else if(!homeSongs.isEmpty())
				player().play(homeSongs, 0);
			return true;
		}
		y += 104;
		int count = 4;
		int gap = 6;
		int width = right - left - 16;
		int cardWidth = (width - gap * (count - 1)) / count;
		for(int index = 0; index < count; index++)
		{
			int cardLeft = left + 8 + index * (cardWidth + gap);
			if(contains(mouseX, mouseY, cardLeft, y, cardLeft + cardWidth,
				y + 52))
			{
				switch(index)
				{
					case 0 -> ctx.switchPage(MusicContext.Page.LIKE);
					case 1 ->
					{
						if(!playlists.isEmpty())
							ctx.openPlaylist(playlists.get(0));
						else
							loadHome();
					}
					case 2 -> ctx.queueVisible = !ctx.queueVisible;
					case 3 -> ctx.switchPage(MusicContext.Page.SEARCH);
				}
				return true;
			}
		}
		y += 62;
		y += 14;
		for(int index = 0; index < playlists.size(); index++)
		{
			int cardX = left + 8 + index * 61 - ctx.playlistScroll;
			if(contains(mouseX, mouseY, cardX, y, cardX + 52, y + 64))
			{
				ctx.openPlaylist(playlists.get(index));
				return true;
			}
		}
		int songTop = y + 74 + 13;
		int songStart = Math.min(5, homeSongs.size());
		int clicked = songIndex(mouseX, mouseY, left, songTop, right,
			b.bottom - PLAYER_HEIGHT,
			Math.min(6, Math.max(0, homeSongs.size() - songStart)));
		if(clicked >= 0)
		{
			player().play(homeSongs, clicked + songStart);
			return true;
		}
		return false;
	}

	public boolean scrollPlaylist(double delta, MusicContext.Bounds b)
	{
		int available = b.width() - SIDEBAR_WIDTH - 16;
		int maxPlaylistScroll = Math.max(0, playlists.size() * 61 - 6
			- available);
		ctx.playlistScroll = Mth.clamp(ctx.playlistScroll
			+ (delta > 0 ? -22 : 22), 0, maxPlaylistScroll);
		return true;
	}

	public int contentHeight()
	{
		return 226
			+ Math.min(6, Math.max(0, homeSongs.size() - 5)) * SONG_HEIGHT;
	}

	private void loadHome()
	{
		homeLoading = true;
		playlistLoading = true;
		long request = ++ctx.requestSequence;
		player().loadHomeSongs().whenComplete((songs, error) ->
			ctx.runOnClient(() -> {
				if(request != ctx.requestSequence)
					return;
				homeLoading = false;
				if(error == null)
					homeSongs = songs;
				else
					ctx.message = ctx.readableMessage(error);
			}));
		player().loadRecommendedPlaylists().whenComplete((loaded, error) ->
			ctx.runOnClient(() -> {
				playlistLoading = false;
				if(error == null)
					playlists = loaded;
				else if(ctx.message.isBlank())
					ctx.message = ctx.readableMessage(error);
			}));
	}
}

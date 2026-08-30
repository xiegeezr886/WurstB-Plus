package net.wurstclient.clickgui2.music;

import java.util.List;

import net.minecraft.client.gui.GuiGraphics;
import net.wurstclient.clickgui2.FlatRenderer;
import net.wurstclient.clickgui2.supersoft.SuperSoftTheme;
import net.wurstclient.music.NeteaseSong;

/**
 * 歌单详情页：返回按钮 + 歌曲列表。
 */
public final class PlaylistDetailPage extends MusicRegion
{
	public PlaylistDetailPage(MusicContext ctx)
	{
		super(ctx);
	}

	public void render(GuiGraphics graphics, int left, int top, int right, int bottom,
		int mouseX, int mouseY)
	{
		int x = left + 8;
		boolean backHovered = contains(mouseX, mouseY, x, top + 8, x + 20,
			top + 28);
		float back = motion("playlist-back").update(backHovered ? 1 : 0);
		FlatRenderer.fillRoundedRect(graphics, x, top + 8, x + 20, top + 28, 10,
			SuperSoftTheme.mix(0x1AFFFFFF, 0x55FFFFFF, back));
		drawIcon(graphics, ICON_BACK, x + 6, top + 14, 8, TEXT, 1);
		drawText(graphics, ctx.selectedPlaylist.name(), 8, x + 28, top + 10,
			TEXT, right - left - 55);
		drawText(graphics, ctx.playlistSongs.size() + "首歌曲", 5, x + 28,
			top + 21, MUTED, right - left - 55);
		if(ctx.contentLoading)
			renderLoading(graphics, (left + right) / 2, top + 70);
		else
		{
			graphics.enableScissor(left, top + 34, right, bottom);
			renderSongRows(graphics, ctx.playlistSongs, 0, true, x, top + 36 - ctx.scroll, right - 8, bottom, mouseX,
				mouseY);
			graphics.disableScissor();
		}
		renderScrollbar(graphics, right - 3, top + 34, bottom - 5,
			ctx.playlistSongs.size() * SONG_HEIGHT, bottom - top - 36);
	}

	public boolean click(double mouseX, double mouseY, MusicContext.Bounds b)
	{
		int left = b.left + SIDEBAR_WIDTH + 8;
		if(contains(mouseX, mouseY, left, b.top + 8, left + 20, b.top + 28))
		{
			ctx.selectedPlaylist = null;
			ctx.playlistSongs = List.of();
			ctx.scroll = 0;
			ctx.pageMotion.snap(0);
			return true;
		}
		return mouseY >= b.top + 34 && clickSongList(mouseX, mouseY, b,
			ctx.playlistSongs, b.top + 36 - ctx.scroll);
	}

	private boolean clickSongList(double mouseX, double mouseY,
		MusicContext.Bounds b, List<NeteaseSong> songs, int top)
	{
		int left = b.left + SIDEBAR_WIDTH + 8;
		int index = songIndex(mouseX, mouseY, left, top, b.right - 8,
			b.bottom - PLAYER_HEIGHT, songs.size());
		if(index < 0)
			return false;
		player().play(songs, index);
		return true;
	}

	public int contentHeight()
	{
		return ctx.playlistSongs.size() * SONG_HEIGHT;
	}
}

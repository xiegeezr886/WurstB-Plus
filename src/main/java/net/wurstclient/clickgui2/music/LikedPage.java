package net.wurstclient.clickgui2.music;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.client.gui.GuiGraphics;
import net.wurstclient.music.NeteaseSong;

/**
 * 喜欢页：登录检查、分页加载更多。
 */
public final class LikedPage extends MusicRegion
{
	private List<NeteaseSong> likedSongs = List.of();
	private boolean likedLoadingMore;
	private boolean likedHasMore = true;

	public LikedPage(MusicContext ctx)
	{
		super(ctx);
	}

	public void render(GuiGraphics graphics, int left, int top, int right, int bottom,
		int mouseX, int mouseY)
	{
		if(ctx.page == MusicContext.Page.LIKE && player().isLoggedIn()
			&& likedSongs.isEmpty() && !ctx.contentLoading)
			loadLiked();
		drawText(graphics, "我喜欢的音乐", 8, left + 8, top + 10, TEXT,
			right - left - 16);
		if(!player().isLoggedIn())
			drawCenteredText(graphics, "请先登录网易云账号", 7,
				(left + right) / 2, (top + bottom) / 2 - 5, MUTED,
				right - left - 24);
		else if(ctx.contentLoading)
			renderLoading(graphics, (left + right) / 2, top + 70);
		else if(likedSongs.isEmpty())
			drawCenteredText(graphics, "暂无喜欢的歌曲", 7,
				(left + right) / 2, top + 64, MUTED, right - left - 24);
		else
		{
			graphics.enableScissor(left, top + 26, right, bottom);
			renderSongRows(graphics, likedSongs, 0, false, left + 8,
				top + 28 - ctx.scroll, right - 8, bottom, mouseX, mouseY);
			if(likedLoadingMore)
				renderLoading(graphics, (left + right) / 2,
					top + 28 - ctx.scroll + likedSongs.size() * SONG_HEIGHT + 10);
			graphics.disableScissor();
		}
		renderScrollbar(graphics, right - 3, top + 26, bottom - 5,
			likedSongs.size() * SONG_HEIGHT, bottom - top - 28);
	}

	public boolean click(double mouseX, double mouseY, MusicContext.Bounds b)
	{
		int left = b.left + SIDEBAR_WIDTH + 8;
		int top = b.top + 28 - ctx.scroll;
		int index = songIndex(mouseX, mouseY, left, top, b.right - 8,
			b.bottom - PLAYER_HEIGHT, likedSongs.size());
		if(index < 0)
			return false;
		player().play(likedSongs, index);
		return true;
	}

	public void checkLoadMore(int max)
	{
		if(ctx.page == MusicContext.Page.LIKE && player().isLoggedIn()
			&& !ctx.contentLoading && !likedLoadingMore && likedHasMore
			&& ctx.scroll >= max - SONG_HEIGHT && likedSongs.size() >= 50)
			loadMoreLiked();
	}

	public int contentHeight()
	{
		return likedSongs.size() * SONG_HEIGHT;
	}

	public void clear()
	{
		likedSongs = List.of();
		likedLoadingMore = false;
		likedHasMore = true;
	}

	private void loadLiked()
	{
		ctx.contentLoading = true;
		likedHasMore = true;
		long request = ++ctx.requestSequence;
		player().loadLikedSongs(0).whenComplete((songs, error) ->
			ctx.runOnClient(() -> {
				if(request != ctx.requestSequence
					|| ctx.page != MusicContext.Page.LIKE)
					return;
				ctx.contentLoading = false;
				if(error == null)
				{
					likedSongs = songs;
					likedHasMore = songs.size() >= 50;
				}else
					ctx.message = ctx.readableMessage(error);
			}));
	}

	private void loadMoreLiked()
	{
		likedLoadingMore = true;
		player().loadLikedSongs(likedSongs.size()).whenComplete((songs, error) ->
			ctx.runOnClient(() -> {
				likedLoadingMore = false;
				if(error == null && !songs.isEmpty())
				{
					ArrayList<NeteaseSong> combined =
						new ArrayList<>(likedSongs);
					combined.addAll(songs);
					likedSongs = List.copyOf(combined);
				}
				if(error == null)
					likedHasMore = songs.size() >= 50;
			}));
	}
}

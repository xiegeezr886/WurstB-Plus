package net.wurstclient.clickgui2.music;

import java.util.List;

import net.minecraft.client.gui.GuiGraphics;
import net.wurstclient.clickgui2.FlatRenderer;
import net.wurstclient.clickgui2.supersoft.SuperSoftTheme;
import net.wurstclient.clickgui2.supersoft.UiTween;
import net.wurstclient.music.NeteaseSong;
import org.lwjgl.glfw.GLFW;

/**
 * 搜索页：输入框、搜索按钮、结果列表。
 */
public final class SearchPage extends MusicRegion
{
	private final UiTween searchMotion = new UiTween(0, 200);
	private List<NeteaseSong> searchSongs = List.of();
	private String query = "";
	private boolean inputFocused;

	public SearchPage(MusicContext ctx)
	{
		super(ctx);
	}

	public void render(GuiGraphics graphics, int left, int top, int right, int bottom,
		int mouseX, int mouseY)
	{
		int inputLeft = left + 8;
		int inputRight = right - 38;
		boolean inputHovered = contains(mouseX, mouseY, inputLeft, top + 8,
			inputRight, top + 30);
		float focus = searchMotion.update(inputFocused || inputHovered ? 1 : 0);
		FlatRenderer.fillRoundedRect(graphics, inputLeft, top + 8, inputRight,
			top + 30, 4, CARD);
		FlatRenderer.drawRoundedOutline(graphics, inputLeft, top + 8, inputRight,
			top + 30, 4, SuperSoftTheme.mix(withAlpha(accent(), 0.3F), accent(),
				focus));
		String display = query.isEmpty() ? "输入歌曲名或歌手..." : query;
		drawText(graphics, display, query.isEmpty() ? 6 : 7, inputLeft + 7,
			top + 15, query.isEmpty() ? 0x669F8997 : TEXT,
			inputRight - inputLeft - 14);
		if(inputFocused && System.currentTimeMillis() / 500 % 2 == 0)
		{
			int cursor = inputLeft + 7 + font().width(font().plainSubstrByWidth(
				query, inputRight - inputLeft - 14));
			graphics.fill(cursor, top + 13, cursor + 1, top + 26, accent());
		}
		boolean searchHovered = contains(mouseX, mouseY, right - 32, top + 8,
			right - 8, top + 30);
		float searchHover = motion("search-button").update(
			searchHovered ? 1 : 0);
		int inset = Math.round(searchHover);
		FlatRenderer.fillRoundedRect(graphics, right - 32 - inset,
			top + 8 - inset, right - 8 + inset, top + 30 + inset, 4, accent());
		drawIcon(graphics, ICON_SEARCH, right - 26, top + 13, 12, TEXT, 1);
		if(ctx.contentLoading)
			renderLoading(graphics, (left + right) / 2, top + 72);
		else if(searchSongs.isEmpty())
			drawCenteredText(graphics,
				ctx.message.isBlank() ? "输入关键词开始搜索" : ctx.message, 7,
				(left + right) / 2, top + 66, MUTED, right - left - 24);
		else
		{
			graphics.enableScissor(left, top + 36, right, bottom);
			renderSongRows(graphics, searchSongs, 0, true, left + 8,
				top + 38 - ctx.scroll, right - 8, bottom, mouseX, mouseY);
			graphics.disableScissor();
		}
		renderScrollbar(graphics, right - 3, top + 36, bottom - 5,
			searchSongs.size() * SONG_HEIGHT, bottom - top - 38);
	}

	public boolean click(double mouseX, double mouseY, MusicContext.Bounds b)
	{
		int left = b.left + SIDEBAR_WIDTH;
		int inputLeft = left + 8;
		int inputRight = b.right - 38;
		if(contains(mouseX, mouseY, inputLeft, b.top + 8, inputRight,
			b.top + 30))
		{
			inputFocused = true;
			return true;
		}
		inputFocused = false;
		if(contains(mouseX, mouseY, b.right - 32, b.top + 8, b.right - 8,
			b.top + 30))
		{
			runSearch();
			return true;
		}
		return mouseY >= b.top + 36 && clickSongList(mouseX, mouseY, b,
			searchSongs, b.top + 38 - ctx.scroll);
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

	public boolean keyPressed(int keyCode)
	{
		if(keyCode == GLFW.GLFW_KEY_ENTER)
		{
			runSearch();
			return true;
		}
		if(keyCode == GLFW.GLFW_KEY_BACKSPACE && !query.isEmpty())
		{
			query = query.substring(0, query.length() - 1);
			return true;
		}
		return false;
	}

	public boolean charTyped(char codePoint)
	{
		if(Character.isISOControl(codePoint))
			return false;
		if(query.length() < 80)
			query += codePoint;
		return true;
	}

	public boolean hasInputFocus()
	{
		return inputFocused;
	}

	public void resetFocus()
	{
		inputFocused = false;
	}

	public int contentHeight()
	{
		return searchSongs.size() * SONG_HEIGHT;
	}

	private void runSearch()
	{
		String search = query.trim();
		if(search.isEmpty())
			return;
		ctx.contentLoading = true;
		ctx.message = "";
		ctx.scroll = 0;
		long request = ++ctx.requestSequence;
		player().search(search).whenComplete((songs, error) ->
			ctx.runOnClient(() -> {
				if(request != ctx.requestSequence)
					return;
				ctx.contentLoading = false;
				if(error == null)
				{
					searchSongs = songs;
					ctx.message = songs.isEmpty() ? "没有找到歌曲" : "";
				}else
				{
					searchSongs = List.of();
					ctx.message = ctx.readableMessage(error);
				}
			}));
	}
}

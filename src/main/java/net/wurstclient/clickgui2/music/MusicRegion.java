package net.wurstclient.clickgui2.music;

import java.util.List;
import java.util.Locale;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.wurstclient.clickgui2.FlatRenderer;
import net.wurstclient.clickgui2.PingFangFont;
import net.wurstclient.clickgui2.supersoft.SuperSoftTheme;
import net.wurstclient.clickgui2.supersoft.UiTween;
import net.wurstclient.gui.visual.VisualTheme;
import net.wurstclient.music.NeteaseMusicPlayer;
import net.wurstclient.music.NeteaseSong;

/**
 * 网易云音乐屏幕所有子区域的公共基类：布局常量、图标、文字/封面/列表绘制
 * 助手与命中测试工具。子组件只持有一个 {@link MusicContext}。
 */
public abstract class MusicRegion
{
	// PVPUtils 音乐 GUI 布局常量（1:1 对齐）
	public static final int PANEL_WIDTH = 740;
	public static final int PANEL_HEIGHT = 500;
	public static final int SIDEBAR_WIDTH = 170;
	public static final int PLAYER_HEIGHT = 78;
	public static final int SONG_HEIGHT = 26;
	static final int BACKGROUND = VisualTheme.BACKGROUND;
	static final int CARD = VisualTheme.CONTROL;
	static final int TEXT = VisualTheme.TEXT;
	static final int MUTED = VisualTheme.TEXT_MUTED;
	static final ResourceLocation ICON_PLAY = icon("play_fill");
	static final ResourceLocation ICON_PAUSE = icon("pause");
	static final ResourceLocation ICON_PREVIOUS = icon("music_last");
	static final ResourceLocation ICON_NEXT = icon("music_next");
	static final ResourceLocation ICON_SEARCH = icon("search");
	static final ResourceLocation ICON_BACK = icon("left");
	static final ResourceLocation ICON_CLOSE = icon("disable");

	protected final MusicContext ctx;

	protected MusicRegion(MusicContext ctx)
	{
		this.ctx = ctx;
	}

	protected int accent()
	{
		return ctx.accentColor;
	}

	protected NeteaseMusicPlayer player()
	{
		return ctx.player;
	}

	protected Font font()
	{
		return Minecraft.getInstance().font;
	}

	protected MusicContext.Bounds bounds()
	{
		return ctx.bounds();
	}

	protected UiTween motion(String id)
	{
		return ctx.motion(id);
	}

	protected void renderSongRows(GuiGraphics graphics, List<NeteaseSong> rows,
		int indexOffset, boolean showIndex, int left, int top, int right,
		int bottom, int mouseX, int mouseY)
	{
		NeteaseSong current = player().getCurrentSong();
		for(int index = 0; index < rows.size(); index++)
		{
			int rowTop = top + index * SONG_HEIGHT;
			if(rowTop + SONG_HEIGHT <= bounds().top || rowTop >= bottom)
				continue;
			NeteaseSong song = rows.get(index);
			boolean selected = current != null && current.id() == song.id();
			boolean hovered = contains(mouseX, mouseY, left, rowTop, right,
				rowTop + SONG_HEIGHT - 2);
			float highlight = motion("song-" + song.id()).update(
				selected ? 1 : hovered ? 0.62F : 0);
			FlatRenderer.fillRoundedRect(graphics, left, rowTop, right,
				rowTop + SONG_HEIGHT - 2, 4,
				SuperSoftTheme.mix(0x1AFFFFFF,
					withAlpha(accent(), 0.32F), highlight));
			int coverLeft = left + 3;
			if(showIndex)
			{
				int shownIndex = index + indexOffset + 1;
				String number = Integer.toString(shownIndex);
				drawCenteredText(graphics, number, 6, left + 9, rowTop + 9,
					shownIndex <= 3 ? accent() : withAlpha(TEXT, 0.5F), 14);
				coverLeft += 14;
			}
			drawCover(graphics, song.coverUrl(), coverLeft, rowTop + 2,
				coverLeft + 20, rowTop + 22, 1);
			FlatRenderer.drawRoundedOutline(graphics, coverLeft, rowTop + 2,
				coverLeft + 20, rowTop + 22, 4, 0x1AFFFFFF);
			int titleLeft = coverLeft + 25;
			drawText(graphics, song.name(), 6, titleLeft, rowTop + 4,
				selected ? accent() : TEXT, right - titleLeft - 18);
			drawText(graphics, song.artist(), 5, titleLeft, rowTop + 15, MUTED,
				right - titleLeft - 18);
			if(selected && player().getState()
				== NeteaseMusicPlayer.PlaybackState.PLAYING)
				drawPlaying(graphics, right - 12, rowTop + 13, accent());
		}
	}

	protected void renderScrollbar(GuiGraphics graphics, int x, int top,
		int bottom, int contentHeight, int viewport)
	{
		if(contentHeight <= viewport || viewport <= 0)
			return;
		int height = bottom - top;
		int thumb = Math.max(14, Math.round(height * viewport
			/ (float)contentHeight));
		int maxScroll = contentHeight - viewport;
		int thumbY = top + Math.round((height - thumb) * ctx.scroll
			/ (float)Math.max(1, maxScroll));
		graphics.fill(x, top, x + 2, bottom, 0x22FFFFFF);
		graphics.fill(x, thumbY, x + 2, thumbY + thumb,
			withAlpha(accent(), 0.6F));
	}

	protected void renderLoading(GuiGraphics graphics, int centerX, int centerY)
	{
		int phase = (int)(System.currentTimeMillis() / 120 % 8);
		for(int index = 0; index < 8; index++)
		{
			double angle = Math.PI * 2 * index / 8;
			int x = centerX + (int)Math.round(Math.cos(angle) * 8);
			int y = centerY + (int)Math.round(Math.sin(angle) * 8);
			graphics.fill(x, y, x + 2, y + 2,
				withAlpha(accent(), index == phase ? 1 : 0.22F));
		}
	}

	protected void drawCover(GuiGraphics graphics, String url, int left, int top,
		int right, int bottom, float alpha)
	{
		if(right <= left || bottom <= top)
			return;
		NeteaseImageCache.Texture texture = ctx.images.get(url);
		if(texture == null)
		{
			graphics.fill(left, top, right, bottom, CARD);
			graphics.drawCenteredString(font(), "♪", (left + right) / 2,
				(top + bottom - font().lineHeight) / 2, withAlpha(MUTED, alpha));
			return;
		}
		int sourceWidth = texture.width();
		int sourceHeight = texture.height();
		int cropX = 0;
		int cropY = 0;
		int cropWidth = sourceWidth;
		int cropHeight = sourceHeight;
		int targetWidth = right - left;
		int targetHeight = bottom - top;
		if(sourceWidth * targetHeight > sourceHeight * targetWidth)
		{
			cropWidth = Math.max(1, sourceHeight * targetWidth / targetHeight);
			cropX = (sourceWidth - cropWidth) / 2;
		}else
		{
			cropHeight = Math.max(1, sourceWidth * targetHeight / targetWidth);
			cropY = (sourceHeight - cropHeight) / 2;
		}
		graphics.setColor(1, 1, 1, Mth.clamp(alpha, 0, 1));
		graphics.blit(texture.location(), left, top, targetWidth, targetHeight,
			cropX, cropY, cropWidth, cropHeight, sourceWidth, sourceHeight);
		graphics.setColor(1, 1, 1, 1);
	}

	protected void drawText(GuiGraphics graphics, String text, float size,
		int x, int y, int color, int maxWidth)
	{
		float scale = size / font().lineHeight;
		String shown = fitText(text, scale, maxWidth);
		graphics.pose().pushPose();
		graphics.pose().translate(x, y, 0);
		graphics.pose().scale(scale, scale, 1);
		graphics.drawString(font(), PingFangFont.text(shown), 0, 0, color,
			false);
		graphics.pose().popPose();
	}

	protected void drawCenteredText(GuiGraphics graphics, String text,
		float size, int centerX, int y, int color, int maxWidth)
	{
		float scale = size / font().lineHeight;
		String shown = fitText(text, scale, maxWidth);
		graphics.pose().pushPose();
		graphics.pose().translate(centerX, y, 0);
		graphics.pose().scale(scale, scale, 1);
		graphics.drawString(font(), PingFangFont.text(shown),
			-PingFangFont.width(font(), shown) / 2, 0, color, false);
		graphics.pose().popPose();
	}

	protected void drawTextRight(GuiGraphics graphics, String text, float size,
		int right, int y, int color, int maxWidth)
	{
		float scale = size / font().lineHeight;
		String shown = fitText(text, scale, maxWidth);
		graphics.pose().pushPose();
		graphics.pose().translate(right, y, 0);
		graphics.pose().scale(scale, scale, 1);
		graphics.drawString(font(), PingFangFont.text(shown),
			-PingFangFont.width(font(), shown), 0, color, false);
		graphics.pose().popPose();
	}

	protected void drawWrappedCenteredText(GuiGraphics graphics, String text,
		float size, int centerX, int y, int color, int maxWidth, int maxLines)
	{
		float scale = size / font().lineHeight;
		int baseWidth = Math.max(1, (int)(maxWidth / scale));
		String remaining = text;
		for(int line = 0; line < maxLines && !remaining.isEmpty(); line++)
		{
			String shown = PingFangFont.trim(font(), remaining, baseWidth);
			if(shown.isEmpty())
				break;
			if(line == maxLines - 1 && shown.length() < remaining.length())
				shown = PingFangFont.trim(font(), remaining, baseWidth);
			drawCenteredText(graphics, shown, size, centerX,
				y + Math.round(line * (size + 1)), color, maxWidth);
			remaining = remaining.substring(shown.length()).stripLeading();
		}
	}

	protected String fitText(String text, float scale, int maxWidth)
	{
		if(maxWidth <= 0)
			return text;
		return PingFangFont.trim(font(), text,
			Math.max(1, (int)(maxWidth / Math.max(0.01F, scale))));
	}

	protected void renderControl(GuiGraphics graphics, String id, int x, int y,
		int radius, int mouseX, int mouseY, boolean primary)
	{
		boolean hovered = distance(mouseX, mouseY, x, y) <= radius + 2;
		float hover = motion("control-" + id).update(hovered ? 1 : 0);
		if(primary)
			FlatRenderer.fillRoundedRect(graphics, x - radius - Math.round(hover),
				y - radius - Math.round(hover), x + radius + Math.round(hover),
				y + radius + Math.round(hover), radius + 2, accent());
		if(id.equals("toggle"))
		{
			if(player().getState() == NeteaseMusicPlayer.PlaybackState.PLAYING)
				drawIcon(graphics, ICON_PAUSE, x - 8, y - 8, 16, TEXT, 1);
			else
				drawIcon(graphics, ICON_PLAY, x - 8, y - 8, 16, TEXT, 1);
		}else if(id.endsWith("previous"))
			drawIcon(graphics, ICON_PREVIOUS, x - 7, y - 7, 14,
				SuperSoftTheme.mix(MUTED, TEXT, hover), 1);
		else
			drawIcon(graphics, ICON_NEXT, x - 7, y - 7, 14,
				SuperSoftTheme.mix(MUTED, TEXT, hover), 1);
	}

	public static int songIndex(double mouseX, double mouseY, int left, int top,
		int right, int bottom, int size)
	{
		if(!contains(mouseX, mouseY, left, top, right, bottom))
			return -1;
		int index = (int)((mouseY - top) / SONG_HEIGHT);
		return index >= 0 && index < size ? index : -1;
	}

	static String formatCount(long count)
	{
		if(count >= 100_000_000)
			return String.format(Locale.ROOT, "%.1f亿",
				count / 100_000_000D);
		if(count >= 10_000)
			return String.format(Locale.ROOT, "%.1f万", count / 10_000D);
		return Long.toString(count);
	}

	private static ResourceLocation icon(String name)
	{
		return new ResourceLocation("wurst", "textures/gui/netease/" + name
			+ ".png");
	}

	static void drawIcon(GuiGraphics graphics, ResourceLocation icon, int x,
		int y, int size, int color, float alpha)
	{
		float red = (color >> 16 & 0xFF) / 255F;
		float green = (color >> 8 & 0xFF) / 255F;
		float blue = (color & 0xFF) / 255F;
		float actualAlpha = (color >>> 24) / 255F * alpha;
		graphics.setColor(red, green, blue, actualAlpha);
		graphics.blit(icon, x, y, size, size, 0, 0, 200, 200, 200, 200);
		graphics.setColor(1, 1, 1, 1);
	}

	static void drawVolume(GuiGraphics graphics, int x, int y, int color,
		boolean loud)
	{
		graphics.fill(x - 6, y - 2, x - 3, y + 3, color);
		graphics.fill(x - 3, y - 4, x - 1, y + 5, color);
		graphics.fill(x - 1, y - 3, x + 1, y + 4, color);
		graphics.fill(x + 2, y - 2, x + 3, y + 3, color);
		if(loud)
		{
			graphics.fill(x + 4, y - 4, x + 5, y + 5, color);
			graphics.fill(x + 6, y - 2, x + 7, y + 3, color);
		}
	}

	static void drawQueue(GuiGraphics graphics, int x, int y, int color)
	{
		for(int row = -5; row <= 5; row += 5)
		{
			graphics.fill(x - 6, y + row, x - 4, y + row + 2, color);
			graphics.fill(x - 2, y + row, x + 6, y + row + 2, color);
		}
	}

	static void drawPlaying(GuiGraphics graphics, int x, int y, int color)
	{
		int phase = (int)(System.currentTimeMillis() / 130 % 4);
		for(int index = 0; index < 3; index++)
		{
			int height = 3 + (phase + index * 2) % 4;
			graphics.fill(x + index * 3, y - height / 2, x + index * 3 + 2,
				y + (height + 1) / 2, color);
		}
	}

	static void drawPlay(GuiGraphics graphics, int x, int y, int color)
	{
		for(int row = -6; row <= 6; row++)
			graphics.fill(x - 4, y + row, x - 4 + 7 - Math.abs(row), y + row + 1,
				color);
	}

	public static boolean contains(double x, double y, int left, int top,
		int right, int bottom)
	{
		return x >= left && x < right && y >= top && y < bottom;
	}

	static double distance(double x, double y, double targetX, double targetY)
	{
		return Math.hypot(x - targetX, y - targetY);
	}

	public static int withAlpha(int color, float alpha)
	{
		return Math.round(Mth.clamp(alpha, 0, 1) * 255) << 24
			| color & 0xFFFFFF;
	}

	public static int lerpAccent(int from, int to, float t)
	{
		float progress = Mth.clamp(t, 0, 1);
		int r = Math.round(Mth.lerp(progress, from >>> 16 & 0xFF,
			to >>> 16 & 0xFF));
		int g = Math.round(Mth.lerp(progress, from >>> 8 & 0xFF,
			to >>> 8 & 0xFF));
		int b = Math.round(Mth.lerp(progress, from & 0xFF, to & 0xFF));
		float luminance = (0.299F * r + 0.587F * g + 0.114F * b) / 255F;
		if(luminance < 0.35F)
		{
			float boost = Math.min(1.6F, 0.35F / Math.max(luminance, 0.01F));
			r = Math.min(255, Math.round(r * boost));
			g = Math.min(255, Math.round(g * boost));
			b = Math.min(255, Math.round(b * boost));
		}
		return 0xFF000000 | r << 16 | g << 8 | b;
	}
}

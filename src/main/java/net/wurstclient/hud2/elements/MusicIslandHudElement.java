/*
 * Copyright (c) 2014-2025 Wurst-Imperium and contributors.
 *
 * This source code is subject to the terms of the GNU General Public
 * License, version 3. If a copy of the GPL was not distributed with this
 * file, You can obtain one at: https://www.gnu.org/licenses/gpl-3.0.txt
 */
package net.wurstclient.hud2.elements;

import java.util.List;

import com.mojang.blaze3d.platform.NativeImage;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.texture.AbstractTexture;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.wurstclient.WurstClient;
import net.wurstclient.clickgui2.FlatRenderer;
import net.wurstclient.clickgui2.PingFangFont;
import net.wurstclient.clickgui2.music.NeteaseImageCache;
import net.wurstclient.gui.visual.VisualTheme;
import net.wurstclient.hud2.HudElement;
import net.wurstclient.hud2.HudLayout.HudElementConfig;
import net.wurstclient.hud2.HudManager;
import net.wurstclient.music.LyricLine;
import net.wurstclient.music.NeteaseMusicPlayer;
import net.wurstclient.music.NeteaseMusicPlayer.PlaybackState;
import net.wurstclient.music.NeteaseSong;
import net.wurstclient.music.PlayerListener;
import net.wurstclient.util.ScreenRegistry;

/**
 * 音乐灵动岛：紧凑胶囊与展开卡片之间用弹簧形变切换（规格见
 * {@code _wi_ref/island-spec.md}）。
 *
 * <p>
 * 尺寸、时序、弹簧、可视化柱平滑与取色全部来自 {@link MusicIslandState}
 * （纯逻辑、可单测），这里只负责测量文字、算坐标与调用绘制原语。
 */
public final class MusicIslandHudElement extends HudElement
{
	private static final int BACKGROUND = VisualTheme.SURFACE_90;
	private static final int OUTLINE = VisualTheme.BORDER;
	private static final int TEXT_PRIMARY = VisualTheme.TEXT;
	private static final int TEXT_SECONDARY = VisualTheme.TEXT_MUTED;
	private static final int TRACK = 0x1FFFFFFF;
	private static final int COVER_PLACEHOLDER = 0x26FFFFFF;
	private static final int COVER_BORDER = 0x33FFFFFF;
	
	/** 展开态内容留白：规格 §2.4 的 24/360 比例。 */
	private static final int EXPANDED_PADDING = 8;
	private static final int EXPANDED_COVER = 24;
	private static final int EXPANDED_COVER_GAP = 7;
	private static final float TITLE_SIZE = 9F;
	private static final float ARTIST_SIZE = 7.5F;
	private static final float LYRIC_SIZE = 8F;
	private static final float TIME_SIZE = 6F;
	private static final int TIME_TEXT_HEIGHT = 6;
	private static final int TIME_PITCH = 17;
	private static final int PROGRESS_HEIGHT = 2;
	private static final int VISUAL_BARS = 6;
	private static final int VISUAL_BAR_WIDTH = 2;
	private static final int VISUAL_BAR_GAP = 1;
	private static final int VISUAL_MAX_HEIGHT = 16;
	private static final int VISUAL_RIGHT_INSET = 10;
	private static final int VISUAL_BASELINE_OFFSET = 24;
	
	/** 紧凑态：规格 §2.3 的比例（内缩/封面尺寸都相对胶囊高度）。 */
	private static final int COMPACT_PADDING = 6;
	private static final int COMPACT_COVER = 13;
	private static final int COMPACT_TEXT_X = 23;
	private static final float COMPACT_TEXT_SIZE = 8F;
	private static final int COMPACT_MIN_CONTENT = 24;
	private static final int COMPACT_VISUAL_WIDTH = 18;
	private static final int COMPACT_VISUAL_GAP = 5;
	
	/** 封面取色失败时的兜底主色（规格 §4.1 的默认板）。 */
	private static final int FALLBACK_PRIMARY = 0xFFB4B4B4;
	private static final int FALLBACK_SECONDARY = 0xFF646464;
	
	private final MusicIslandState state = new MusicIslandState();
	private final float[] visualBars = new float[VISUAL_BARS];
	private final PlayerListener listener = new PlayerListener()
	{
		@Override
		public void onSongChanged(NeteaseSong song)
		{
			songDirty = true;
		}
	};
	
	private volatile boolean songDirty;
	private NeteaseImageCache covers = new NeteaseImageCache();
	private volatile boolean coversClosed;
	
	private int compactContentWidth = MusicIslandState.COMPACT_MIN_WIDTH;
	private long lastRenderNanos;
	private float smoothedProgress;
	private long lastProgressSongId = Long.MIN_VALUE;
	private long lastProgressMs;
	private long paletteSongId = Long.MIN_VALUE;
	private String paletteUrl = "";
	private int palettePrimary = FALLBACK_PRIMARY;
	private int paletteSecondary = FALLBACK_SECONDARY;
	
	public MusicIslandHudElement()
	{
		super("music_island", "\u97f3\u4e50\u7075\u52a8\u5c9b");
	}
	
	@Override
	public int getWidth()
	{
		long now = System.nanoTime() / 1_000_000L;
		return Math.max(1, state.width(now, compactContentWidth));
	}
	
	@Override
	public int getHeight()
	{
		long now = System.nanoTime() / 1_000_000L;
		return Math.max(1, state.height(now));
	}
	
	@Override
	public boolean renderEditorPreview()
	{
		return true;
	}
	
	@Override
	public void onEnable(HudManager manager)
	{
		if(coversClosed)
		{
			covers = new NeteaseImageCache();
			coversClosed = false;
		}
		
		NeteaseMusicPlayer.INSTANCE.addListener(listener);
	}
	
	@Override
	public void onDisable(HudManager manager)
	{
		NeteaseMusicPlayer.INSTANCE.removeListener(listener);
		
		// 释放封面贴图，避免元素被关掉后还占着显存
		coversClosed = true;
		covers.close();
	}
	
	@Override
	public void render(GuiGraphics graphics, int x, int y, float partialTicks)
	{
		Minecraft mc = WurstClient.MC;
		
		if(mc == null || mc.font == null)
			return;
		
		long nanoTime = System.nanoTime();
		long nowMs = nanoTime / 1_000_000L;
		long deltaNanos = lastRenderNanos == 0L ? 0L
			: nanoTime - lastRenderNanos;
		lastRenderNanos = nanoTime;
		float deltaSeconds = Mth.clamp(deltaNanos / 1_000_000_000F, 0F, 0.1F);
		Font font = mc.font;
		
		NeteaseMusicPlayer player = NeteaseMusicPlayer.INSTANCE;
		NeteaseSong song = player.getCurrentSong();
		PlaybackState playback = player.getState();
		List<LyricLine> lyrics = player.getLyrics();
		boolean preview = ScreenRegistry.HUD_EDITOR.isOpen();
		boolean playing = playback == PlaybackState.PLAYING;
		boolean loading = playback == PlaybackState.LOADING;
		boolean hasSong = song != null;
		
		updateState(nowMs, x, y, preview, playing, hasSong,
			song == null ? 0L : song.id());
		
		boolean musicVisible = playing || loading;
		String compactText = compactText(mc, playing, hasSong);
		
		// 紧凑宽度按内容自适应（规格 §5.1）：文字宽 + 左右缩进，两端由状态机夹住
		int textWidth = PingFangFont.width(font, compactText);
		int contentWidth = COMPACT_PADDING * 2 + COMPACT_COVER + COMPACT_PADDING
			+ textWidth + COMPACT_VISUAL_GAP + COMPACT_VISUAL_WIDTH;
		compactContentWidth =
			MusicIslandState.compactWidthFor(contentWidth);
		
		int width = state.width(nowMs, compactContentWidth);
		int height = state.height(nowMs);
		float radius = state.radius(nowMs);
		
		float compactAlpha = state.compactAlpha(nowMs);
		float expandedAlpha = state.expandedAlpha(nowMs);
		
		HudElementConfig config = WurstClient.INSTANCE.getHudManager()
			.getLayout().get(getId());
		float hudScale = config == null ? 1F : config.getScale();
		
		drawBody(graphics, x, y, width, height, radius);
		clipTo(graphics, x, y, width, height, hudScale);
		
		try
		{
			if(compactAlpha > 0.01F)
				drawCompact(graphics, font, x, y, width, compactText,
					musicVisible, playing, compactAlpha, deltaSeconds);
			
			if(expandedAlpha > 0.01F)
			{
				updatePalette(song);
				drawExpanded(graphics, font, x, y, width, height, song,
					lyrics, player, preview, musicVisible, expandedAlpha,
					deltaSeconds);
			}
		}finally
		{
			graphics.disableScissor();
		}
	}
	
	private void updateState(long nowMs, int x, int y, boolean preview,
		boolean playing, boolean hasSong, long songId)
	{
		// 编辑器里画的是预览，鼠标不一定在游戏窗口里，直接按悬停处理
		boolean hovered = preview || isCursorOnIsland(x, y);
		state.update(nowMs, hovered, playing, songId);
		
		if(songDirty)
		{
			songDirty = false;
			paletteSongId = Long.MIN_VALUE;
		}
	}
	
	/**
	 * 光标命中测试。HUD 元素按 {@code config.getScale()} 放大，而
	 * {@code MouseHandler} 的坐标是不缩放的 GUI 坐标，所以这里要反向映射回去
	 * （与 {@code HudManager#renderElement} 里"先移到元素左上角再缩放"的顺序一致）。
	 */
	private boolean isCursorOnIsland(int x, int y)
	{
		Minecraft mc = WurstClient.MC;
		
		if(mc == null || mc.mouseHandler == null)
			return false;
		
		long nowMs = System.nanoTime() / 1_000_000L;
		HudElementConfig config = WurstClient.INSTANCE.getHudManager()
			.getLayout().get(getId());
		float scale = config == null || config.getScale() <= 0F ? 1F
			: config.getScale();
		double localX = x + (mc.mouseHandler.xpos() - x) / scale;
		double localY = y + (mc.mouseHandler.ypos() - y) / scale;
		
		return localX >= x
			&& localX < x + state.width(nowMs, compactContentWidth)
			&& localY >= y && localY < y + state.height(nowMs);
	}
	
	private void drawBody(GuiGraphics graphics, int x, int y, int width,
		int height, float radius)
	{
		int corner = Math.max(1, Math.round(radius));
		FlatRenderer.fillRoundedRect(graphics, x, y, x + width, y + height,
			corner, BACKGROUND);
		FlatRenderer.drawRoundedOutline(graphics, x, y, x + width, y + height,
			corner, OUTLINE);
	}
	
	/**
	 * 裁剪到岛体。{@code HudManager} 已经为元素压过一层
	 * {@code config.getScale()} 的 pose 缩放，而剪刀是屏幕像素，所以这里乘回去
	 * （与原元素、{@code MusicLyricsHudElement} 的做法一致）。
	 */
	private void clipTo(GuiGraphics graphics, int x, int y, int width,
		int height, float hudScale)
	{
		graphics.enableScissor(x, y, x + Math.round(width * hudScale),
			y + Math.round(height * hudScale));
	}
	
	/**
	 * 紧凑态固定槽位的内容（规格 §5.2）：正在放歌时显示歌名，否则显示 FPS。
	 * 槽位不随时间轮换，所以这里没有轮换索引。
	 */
	private String compactText(Minecraft mc, boolean playing, boolean hasSong)
	{
		if(playing && hasSong)
		{
			NeteaseSong song = NeteaseMusicPlayer.INSTANCE.getCurrentSong();
			return song == null ? "" : song.name();
		}
		
		return mc.getFps() + " FPS";
	}
	
	private void drawCompact(GuiGraphics graphics, Font font, int x, int y,
		int width, String text, boolean musicVisible, boolean playing,
		float alpha, float deltaSeconds)
	{
		int centerY = y + MusicIslandState.COMPACT_HEIGHT / 2;
		int coverSize = COMPACT_COVER;
		int coverY = centerY - coverSize / 2;
		
		drawRoundedCover(graphics, x + COMPACT_PADDING, coverY, coverSize,
			alpha);
		
		// 规格 §2.3：紧凑态 = 左封面 + 中歌词 + 右可视化，三个固定槽位
		drawPlayState(graphics, x + COMPACT_TEXT_X - 8, centerY - 3,
			musicVisible, playing, alpha);
		
		int textX = x + COMPACT_TEXT_X;
		int textRight = x + width - COMPACT_VISUAL_WIDTH
			- COMPACT_VISUAL_GAP;
		int available = Math.max(COMPACT_MIN_CONTENT, textRight - textX);
		String shown = trim(font, text, available);
		drawText(graphics, font, shown, COMPACT_TEXT_SIZE, textX,
			(float)centerY - COMPACT_TEXT_SIZE / 2F, TEXT_PRIMARY, alpha);
		
		drawVisualiser(graphics, x + width - COMPACT_VISUAL_WIDTH,
			centerY + 3, COMPACT_VISUAL_WIDTH, 8, musicVisible,
			deltaSeconds, alpha);
	}
	
	private void drawExpanded(GuiGraphics graphics, Font font, int x, int y,
		int width, int height, NeteaseSong song, List<LyricLine> lyrics,
		NeteaseMusicPlayer player, boolean preview, boolean musicVisible,
		float alpha, float deltaSeconds)
	{
		int padding = EXPANDED_PADDING;
		int coverSize = EXPANDED_COVER;
		int coverX = x + padding;
		int coverY = y + padding;
		int textX = coverX + coverSize + EXPANDED_COVER_GAP;
		int textRight = x + width - padding;
		int available = Math.max(8, textRight - textX);
		
		drawRoundedCover(graphics, coverX, coverY, coverSize, alpha);
		
		String title = song == null
			? (preview ? "Now Playing: NetEase Music" : "No Music playing")
			: song.name();
		String artist = song == null ? "Unknown Artist" : song.artist();
		float titleY = coverY + 2F;
		
		drawText(graphics, font, trim(font, title, available), TITLE_SIZE,
			textX, titleY, TEXT_PRIMARY, alpha);
		drawText(graphics, font, trim(font, artist, available), ARTIST_SIZE,
			textX, titleY + TITLE_SIZE + 1F, TEXT_SECONDARY, alpha);
		
		int visualRight = x + width - VISUAL_RIGHT_INSET;
		drawVisualiser(graphics, visualRight - visualWidth(),
			Math.round(titleY) + VISUAL_BASELINE_OFFSET, visualWidth(),
			VISUAL_MAX_HEIGHT, musicVisible, deltaSeconds, alpha);
		
		drawProgress(graphics, font, x, y, width, height, player, song,
			musicVisible, alpha);
		
		int lyricIndex = MusicIslandState.currentLyricIndex(lyrics,
			player.getAdjustedLyricPositionMs());
		if(lyricIndex >= 0 && lyricIndex < lyrics.size())
			drawText(graphics, font,
				trim(font, lyrics.get(lyricIndex).text(), available),
				LYRIC_SIZE, textX, y + height - padding - LYRIC_SIZE,
				TEXT_SECONDARY, alpha);
		else
			drawText(graphics, font,
				preview ? "Music flows with every adventure" : "\u266a",
				LYRIC_SIZE, textX, y + height - padding - LYRIC_SIZE,
				TEXT_SECONDARY, alpha);
	}
	
	private void drawProgress(GuiGraphics graphics, Font font, int x, int y,
		int width, int height, NeteaseMusicPlayer player, NeteaseSong song,
		boolean audio, float alpha)
	{
		long duration = song == null ? 0L : song.durationMs();
		if(duration <= 0L)
			duration = player.getDurationMs();
		
		long songId = song == null ? -1L : song.id();
		long position = player.getPositionMs();
		float raw = MusicIslandState.progress(position, duration);
		
		if(songId != lastProgressSongId || position < lastProgressMs)
		{
			// 换歌或往回拖：直接吸附，别让指针从上一首的位置飞过去
			lastProgressSongId = songId;
			smoothedProgress = raw;
		}else
			smoothedProgress =
				MusicIslandState.smoothProgress(smoothedProgress, raw, false);
		
		lastProgressMs = position;
		
		int timeY = y + height - EXPANDED_PADDING - 4 - TIME_TEXT_HEIGHT;
		int barY = timeY + 1;
		int barLeft = x + EXPANDED_PADDING + TIME_PITCH;
		int barRight = x + width - EXPANDED_PADDING - TIME_PITCH;
		
		drawText(graphics, font,
			NeteaseMusicPlayer.formatTime(position), TIME_SIZE,
			x + EXPANDED_PADDING, timeY, TEXT_SECONDARY, alpha);
		drawTextRight(graphics, font,
			duration <= 0L ? "--:--"
				: "-" + NeteaseMusicPlayer.formatTime(duration - position),
			TIME_SIZE, x + width - EXPANDED_PADDING, timeY, TEXT_SECONDARY,
			alpha);
		
		FlatRenderer.fillRoundedRect(graphics, barLeft, barY, barRight,
			barY + PROGRESS_HEIGHT, 1, withAlpha(TRACK, alpha));
		
		int fillRight =
			barLeft + Math.round((barRight - barLeft) * smoothedProgress);
		
		if(fillRight > barLeft)
			FlatRenderer.fillRoundedRect(graphics, barLeft, barY, fillRight,
				barY + PROGRESS_HEIGHT, 1,
				withAlpha(palettePrimary, alpha * (audio ? 1F : 0.72F)));
	}
	
	/**
	 * 规格 §6.4 的可视化：本工程没有真实频谱（{@code NeteaseMusicPlayer} 不暴露
	 * 频谱数据），所以这里用播放状态与时间合成一组伪频谱，再走状态机里那套
	 * 上升 0.6 / 下降 0.08 的非对称平滑；没有在播时目标回到按高度算出的底线，
	 * 柱子会自己落回去。
	 */
	private void drawVisualiser(GuiGraphics graphics, int x, int bottomY,
		int totalWidth, int maxHeight, boolean musicVisible,
		float deltaSeconds, float alpha)
	{
		int pitch = VISUAL_BAR_WIDTH + VISUAL_BAR_GAP;
		int usable = Math.max(VISUAL_BAR_WIDTH,
			totalWidth - (VISUAL_BARS - 1) * pitch);
		int barWidth = Math.max(1, usable / VISUAL_BARS);
		float baseHeight = Math.max(1F, maxHeight * 0.16F);
		float elapsed =
			(System.nanoTime() % 3_600_000_000_000L) / 1_000_000_000F;
		
		for(int index = 0; index < VISUAL_BARS; index++)
		{
			float target = baseHeight;
			
			if(musicVisible)
			{
				double wave = Math.sin(elapsed * 4.2D + index * 1.7D) * 0.5D
					+ Math.sin(elapsed * 2.3D + index * 0.9D) * 0.3D
					+ Math.sin(elapsed * 7.1D + index * 2.6D) * 0.2D;
				target = (float)(baseHeight
					+ (wave + 1D) * 0.5D * (maxHeight - baseHeight));
			}
			
			visualBars[index] = MusicIslandState.smoothVisual(
				visualBars[index], target, deltaSeconds);
			
			int barX = x + index * pitch;
			int barBottom = Math.round(bottomY);
			int barTop =
				barBottom - Math.max(1, Math.round(visualBars[index]));
			int left = barX;
			int right = barX + barWidth;
			
			if(right <= left || barTop >= barBottom)
				continue;
			
			// 规格 §4.2：柱子用调色板渐变，逐列插值出横向渐变的观感
			FlatRenderer.fillRoundedRect(graphics, left, barTop, right,
				barBottom, 1, withAlpha(palettePrimary, alpha));
			
			for(int column = left; column < right; column++)
			{
				float amount = (column - left + 0.5F)
					/ Math.max(1, right - left);
				graphics.fill(column, barTop, column + 1, barBottom,
					withAlpha(VisualTheme.mix(palettePrimary, paletteSecondary,
						amount), alpha));
			}
		}
	}
	
	private int visualWidth()
	{
		return VISUAL_BARS * VISUAL_BAR_WIDTH
			+ (VISUAL_BARS - 1) * VISUAL_BAR_GAP;
	}
	
	/** 封面：加载好就贴图，没加载好就画占位块；两者都带描边。 */
	private void drawRoundedCover(GuiGraphics graphics, int x, int y, int size,
		float alpha)
	{
		NeteaseImageCache.Texture cover = coverInfo();
		int corner = Math.max(1, size / 4);
		
		if(cover == null)
		{
			FlatRenderer.fillRoundedRect(graphics, x, y, x + size, y + size,
				corner, withAlpha(COVER_PLACEHOLDER, alpha));
		}else
		{
			int sourceWidth = cover.width();
			int sourceHeight = cover.height();
			int cropX = 0;
			int cropY = 0;
			int cropWidth = sourceWidth;
			int cropHeight = sourceHeight;
			
			// 居中正方形裁切（规格 §2.3 的 cover 语义）
			if(sourceWidth > sourceHeight)
			{
				cropWidth = sourceHeight;
				cropX = (sourceWidth - sourceHeight) / 2;
			}else if(sourceHeight > sourceWidth)
			{
				cropHeight = sourceWidth;
				cropY = (sourceHeight - sourceWidth) / 2;
			}
			
			graphics.setColor(1F, 1F, 1F, Mth.clamp(alpha, 0F, 1F));
			
			try
			{
				graphics.blit(cover.location(), x, y, size, size, cropX, cropY,
					cropWidth, cropHeight, sourceWidth, sourceHeight);
			}finally
			{
				graphics.setColor(1F, 1F, 1F, 1F);
			}
		}
		
		FlatRenderer.drawRoundedOutline(graphics, x, y, x + size, y + size,
			corner, withAlpha(COVER_BORDER, alpha));
	}
	
	/**
	 * 播放状态的极简指示：暂停画两条竖线，播放画一个等宽三角。紧凑态里画在
	 * 封面右侧，不遮挡封面本身。
	 */
	private void drawPlayState(GuiGraphics graphics, int x, int y,
		boolean visible, boolean playing, float alpha)
	{
		if(!visible)
			return;
		
		int color = withAlpha(TEXT_PRIMARY, alpha);
		
		if(!playing)
		{
			graphics.fill(x, y, x + 1, y + 6, color);
			graphics.fill(x + 2, y, x + 3, y + 6, color);
			return;
		}
		
		for(int row = 0; row < 5; row++)
			graphics.fill(x, y + row, x + 5 - Math.abs(row - 2), y + row + 1,
				color);
	}
	
	private NeteaseImageCache.Texture coverInfo()
	{
		NeteaseSong song = NeteaseMusicPlayer.INSTANCE.getCurrentSong();
		
		if(coversClosed || song == null || song.coverUrl().isBlank())
			return null;
		
		try
		{
			return covers.get(song.coverUrl());
		}catch(RuntimeException e)
		{
			// 缓存内部可能已经释放，取不到封面不该打断整帧渲染
			return null;
		}
	}
	
	/**
	 * 调色板（规格 §4.1）：封面重采样成 8×8 求算术平均，再按 1.3 / 1.5 放大
	 * 并施加亮度下限。结果按歌曲缓存，换歌时才重算。
	 */
	private void updatePalette(NeteaseSong song)
	{
		long songId = song == null ? -1L : song.id();
		String url = song == null ? "" : song.coverUrl();
		
		// 首次运行时封面往往还在下载，结果会停在兜底色上，所以还没取到真实
		// 颜色时要允许每帧重试一次（有封面后每首歌只会算一次）
		boolean retryFallback = palettePrimary == FALLBACK_PRIMARY
			&& song != null && !url.isBlank();
		
		if(songId == paletteSongId && url.equals(paletteUrl) && !retryFallback)
			return;
		
		paletteSongId = songId;
		paletteUrl = url;
		palettePrimary = FALLBACK_PRIMARY;
		paletteSecondary = FALLBACK_SECONDARY;
		
		int[] average = averageCoverColor();
		
		if(average == null)
			return;
		
		int rgb = average[0] << 16 | average[1] << 8 | average[2];
		palettePrimary = MusicIslandState.accentFromAverage(rgb, 1.3F);
		paletteSecondary = MusicIslandState.accentFromAverage(rgb, 1.5F);
	}
	
	private int[] averageCoverColor()
	{
		NeteaseImageCache.Texture cover = coverInfo();
		
		if(cover == null)
			return null;
		
		NativeImage pixels = pixelsOf(cover.location());
		
		if(pixels == null || pixels.getWidth() <= 0 || pixels.getHeight() <= 0)
			return null;
		
		long red = 0L;
		long green = 0L;
		long blue = 0L;
		int samples = 0;
		int width = pixels.getWidth();
		int height = pixels.getHeight();
		
		for(int cellY = 0; cellY < 8; cellY++)
			for(int cellX = 0; cellX < 8; cellX++)
			{
				int pixelX = Math.min(width - 1,
					(cellX * 2 + 1) * width / 16);
				int pixelY = Math.min(height - 1,
					(cellY * 2 + 1) * height / 16);
				int pixel = pixels.getPixelRGBA(pixelX, pixelY);
				
				// getPixelRGBA 的低字节是红（NativeImage 的 RGBA 字节序）
				red += pixel & 0xFF;
				green += pixel >>> 8 & 0xFF;
				blue += pixel >>> 16 & 0xFF;
				samples++;
			}
		
		if(samples == 0)
			return null;
		
		return new int[] {(int)(red / samples), (int)(green / samples),
			(int)(blue / samples)};
	}
	
	/**
	 * {@code TextureManager.getTexture} 对未知位置返回 missing texture，
	 * 所以"是 DynamicTexture"本身就是判断，不需要捕异常。
	 */
	private static NativeImage pixelsOf(ResourceLocation location)
	{
		try
		{
			AbstractTexture texture = Minecraft.getInstance()
				.getTextureManager().getTexture(location);
			return texture instanceof DynamicTexture dynamic
				? dynamic.getPixels() : null;
		}catch(RuntimeException e)
		{
			return null;
		}
	}
	
	/** 按目标字号绘制：字号是"以行为单位的高度"，因此缩放 = 字号/行高。 */
	private void drawText(GuiGraphics graphics, Font font, String text,
		float size, float x, float y, int color, float alpha)
	{
		if(text.isEmpty() || alpha <= 0.01F)
			return;
		
		float scale = size / font.lineHeight;
		graphics.pose().pushPose();
		
		try
		{
			graphics.pose().translate(x, y, 0);
			graphics.pose().scale(scale, scale, 1F);
			graphics.drawString(font, text, 0, 0, withAlpha(color, alpha),
				false);
		}finally
		{
			graphics.pose().popPose();
		}
	}
	
	private void drawTextRight(GuiGraphics graphics, Font font, String text,
		float size, float right, float y, int color, float alpha)
	{
		if(text.isEmpty() || alpha <= 0.01F)
			return;
		
		float scale = size / font.lineHeight;
		graphics.pose().pushPose();
		
		try
		{
			graphics.pose().translate(right, y, 0);
			graphics.pose().scale(scale, scale, 1F);
			graphics.drawString(font, text, -font.width(text), 0,
				withAlpha(color, alpha), false);
		}finally
		{
			graphics.pose().popPose();
		}
	}
	
	/** 按像素宽度截断文字，避免溢出岛体。 */
	private static String trim(Font font, String text, int maxWidth)
	{
		if(text == null || text.isEmpty() || font.width(text) <= maxWidth)
			return text == null ? "" : text;
		
		int low = 0;
		int high = text.length();
		
		while(low < high)
		{
			int middle = (low + high + 1) >>> 1;
			
			if(font.width(text.substring(0, middle)) <= maxWidth)
				low = middle;
			else
				high = middle - 1;
		}
		
		return text.substring(0, low);
	}
	
	private static int withAlpha(int color, float alpha)
	{
		int applied = Math.round(Mth.clamp(alpha, 0F, 1F) * (color >>> 24));
		return applied << 24 | color & 0x00FFFFFF;
	}
	
	@Override
	public HudElementConfig getDefaultLayout()
	{
		return new HudElementConfig(HudElementConfig.HORIZONTAL_CENTER,
			HudElementConfig.VERTICAL_TOP, 0, 16);
	}
}

package net.wurstclient.clickgui2.music;

import java.util.List;
import java.util.Locale;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.util.Mth;
import net.wurstclient.clickgui2.FlatRenderer;
import net.wurstclient.clickgui2.supersoft.SuperSoftTheme;
import net.wurstclient.music.LyricLine;
import net.wurstclient.music.NeteaseMusicPlayer;
import net.wurstclient.music.NeteaseSong;
import net.wurstclient.music.apple.AppleLyricPlayer;

/**
 * 播放详情全屏覆盖层：大封面、歌词列、歌词偏移、进度条、控件、音量。
 */
public final class PlayerDetailOverlay extends MusicRegion
{
	private final AppleLyricPlayer appleLyrics = new AppleLyricPlayer();
	private long appleSongId = Long.MIN_VALUE;
	private final CoverParticleSystem particles = new CoverParticleSystem();
	private final CoverRippleSystem ripples = new CoverRippleSystem();
	private final StarRiverBackground starRiver = new StarRiverBackground();
	private boolean draggingDetailProgress;
	private boolean draggingDetailVolume;
	private boolean draggingCover;

	public PlayerDetailOverlay(MusicContext ctx)
	{
		super(ctx);
	}

	public void render(GuiGraphics graphics, MusicContext.Bounds b, int mouseX,
		int mouseY)
	{
		float progress = ctx.detailMotion.update(ctx.detailVisible ? 1 : 0);
		if(progress <= 0.001F)
			return;
		int offset = Math.round((1 - progress) * b.height());
		int top = b.top + offset;
		int panelBottom = b.bottom + offset;
		FlatRenderer.fillRoundedRect(graphics, b.left, top, b.right,
			panelBottom, 10, BACKGROUND);
		NeteaseSong song = player().getCurrentSong();
		if(song == null)
			return;
		drawCover(graphics, song.coverUrl(), b.left, top, b.right, panelBottom,
			0.16F);
		graphics.fill(b.left, top, b.right, panelBottom, 0xE7080A0E);
		graphics.fill(b.left, top, b.right, top + 1, 0x24FFFFFF);
		graphics.fill(b.left, top, b.left + 2, panelBottom,
			withAlpha(accent(), 0.45F));

		boolean closeHovered = contains(mouseX, mouseY, b.left + 8, top + 8,
			b.left + 28, top + 28);
		float closeHover = motion("detail-close").update(closeHovered ? 1 : 0);
		FlatRenderer.fillRoundedRect(graphics, b.left + 8, top + 8,
			b.left + 28, top + 28, 10,
			SuperSoftTheme.mix(0x1AFFFFFF, 0x44FFFFFF, closeHover));
		drawIcon(graphics, ICON_CLOSE, b.left + 15, top + 15, 6, TEXT, 1);
		drawText(graphics, "NOW PLAYING", 5, b.left + 36, top + 10,
			withAlpha(accent(), 0.82F), 100);
		drawText(graphics, song.name(), 10, b.left + 36, top + 21, TEXT, 116);
		drawText(graphics, song.artist(), 6, b.left + 36, top + 36, MUTED, 116);

		int coverSize = Math.max(96, Math.min(120, b.height() - 132));
		int coverLeft = b.left + 28;
		int coverTop = top + 53;
		FlatRenderer.fillRoundedRect(graphics, coverLeft - 5, coverTop + 5,
			coverLeft + coverSize + 5, coverTop + coverSize + 8, 9, 0x55000000);
		drawCover(graphics, song.coverUrl(), coverLeft, coverTop,
			coverLeft + coverSize, coverTop + coverSize, 1);
		FlatRenderer.drawRoundedOutline(graphics, coverLeft, coverTop,
			coverLeft + coverSize, coverTop + coverSize, 8, 0x2AFFFFFF);
		if(player().getState() == NeteaseMusicPlayer.PlaybackState.PLAYING)
		{
			int pulse = Math.round((float)(Math.sin(System.currentTimeMillis()
				/ 420D) * 0.5 + 0.5) * 40);
			FlatRenderer.drawRoundedOutline(graphics, coverLeft - 2, coverTop - 2,
				coverLeft + coverSize + 2, coverTop + coverSize + 2, 9,
				withAlpha(accent(), (70 + pulse) / 255F));
		}
		// MineRadio 封面交互：按住封面时粒子迸发 + 波纹扩散
		particles.update();
		particles.render(graphics);
		ripples.update();
		ripples.render(graphics);

		int lyricLeft = b.left + 154;
		int lyricRight = b.right - 12;
		drawText(graphics, "LYRICS", 5, lyricLeft + 8, top + 17,
			withAlpha(MUTED, 0.8F), lyricRight - lyricLeft - 16);
		renderLyricTiming(graphics, lyricRight, top, mouseX, mouseY);
		// MineRadio 星河流：播放时歌词区背景漂浮闪烁星点
		boolean playing = player().getState()
			== NeteaseMusicPlayer.PlaybackState.PLAYING;
		float energy = playing ? 0.65F : 0.05F;
		starRiver.update(energy);
		starRiver.render(graphics, lyricLeft, top + 34, lyricRight,
			panelBottom - 66, energy);
		renderDetailLyrics(graphics, lyricLeft, top + 34, lyricRight,
			panelBottom - 66);
		int progressLeft = b.left + 16;
		int progressRight = b.right - 16;
		int progressY = panelBottom - 51;
		long duration = player().getDurationMs();
		float songProgress = duration <= 0 ? 0
			: Mth.clamp(player().getPositionMs() / (float)duration, 0, 1);
		graphics.fill(progressLeft, progressY, progressRight, progressY + 2,
			0x26FFFFFF);
		int thumbX = progressLeft
			+ Math.round((progressRight - progressLeft) * songProgress);
		graphics.fill(progressLeft, progressY, thumbX, progressY + 2, accent());
		FlatRenderer.fillRoundedRect(graphics, thumbX - 3, progressY - 3,
			thumbX + 4, progressY + 5, 4, TEXT);
		drawText(graphics,
			NeteaseMusicPlayer.formatTime(player().getPositionMs()), 5,
			progressLeft, progressY + 6, withAlpha(TEXT, 0.5F), 40);
		String durationText = NeteaseMusicPlayer.formatTime(duration);
		drawTextRight(graphics, durationText, 5, progressRight, progressY + 6,
			withAlpha(TEXT, 0.5F), 40);

		int controlY = panelBottom - 20;
		int toggleX = b.left + b.width() / 2;
		int previousX = toggleX - 31;
		int nextX = toggleX + 31;
		renderControl(graphics, "detail-previous", previousX, controlY, 10,
			mouseX, mouseY, false);
		renderControl(graphics, "toggle", toggleX, controlY, 15, mouseX, mouseY,
			true);
		renderControl(graphics, "detail-next", nextX, controlY, 10,
			mouseX, mouseY, false);
		drawVolume(graphics, b.right - 99, controlY, withAlpha(TEXT, 0.6F),
			false);
		int volumeLeft = b.right - 84;
		int volumeRight = b.right - 28;
		graphics.fill(volumeLeft, controlY - 1, volumeRight, controlY + 1,
			0x33FFFFFF);
		graphics.fill(volumeLeft, controlY - 1,
			volumeLeft + Math.round((volumeRight - volumeLeft)
				* player().getVolume()),
			controlY + 1, accent());
	}

	private void renderDetailLyrics(GuiGraphics graphics, int left, int top,
		int right, int bottom)
	{
		List<LyricLine> lyrics = player().getLyrics();
		NeteaseSong song = player().getCurrentSong();
		if(lyrics.isEmpty() || song == null)
		{
			drawCenteredText(graphics, "No lyrics available", 7,
				(left + right) / 2, (top + bottom) / 2, MUTED,
				right - left - 8);
			return;
		}
		// Apple Music 风格歌词播放器（applemusic-like-lyrics 1:1 移植）
		if(song.id() != appleSongId)
		{
			appleSongId = song.id();
			appleLyrics.setLyricLines(lyrics,
				player().getAdjustedLyricPositionMs());
		}
		appleLyrics.setContainerHeight(bottom - top);
		appleLyrics.setPlaying(player().getState()
			== NeteaseMusicPlayer.PlaybackState.PLAYING);
		appleLyrics.setCurrentTime(player().getAdjustedLyricPositionMs(),
			false);
		// 每帧推进弹簧（位置/缩放解析解），否则所有行冻结在初始位置
		appleLyrics.update();
		// Skia 区域管线：真模糊/真渐变/真缩放的矢量歌词；失败时回退 MC 字体
		org.jetbrains.skia.Canvas canvas = null;
		try
		{
			canvas = net.wurstclient.render.skia.SkiaRegionRenderer.get()
				.beginRegion(left, top, right - left, bottom - top);
		}catch(Throwable t)
		{
			// native 初始化失败 → MC 字体路径
		}
		if(canvas != null)
		{
			try
			{
				appleLyrics.renderSkia(canvas, left, top, right, bottom,
					0xFF007CFF, 0x59FFFFFF);
			}finally
			{
				net.wurstclient.render.skia.SkiaRegionRenderer.get()
					.endRegion(graphics);
			}
			return;
		}
		appleLyrics.render(graphics, left + 4, top, right - 4, bottom,
			0xFF007CFF, 0x59FFFFFF);
	}

	/**
	 * 详情层歌词区域的滚轮浏览；鼠标不在歌词区域内时不消费事件。
	 */
	public boolean scrollLyrics(double mouseX, double mouseY, double delta,
		MusicContext.Bounds b)
	{
		if(ctx.detailMotion.get() < 0.9F)
			return false;
		int offset = Math.round((1 - ctx.detailMotion.get()) * b.height());
		int top = b.top + offset;
		int panelBottom = b.bottom + offset;
		int lyricLeft = b.left + 154;
		int lyricRight = b.right - 12;
		int lyricTop = top + 34;
		int lyricBottom = panelBottom - 66;
		if(!contains(mouseX, mouseY, lyricLeft, lyricTop, lyricRight,
			lyricBottom))
			return false;
		appleLyrics.scroll(delta);
		return true;
	}

	private void renderLyricTiming(GuiGraphics graphics, int right, int top,
		int mouseX, int mouseY)
	{
		int center = right - 43;
		renderTimingButton(graphics, center - 29, top + 19, "-", mouseX,
			mouseY);
		boolean resetHovered = contains(mouseX, mouseY, center - 19, top + 12,
			center + 19, top + 27);
		float resetHover = motion("lyric-reset").update(resetHovered ? 1 : 0);
		FlatRenderer.fillRoundedRect(graphics, center - 19, top + 12,
			center + 19, top + 27, 5,
			SuperSoftTheme.mix(0x16FFFFFF, withAlpha(accent(), 0.19F),
				resetHover));
		String offset = String.format(Locale.ROOT, "%+.1fs",
			player().getLyricOffsetMs() / 1000D);
		drawCenteredText(graphics, offset, 5, center, top + 17,
			resetHovered ? TEXT : MUTED, 34);
		renderTimingButton(graphics, center + 29, top + 19, "+", mouseX,
			mouseY);
	}

	private void renderTimingButton(GuiGraphics graphics, int x, int y,
		String label, int mouseX, int mouseY)
	{
		boolean hovered = contains(mouseX, mouseY, x - 8, y - 7, x + 8,
			y + 8);
		float hover = motion("lyric-timing-" + label).update(hovered ? 1 : 0);
		FlatRenderer.fillRoundedRect(graphics, x - 8, y - 7, x + 8, y + 8,
			5, SuperSoftTheme.mix(0x16FFFFFF, withAlpha(accent(), 0.19F),
				hover));
		drawCenteredText(graphics, label, 6, x, y - 3,
			hovered ? TEXT : MUTED, 12);
	}

	public boolean click(double mouseX, double mouseY, MusicContext.Bounds b)
	{
		if(ctx.detailMotion.get() < 0.9F)
			return true;
		int top = b.top + Math.round((1 - ctx.detailMotion.get()) * b.height());
		if(contains(mouseX, mouseY, b.left + 8, top + 8, b.left + 28,
			top + 28))
		{
			ctx.detailVisible = false;
			return true;
		}
		// MineRadio 封面粒子：按住封面从指针位置迸发
		int coverSize = Math.max(96, Math.min(120, b.height() - 132));
		int coverLeft = b.left + 28;
		int coverTop = top + 53;
		if(contains(mouseX, mouseY, coverLeft, coverTop, coverLeft + coverSize,
			coverTop + coverSize))
		{
			draggingCover = true;
			particles.spawnBurst(mouseX, mouseY, accent(), 8);
			ripples.spawnRipple(mouseX, mouseY, accent());
			return true;
		}
		int lyricRight = b.right - 12;
		int timingCenter = lyricRight - 43;
		if(contains(mouseX, mouseY, timingCenter - 37, top + 12,
			timingCenter - 21, top + 27))
		{
			player().adjustLyricOffset(-100);
			return true;
		}
		if(contains(mouseX, mouseY, timingCenter - 19, top + 12,
			timingCenter + 19, top + 27))
		{
			player().resetLyricOffset();
			return true;
		}
		if(contains(mouseX, mouseY, timingCenter + 21, top + 12,
			timingCenter + 37, top + 27))
		{
			player().adjustLyricOffset(100);
			return true;
		}
		int panelBottom =
			b.bottom + Math.round((1 - ctx.detailMotion.get()) * b.height());
		int lyricLeft = b.left + 154;
		int lyricTop = top + 34;
		int lyricBottom = panelBottom - 66;
		if(contains(mouseX, mouseY, lyricLeft, lyricTop, lyricRight,
			lyricBottom))
		{
			List<LyricLine> lyrics = player().getLyrics();
			int hit = appleLyrics.hitLine(mouseY, lyricTop, lyricBottom);
			if(hit >= 0 && hit < lyrics.size())
			{
				player().seekTo(appleLyrics.lineTime(hit));
				return true;
			}
		}
		int progressLeft = b.left + 16;
		int progressRight = b.right - 16;
		int progressY = b.bottom - 51;
		if(contains(mouseX, mouseY, progressLeft, progressY - 6, progressRight,
			progressY + 9) && player().getDurationMs() > 0)
		{
			draggingDetailProgress = true;
			seekDetail(mouseX, b);
			return true;
		}
		int controlY = b.bottom - 20;
		int toggleX = b.left + b.width() / 2;
		if(distance(mouseX, mouseY, toggleX - 31, controlY) <= 13)
			player().playPrevious();
		else if(distance(mouseX, mouseY, toggleX, controlY) <= 17)
			player().toggle();
		else if(distance(mouseX, mouseY, toggleX + 31, controlY) <= 13)
			player().playNext();
		else if(contains(mouseX, mouseY, b.right - 88, controlY - 8,
			b.right - 24, controlY + 8))
		{
			draggingDetailVolume = true;
			setDetailVolume(mouseX, b);
		}
		return true;
	}

	public boolean drag(double mouseX, double mouseY, int button, MusicContext.Bounds b)
	{
		if(button == 0 && draggingCover)
		{
			// 按住拖动封面时持续迸发粒子
			particles.spawnBurst(mouseX, mouseY, accent(), 3);
			return true;
		}		if(button == 0 && draggingDetailProgress)
		{
			seekDetail(mouseX, b);
			return true;
		}
		if(button == 0 && draggingDetailVolume)
		{
			setDetailVolume(mouseX, b);
			return true;
		}
		return false;
	}

	public boolean release(int button)
	{
		if(button == 0 && draggingCover)
		{
			draggingCover = false;
			return true;
		}
		if(button == 0 && (draggingDetailProgress || draggingDetailVolume))
		{
			draggingDetailProgress = false;
			draggingDetailVolume = false;
			return true;
		}
		return false;
	}

	private void seekDetail(double mouseX, MusicContext.Bounds b)
	{
		long duration = player().getDurationMs();
		if(duration <= 0)
			return;
		float value = Mth.clamp((float)((mouseX - (b.left + 16))
			/ (b.width() - 32D)), 0, 1);
		player().seekTo(Math.round(duration * value));
	}

	private void setDetailVolume(double mouseX, MusicContext.Bounds b)
	{
		player().setVolume(Mth.clamp((float)((mouseX - (b.right - 84)) / 56D),
			0, 1));
	}
}

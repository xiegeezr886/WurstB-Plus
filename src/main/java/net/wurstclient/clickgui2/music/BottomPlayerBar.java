package net.wurstclient.clickgui2.music;

import java.util.List;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.util.Mth;
import net.wurstclient.clickgui2.FlatRenderer;
import net.wurstclient.clickgui2.supersoft.SuperSoftTheme;
import net.wurstclient.music.NeteaseMusicPlayer;
import net.wurstclient.music.NeteaseSong;

/**
 * 播放栏区域：顶部全局进度条 + 底部播放栏 + 队列弹层。
 */
public final class BottomPlayerBar extends MusicRegion
{
	private boolean draggingPanelProgress;
	private boolean draggingPanelVolume;
	private int queueScroll;

	public BottomPlayerBar(MusicContext ctx)
	{
		super(ctx);
	}

	public void renderTopProgressBar(GuiGraphics graphics, MusicContext.Bounds b,
		int mouseX, int mouseY)
	{
		long duration = player().getDurationMs();
		float progress = duration <= 0 ? 0
			: Mth.clamp(player().getPositionMs() / (float)duration, 0, 1);
		int progressLeft = b.left + 10;
		int progressRight = b.right - 10;
		boolean progressHovered = contains(mouseX, mouseY, progressLeft,
			b.top + 8, progressRight, b.top + 18);
		float progressHover = motion("panel-progress").update(
			progressHovered || draggingPanelProgress ? 1 : 0);
		int progressY = b.top + 13;
		int barHeight = 1 + Math.round(progressHover);
		FlatRenderer.fillRoundedRect(graphics, progressLeft, progressY,
			progressRight, progressY + barHeight, barHeight / 2 + 1,
			0x2AFFFFFF);
		int thumbX = progressLeft
			+ Math.round((progressRight - progressLeft) * progress);
		if(thumbX > progressLeft)
			FlatRenderer.fillRoundedRect(graphics, progressLeft, progressY,
				thumbX, progressY + barHeight, barHeight / 2 + 1, accent());
	}

	public void render(GuiGraphics graphics, MusicContext.Bounds b, int mouseX,
		int mouseY)
	{
		int top = b.bottom - PLAYER_HEIGHT;
		FlatRenderer.fillRoundedRect(graphics, b.left + 2, top + 7,
			b.right - 2, b.bottom - 1, 14, 0x66000000);
		FlatRenderer.fillRoundedRect(graphics, b.left + 4, top + 4,
			b.right - 4, b.bottom - 4, 14, 0xC7111315);
		FlatRenderer.drawRoundedOutline(graphics, b.left + 4, top + 4,
			b.right - 4, b.bottom - 4, 14, 0x20FFFFFF);

		int toggleX = b.left + b.width() / 2;
		int controlY = top + 32;

		NeteaseSong song = player().getCurrentSong();
		if(song != null)
		{
			int coverLeft = b.left + 10;
			int coverTop = top + 8;
			int coverSize = 44;
			FlatRenderer.drawRoundedOutline(graphics, coverLeft - 1,
				coverTop - 1, coverLeft + coverSize + 1,
				coverTop + coverSize + 1, 9, withAlpha(accent(), 0.4F));
			drawCover(graphics, song.coverUrl(), coverLeft, coverTop,
				coverLeft + coverSize, coverTop + coverSize, 1);
			FlatRenderer.drawRoundedOutline(graphics, coverLeft, coverTop,
				coverLeft + coverSize, coverTop + coverSize, 8, 0x2AFFFFFF);
			int infoLeft = coverLeft + coverSize + 8;
			int infoRight = toggleX - 62;
			drawText(graphics, song.name(), 8, infoLeft, top + 15, TEXT,
				infoRight - infoLeft);
			drawText(graphics, song.artist(), 6, infoLeft, top + 28, MUTED,
				infoRight - infoLeft);
			drawText(graphics,
				NeteaseMusicPlayer.formatTime(player().getPositionMs()) + " / "
					+ NeteaseMusicPlayer.formatTime(player().getDurationMs()),
				5, infoLeft, top + 41, withAlpha(TEXT, 0.42F),
				infoRight - infoLeft);
		}else
		{
			FlatRenderer.fillRoundedRect(graphics, b.left + 10, top + 8,
				b.left + 54, top + 52, 10, 0x12FFFFFF);
			drawText(graphics, "MINERADIO", 8, b.left + 62, top + 18, TEXT,
				120);
			drawText(graphics, "Ready to play", 6, b.left + 62, top + 32,
				MUTED, 120);
		}

		int previousX = toggleX - 31;
		int nextX = toggleX + 31;
		renderModeButton(graphics, toggleX - 62, controlY, mouseX, mouseY);
		renderControl(graphics, "previous", previousX, controlY, 10, mouseX,
			mouseY, false);
		renderControl(graphics, "toggle", toggleX, controlY, 16, mouseX,
			mouseY, true);
		renderControl(graphics, "next", nextX, controlY, 10, mouseX,
			mouseY, false);
		renderLyricsButton(graphics, toggleX + 62, controlY, mouseX, mouseY);

		int volumeRight = b.right - 44;
		int volumeLeft = volumeRight - 58;
		int volumeY = controlY;
		drawVolume(graphics, volumeLeft - 12, volumeY, MUTED,
			player().getVolume() > 0.55F);
		graphics.fill(volumeLeft, volumeY, volumeRight, volumeY + 2,
			0x2AFFFFFF);
		graphics.fill(volumeLeft, volumeY,
			volumeLeft + Math.round((volumeRight - volumeLeft)
				* player().getVolume()),
			volumeY + 2, accent());
		drawText(graphics, Math.round(player().getVolume() * 100) + "%", 5,
			volumeRight + 4, volumeY - 4, withAlpha(TEXT, 0.55F), 24);
		boolean queueHovered = contains(mouseX, mouseY, b.right - 34, top + 16,
			b.right - 8, top + 48);
		float queueHover = motion("queue-button").update(
			ctx.queueVisible ? 1 : queueHovered ? 0.65F : 0);
		FlatRenderer.fillRoundedRect(graphics, b.right - 34, top + 16,
			b.right - 8, top + 48, 8,
			SuperSoftTheme.mix(0x00FFFFFF,
				withAlpha(accent(), 0.14F), queueHover));
		drawQueue(graphics, b.right - 21, controlY,
			SuperSoftTheme.mix(MUTED, TEXT, queueHover));
	}

	private void renderModeButton(GuiGraphics graphics, int x, int y,
		int mouseX, int mouseY)
	{
		boolean hovered = distance(mouseX, mouseY, x, y) <= 12;
		float hover = motion("play-mode").update(hovered ? 1 : 0);
		FlatRenderer.fillRoundedRect(graphics, x - 11, y - 11, x + 11,
			y + 11, 7, SuperSoftTheme.mix(0x00FFFFFF,
				withAlpha(accent(), 0.13F), hover));
		drawCenteredText(graphics, playbackModeLabel(), 6, x, y - 3,
			hovered ? TEXT : MUTED, 18);
	}

	private void renderLyricsButton(GuiGraphics graphics, int x, int y,
		int mouseX, int mouseY)
	{
		boolean hovered = distance(mouseX, mouseY, x, y) <= 12;
		float hover = motion("lyrics-button").update(
			ctx.detailVisible ? 1 : hovered ? 0.65F : 0);
		FlatRenderer.fillRoundedRect(graphics, x - 11, y - 11, x + 11,
			y + 11, 7,
			SuperSoftTheme.mix(0x00FFFFFF, withAlpha(accent(), 0.16F), hover));
		drawCenteredText(graphics, "词", 7, x, y - 4,
			SuperSoftTheme.mix(MUTED, TEXT, hover), 18);
	}

	private String playbackModeLabel()
	{
		return switch(player().getPlaybackMode())
		{
			case LOOP_ALL -> "循";
			case REPEAT_ONE -> "单";
			case SHUFFLE -> "随";
		};
	}

	public void renderQueuePopover(GuiGraphics graphics, MusicContext.Bounds b,
		int mouseX, int mouseY)
	{
		float progress = ctx.queueMotion.update(ctx.queueVisible ? 1 : 0);
		if(progress <= 0.001F || ctx.detailMotion.get() > 0.01F)
			return;
		int right = b.right - 8;
		int left = right - 176;
		int bottom = b.bottom - PLAYER_HEIGHT - 6
			+ Math.round((1 - progress) * 12);
		int top = bottom - 154;
		FlatRenderer.fillRoundedRect(graphics, left - 3, top + 3, right + 3,
			bottom + 4, 8, withAlpha(0xFF000000, progress * 0.45F));
		FlatRenderer.fillRoundedRect(graphics, left, top, right, bottom, 7,
			withAlpha(0xFF0B0E13, progress * 0.96F));
		FlatRenderer.drawRoundedOutline(graphics, left, top, right, bottom, 7,
			withAlpha(0xFFB8FFF5, progress * 0.12F));
		drawText(graphics, "CURRENT QUEUE", 7, left + 10, top + 9,
			withAlpha(TEXT, progress), 100);
		List<NeteaseSong> queue = player().getPlaylist();
		drawTextRight(graphics, queue.size() + " tracks", 5, right - 10,
			top + 10, withAlpha(MUTED, progress), 50);
		FlatRenderer.fillRoundedRect(graphics, left + 8, top + 25, left + 54,
			top + 41, 5, withAlpha(0xFF20272D, progress));
		drawCenteredText(graphics, playbackModeName(), 5, left + 31, top + 30,
			withAlpha(TEXT, progress), 40);
		FlatRenderer.fillRoundedRect(graphics, right - 44, top + 25, right - 8,
			top + 41, 5, withAlpha(0xFF20272D, progress));
		drawCenteredText(graphics, "清空", 5, right - 26, top + 30,
			withAlpha(MUTED, progress), 30);

		int visibleRows = 4;
		int maxScroll = Math.max(0, queue.size() - visibleRows);
		queueScroll = Mth.clamp(queueScroll, 0, maxScroll);
		int rowTop = top + 46;
		for(int visible = 0; visible < visibleRows; visible++)
		{
			int index = queueScroll + visible;
			if(index >= queue.size())
				break;
			NeteaseSong queuedSong = queue.get(index);
			int y = rowTop + visible * 25;
			boolean current = index == player().getCurrentIndex();
			boolean hovered = contains(mouseX, mouseY, left + 6, y, right - 6,
				y + 22);
			float hover = motion("queue-row-" + queuedSong.id()).update(
				current ? 1 : hovered ? 0.55F : 0);
			int rowColor = SuperSoftTheme.mix(TEXT, accent(), hover);
			FlatRenderer.fillRoundedRect(graphics, left + 6, y, right - 6,
				y + 22, 5,
				withAlpha(rowColor, progress * (0.03F + hover * 0.08F)));
			if(current)
				graphics.fill(left + 6, y + 4, left + 8, y + 18,
					withAlpha(accent(), progress));
			drawCover(graphics, queuedSong.coverUrl(), left + 11, y + 2,
				left + 29, y + 20, progress);
			drawText(graphics, queuedSong.name(), 6, left + 34, y + 3,
				withAlpha(current ? TEXT : 0xFFD8DFDE, progress), 112);
			drawText(graphics, queuedSong.artist(), 4, left + 34, y + 13,
				withAlpha(MUTED, progress), 112);
			if(current && player().getState()
				== NeteaseMusicPlayer.PlaybackState.PLAYING)
				drawPlaying(graphics, right - 18, y + 11, accent());
		}
		if(queue.isEmpty())
			drawCenteredText(graphics, "Queue is empty", 6, (left + right) / 2,
				top + 72, withAlpha(MUTED, progress), 140);
		else if(maxScroll > 0)
		{
			int trackTop = top + 47;
			int trackBottom = bottom - 7;
			int thumbHeight = Math.max(12,
				(trackBottom - trackTop) * visibleRows / queue.size());
			int thumbTop = trackTop + Math.round((trackBottom - trackTop
				- thumbHeight) * queueScroll / (float)maxScroll);
			graphics.fill(right - 4, trackTop, right - 3, trackBottom, 0x1FFFFFFF);
			graphics.fill(right - 4, thumbTop, right - 2,
				thumbTop + thumbHeight, withAlpha(accent(), progress * 0.55F));
		}
	}

	private String playbackModeName()
	{
		return switch(player().getPlaybackMode())
		{
			case LOOP_ALL -> "顺序循环";
			case REPEAT_ONE -> "单曲循环";
			case SHUFFLE -> "随机播放";
		};
	}

	public boolean click(double mouseX, double mouseY, MusicContext.Bounds b)
	{
		int top = b.bottom - PLAYER_HEIGHT;
		if(!contains(mouseX, mouseY, b.left, top, b.right, b.bottom))
			return false;
		long duration = player().getDurationMs();
		if(contains(mouseX, mouseY, b.left + 10, b.top + 8, b.right - 10,
			b.top + 18) && duration > 0)
		{
			draggingPanelProgress = true;
			seekPanelProgress(mouseX, b);
			return true;
		}
		int toggleX = b.left + b.width() / 2;
		int controlY = top + 32;
		int previousX = toggleX - 31;
		int nextX = toggleX + 31;
		if(distance(mouseX, mouseY, toggleX - 62, controlY) <= 12)
			player().cyclePlaybackMode();
		else if(distance(mouseX, mouseY, previousX, controlY) <= 13)
			player().playPrevious();
		else if(distance(mouseX, mouseY, toggleX, controlY) <= 17)
			player().toggle();
		else if(distance(mouseX, mouseY, nextX, controlY) <= 13)
			player().playNext();
		else if(distance(mouseX, mouseY, toggleX + 62, controlY) <= 12
			&& player().getCurrentSong() != null)
		{
			ctx.queueVisible = false;
			ctx.detailVisible = true;
		}
		else if(contains(mouseX, mouseY, b.right - 34, top + 16, b.right - 8,
			top + 48))
			ctx.queueVisible = !ctx.queueVisible;
		else if(contains(mouseX, mouseY, b.right - 116, top + 20,
			b.right - 40, top + 44))
		{
			draggingPanelVolume = true;
			setPanelVolume(mouseX, b);
		}
		else if(player().getCurrentSong() != null)
		{
			ctx.queueVisible = false;
			ctx.detailVisible = true;
		}
		return true;
	}

	public boolean clickQueuePopover(double mouseX, double mouseY,
		MusicContext.Bounds b)
	{
		if(ctx.queueMotion.get() < 0.82F)
			return true;
		int right = b.right - 8;
		int left = right - 176;
		int bottom = b.bottom - PLAYER_HEIGHT - 6;
		int top = bottom - 154;
		if(!contains(mouseX, mouseY, left, top, right, bottom))
			return false;
		if(contains(mouseX, mouseY, left + 8, top + 25, left + 54,
			top + 41))
		{
			player().cyclePlaybackMode();
			return true;
		}
		if(contains(mouseX, mouseY, right - 44, top + 25, right - 8,
			top + 41))
		{
			player().clearPlaylist();
			queueScroll = 0;
			return true;
		}
		int rowTop = top + 46;
		if(contains(mouseX, mouseY, left + 6, rowTop, right - 6,
			rowTop + 100))
		{
			int visible = (int)((mouseY - rowTop) / 25);
			int index = queueScroll + visible;
			List<NeteaseSong> queue = player().getPlaylist();
			if(index >= 0 && index < queue.size())
				player().play(queue, index);
		}
		return true;
	}

	public boolean scrollQueue(double delta, double mouseX, double mouseY,
		MusicContext.Bounds b)
	{
		int right = b.right - 8;
		int left = right - 176;
		int bottom = b.bottom - PLAYER_HEIGHT - 6;
		if(!contains(mouseX, mouseY, left, bottom - 154, right, bottom))
			return false;
		int max = Math.max(0, player().getPlaylist().size() - 4);
		queueScroll = Mth.clamp(queueScroll + (delta > 0 ? -1 : 1), 0,
			max);
		return true;
	}

	public boolean drag(double mouseX, double mouseY, int button, MusicContext.Bounds b)
	{
		if(button == 0 && draggingPanelProgress)
		{
			seekPanelProgress(mouseX, b);
			return true;
		}
		if(button == 0 && draggingPanelVolume)
		{
			setPanelVolume(mouseX, b);
			return true;
		}
		return false;
	}

	public boolean release(int button)
	{
		if(button == 0 && (draggingPanelProgress || draggingPanelVolume))
		{
			draggingPanelProgress = false;
			draggingPanelVolume = false;
			return true;
		}
		return false;
	}

	private void seekPanelProgress(double mouseX, MusicContext.Bounds b)
	{
		long duration = player().getDurationMs();
		if(duration <= 0)
			return;
		float value = Mth.clamp((float)((mouseX - (b.left + 10))
			/ (b.width() - 20D)), 0, 1);
		player().seekTo(Math.round(duration * value));
	}

	private void setPanelVolume(double mouseX, MusicContext.Bounds b)
	{
		player().setVolume(Mth.clamp((float)((mouseX - (b.right - 116)) / 76D),
			0, 1));
	}
}

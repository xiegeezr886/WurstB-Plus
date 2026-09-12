package net.wurstclient.hud2.elements;

import java.util.List;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.util.Mth;
import net.wurstclient.WurstClient;
import net.wurstclient.clickgui2.FlatRenderer;
import net.wurstclient.hud2.HudElement;
import net.wurstclient.hud2.HudLayout.HudElementConfig;
import net.wurstclient.hud2.HudManager;
import net.wurstclient.gui.visual.VisualTheme;
import net.wurstclient.music.LyricLine;
import net.wurstclient.music.NeteaseMusicPlayer;
import net.wurstclient.music.NeteaseMusicPlayer.PlaybackState;
import net.wurstclient.music.NeteaseSong;
import net.wurstclient.music.PlayerListener;
import net.wurstclient.music.apple.AppleLyricPlayer;
import net.wurstclient.util.ScreenRegistry;

public final class MusicLyricsHudElement extends HudElement
{
	private static final int WIDTH = 280;
	private static final int HEIGHT = 92;
	private static final int BACKGROUND = VisualTheme.SURFACE_68;
	private static final int OUTLINE = VisualTheme.BORDER;

	private final AppleLyricPlayer lyricsView = new AppleLyricPlayer();
	private long displayedSongId = Long.MIN_VALUE;
	private float visibility;
	private long lastRenderNanos;
	private volatile boolean songChangedPending;
	private final PlayerListener listener = new PlayerListener()
	{
		@Override
		public void onSongChanged(NeteaseSong song)
		{
			songChangedPending = true;
		}

		@Override
		public void onLyricsLoaded(List<LyricLine> lyrics)
		{
			songChangedPending = true;
		}
	};

	public MusicLyricsHudElement()
	{
		super("music_lyrics", "\u5e38\u9a7b\u6b4c\u8bcd");
	}

	@Override
	public int getWidth()
	{
		return WIDTH;
	}

	@Override
	public int getHeight()
	{
		return HEIGHT;
	}

	@Override
	public boolean renderEditorPreview()
	{
		return true;
	}

	@Override
	public void onEnable(HudManager manager)
	{
		NeteaseMusicPlayer.INSTANCE.addListener(listener);
	}

	@Override
	public void onDisable(HudManager manager)
	{
		NeteaseMusicPlayer.INSTANCE.removeListener(listener);
	}

	@Override
	public void render(GuiGraphics graphics, int x, int y, float partialTicks)
	{
		NeteaseMusicPlayer player = NeteaseMusicPlayer.INSTANCE;
		NeteaseSong song = player.getCurrentSong();
		List<LyricLine> lyrics = player.getLyrics();
		boolean preview = ScreenRegistry.HUD_EDITOR.isOpen();
		boolean playing = player.getState() == PlaybackState.PLAYING;
		boolean hasLyrics = song != null && !lyrics.isEmpty();
		updateVisibility(preview || playing && hasLyrics);
		if(visibility < 0.01F)
			return;

		long songId = song == null ? -1 : song.id();
		if(songChangedPending || songId != displayedSongId)
		{
			songChangedPending = false;
			displayedSongId = songId;
			List<LyricLine> shown = lyrics.isEmpty() && preview
				? List.of(new LyricLine(0, "Music flows with every adventure"),
					new LyricLine(4_000, "NetEase Cloud Music"))
				: lyrics;
			lyricsView.setLyricLines(shown, player.getAdjustedLyricPositionMs());
		}
		lyricsView.setContentWidth(WIDTH - 8);
		lyricsView.setContainerHeight(HEIGHT - 8);
		lyricsView.setPlaying(playing || preview);
		lyricsView.setCurrentTime(preview && lyrics.isEmpty()
			? 1_200 : player.getAdjustedLyricPositionMs(), false);
		lyricsView.update();

		FlatRenderer.fillRoundedRect(graphics, x, y, x + WIDTH, y + HEIGHT,
			10, withOpacity(BACKGROUND, visibility));
		FlatRenderer.drawRoundedOutline(graphics, x, y, x + WIDTH,
			y + HEIGHT, 10, withOpacity(OUTLINE, visibility));
		HudElementConfig config = WurstClient.INSTANCE.getHudManager().getLayout()
			.get(getId());
		float hudScale = config == null ? 1 : config.getScale();
		graphics.enableScissor(x, y, x + Math.round(WIDTH * hudScale),
			y + Math.round(HEIGHT * hudScale));
		try
		{
			lyricsView.render(graphics, x + 4, y + 4, x + WIDTH - 4,
				y + HEIGHT - 4);
		}finally
		{
			graphics.disableScissor();
		}
	}

	private void updateVisibility(boolean visible)
	{
		long now = System.nanoTime();
		if(lastRenderNanos == 0)
		{
			visibility = visible ? 1 : 0;
			lastRenderNanos = now;
			return;
		}
		float delta = Math.min(0.05F,
			(now - lastRenderNanos) / 1_000_000_000F);
		lastRenderNanos = now;
		float target = visible ? 1 : 0;
		float speed = visible ? 7 : 11;
		visibility += (target - visibility)
			* (1 - (float)Math.exp(-speed * delta));
		if(Math.abs(target - visibility) < 0.005F)
			visibility = target;
	}

	private static int withOpacity(int color, float opacity)
	{
		int alpha = Math.round((color >>> 24) * Mth.clamp(opacity, 0, 1));
		return color & 0x00FFFFFF | alpha << 24;
	}

	@Override
	public HudElementConfig getDefaultLayout()
	{
		return new HudElementConfig(HudElementConfig.HORIZONTAL_CENTER,
			HudElementConfig.VERTICAL_BOTTOM, 0, 45);
	}
}

package net.wurstclient.hud2.elements;

import java.util.List;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.util.Mth;
import net.wurstclient.WurstClient;
import net.wurstclient.clickgui2.FlatRenderer;
import net.wurstclient.hud2.HudElement;
import net.wurstclient.hud2.HudLayout.HudElementConfig;
import net.wurstclient.gui.visual.VisualTheme;
import net.wurstclient.music.LyricLine;
import net.wurstclient.music.LyricParser;
import net.wurstclient.music.NeteaseMusicPlayer;
import net.wurstclient.music.NeteaseMusicPlayer.PlaybackState;
import net.wurstclient.music.NeteaseSong;
import net.wurstclient.util.ScreenRegistry;

public final class MusicLyricsHudElement extends HudElement
{
	private static final int WIDTH = 200;
	private static final int HEIGHT = 40;
	private static final int BACKGROUND = VisualTheme.SURFACE_68;
	private static final int OUTLINE = VisualTheme.BORDER;
	private static final int TEXT = VisualTheme.TEXT;
	private static final long TRANSITION_NANOS = 340_000_000L;

	private long displayedSongId = Long.MIN_VALUE;
	private int displayedIndex = Integer.MIN_VALUE;
	private String previousText = "";
	private long transitionStarted;
	private float visibility;
	private long lastRenderNanos;

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
	public void render(GuiGraphics graphics, int x, int y, float partialTicks)
	{
		NeteaseMusicPlayer player = NeteaseMusicPlayer.INSTANCE;
		NeteaseSong song = player.getCurrentSong();
		List<LyricLine> lyrics = player.getLyrics();
		boolean preview = ScreenRegistry.HUD_EDITOR.isOpen();
		boolean playing = player.getState() == PlaybackState.PLAYING;
		int index = preview && lyrics.isEmpty() ? 0
			: LyricParser.findCurrentIndex(lyrics,
				player.getAdjustedLyricPositionMs());
		boolean hasLyrics = song != null && index >= 0 && !lyrics.isEmpty();
		updateVisibility(preview || playing && hasLyrics);
		if(visibility < 0.01F)
			return;

		String current = preview && lyrics.isEmpty()
			? "Music flows with every adventure"
			: lyrics.get(index).text();
		String next = preview && lyrics.isEmpty() ? "NetEase Cloud Music"
			: index + 1 < lyrics.size() ? lyrics.get(index + 1).text() : "";
		long songId = song == null ? -1 : song.id();
		long now = System.nanoTime();
		if(songId != displayedSongId)
		{
			displayedSongId = songId;
			displayedIndex = Integer.MIN_VALUE;
			previousText = "";
		}
		if(index != displayedIndex)
		{
			if(displayedIndex != Integer.MIN_VALUE)
				previousText = currentTextFor(lyrics, displayedIndex, current);
			displayedIndex = index;
			transitionStarted = now;
		}
		float transition = transitionStarted == 0 ? 1 : Mth.clamp(
			(now - transitionStarted) / (float)TRANSITION_NANOS, 0, 1);
		transition = smoothStep(transition);

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
			if(!previousText.isEmpty() && transition < 1)
				drawScaledCentered(graphics, previousText, x + WIDTH / 2,
					y + 15 - Math.round(transition * 13), 1.05F - 0.15F
						* transition,
					visibility * (1 - transition * 0.5F));
			drawScaledCentered(graphics, current, x + WIDTH / 2,
				y + 15 + Math.round((1 - transition) * 13),
				0.9F + 0.15F * transition,
				visibility * (0.5F + transition * 0.5F));
			if(!next.isEmpty())
				drawScaledCentered(graphics, next, x + WIDTH / 2,
					y + 29 + Math.round((1 - transition) * 4), 0.9F,
					visibility * 0.5F);
		}finally
		{
			graphics.disableScissor();
		}
	}

	private String currentTextFor(List<LyricLine> lyrics, int index,
		String fallback)
	{
		if(index >= 0 && index < lyrics.size())
			return lyrics.get(index).text();
		return fallback;
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

	private void drawScaledCentered(GuiGraphics graphics, String text,
		int centerX, int y, float scale, float opacity)
	{
		Font font = WurstClient.MC.font;
		String shown = font.plainSubstrByWidth(text,
			Math.round((WIDTH - 18) / scale));
		graphics.pose().pushPose();
		graphics.pose().translate(centerX, y, 0);
		graphics.pose().scale(scale, scale, 1);
		graphics.drawString(font, shown, -font.width(shown) / 2, 0,
			withOpacity(TEXT, opacity), false);
		graphics.pose().popPose();
	}

	private static float smoothStep(float value)
	{
		return value * value * (3 - 2 * value);
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

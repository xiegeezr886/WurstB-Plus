package net.wurstclient.hud2.elements;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.util.Mth;
import net.wurstclient.WurstClient;
import net.wurstclient.clickgui2.FlatRenderer;
import net.wurstclient.hud2.HudElement;
import net.wurstclient.hud2.HudLayout.HudElementConfig;
import net.wurstclient.gui.visual.VisualTheme;
import net.wurstclient.music.NeteaseMusicPlayer;
import net.wurstclient.music.NeteaseMusicPlayer.PlaybackState;
import net.wurstclient.music.NeteaseSong;
import net.wurstclient.util.ScreenRegistry;

public final class MusicIslandHudElement extends HudElement
{
	private static final int MIN_WIDTH = 94;
	private static final int MAX_WIDTH = 250;
	private static final int HEIGHT = 20;
	private static final int ACCENT = 0xFFEC4141;
	private static final int BACKGROUND = VisualTheme.SURFACE_85;
	private static final int OUTLINE = VisualTheme.BORDER;
	private static final int PRIMARY_TEXT = VisualTheme.TEXT;

	private float animatedWidth = MIN_WIDTH;
	private long lastRenderNanos;

	public MusicIslandHudElement()
	{
		super("music_island", "\u97f3\u4e50\u7075\u52a8\u5c9b");
	}

	@Override
	public int getWidth()
	{
		return Math.round(animatedWidth);
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
		boolean preview = ScreenRegistry.HUD_EDITOR.isOpen();
		boolean showMusic = preview || song != null
			&& (player.getState() == PlaybackState.PLAYING
				|| player.getState() == PlaybackState.LOADING);

		Font font = WurstClient.MC.font;
		String base = "WurstB FPS: " + WurstClient.MC.getFps();
		String music = preview && song == null ? "Now Playing: NetEase Music"
			: song == null ? "" : "\u6b63\u5728\u64ad\u653e: " + song.name();
		music = font.plainSubstrByWidth(music, 132);
		int baseWidth = font.width(base) + 20;
		int targetWidth = baseWidth;
		if(showMusic)
			targetWidth += font.width("  \u00b7  " + music) + 14;
		targetWidth = Mth.clamp(targetWidth, MIN_WIDTH, MAX_WIDTH);
		updateWidth(targetWidth);

		int width = getWidth();
		FlatRenderer.fillRoundedRect(graphics, x, y, x + width, y + HEIGHT,
			10, BACKGROUND);
		FlatRenderer.drawRoundedOutline(graphics, x, y, x + width,
			y + HEIGHT, 10, OUTLINE);

		HudElementConfig config = WurstClient.INSTANCE.getHudManager().getLayout()
			.get(getId());
		float hudScale = config == null ? 1 : config.getScale();
		graphics.enableScissor(x, y, x + Math.round(width * hudScale),
			y + Math.round(HEIGHT * hudScale));
		try
		{
			graphics.drawString(font, base, x + 10, y + 6, PRIMARY_TEXT, false);
			if(showMusic)
			{
				int musicX = x + baseWidth;
				graphics.drawString(font, "\u00b7", musicX, y + 6,
					VisualTheme.TEXT_MUTED, false);
				graphics.drawString(font, music, musicX + 8, y + 6,
					PRIMARY_TEXT, false);
				drawCoverIndicator(graphics, x + width - 15, y + 5);
			}
		}finally
		{
			graphics.disableScissor();
		}
	}

	private void updateWidth(int targetWidth)
	{
		long now = System.nanoTime();
		if(lastRenderNanos == 0)
		{
			animatedWidth = targetWidth;
			lastRenderNanos = now;
			return;
		}
		float delta = Math.min(0.05F,
			(now - lastRenderNanos) / 1_000_000_000F);
		lastRenderNanos = now;
		float step = 1 - (float)Math.exp(-12 * delta);
		animatedWidth = Mth.lerp(step, animatedWidth, targetWidth);
		if(Math.abs(targetWidth - animatedWidth) < 0.1F)
			animatedWidth = targetWidth;
	}

	private void drawCoverIndicator(GuiGraphics graphics, int x, int y)
	{
		FlatRenderer.fillRoundedRect(graphics, x, y, x + 10, y + 10, 2,
			VisualTheme.CONTROL);
		long now = System.nanoTime();
		for(int i = 0; i < 3; i++)
		{
			double phase = now / 150_000_000D + i * 1.35;
			int height = 2 + (int)Math.round((Math.sin(phase) + 1) * 1.5);
			int barX = x + 2 + i * 2;
			graphics.fill(barX, y + 8 - height, barX + 1, y + 8, ACCENT);
		}
	}

	@Override
	public HudElementConfig getDefaultLayout()
	{
		return new HudElementConfig(HudElementConfig.HORIZONTAL_CENTER,
			HudElementConfig.VERTICAL_TOP, 0, 16);
	}
}

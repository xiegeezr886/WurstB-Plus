/*
 * Copyright (c) 2014-2025 Wurst-Imperium and contributors.
 *
 * This source code is subject to the terms of the GNU General Public
 * License, version 3. If a copy of the GPL was not distributed with this
 * file, You can obtain one at: https://www.gnu.org/licenses/gpl-3.0.txt
 */
package net.wurstclient.twilight;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;
import java.util.function.Supplier;

import org.lwjgl.glfw.GLFW;

import com.mojang.blaze3d.platform.NativeImage;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.wurstclient.music.LyricLine;
import net.wurstclient.music.NeteaseMusicPlayer;
import net.wurstclient.music.NeteasePlaylist;
import net.wurstclient.music.NeteaseSong;
import net.wurstclient.music.PlayerListener;
import net.wurstclient.music.apple.AppleLyricPlayer;
import net.wurstclient.twilight.TwilightHomeLayout.Home;
import net.wurstclient.twilight.TwilightShellLayout.Frame;
import net.wurstclient.twilight.TwilightShellLayout.Rect;

/**
 * The Twilight Echo shell with the streaming home page.
 *
 * <p>
 * Geometry comes from {@link TwilightShellLayout} and {@link TwilightHomeLayout}
 * (both reproduce the effective {@code paper-light} values and the reference
 * screenshots), colours from {@link TwilightTheme}, the accent from
 * {@link TwilightAccent} and everything is drawn with {@link TwilightSkia}. When
 * Skia is unavailable the same layout is painted with vanilla rectangles so the
 * screen still works, just less precisely.
 *
 * <p>
 * Opened with {@code .twilight}. The lyrics page is not part of the reference
 * port on purpose: the existing AMLL renderer stays.
 */
public final class TwilightShellScreen extends Screen
{
	/** Measured in {@code streaming-home.png} until the theme exposes it. */
	private static final int PAGE_BG = 0xFFF4F4F7;
	
	private static final String[] NAV_LABELS = {"主页", "发现歌单", "音乐库",
		"最近播放", "Bilibili"};
	
	private static final String PAGE_SUBTITLE = "下午好，继续享受音乐";
	private static final String SEARCH_HINT = "搜索歌曲、歌手、歌单";
	
	/**
	 * Cover rounding, in the reference's CSS pixels. Small covers keep the list
	 * radius so they match their placeholders; the immersive cover is the one
	 * large surface and gets the larger radius the reference uses there.
	 */
	private static final int BAR_COVER_RADIUS = 10;
	private static final int IMMERSIVE_COVER_RADIUS = 24;
	
	private static final String HERO_DAY = "31";
	private static final String HERO_DATE = "7月31日 · 周五";
	private static final String HERO_UPDATE = "每日 06:00 更新";
	private static final String HERO_TITLE = "每日推荐";
	private static final String HERO_TITLE_EN = "DAILY MIX";
	private static final String HERO_DESC_1 = "从你的听歌足迹里长出来的今日歌单，";
	private static final String HERO_DESC_2 = "每一首都有它出现的理由。";
	private static final String HERO_PLAY = "播放全部";
	private static final String HERO_OPEN = "查看全部";
	
	private static final String DUO_LEFT_NAME = "私人漫游";
	private static final String DUO_LEFT_SUB = "Roaming FM · 随心而行的电台";
	private static final String DUO_RIGHT_NAME = "私人雷达";
	private static final String DUO_RIGHT_SUB = "Private Radar · 捕捉你错过的好歌";
	
	private static final String SECTION_TITLE = "今日为你精选";
	private static final String SECTION_SUB = "点一首开始播放，队列会自动接上整份每日推荐";
	private static final String SECTION_MORE = "完整歌单";
	
	private static final String NOW_TITLE = "Bismuth";
	private static final String NOW_ARTIST = "Ludicin";
	
	private static final float RADIUS_BAR = 14F;
	private static final float RADIUS_COVER = 6F;
	private static final float[] COLLAGE_ROTATION = {-3F, 5F, -7F};
	
	private final TwilightTheme theme = TwilightTheme.light();
	
	/**
	 * The accent follows the playing track. Until that is wired the fallback is
	 * the reference's blue; a later round pushes the sampled cover accent in
	 * through {@link #setPalette}.
	 */
	private TwilightAccent.Palette palette = TwilightAccent.fallback();
	
	/**
	 * The player is a process-wide singleton ({@code NeteaseMusicPlayer} is an
	 * enum), so playback survives opening and closing this screen and the old
	 * screens and this one never run two engines at once.
	 */
	private static final NeteaseMusicPlayer PLAYER = NeteaseMusicPlayer.INSTANCE;
	
	private Frame frame;
	private Home home;
	private int activeNav;
	private int hoverNav = -1;
	
	private TwilightCoverCache covers;
	private List<NeteaseSong> homeSongs;
	private boolean homeLoading;
	private String statusLine;
	private Rect[] chartRows;
	private int hoverChart = -1;
	
	private List<NeteasePlaylist> playlists;
	private List<NeteaseSong> likedSongs;
	private boolean pageLoading;
	private String pageStatus;
	private Rect[] pageRows;
	private Rect[] playlistCards;
	private int hoverPageRow = -1;
	private int hoverPlaylist = -1;
	
	/**
	 * Scrolling steps whole rows: the rectangles stay where they are and the
	 * <em>content</em> moves, which keeps the drawing free of clipping problems
	 * (there is no scissor inside the Skia region).
	 */
	private int scrollRows;
	
	/** Search: the header pill doubles as the screen's only text input. */
	private final StringBuilder search = new StringBuilder();
	private boolean searchFocused;
	private boolean searchMode;
	private List<NeteaseSong> searchResults;
	
	/**
	 * The immersive player page. Lyrics are drawn by the existing Apple Music
	 * like Lyrics renderer, not by anything from the reference project.
	 */
	private final AppleLyricPlayer appleLyrics = new AppleLyricPlayer();
	private boolean immersive;
	private long displayedSongId = -1L;
	
	/** Sub-row scroll offset in pixels; {@link #scrollRows} carries the rows. */
	private float scrollSub;
	private int displayedLyricCount = -1;
	
	/**
	 * Mirror of the player state, refreshed by {@link #listener} on whichever
	 * thread the player uses. Only primitives are copied here; textures are
	 * fetched during rendering so nothing touches GL off the render thread.
	 */
	private long positionMs;
	private long durationMs;
	
	private final PlayerListener listener = new PlayerListener()
	{
		@Override
		public void onSongChanged(NeteaseSong song)
		{
			accentPending = true;
			coverSong = null;
		}
		
		@Override
		public void onPositionChanged(long position, long duration)
		{
			positionMs = position;
			durationMs = duration;
		}
	};
	
	/** Set by the listener (any thread), consumed by {@link #refreshAccent()}. */
	private boolean accentPending;
	private NeteaseSong coverSong;
	private int coverCooldown;
	
	/** Where ESC returns to; {@code null} closes to the game. */
	private final Screen parent;
	
	/**
	 * The local NeteaseCloudMusicApiEnhanced client, the data source the
	 * reference uses. Recreated by {@link #init()} like the cover cache because
	 * {@link #removed()} shuts its worker threads down.
	 */
	private TwilightMusicService service;
	
	/**
	 * QR login overlay. The PNG comes from the local service as a data URL, so
	 * no QR encoder is needed here; {@link #qrTexture} is registered with the
	 * global texture manager and released in {@link #removed()}.
	 */
	private boolean loginOverlay;
	private TwilightMusicService.QrSession qrSession;
	private String qrStatus = "";
	private String qrError = "";
	private ResourceLocation qrTexture;
	private int qrTextureSize;
	private int qrCooldown;
	private boolean qrBusy;
	
	/**
	 * Playlist detail page, the view the reference opens when a card is
	 * clicked instead of playing it straight away. Null means "no detail page",
	 * and the track list reuses the same row renderer as the other pages.
	 */
	private NeteasePlaylist openedPlaylist;
	private List<NeteaseSong> openedTracks;
	private String openedStatus;
	
	public TwilightShellScreen()
	{
		this(null);
	}
	
	public TwilightShellScreen(Screen parent)
	{
		super(Component.literal("Twilight Echo"));
		this.parent = parent;
	}
	
	@Override
	public void onClose()
	{
		if(parent != null)
			Minecraft.getInstance().setScreen(parent);
		else
			super.onClose();
	}
	
	@Override
	protected void init()
	{
		super.init();
		
		/*
		 * init() runs again on every window resize, so drop whatever the
		 * previous pass registered before replacing it: otherwise each resize
		 * would leak a texture cache and stack another listener on the player.
		 */
		PLAYER.removeListener(listener);
		
		if(covers != null)
		{
			covers.close();
			covers = null;
		}
		
		if(service != null)
		{
			service.close();
			service = null;
		}
		
		covers = new TwilightCoverCache();
		service = new TwilightMusicService();
		PLAYER.addListener(listener);
		accentPending = true;
		coverSong = null;
		loadHome();
	}
	
	@Override
	public void removed()
	{
		PLAYER.removeListener(listener);
		
		if(covers != null)
		{
			covers.close();
			covers = null;
		}
		
		if(service != null)
		{
			service.close();
			service = null;
		}
		
		clearQrTexture();
		super.removed();
	}
	
	/**
	 * Loads the daily recommendation once. The header reports the outcome in
	 * {@link #statusLine} instead of failing silently.
	 */
	private void loadHome()
	{
		if(homeLoading || homeSongs != null)
			return;
		
		homeLoading = true;
		statusLine = "正在加载每日推荐…";
		
		load(() -> service.dailySongs(), () -> PLAYER.loadHomeSongs(),
			songs -> {
				homeLoading = false;
				
				if(songs.isEmpty())
				{
					statusLine = "每日推荐不可用，请先登录网易云账号";
					return;
				}
				
				homeSongs = songs;
				statusLine =
					"每日推荐已就绪，共 " + songs.size() + " 首" + sourceTag();
			});
	}
	
	/**
	 * Asks the local NeteaseCloudMusicApiEnhanced service first and falls back
	 * to the repository's own direct API when that service is not running -
	 * which is the normal case for players who never installed it. The direct
	 * path is tried whenever the service produced nothing, so a reachable but
	 * logged-out service does not hide the direct result.
	 *
	 * <p>
	 * The consumer always runs on the client thread, as the direct calls did
	 * before this existed.
	 */
	private <T> void load(Supplier<CompletableFuture<List<T>>> local,
		Supplier<CompletableFuture<List<T>>> direct, Consumer<List<T>> apply)
	{
		CompletableFuture<List<T>> first = null;
		
		if(service != null)
			try
			{
				first = local.get();
			}catch(RuntimeException e)
			{
				first = null;
			}
		
		if(first == null)
		{
			fallback(direct, apply);
			return;
		}
		
		first.whenComplete((result, error) -> {
			if(error == null && result != null && !result.isEmpty())
				Minecraft.getInstance().execute(() -> apply.accept(result));
			else
				fallback(direct, apply);
		});
	}
	
	/** Second half of {@link #load}: the direct API, or an empty result. */
	private <T> void fallback(Supplier<CompletableFuture<List<T>>> direct,
		Consumer<List<T>> apply)
	{
		if(direct == null)
		{
			Minecraft.getInstance().execute(() -> apply.accept(List.of()));
			return;
		}
		
		direct.get().whenComplete((result, error) ->
			Minecraft.getInstance().execute(() -> apply
				.accept(error == null && result != null ? result : List.of())));
	}
	
	/** Which source answered, for the header text. */
	private String sourceTag()
	{
		return service != null && service.isReachable() ? "（本地增强服务）"
			: "（直连）";
	}
	
	/** Starts the daily recommendation, loading it first if necessary. */
	private void playHome()
	{
		if(homeSongs == null || homeSongs.isEmpty())
		{
			loadHome();
			return;
		}
		
		PLAYER.play(homeSongs, 0);
	}
	
	/**
	 * Follows the playing track: the cover's sampled accent becomes the accent
	 * of the whole interface. Render thread only; while a cover is still
	 * downloading it retries about twice per second instead of every frame.
	 */
	private void refreshAccent()
	{
		NeteaseSong song = PLAYER.getCurrentSong();
		
		if(!accentPending && song == coverSong)
			return;
		
		if(song != coverSong)
		{
			coverSong = song;
			accentPending = true;
			coverCooldown = 0;
		}
		
		if(covers == null || song == null || song.coverUrl() == null
			|| song.coverUrl().isBlank())
		{
			accentPending = false;
			return;
		}
		
		if(coverCooldown > 0)
		{
			coverCooldown--;
			return;
		}
		
		coverCooldown = 40;
		TwilightCoverCache.Texture texture = covers.get(song.coverUrl());
		
		if(texture == null)
			return;
		
		accentPending = false;
		setPalette(TwilightAccent.fromAccent(texture.accent(), false));
	}
	
	private String nowTitle()
	{
		NeteaseSong song = PLAYER.getCurrentSong();
		return song != null && song.name() != null ? song.name() : NOW_TITLE;
	}
	
	private String nowArtist()
	{
		NeteaseSong song = PLAYER.getCurrentSong();
		return song != null && song.artist() != null ? song.artist() : NOW_ARTIST;
	}
	
	private boolean isPlaying()
	{
		return PLAYER.getState() == NeteaseMusicPlayer.PlaybackState.PLAYING;
	}
	
	private String pageTitle()
	{
		if(openedPlaylist != null)
			return openedPlaylist.name();
		
		int index = Math.max(0, Math.min(NAV_LABELS.length - 1, activeNav));
		return NAV_LABELS[index];
	}
	
	private String pageSubtitle()
	{
		if(openedPlaylist != null)
			return openedSubtitle();
		
		if(pageStatus != null)
			return pageStatus;
		
		return activeNav == 0 && statusLine != null ? statusLine
			: PAGE_SUBTITLE;
	}
	
	/**
	 * Replaces the palette, e.g. with {@link TwilightAccent#fromAccent(int,
	 * boolean)} of the playing track, so the whole interface follows the cover.
	 */
	public void setPalette(TwilightAccent.Palette replacement)
	{
		if(replacement != null)
			palette = replacement;
	}
	
	public TwilightAccent.Palette getPalette()
	{
		return palette;
	}
	
	@Override
	public void render(GuiGraphics graphics, int mouseX, int mouseY,
		float partialTick)
	{
		frame = TwilightShellLayout.layout(width, height, false);
		home = TwilightHomeLayout.layout(frame);
		hoverNav = frame.navItemAt(mouseX, mouseY);
		refreshAccent();
		
		chartRows = TwilightListLayout.chartRows(frame, chartArea());
		/*
		 * 行是按原矩形上移 scrollOffset() 画出来的，所以判断悬停时要把鼠标
		 * 坐标加回去，否则高亮会比内容慢半行。
		 */
		hoverChart = homeSongs == null ? -1 : TwilightListLayout.rowAt(frame,
			chartRows, mouseX, mouseY + scrollOffset());
		
		if(homeSongs != null && hoverChart >= homeSongs.size())
			hoverChart = -1;
		
		pageRows = TwilightListLayout.listRows(frame,
			openedPlaylist == null ? frame.contentBody : detailListArea(), 12);
		playlistCards = playlistGrid();
		
		List<NeteaseSong> pageList =
			activeNav == 0 && !searchMode ? null : pageSongs();
		hoverPageRow = pageList == null ? -1 : TwilightListLayout.rowAt(frame,
			pageRows, mouseX, mouseY + scrollOffset());
		
		if(pageList != null && hoverPageRow >= pageList.size())
			hoverPageRow = -1;
		
		hoverPlaylist = activeNav == 1 && openedPlaylist == null
			? playlistAt(mouseX, mouseY) : -1;
		
		if(immersive)
		{
			hoverNav = -1;
			hoverChart = -1;
			hoverPageRow = -1;
			hoverPlaylist = -1;
		}
		
		if(loginOverlay)
			tickLoginOverlay();
		
		if(TwilightSkia.begin(graphics, 0, 0, width, height))
		{
			if(immersive)
				drawImmersive();
			else
				renderSkia();
			
			TwilightSkia.end(graphics);
		}else
			renderFallback(graphics);
		
		drawCovers(graphics);
		drawImmersiveLyrics(graphics);
		
		super.render(graphics, mouseX, mouseY, partialTick);
		drawLoginOverlay(graphics);
	}
	
	// ------------------------------------------------------------------
	// Skia 路径
	// ------------------------------------------------------------------
	
	private void renderSkia()
	{
		int accent = palette.accent;
		int text = theme.bodyText();
		int muted = theme.mutedText();
		int line = theme.shellLine();
		
		TwilightSkia.fillRect(0, 0, width, height, PAGE_BG);
		drawSidebar(accent, text, muted, line);
		drawHeader(accent, text, muted, line);
		drawHome(accent, text, muted, line);
		drawPlayerBar(accent, text, muted);
	}
	
	private void drawSidebar(int accent, int text, int muted, int line)
	{
		Rect panel = frame.sidebar;
		float radius = frame.px(TwilightShellLayout.SIDEBAR_RADIUS_RIGHT);
		
		TwilightSkia.softShadow(panel.x(), panel.y(), panel.width(),
			panel.height(), radius, 18F, theme.shellSidebarShadowColor());
		
		// border-radius: 0 26px 26px 0
		TwilightSkia.fillRoundRectCorners(panel.x(), panel.y(), panel.width(),
			panel.height(),
			new float[]{0, 0, radius, radius, radius, radius, 0, 0},
			theme.shellSidebarBg());
		TwilightSkia.fillRect(panel.right() - 1, panel.y(), 1, panel.height(),
			line);
		
		for(int i = 0; i < 3; i++)
		{
			Rect button = frame.brandButton(i);
			float toolRadius = frame.px(TwilightShellLayout.RADIUS_TOOL);
			
			TwilightSkia.fillRoundRect(button.x(), button.y(), button.width(),
				button.height(), toolRadius,
				TwilightTheme.withAlpha(text, 0.06F));
			TwilightSkia.strokeRoundRect(button.x(), button.y(), button.width(),
				button.height(), toolRadius, 1F,
				TwilightTheme.withAlpha(text, 0.10F));
		}
		
		for(int i = 0; i < NAV_LABELS.length; i++)
		{
			Rect item = frame.navItem(i);
			
			if(item == null)
				break;
			
			boolean active = i == activeNav;
			boolean hovered = i == hoverNav;
			float itemRadius = frame.px(TwilightShellLayout.RADIUS_ITEM);
			
			if(active)
			{
				TwilightSkia.fillRoundRect(item.x(), item.y(), item.width(),
					item.height(), itemRadius,
					TwilightTheme.withAlpha(accent, TwilightAccent.SOFT_ALPHA));
				TwilightSkia.strokeRoundRect(item.x(), item.y(), item.width(),
					item.height(), itemRadius, 1F, line);
				
				Rect indicator = frame.navIndicator(i);
				
				if(indicator != null)
					TwilightSkia.fillRoundRect(indicator.x(), indicator.y(),
						indicator.width(), indicator.height(), 999F, accent);
			}else if(hovered)
				TwilightSkia.fillRoundRect(item.x()
					+ frame.px(TwilightShellLayout.NAV_HOVER_TRANSLATE_X),
					item.y(), item.width(), item.height(), itemRadius,
					TwilightTheme.withAlpha(0xFF0F172A, 0.04F));
			
			float labelSize = frame.px(13);
			TwilightSkia.text(NAV_LABELS[i], item.x() + frame.px(14),
				item.centerY() - TwilightSkia.textHeight(labelSize,
					TwilightSkia.Weight.REGULAR) / 2F,
				labelSize, active ? TwilightSkia.Weight.SEMIBOLD
					: TwilightSkia.Weight.REGULAR,
				active ? accent : hovered ? text : muted);
		}
	}
	
	private void drawHeader(int accent, int text, int muted, int line)
	{
		Rect header = frame.contentHeader;
		float titleSize = frame.px(26);
		float subtitleSize = frame.px(13);
		int margin = frame.px(TwilightShellLayout.CONTENT_MARGIN);
		float left = header.x() + margin;
		float top = header.y() + frame.px(18);
		
		TwilightSkia.text(pageTitle(), left, top, titleSize,
			TwilightSkia.Weight.SEMIBOLD, text);
		TwilightSkia.text(pageSubtitle(), left,
			top + TwilightSkia.textHeight(titleSize,
				TwilightSkia.Weight.SEMIBOLD) + frame.px(2),
			subtitleSize, TwilightSkia.Weight.REGULAR, muted);
		
		float pillWidth = frame.px(220);
		float pillHeight = frame.px(32);
		float pillX = header.right() - margin - pillWidth;
		float pillY = header.y() + frame.px(22);
		
		TwilightSkia.fillRoundRect(pillX, pillY, pillWidth, pillHeight, 999F,
			0xFFFFFFFF);
		TwilightSkia.strokeRoundRect(pillX, pillY, pillWidth, pillHeight, 999F,
			1F, line);
		boolean empty = search.length() == 0;
		TwilightSkia.text(empty && !searchFocused ? SEARCH_HINT
			: search + (searchFocused ? "_" : ""), pillX + frame.px(16),
			pillY + (pillHeight - TwilightSkia.textHeight(frame.px(12),
				TwilightSkia.Weight.REGULAR)) / 2F,
			frame.px(12), TwilightSkia.Weight.REGULAR,
			empty && !searchFocused ? muted : text);
		
		Rect account = loginPill();
		boolean loggedIn = PLAYER.isLoggedIn();
		TwilightSkia.fillRoundRect(account.x(), account.y(), account.width(),
			account.height(), 999F, loggedIn ? 0xFFEDF2FF : 0xFFFFFFFF);
		TwilightSkia.strokeRoundRect(account.x(), account.y(), account.width(),
			account.height(), 999F, 1F, line);
		TwilightSkia.textCentered(accountLabel(), account.centerX(),
			account.y() + (account.height() - TwilightSkia.textHeight(
				frame.px(12), TwilightSkia.Weight.SEMIBOLD)) / 2F,
			frame.px(12), TwilightSkia.Weight.SEMIBOLD,
			loggedIn ? accent : text);
	}
	
	private void drawHome(int accent, int text, int muted, int line)
	{
		if(searchMode)
		{
			drawPage(accent, text, muted, line);
			return;
		}
		
		if(activeNav != 0)
		{
			drawPage(accent, text, muted, line);
			return;
		}
		
		drawHero(accent, text, muted, line);
		drawDuo(accent, text, muted, line, home.duoCardLeft, DUO_LEFT_NAME,
			DUO_LEFT_SUB);
		drawDuo(accent, text, muted, line, home.duoCardRight, DUO_RIGHT_NAME,
			DUO_RIGHT_SUB);
		drawSectionHead(text, muted, line);
		drawChart(accent, text, muted, line);
	}
	
	private void drawHero(int accent, int text, int muted, int line)
	{
		Rect hero = home.hero;
		float radius = frame.px(TwilightHomeLayout.RADIUS_LARGE);
		
		TwilightSkia.softShadow(hero.x(), hero.y() + frame.px(6), hero.width(),
			hero.height(), radius, 22F, TwilightTheme.withAlpha(0xFF0F172A,
				0.08F));
		TwilightSkia.fillVerticalGradient(hero.x(), hero.y(), hero.width(),
			hero.height(), radius, TwilightAccent.mix(0xFFFFFFFF, accent, 0.10F),
			TwilightAccent.mix(0xFFFFFFFF, accent, 0.03F));
		TwilightSkia.strokeRoundRect(hero.x(), hero.y(), hero.width(),
			hero.height(), radius, 1F, line);
		
		// the collage on the right, behind the copy
		Rect[] covers = home.collage(frame);
		
		for(int i = covers.length - 1; i >= 0; i--)
		{
			Rect cover = covers[i];
			float coverRadius = frame.px(TwilightHomeLayout.COLLAGE_RADIUS);
			float tint = i == 0 ? 0.45F : i == 1 ? 0.32F : 0.26F;
			
			TwilightSkia.save();
			TwilightSkia.rotate(COLLAGE_ROTATION[i], cover.centerX(),
				cover.centerY());
			TwilightSkia.softShadow(cover.x() + frame.px(4),
				cover.y() + frame.px(10), cover.width(), cover.height(),
				coverRadius, 12F, 0x3D0F172A);
			TwilightSkia.fillRoundRect(cover.x(), cover.y(), cover.width(),
				cover.height(), coverRadius,
				TwilightAccent.mix(accent, 0xFF1F2937, 0.25F + tint * 0.4F));
			TwilightSkia.restore();
		}
		
		// the copy column
		float padding = home.copyPadding;
		float left = home.heroCopy.x() + padding;
		float y = home.heroCopy.y() + padding;
		float badge = frame.px(TwilightHomeLayout.DAY_BADGE);
		
		TwilightSkia.fillRoundRect(left, y, badge, badge,
			frame.px(TwilightHomeLayout.DAY_BADGE_RADIUS),
			TwilightTheme.withAlpha(text, 0.92F));
		TwilightSkia.textCentered(HERO_DAY, left + badge / 2F,
			y + (badge - TwilightSkia.textHeight(frame.px(20),
				TwilightSkia.Weight.SEMIBOLD)) / 2F,
			frame.px(20), TwilightSkia.Weight.SEMIBOLD, 0xFFFFFFFF);
		
		float metaX = left + badge + frame.px(12);
		float metaSize = frame.px(13);
		
		TwilightSkia.text(HERO_DATE, metaX,
			y + frame.px(4), metaSize, TwilightSkia.Weight.SEMIBOLD, text);
		TwilightSkia.text(HERO_UPDATE, metaX, y + frame.px(24),
			frame.px(11), TwilightSkia.Weight.LIGHT, muted);
		
		y += badge + frame.px(TwilightHomeLayout.TITLE_MARGIN);
		float titleSize = home.titleSize;
		
		TwilightSkia.text(HERO_TITLE, left, y, titleSize,
			TwilightSkia.Weight.SEMIBOLD, text);
		y += TwilightSkia.textHeight(titleSize, TwilightSkia.Weight.SEMIBOLD)
			+ frame.px(TwilightHomeLayout.TITLE_EN_MARGIN);
		
		drawSpacedText(HERO_TITLE_EN, left, y, frame.px(14),
			TwilightHomeLayout.TITLE_EN_SPACING,
			TwilightAccent.mix(accent, text, 0.22F));
		y += TwilightSkia.textHeight(frame.px(14), TwilightSkia.Weight.SEMIBOLD)
			+ frame.px(TwilightHomeLayout.DESC_MARGIN);
		
		float descSize = frame.px(14);
		TwilightSkia.text(HERO_DESC_1, left, y, descSize,
			TwilightSkia.Weight.REGULAR, muted);
		TwilightSkia.text(HERO_DESC_2, left,
			y + descSize * TwilightHomeLayout.DESC_LINE_HEIGHT, descSize,
			TwilightSkia.Weight.REGULAR, muted);
		
		// the two pills, anchored to the copy padding at the bottom
		Rect primary = home.primaryCta;
		Rect secondary = home.secondaryCta;
		float ctaSize = frame.px(14);
		float glyphInset = frame.px(TwilightHomeLayout.CTA_PRIMARY_PADDING);
		
		TwilightSkia.softShadow(primary.x(), primary.y() + frame.px(4),
			primary.width(), primary.height(), 999F, 10F,
			TwilightTheme.withAlpha(text, 0.26F));
		TwilightSkia.fillRoundRect(primary.x(), primary.y(), primary.width(),
			primary.height(), 999F, TwilightTheme.withAlpha(text, 0.92F));
		TwilightSkia.playGlyph(primary.x() + glyphInset,
			primary.centerY(), frame.px(11), 0xFFFFFFFF);
		TwilightSkia.text(HERO_PLAY, primary.x() + glyphInset + frame.px(12),
			primary.centerY() - TwilightSkia.textHeight(ctaSize,
				TwilightSkia.Weight.SEMIBOLD) / 2F,
			ctaSize, TwilightSkia.Weight.SEMIBOLD, 0xFFFFFFFF);
		
		TwilightSkia.strokeRoundRect(secondary.x(), secondary.y(),
			secondary.width(), secondary.height(), 999F, 1F,
			TwilightTheme.withAlpha(text, 0.18F));
		TwilightSkia.text(HERO_OPEN, secondary.x()
			+ frame.px(TwilightHomeLayout.CTA_SECONDARY_PADDING),
			secondary.centerY() - TwilightSkia.textHeight(ctaSize,
				TwilightSkia.Weight.REGULAR) / 2F,
			ctaSize, TwilightSkia.Weight.REGULAR, text);
	}
	
	private void drawDuo(int accent, int text, int muted, int line, Rect card,
		String name, String sub)
	{
		float radius = frame.px(TwilightHomeLayout.RADIUS_LARGE);
		
		TwilightSkia.fillRoundRect(card.x(), card.y(), card.width(),
			card.height(), radius, 0xFFFFFFFF);
		TwilightSkia.strokeRoundRect(card.x(), card.y(), card.width(),
			card.height(), radius, 1F, line);
		
		Rect[] covers = TwilightHomeLayout.duoCovers(frame, card);
		float coverRadius = frame.px(TwilightHomeLayout.DUO_COVER_RADIUS);
		int[] coverTint = {0x000000, 0x000000, 0x000000};
		
		for(int i = 0; i < covers.length; i++)
			TwilightSkia.fillRoundRect(covers[i].x(), covers[i].y(),
				covers[i].width(), covers[i].height(), coverRadius,
				TwilightAccent.mix(accent, 0xFF374151,
					0.3F + i * 0.12F + coverTint[i]));
		
		float nameSize = frame.px(19);
		float subSize = frame.px(12);
		float textX = card.x() + frame.px(TwilightHomeLayout.DUO_PADDING_X);
		
		TwilightSkia.text(name, textX,
			card.centerY() - TwilightSkia.textHeight(nameSize,
				TwilightSkia.Weight.SEMIBOLD) - frame.px(2),
			nameSize, TwilightSkia.Weight.SEMIBOLD, text);
		TwilightSkia.text(sub, textX, card.centerY() + frame.px(2), subSize,
			TwilightSkia.Weight.REGULAR, muted);
		
		Rect arrow = TwilightHomeLayout.duoArrow(frame, card);
		TwilightSkia.strokeRoundRect(arrow.x(), arrow.y(), arrow.width(),
			arrow.height(), 999F, 1F, TwilightTheme.withAlpha(text, 0.14F));
		TwilightSkia.skipGlyph(arrow.centerX(), arrow.centerY(),
			frame.px(10), true, text);
	}
	
	private void drawSectionHead(int text, int muted, int line)
	{
		Rect head = home.sectionHead;
		float titleSize = frame.px(21);
		float subSize = frame.px(12);
		
		TwilightSkia.text(SECTION_TITLE, head.x(), head.y(), titleSize,
			TwilightSkia.Weight.SEMIBOLD, text);
		TwilightSkia.text(SECTION_SUB, head.x(),
			head.y() + TwilightSkia.textHeight(titleSize,
				TwilightSkia.Weight.SEMIBOLD) + frame.px(4),
			subSize, TwilightSkia.Weight.REGULAR, muted);
		
		Rect more = home.sectionMore;
		float moreSize = frame.px(12);
		
		TwilightSkia.fillRoundRect(more.x(), more.y(), more.width(),
			more.height(), 999F, 0xFFFFFFFF);
		TwilightSkia.strokeRoundRect(more.x(), more.y(), more.width(),
			more.height(), 999F, 1F, line);
		TwilightSkia.text(SECTION_MORE, more.x()
			+ frame.px(TwilightHomeLayout.SECTION_MORE_PADDING),
			more.centerY() - TwilightSkia.textHeight(moreSize,
				TwilightSkia.Weight.REGULAR) / 2F,
			moreSize, TwilightSkia.Weight.REGULAR, text);
	}
	
	/** The reference gives the latin kicker a letter-spacing of .42em. */
	/**
	 * The area left for the chart: everything below the section head, clamped
	 * to the content body so nothing is drawn over the player bar.
	 */
	private Rect chartArea()
	{
		Rect body = frame.contentBody;
		int top = home.sectionHead.bottom() + frame.px(12);
		return new Rect(body.x(), top, body.width(),
			Math.max(0, body.bottom() - top));
	}
	
	/**
	 * "今日为你精选": the daily recommendation as two columns of four rows. Rows
	 * that would not fit above the player bar are skipped rather than clipped.
	 */
	private void drawChart(int accent, int text, int muted, int line)
	{
		if(homeSongs == null || homeSongs.isEmpty())
		{
			TwilightSkia.text(
				statusLine != null ? statusLine : "每日推荐将在加载完成后显示在这里",
				home.sectionHead.x(), home.sectionHead.bottom() + frame.px(16),
				frame.px(12), TwilightSkia.Weight.REGULAR, muted);
			return;
		}
		
		int first = firstChartSong();
		Rect[] rows = shifted(chartRows);
		int limit = Math.min(homeSongs.size() - first, rows.length);
		
		/*
		 * 亚行偏移会把第一行推到区域外，所以在 Skia region 内裁剪一次，别让
		 * 它压到上面的 hero 上。
		 */
		Rect area = chartArea();
		TwilightSkia.save();
		TwilightSkia.clipRoundRect(area.x(), area.y(), area.width(),
			area.height(), 0F);
		
		for(int i = 0; i < limit; i++)
		{
			Rect row = rows[i];
			
			if(row.y() + row.height() > frame.contentBody.bottom())
				continue;
			
			NeteaseSong song = homeSongs.get(first + i);
			
			if(i == hoverChart)
				TwilightSkia.fillRoundRect(row.x(), row.y(), row.width(),
					row.height(), frame.px(12),
					TwilightTheme.withAlpha(accent, 0.1F));
			
			Rect cover = TwilightListLayout.rowCover(frame, row, true);
			TwilightSkia.fillRoundRect(cover.x(), cover.y(), cover.width(),
				cover.height(), frame.px(TwilightListLayout.COVER_RADIUS),
				TwilightTheme.withAlpha(accent, 0.35F));
			
			Rect title = TwilightListLayout.rowTitle(frame, row, true);
			TwilightSkia.text(song.name() != null ? song.name() : "未知歌曲",
				title.x(), title.y() + frame.px(4), frame.px(13),
				TwilightSkia.Weight.SEMIBOLD, text);
			TwilightSkia.text(
				song.artist() != null ? song.artist() : "未知歌手", title.x(),
				title.y() + frame.px(22), frame.px(11),
				TwilightSkia.Weight.REGULAR, muted);
		}
		
		TwilightSkia.restore();
	}
	
	/** Loads whatever the newly selected page needs, at most once per page. */
	private void ensurePage()
	{
		if(activeNav == 0)
		{
			loadHome();
			return;
		}
		
		if(pageLoading)
			return;
		
		if(activeNav == 1)
		{
			if(playlists != null)
				return;
			
			pageLoading = true;
			pageStatus = "正在加载推荐歌单…";
			
			load(() -> service.recommendedPlaylists(6),
				() -> PLAYER.loadRecommendedPlaylists(), result -> {
					pageLoading = false;
					
					if(result.isEmpty())
					{
						pageStatus = "推荐歌单不可用，请先登录网易云账号";
						return;
					}
					
					playlists = result;
					pageStatus = null;
				});
			return;
		}
		
		if(activeNav == 2)
		{
			if(likedSongs != null)
				return;
			
			pageLoading = true;
			pageStatus = "正在加载音乐库…";
			
			load(() -> service.likedSongs(50, 0),
				() -> PLAYER.loadLikedSongs(0), result -> {
					pageLoading = false;
					
					if(result.isEmpty())
					{
						pageStatus = "音乐库不可用，请先登录网易云账号";
						return;
					}
					
					likedSongs = result;
					pageStatus = null;
				});
		}
	}
	
	/** The songs a list page shows; the queue page reads them live. */
	private List<NeteaseSong> pageSongs()
	{
		if(openedPlaylist != null)
			return openedTracks;
		
		if(searchMode)
			return searchResults;
		
		if(activeNav == 2)
			return likedSongs;
		
		if(activeNav == 3)
			return PLAYER.getPlaylist();
		
		return null;
	}
	
	private String pageStatusText(String fallback)
	{
		return pageStatus != null ? pageStatus : fallback;
	}
	
	/** Two columns of playlist cards inside the content body. */
	private Rect[] playlistGrid()
	{
		Rect area = frame.contentBody;
		int columns = 2;
		int gap = (int)frame.px(20);
		int width = Math.max(0,
			(int)((area.width() - gap * (columns - 1)) / columns));
		int height = (int)frame.px(158);
		Rect[] cards = new Rect[6];
		
		for(int i = 0; i < cards.length; i++)
			cards[i] = new Rect(area.x() + i % columns * (width + gap),
				area.y() + i / columns * (height + gap), width, height);
		
		return cards;
	}
	
	private int playlistAt(double mouseX, double mouseY)
	{
		if(playlists == null || playlistCards == null)
			return -1;
		
		for(int i = 0; i < Math.min(playlists.size(),
			playlistCards.length); i++)
			if(playlistCards[i].contains(mouseX, mouseY))
				return i;
		
		return -1;
	}
	
	/**
	 * Opens the detail page for a playlist. The reference shows the tracks with
	 * a "play all" button instead of starting playback on a card click, so this
	 * no longer plays anything by itself.
	 */
	private void openPlaylist(NeteasePlaylist playlist)
	{
		openedPlaylist = playlist;
		openedTracks = List.of();
		openedStatus = "正在加载《" + playlist.name() + "》…";
		scrollRows = 0;
		scrollSub = 0;
		hoverPageRow = -1;
		
		load(() -> service.playlistTracks(playlist.id(), 100),
			() -> PLAYER.loadPlaylist(playlist), songs -> {
				// 期间可能已经返回或换了别的歌单
				if(!playlist.equals(openedPlaylist))
					return;
				
				openedTracks = songs;
				openedStatus = songs.isEmpty() ? "歌单加载失败" : null;
			});
	}
	
	private void closePlaylistDetail()
	{
		openedPlaylist = null;
		openedTracks = null;
		openedStatus = null;
		scrollRows = 0;
		scrollSub = 0;
		hoverPageRow = -1;
	}
	
	/** "播放全部" on the detail page. */
	private void playOpenedPlaylist()
	{
		if(openedPlaylist == null || openedTracks == null
			|| openedTracks.isEmpty())
			return;
		
		PLAYER.play(openedTracks, 0);
		statusLine = "正在播放《" + openedPlaylist.name() + "》";
	}
	
	private String openedSubtitle()
	{
		if(openedStatus != null)
			return openedStatus;
		
		StringBuilder line = new StringBuilder("共 ");
		line.append(openedTracks == null ? 0 : openedTracks.size())
			.append(" 首");
		
		if(openedPlaylist != null && openedPlaylist.playCount() > 0)
			line.append(" · 播放 ")
				.append(formatPlayCount(openedPlaylist.playCount()));
		
		return line.append(sourceTag()).toString();
	}
	
	/** 网易云自己就用「万 / 亿」计数，这里跟参考一样压成一个大数。 */
	private static String formatPlayCount(long count)
	{
		if(count >= 100_000_000L)
			return String.format("%.1f亿", count / 100_000_000D);
		
		if(count >= 10_000L)
			return String.format("%.1f万", count / 10_000D);
		
		return Long.toString(count);
	}
	
	/** The body rect below the detail header, so rows never overlap it. */
	private Rect detailListArea()
	{
		Rect body = frame.contentBody;
		int header = (int)frame.px(96);
		return new Rect(body.x(), body.y() + header, body.width(),
			Math.max(1, body.height() - header));
	}
	
	private Rect detailBackButton()
	{
		Rect area = frame.contentBody;
		return new Rect(area.right() - (int)frame.px(64),
			area.y() + (int)frame.px(28), (int)frame.px(64),
			(int)frame.px(34));
	}
	
	private Rect detailPlayButton()
	{
		Rect back = detailBackButton();
		int width = (int)frame.px(104);
		return new Rect(back.x() - (int)frame.px(10) - width, back.y(), width,
			back.height());
	}
	
	/**
	 * The playlist detail header: cover, name, track count and the two actions.
	 * The tracks under it are drawn by the shared list branch of
	 * {@link #drawPage}.
	 */
	private void drawPlaylistDetail(int accent, int text, int muted, Rect area)
	{
		NeteasePlaylist playlist = openedPlaylist;
		float coverSize = frame.px(72);
		float coverX = area.x();
		float coverY = area.y() + frame.px(6);
		float labelX = coverX + coverSize + frame.px(14);
		
		TwilightSkia.fillRoundRect(coverX, coverY, coverSize, coverSize,
			frame.px(14), TwilightTheme.withAlpha(accent, 0.35F));
		TwilightSkia.text(playlist.name(), labelX, coverY + frame.px(4),
			frame.px(20), TwilightSkia.Weight.SEMIBOLD, text);
		TwilightSkia.text(openedSubtitle(), labelX, coverY + frame.px(32),
			frame.px(12), TwilightSkia.Weight.REGULAR, muted);
		
		Rect play = detailPlayButton();
		TwilightSkia.fillRoundRect(play.x(), play.y(), play.width(),
			play.height(), 999F, accent);
		TwilightSkia.textCentered("播放全部", play.centerX(),
			play.y() + (play.height() - TwilightSkia.textHeight(frame.px(12),
				TwilightSkia.Weight.SEMIBOLD)) / 2F,
			frame.px(12), TwilightSkia.Weight.SEMIBOLD, 0xFFFFFFFF);
		
		Rect back = detailBackButton();
		TwilightSkia.strokeRoundRect(back.x(), back.y(), back.width(),
			back.height(), 999F, 1F, TwilightTheme.withAlpha(text, 0.18F));
		TwilightSkia.textCentered("返回", back.centerX(),
			back.y() + (back.height() - TwilightSkia.textHeight(frame.px(12),
				TwilightSkia.Weight.REGULAR)) / 2F,
			frame.px(12), TwilightSkia.Weight.REGULAR, muted);
	}
	
	/**
	 * Everything that is not the home page: the recommended playlists, the
	 * liked songs, the live queue and the placeholder for the reference's
	 * Bilibili tab, which needs a site we do not talk to.
	 */
	private void drawPage(int accent, int text, int muted, int line)
	{
		Rect area = frame.contentBody;
		
		TwilightSkia.fillRect(area.x(), area.y() - frame.px(10), area.width(),
			1F, line);
		
		if(openedPlaylist != null)
			drawPlaylistDetail(accent, text, muted, area);
		else if(activeNav == 1)
		{
			drawPlaylists(accent, text, muted, area);
			return;
		}
		
		if(activeNav == 4)
		{
			TwilightSkia.text("Bilibili 内容未接入：参考项目这一项依赖站外接口。",
				area.x(), area.y() + frame.px(8), frame.px(12),
				TwilightSkia.Weight.REGULAR, muted);
			return;
		}
		
		List<NeteaseSong> songs = pageSongs();
		
		if(songs == null || songs.isEmpty())
		{
			TwilightSkia.text(openedPlaylist != null
				? pageStatusText("歌单为空")
				: activeNav == 3 ? "当前没有播放队列"
					: pageStatusText("音乐库为空"),
				area.x(), area.y() + frame.px(8), frame.px(12),
				TwilightSkia.Weight.REGULAR, muted);
			return;
		}
		
		int first = firstListSong(songs);
		Rect[] rows = shifted(pageRows);
		int limit = Math.min(songs.size() - first, rows.length);
		
		TwilightSkia.save();
		TwilightSkia.clipRoundRect(area.x(), area.y(), area.width(),
			area.height(), 0F);
		
		for(int i = 0; i < limit; i++)
		{
			Rect row = rows[i];
			
			if(row.y() + row.height() > area.bottom())
				continue;
			
			NeteaseSong song = songs.get(first + i);
			
			if(i == hoverPageRow)
				TwilightSkia.fillRoundRect(row.x(), row.y(), row.width(),
					row.height(), frame.px(12),
					TwilightTheme.withAlpha(accent, 0.1F));
			
			Rect index = TwilightListLayout.rowIndex(frame, row);
			TwilightSkia.text(String.valueOf(first + i + 1), index.x(),
				index.y() + frame.px(6), frame.px(12),
				TwilightSkia.Weight.SEMIBOLD, muted);
			
			Rect cover = TwilightListLayout.rowCover(frame, row, false);
			TwilightSkia.fillRoundRect(cover.x(), cover.y(), cover.width(),
				cover.height(), frame.px(TwilightListLayout.COVER_RADIUS),
				TwilightTheme.withAlpha(accent, 0.35F));
			
			Rect title = TwilightListLayout.rowTitle(frame, row, false);
			TwilightSkia.text(song.name() != null ? song.name() : "未知歌曲",
				title.x(), title.y() + frame.px(6), frame.px(13),
				TwilightSkia.Weight.SEMIBOLD, text);
			TwilightSkia.text(song.artist() != null ? song.artist() : "未知歌手",
				title.x(), title.y() + frame.px(24), frame.px(11),
				TwilightSkia.Weight.REGULAR, muted);
		}
		
		TwilightSkia.restore();
	}
	
	private void drawPlaylists(int accent, int text, int muted, Rect area)
	{
		if(playlists == null || playlists.isEmpty())
		{
			TwilightSkia.text(pageStatusText("暂无推荐歌单"), area.x(),
				area.y() + frame.px(8), frame.px(12),
				TwilightSkia.Weight.REGULAR, muted);
			return;
		}
		
		int limit = Math.min(playlists.size(), playlistCards.length);
		
		for(int i = 0; i < limit; i++)
		{
			Rect card = playlistCards[i];
			NeteasePlaylist playlist = playlists.get(i);
			
			if(card.y() + card.height() > area.bottom())
				continue;
			
			TwilightSkia.fillRoundRect(card.x(), card.y(), card.width(),
				card.height(), frame.px(16),
				i == hoverPlaylist ? TwilightTheme.withAlpha(accent, 0.18F)
					: 0xFFFFFFFF);
			
			int inset = (int)frame.px(12);
			float coverHeight = card.height() - frame.px(56);
			TwilightSkia.fillRoundRect(card.x() + inset, card.y() + inset,
				card.width() - inset * 2, coverHeight, frame.px(12),
				TwilightTheme.withAlpha(accent, 0.35F));
			
			TwilightSkia.text(playlist.name(), card.x() + inset,
				card.bottom() - frame.px(34), frame.px(13),
				TwilightSkia.Weight.SEMIBOLD, text);
			TwilightSkia.text(playlist.playCount() + " 次播放",
				card.x() + inset, card.bottom() - frame.px(18), frame.px(11),
				TwilightSkia.Weight.REGULAR, muted);
		}
	}
	
	/**
	 * Real cover art on top of the drawn placeholders.
	 *
	 * <p>
	 * The Skia region is already uploaded when this runs, so these are plain
	 * vanilla blits: the same rectangles as the accent placeholder squares,
	 * cropped like CSS {@code object-fit: cover}. They are not rounded, because
	 * a blit cannot be clipped to a rounded rect - the placeholder behind them
	 * still provides the rounded tint for missing covers.
	 */
	private void drawCovers(GuiGraphics graphics)
	{
		if(frame == null)
			return;
		
		if(immersive)
		{
			Rect big = immersiveCover();
			drawCover(graphics, PLAYER.getCurrentSong(), big.x(), big.y(),
				big.width(), big.height(), (int)frame.px(IMMERSIVE_COVER_RADIUS));
			return;
		}
		
		Rect bar = frame.playerBar;
		float size = frame.px(44);
		float coverX = bar.x() + frame.px(13);
		float coverY = bar.centerY() - size / 2F;
		drawCover(graphics, PLAYER.getCurrentSong(), (int)coverX, (int)coverY,
			(int)size, (int)size, (int)frame.px(BAR_COVER_RADIUS));
		
		if(activeNav == 0)
		{
			if(homeSongs == null || chartRows == null)
				return;
			
			int limit = Math.min(homeSongs.size() - firstChartSong(),
				chartRows.length);
			int first = firstChartSong();
			Rect[] rows = shifted(chartRows);
			Rect clip = frame.contentBody;
			
			// 封面是原版 blit，Skia 的裁剪够不到，这里用矩形裁剪兜住
			graphics.enableScissor(clip.x(), clip.y(), clip.right(),
				clip.bottom());
			
			for(int i = 0; i < limit; i++)
			{
				Rect row = rows[i];
				
				if(row.y() + row.height() > frame.contentBody.bottom())
					continue;
				
				Rect cover = TwilightListLayout.rowCover(frame, row, true);
				drawCover(graphics, homeSongs.get(first + i), cover.x(),
					cover.y(), cover.width(), cover.height(),
					(int)frame.px(TwilightListLayout.COVER_RADIUS));
			}
			
			graphics.disableScissor();
			return;
		}
		
		if(activeNav == 1)
		{
			if(playlists == null || playlistCards == null)
				return;
			
			int limit = Math.min(playlists.size(), playlistCards.length);
			int inset = (int)frame.px(12);
			
			for(int i = 0; i < limit; i++)
			{
				Rect card = playlistCards[i];
				
				if(card.y() + card.height() > frame.contentBody.bottom())
					continue;
				
				drawCover(graphics, playlists.get(i).coverUrl(),
					card.x() + inset, card.y() + inset,
					card.width() - inset * 2,
					card.height() - (int)frame.px(56),
					(int)frame.px(TwilightListLayout.COVER_RADIUS));
			}
			return;
		}
		
		List<NeteaseSong> songs = pageSongs();
		
		if(songs == null || pageRows == null)
			return;
		
		int first = firstListSong(songs);
		Rect[] rows = shifted(pageRows);
		int limit = Math.min(songs.size() - first, rows.length);
		Rect clip = frame.contentBody;
		
		graphics.enableScissor(clip.x(), clip.y(), clip.right(),
			clip.bottom());
		
		for(int i = 0; i < limit; i++)
		{
			Rect row = rows[i];
			
			if(row.y() + row.height() > frame.contentBody.bottom())
				continue;
			
			Rect cover = TwilightListLayout.rowCover(frame, row, false);
			drawCover(graphics, songs.get(first + i), cover.x(), cover.y(),
				cover.width(), cover.height(),
				(int)frame.px(TwilightListLayout.COVER_RADIUS));
		}
		
		graphics.disableScissor();
	}
	
	private void drawCover(GuiGraphics graphics, NeteaseSong song, int x, int y,
		int coverWidth, int coverHeight, int radius)
	{
		if(song != null)
			drawCover(graphics, song.coverUrl(), x, y, coverWidth, coverHeight,
				radius);
	}
	
	/**
	 * Draws a cover from the masked cache, so the corners come out rounded like
	 * the reference's {@code border-radius}. The mask lives in the texture
	 * because a clip cannot reach a vanilla blit and the covers sit on glass.
	 */
	private void drawCover(GuiGraphics graphics, String url, int x, int y,
		int coverWidth, int coverHeight, int radius)
	{
		if(covers == null || url == null || url.isBlank() || coverWidth <= 0
			|| coverHeight <= 0)
			return;
		
		TwilightCoverCache.Texture texture =
			covers.get(url, Math.min(coverWidth, coverHeight), radius);
		
		if(texture == null)
			return;
		
		int[] crop = TwilightCoverFit.sourceRect(texture.width(),
			texture.height(), coverWidth, coverHeight);
		
		graphics.blit(texture.location(), x, y, coverWidth, coverHeight,
			crop[0], crop[1], crop[2], crop[3], texture.width(),
			texture.height());
	}
	
	// ------------------------------------------------------------------
	// 沉浸播放页：外壳自绘，歌词交给现有 AMLL 渲染器
	// ------------------------------------------------------------------
	
	private Rect immersiveCover()
	{
		int size = (int)frame.px(300);
		int x = (int)frame.px(72);
		return new Rect(x, (height - size) / 2 - (int)frame.px(24), size, size);
	}
	
	private Rect immersiveLyrics()
	{
		int left = immersiveCover().right() + (int)frame.px(48);
		int right = width - (int)frame.px(72);
		int top = (int)frame.px(96);
		int bottom = height - (int)frame.px(190);
		return new Rect(left, top, Math.max(0, right - left),
			Math.max(0, bottom - top));
	}
	
	private Rect immersiveBack()
	{
		int size = (int)frame.px(34);
		return new Rect((int)frame.px(28), (int)frame.px(26),
			(int)frame.px(56), size);
	}
	
	private Rect immersivePlayButton()
	{
		int size = (int)frame.px(56);
		return new Rect(width / 2 - size / 2, height - (int)frame.px(124), size,
			size);
	}
	
	private Rect immersiveTransport(int direction)
	{
		int size = (int)frame.px(40);
		int centreX = width / 2 + direction * (int)frame.px(80);
		return new Rect(centreX - size / 2, height - (int)frame.px(116), size,
			size);
	}
	
	private Rect immersiveProgress()
	{
		int margin = (int)frame.px(160);
		return new Rect(margin, height - (int)frame.px(58),
			Math.max(0, width - margin * 2), (int)frame.px(4));
	}
	
	/**
	 * The immersive page shell: cover, titles, controls and a tinted backdrop.
	 * The reference blurs the cover behind everything; without a large area
	 * blur this uses a gradient mixed from the cover accent instead.
	 */
	private void drawImmersive()
	{
		int accent = palette.accent;
		int text = theme.bodyText();
		int muted = theme.mutedText();
		
		TwilightSkia.fillVerticalGradient(0, 0, width, height, 0F,
			TwilightAccent.mix(palette.heroFrom, 0xFF0B1220, 0.45F), PAGE_BG);
		
		Rect cover = immersiveCover();
		TwilightSkia.softShadow(cover.x(), cover.y() + frame.px(10),
			cover.width(), cover.height(), frame.px(24), 30F,
			TwilightTheme.withAlpha(0xFF0F172A, 0.24F));
		TwilightSkia.fillRoundRect(cover.x(), cover.y(), cover.width(),
			cover.height(), frame.px(24),
			TwilightTheme.withAlpha(accent, 0.35F));
		
		float titleSize = frame.px(26);
		TwilightSkia.text(nowTitle(), cover.x(),
			cover.bottom() + frame.px(26), titleSize,
			TwilightSkia.Weight.SEMIBOLD, text);
		TwilightSkia.text(nowArtist(), cover.x(),
			cover.bottom() + frame.px(26)
				+ TwilightSkia.textHeight(titleSize,
					TwilightSkia.Weight.SEMIBOLD)
				+ frame.px(8),
			frame.px(14), TwilightSkia.Weight.REGULAR, muted);
		
		Rect back = immersiveBack();
		TwilightSkia.fillRoundRect(back.x(), back.y(), back.width(),
			back.height(), 999F, 0xFFFFFFFF);
		TwilightSkia.text("返回", back.x() + frame.px(10),
			back.centerY() - TwilightSkia.textHeight(frame.px(11),
				TwilightSkia.Weight.REGULAR) / 2F,
			frame.px(11), TwilightSkia.Weight.REGULAR, text);
		
		Rect play = immersivePlayButton();
		TwilightSkia.fillRoundRect(play.x(), play.y(), play.width(),
			play.height(), 999F, accent);
		
		if(isPlaying())
			TwilightSkia.pauseGlyph(play.centerX(), play.centerY(),
				frame.px(18), 0xFFFFFFFF);
		else
			TwilightSkia.playGlyph(play.centerX(), play.centerY(),
				frame.px(18), 0xFFFFFFFF);
		
		Rect previous = immersiveTransport(-1);
		Rect next = immersiveTransport(1);
		TwilightSkia.skipGlyph(previous.centerX(), previous.centerY(),
			frame.px(14), false, text);
		TwilightSkia.skipGlyph(next.centerX(), next.centerY(), frame.px(14),
			true, text);
		
		Rect progress = immersiveProgress();
		TwilightSkia.fillRoundRect(progress.x(), progress.y(), progress.width(),
			progress.height(), 999F, TwilightTheme.withAlpha(text, 0.14F));
		
		float ratio = durationMs > 0
			? Math.max(0F, Math.min(1F, positionMs / (float)durationMs)) : 0F;
		
		TwilightSkia.fillRoundRect(progress.x(), progress.y(),
			Math.max(frame.px(2), progress.width() * ratio), progress.height(),
			999F, accent);
		
		String times = NeteaseMusicPlayer.formatTime(positionMs) + " / "
			+ NeteaseMusicPlayer.formatTime(durationMs);
		TwilightSkia.text(times,
			progress.right() - TwilightSkia.textWidth(times, frame.px(12),
				TwilightSkia.Weight.REGULAR),
			progress.y() - TwilightSkia.textHeight(frame.px(12),
				TwilightSkia.Weight.REGULAR) - frame.px(8),
			frame.px(12), TwilightSkia.Weight.REGULAR, muted);
	}
	
	/**
	 * The lyrics panel. It is driven exactly like the lyrics HUD element does:
	 * feed the lines once per song, then update and render every frame. Our own
	 * Skia region has already been uploaded here, so this draws on top of it.
	 */
	private void drawImmersiveLyrics(GuiGraphics graphics)
	{
		if(!immersive || frame == null)
			return;
		
		Rect area = immersiveLyrics();
		
		if(area.width() <= 0 || area.height() <= 0)
			return;
		
		NeteaseSong song = PLAYER.getCurrentSong();
		List<LyricLine> lyrics = PLAYER.getLyrics();
		long songId = song == null ? -1L : song.id();
		
		if(songId != displayedSongId || lyrics.size() != displayedLyricCount)
		{
			displayedSongId = songId;
			displayedLyricCount = lyrics.size();
			appleLyrics.setLyricLines(lyrics,
				PLAYER.getAdjustedLyricPositionMs());
		}
		
		appleLyrics.setContentWidth(area.width());
		appleLyrics.setContainerHeight(area.height());
		appleLyrics.setPlaying(isPlaying());
		appleLyrics.setCurrentTime(PLAYER.getAdjustedLyricPositionMs(), false);
		appleLyrics.update();
		
		graphics.enableScissor(area.x(), area.y(), area.right(), area.bottom());
		try
		{
			appleLyrics.render(graphics, area.x(), area.y(), area.right(),
				area.bottom());
		}finally
		{
			graphics.disableScissor();
		}
	}
	
	/** The immersive page swallows every click so nothing behind it reacts. */
	private boolean handleImmersiveClick(double mouseX, double mouseY)
	{
		if(immersiveBack().contains(mouseX, mouseY))
		{
			immersive = false;
			return true;
		}
		
		if(immersivePlayButton().contains(mouseX, mouseY))
		{
			PLAYER.toggle();
			return true;
		}
		
		if(immersiveTransport(-1).contains(mouseX, mouseY))
		{
			PLAYER.playPrevious();
			return true;
		}
		
		if(immersiveTransport(1).contains(mouseX, mouseY))
		{
			PLAYER.playNext();
			return true;
		}
		
		long duration = PLAYER.getDurationMs();
		Rect progress = immersiveProgress();
		
		if(duration > 0 && progress.contains(mouseX, mouseY))
		{
			double ratio = (mouseX - progress.x())
				/ Math.max(1D, progress.width());
			
			PLAYER.seekTo(
				(long)(duration * Math.max(0D, Math.min(1D, ratio))));
			return true;
		}
		
		return true;
	}
	
	/** First song the chart shows, in songs, after scrolling whole rows. */
	private int firstChartSong()
	{
		if(homeSongs == null)
			return 0;
		
		return Math.min(scrollRows * TwilightListLayout.CHART_COLUMNS,
			Math.max(0, homeSongs.size() - TwilightListLayout.CHART_LIMIT));
	}
	
	/** First song a list page shows after scrolling. */
	private int firstListSong(List<NeteaseSong> songs)
	{
		if(songs == null || pageRows == null)
			return 0;
		
		return Math.min(scrollRows, Math.max(0, songs.size() - pageRows.length));
	}
	
	/**
	 * Sub-row scroll offset in pixels. {@link #scrollRows} already accounts for
	 * the whole rows, so only the remainder is returned; drawing shifts by this
	 * so the wheel slides the list instead of jumping a whole row.
	 */
	private int scrollOffset()
	{
		if(frame == null || scrollSub <= 0)
			return 0;
		
		int rowHeight = Math.max(1,
			(int)frame.px(TwilightListLayout.ROW_HEIGHT));
		return (int)Math.min(rowHeight - 1, scrollSub);
	}
	
	/** The row rects moved up by the sub-row offset, or the originals. */
	private Rect[] shifted(Rect[] rows)
	{
		int offset = scrollOffset();
		
		if(rows == null || offset == 0)
			return rows;
		
		Rect[] result = new Rect[rows.length];
		
		for(int i = 0; i < rows.length; i++)
			result[i] = new Rect(rows[i].x(), rows[i].y() - offset,
				rows[i].width(), rows[i].height());
		
		return result;
	}
	
	@Override
	public boolean mouseScrolled(double mouseX, double mouseY, double delta)
	{
		if(immersive || loginOverlay || frame == null || delta == 0)
			return super.mouseScrolled(mouseX, mouseY, delta);
		
		int rowHeight = Math.max(1,
			(int)frame.px(TwilightListLayout.ROW_HEIGHT));
		int total;
		int visible;
		int maxRows;
		
		if(activeNav == 0)
		{
			total = homeSongs == null ? 0 : homeSongs.size();
			visible = Math.max(1, chartArea().height() / rowHeight)
				* TwilightListLayout.CHART_COLUMNS;
			maxRows = total <= visible ? 0
				: (total - visible + TwilightListLayout.CHART_COLUMNS - 1)
					/ TwilightListLayout.CHART_COLUMNS;
		}else
		{
			List<NeteaseSong> songs = pageSongs();
			total = songs == null ? 0 : songs.size();
			visible = Math.max(1, frame.contentBody.height() / rowHeight);
			maxRows = Math.max(0, total - visible);
		}
		
		if(maxRows <= 0)
			return super.mouseScrolled(mouseX, mouseY, delta);
		
		// 半行一步：整行进位到 scrollRows，不足一行的留在 scrollSub
		float next = scrollSub - (float)Math.signum(delta) * rowHeight / 2F;
		int rows = scrollRows;
		
		while(next >= rowHeight)
		{
			next -= rowHeight;
			rows++;
		}
		
		while(next < 0)
		{
			next += rowHeight;
			rows--;
		}
		
		int clamped = Math.max(0, Math.min(maxRows, rows));
		scrollRows = clamped;
		// 到顶或到底时清掉不足一行的余量，否则末尾会一直露着一段空白
		scrollSub = clamped == rows ? next : 0;
		return true;
	}
	
	/** The header search pill, the same rect {@code drawHeader} paints. */
	private Rect searchPill()
	{
		Rect header = frame.contentHeader;
		int width = (int)frame.px(220);
		int height = (int)frame.px(32);
		return new Rect(header.right() - (int)frame.px(TwilightShellLayout
			.CONTENT_MARGIN) - width, header.y() + (int)frame.px(22), width,
			height);
	}
	
	/** The header account pill, sitting left of the search pill. */
	private Rect loginPill()
	{
		Rect search = searchPill();
		int width = (int)frame.px(112);
		return new Rect(search.x() - (int)frame.px(10) - width, search.y(),
			width, search.height());
	}
	
	private String accountLabel()
	{
		if(!PLAYER.isLoggedIn())
			return "扫码登录";
		
		String nickname =
			PLAYER.getUserProfile() == null ? ""
				: PLAYER.getUserProfile().nickname();
		return nickname.isBlank() ? "已登录" : nickname;
	}
	
	private void openLogin()
	{
		loginOverlay = true;
		qrCooldown = 0;
		qrBusy = false;
		
		if(!PLAYER.isLoggedIn())
			qrStatus = "正在获取二维码…";
	}
	
	private void closeLogin()
	{
		loginOverlay = false;
	}
	
	/**
	 * Drives the QR overlay: asks for a code, then polls it every 40 frames so
	 * the service is not hammered. A successful scan hands the cookie to the
	 * shared player, which is what makes the direct API and the local service
	 * agree on the same account.
	 */
	private void tickLoginOverlay()
	{
		if(PLAYER.isLoggedIn() || qrBusy)
			return;
		
		if(qrCooldown > 0)
		{
			qrCooldown--;
			return;
		}
		
		TwilightMusicService client = service;
		
		if(client == null)
		{
			qrError = "没有本地音乐服务，无法扫码登录";
			qrStatus = "";
			qrCooldown = 200;
			return;
		}
		
		qrCooldown = 40;
		qrBusy = true;
		
		if(qrSession == null)
		{
			client.createQr().whenComplete((session, error) ->
				Minecraft.getInstance().execute(() -> {
					qrBusy = false;
					
					if(error != null || session == null || !session.isUsable())
					{
						qrError = session != null && !session.error().isEmpty()
							? session.error() : "无法获取二维码";
						qrStatus = "";
						return;
					}
					
					qrSession = session;
					qrError = "";
					qrStatus = "用网易云音乐 App 扫码登录";
					uploadQr(session.imageBytes());
				}));
			return;
		}
		
		client.checkQr(qrSession.key()).whenComplete((state, error) ->
			Minecraft.getInstance().execute(() -> {
				qrBusy = false;
				
				if(error != null || state == null)
				{
					qrStatus = "二维码状态查询失败";
					return;
				}
				
				if(state.isSuccess())
				{
					String cookie = state.cookie().isEmpty()
						? client.getLoginCookie() : state.cookie();
					
					qrSession = null;
					clearQrTexture();
					loginOverlay = false;
					
					if(cookie.isEmpty())
					{
						statusLine = "扫码成功，但服务没有返回 cookie";
						return;
					}
					
					statusLine = "正在校验登录…";
					PLAYER.loginWithCookie(cookie)
						.whenComplete((result, loginError) ->
							Minecraft.getInstance().execute(() -> statusLine =
								loginError == null && result != null
									&& result.success()
										? "已登录网易云账号" + sourceTag()
										: "扫码成功，但账号校验失败"));
					return;
				}
				
				if(state.isExpired())
				{
					// 过期就丢掉这张，下一帧重新申请
					qrSession = null;
					clearQrTexture();
					qrStatus = "二维码已过期，正在刷新…";
					qrCooldown = 0;
					return;
				}
				
				qrStatus = state.isScanned() ? "已扫码，请在手机上确认"
					: "用网易云音乐 App 扫码登录";
			}));
	}
	
	/** Uploads the decoded PNG; an unusable image shows the error instead. */
	private void uploadQr(byte[] png)
	{
		clearQrTexture();
		
		if(png.length == 0)
		{
			qrError = "二维码图片无法解析";
			return;
		}
		
		try(ByteArrayInputStream input = new ByteArrayInputStream(png))
		{
			NativeImage image = NativeImage.read(input);
			DynamicTexture texture = new DynamicTexture(image);
			texture.setFilter(false, false);
			ResourceLocation location = new ResourceLocation("wurst",
				"twilight/qr_" + Long.toHexString(System.nanoTime()));
			Minecraft.getInstance().getTextureManager().register(location,
				texture);
			qrTexture = location;
			qrTextureSize = image.getWidth();
		}catch(IOException | RuntimeException e)
		{
			qrError = "二维码图片无法解析";
		}
	}
	
	private void clearQrTexture()
	{
		if(qrTexture == null)
			return;
		
		Minecraft.getInstance().getTextureManager().release(qrTexture);
		qrTexture = null;
		qrTextureSize = 0;
	}
	
	/** The logout button inside the account panel. */
	private Rect logoutButton()
	{
		Rect panel = loginPanel();
		int width = (int)frame.px(132);
		int height = (int)frame.px(32);
		return new Rect(panel.x() + (panel.width() - width) / 2,
			panel.y() + (int)frame.px(124), width, height);
	}
	
	/** The overlay panel, so the click handler and the painter agree on it. */
	private Rect loginPanel()
	{
		int panelWidth = (int)frame.px(304);
		int panelHeight = (int)frame.px(376);
		return new Rect((width - panelWidth) / 2, (height - panelHeight) / 2,
			panelWidth, panelHeight);
	}
	
	/**
	 * The login overlay is drawn with vanilla rectangles after everything else,
	 * so it works on both the Skia and the fallback path and always ends up on
	 * top. Logged in, it doubles as the account panel the reference shows.
	 */
	private void drawLoginOverlay(GuiGraphics graphics)
	{
		if(!loginOverlay)
			return;
		
		graphics.fill(0, 0, width, height, 0xB4000000);
		
		int panelWidth = (int)frame.px(304);
		int panelHeight = (int)frame.px(376);
		Rect panel = new Rect((width - panelWidth) / 2,
			(height - panelHeight) / 2, panelWidth, panelHeight);
		int left = panel.x();
		int top = panel.y();
		int right = panel.right();
		int bottom = panel.bottom();
		
		graphics.fill(left - 1, top - 1, right + 1, bottom + 1, 0xFFDCDCE6);
		graphics.fill(left, top, right, bottom, 0xFFFBFBFD);
		
		graphics.drawCenteredString(font, "网易云账号", width / 2,
			top + (int)frame.px(18), 0xFF1B1B1F);
		
		if(PLAYER.isLoggedIn())
		{
			graphics.drawCenteredString(font, accountLabel(), width / 2,
				top + (int)frame.px(54), 0xFF2563EB);
			graphics.drawCenteredString(font, "已登录，按 ESC 返回", width / 2,
				top + (int)frame.px(82), 0xFF6B6B75);
			
			Rect logout = logoutButton();
			graphics.fill(logout.x(), logout.y(), logout.right(),
				logout.bottom(), 0xFFF2F2F7);
			graphics.fill(logout.x(), logout.y(), logout.right(),
				logout.y() + 1, 0xFFDCDCE6);
			graphics.fill(logout.x(), logout.bottom() - 1, logout.right(),
				logout.bottom(), 0xFFDCDCE6);
			graphics.drawCenteredString(font, "退出登录",
				logout.x() + logout.width() / 2,
				logout.y() + (logout.height() - 8) / 2, 0xFFB3261E);
			return;
		}
		
		int imageSize = (int)frame.px(196);
		int imageLeft = (width - imageSize) / 2;
		int imageTop = top + (int)frame.px(54);
		
		if(qrTexture != null)
		{
			graphics.fill(imageLeft - 6, imageTop - 6, imageLeft + imageSize + 6,
				imageTop + imageSize + 6, 0xFFFFFFFF);
			graphics.setColor(1F, 1F, 1F, 1F);
			graphics.blit(qrTexture, imageLeft, imageTop, imageSize, imageSize,
				0, 0, qrTextureSize, qrTextureSize, qrTextureSize,
				qrTextureSize);
		}else
		{
			graphics.fill(imageLeft, imageTop, imageLeft + imageSize,
				imageTop + imageSize, 0xFFEFEFF4);
			graphics.drawCenteredString(font,
				qrError.isEmpty() ? "正在获取二维码…" : qrError, width / 2,
				imageTop + imageSize / 2, 0xFF6B6B75);
		}
		
		graphics.drawCenteredString(font,
			qrStatus.isEmpty() ? "用网易云音乐 App 扫码登录" : qrStatus,
			width / 2, imageTop + imageSize + (int)frame.px(14), 0xFF6B6B75);
		graphics.drawCenteredString(font, "按 ESC 关闭", width / 2,
			bottom - (int)frame.px(26), 0xFF9A9AA5);
	}
	
	/** Runs the search and shows the result in the content body. */
	private void runSearch()
	{
		String query = search.toString().trim();
		
		if(query.isEmpty())
			return;
		
		searchMode = true;
		searchFocused = false;
		scrollRows = 0;
		scrollSub = 0;
		pageStatus = "正在搜索「" + query + "」…";
		
		load(() -> service.search(query, 30), () -> PLAYER.search(query),
			songs -> {
				if(songs.isEmpty())
				{
					searchResults = List.of();
					pageStatus = "没有搜到「" + query + "」";
					return;
				}
				
				searchResults = songs;
				pageStatus = "搜索「" + query + "」：" + songs.size() + " 首"
					+ sourceTag();
			});
	}
	
	@Override
	public boolean charTyped(char codePoint, int modifiers)
	{
		if(!searchFocused)
			return super.charTyped(codePoint, modifiers);
		
		if(search.length() >= 32 || codePoint < ' ')
			return true;
		
		search.append(codePoint);
		return true;
	}
	
	/**
	 * "私人漫游" (personal FM) uses the service's {@code /personal_fm} route when
	 * the local service is running; without it the in-game substitute is the
	 * daily recommendation started at a random track - roaming, just without a
	 * station to roam in. "私人雷达" opens the recommended playlists instead.
	 */
	private void playRoaming()
	{
		load(() -> service.personalFm(), () -> PLAYER.loadHomeSongs(),
			songs -> {
				if(songs.isEmpty())
				{
					statusLine = "私人漫游不可用，请先登录网易云账号";
					return;
				}
				
				boolean fromFm = service != null && service.isReachable();
				PLAYER.play(songs,
					fromFm ? 0 : (int)(Math.random() * songs.size()));
				statusLine = fromFm ? "私人漫游已就绪（本地增强服务）"
					: "没有本地音乐服务，改为随机播放每日推荐";
			});
	}
	
	private void drawSpacedText(String value, float x, float topY, float size,
		float spacing, int color)
	{
		float cursor = x;
		
		for(int i = 0; i < value.length(); i++)
		{
			String character = value.substring(i, i + 1);
			
			if(!" ".equals(character))
				TwilightSkia.text(character, cursor, topY, size,
					TwilightSkia.Weight.SEMIBOLD, color);
			
			cursor += TwilightSkia.textWidth(character, size,
				TwilightSkia.Weight.SEMIBOLD) + size * spacing;
		}
	}
	
	private void drawPlayerBar(int accent, int text, int muted)
	{
		Rect bar = frame.playerBar;
		
		TwilightSkia.softShadow(bar.x(), bar.y(), bar.width(), bar.height(),
			RADIUS_BAR, 14F, 0x33000000);
		TwilightSkia.fillRoundRect(bar.x(), bar.y(), bar.width(), bar.height(),
			frame.px(RADIUS_BAR), 0xFFFFFFFF);
		
		float coverSize = frame.px(44);
		float coverX = bar.x() + frame.px(13);
		float coverY = bar.centerY() - coverSize / 2F;
		
		TwilightSkia.fillRoundRect(coverX, coverY, coverSize, coverSize,
			frame.px(RADIUS_COVER), TwilightTheme.withAlpha(accent, 0.35F));
		
		float titleSize = frame.px(13);
		float artistSize = frame.px(11);
		float textX = coverX + coverSize + frame.px(12);
		
		TwilightSkia.text(nowTitle(), textX,
			bar.centerY() - TwilightSkia.textHeight(titleSize,
				TwilightSkia.Weight.SEMIBOLD) - 1F,
			titleSize, TwilightSkia.Weight.SEMIBOLD, text);
		TwilightSkia.text(nowArtist(), textX, bar.centerY() + 2F, artistSize,
			TwilightSkia.Weight.REGULAR, muted);
		
		Rect play = frame.playButton();
		Rect previous = frame.transportButton(-1);
		Rect next = frame.transportButton(1);
		
		TwilightSkia.skipGlyph(previous.centerX(), previous.centerY(),
			frame.px(12), false, text);
		TwilightSkia.skipGlyph(next.centerX(), next.centerY(), frame.px(12),
			true, text);
		
		TwilightSkia.fillRoundRect(play.x(), play.y(), play.width(),
			play.height(), 999F, accent);
		
		if(isPlaying())
			TwilightSkia.pauseGlyph(play.centerX(), play.centerY(),
				frame.px(14), 0xFFFFFFFF);
		else
			TwilightSkia.playGlyph(play.centerX(), play.centerY(),
				frame.px(14), 0xFFFFFFFF);
		
		Rect progress = frame.progressBar(
			TwilightShellLayout.PLAYER_BAR_SIDE_MARGIN);
		
		TwilightSkia.fillRoundRect(progress.x(), progress.y(), progress.width(),
			progress.height(), 999F, TwilightTheme.withAlpha(text, 0.12F));
		
		float ratio = durationMs > 0
			? Math.max(0F, Math.min(1F, positionMs / (float)durationMs)) : 0F;
		
		TwilightSkia.fillRoundRect(progress.x(), progress.y(),
			Math.max(frame.px(2), progress.width() * ratio), progress.height(),
			999F, accent);
		
		if(durationMs > 0)
			TwilightSkia.text(NeteaseMusicPlayer.formatTime(positionMs) + " / "
				+ NeteaseMusicPlayer.formatTime(durationMs),
				bar.right() - frame.px(16) - TwilightSkia.textWidth(
					NeteaseMusicPlayer.formatTime(positionMs) + " / "
						+ NeteaseMusicPlayer.formatTime(durationMs),
					frame.px(11), TwilightSkia.Weight.REGULAR),
				bar.centerY() - TwilightSkia.textHeight(frame.px(11),
					TwilightSkia.Weight.REGULAR) / 2F,
				frame.px(11), TwilightSkia.Weight.REGULAR, muted);
	}
	
	// ------------------------------------------------------------------
	// 无 Skia 时的回退（同一套几何，粗略绘制）
	// ------------------------------------------------------------------
	
	private void renderFallback(GuiGraphics graphics)
	{
		int accent = palette.accent;
		int text = theme.bodyText();
		int muted = theme.mutedText();
		Minecraft mc = Minecraft.getInstance();
		
		graphics.fill(0, 0, width, height, PAGE_BG);
		
		Rect panel = frame.sidebar;
		graphics.fill(panel.x(), panel.y(), panel.right(), panel.bottom(),
			theme.shellSidebarBg());
		graphics.fill(panel.right() - 1, panel.y(), panel.right(),
			panel.bottom(), theme.shellLine());
		
		for(int i = 0; i < NAV_LABELS.length; i++)
		{
			Rect item = frame.navItem(i);
			
			if(item == null)
				break;
			
			boolean active = i == activeNav;
			
			if(active)
				graphics.fill(item.x(), item.y(), item.right(), item.bottom(),
					TwilightTheme.withAlpha(accent,
						TwilightAccent.SOFT_ALPHA));
			
			graphics.drawString(mc.font, NAV_LABELS[i],
				item.x() + frame.px(14), item.centerY() - 4,
				active ? accent : muted, false);
		}
		
		graphics.drawString(mc.font, pageTitle(),
			frame.contentHeader.x()
				+ frame.px(TwilightShellLayout.CONTENT_MARGIN),
			frame.contentHeader.y() + frame.px(18), text, false);
		graphics.drawString(mc.font, pageSubtitle(),
			frame.contentHeader.x()
				+ frame.px(TwilightShellLayout.CONTENT_MARGIN),
			frame.contentHeader.y() + frame.px(40), muted, false);
		
		Rect hero = home.hero;
		graphics.fill(hero.x(), hero.y(), hero.right(), hero.bottom(),
			TwilightAccent.mix(0xFFFFFFFF, accent, 0.08F));
		graphics.drawString(mc.font, HERO_TITLE, hero.x() + frame.px(32),
			hero.y() + frame.px(48), text, false);
		graphics.drawString(mc.font, HERO_DESC_1, hero.x() + frame.px(32),
			hero.y() + frame.px(72), muted, false);
		graphics.fill(home.primaryCta.x(), home.primaryCta.y(),
			home.primaryCta.right(), home.primaryCta.bottom(), text);
		graphics.drawString(mc.font, HERO_PLAY,
			home.primaryCta.x() + frame.px(16),
			home.primaryCta.centerY() - 4, 0xFFFFFFFF, false);
		
		graphics.fill(home.duoCardLeft.x(), home.duoCardLeft.y(),
			home.duoCardLeft.right(), home.duoCardLeft.bottom(), 0xFFFFFFFF);
		graphics.drawString(mc.font, DUO_LEFT_NAME,
			home.duoCardLeft.x() + frame.px(22),
			home.duoCardLeft.centerY() - 4, text, false);
		graphics.fill(home.duoCardRight.x(), home.duoCardRight.y(),
			home.duoCardRight.right(), home.duoCardRight.bottom(), 0xFFFFFFFF);
		graphics.drawString(mc.font, DUO_RIGHT_NAME,
			home.duoCardRight.x() + frame.px(22),
			home.duoCardRight.centerY() - 4, text, false);
		
		graphics.drawString(mc.font, SECTION_TITLE, home.sectionHead.x(),
			home.sectionHead.y(), text, false);
		
		Rect bar = frame.playerBar;
		graphics.fill(bar.x(), bar.y(), bar.right(), bar.bottom(), 0xFFFFFFFF);
		graphics.fill(bar.x(), bar.y(), bar.right(), bar.y() + 1,
			theme.shellLine());
		
		Rect play = frame.playButton();
		graphics.fill(play.x(), play.y(), play.right(), play.bottom(), accent);
		graphics.drawString(mc.font, NOW_TITLE, bar.x() + frame.px(70),
			bar.centerY() - 8, text, false);
		graphics.drawString(mc.font, NOW_ARTIST, bar.x() + frame.px(70),
			bar.centerY() + 4, muted, false);
	}
	
	// ------------------------------------------------------------------
	// 交互
	// ------------------------------------------------------------------
	
	@Override
	public boolean mouseClicked(double mouseX, double mouseY, int button)
	{
		if(frame == null)
		{
			frame = TwilightShellLayout.layout(width, height, false);
			home = TwilightHomeLayout.layout(frame);
		}
		
		if(immersive)
			return handleImmersiveClick(mouseX, mouseY);
		
		if(loginOverlay)
		{
			if(PLAYER.isLoggedIn() && logoutButton().contains(mouseX, mouseY))
			{
				PLAYER.logout();
				statusLine = "已退出网易云账号";
				// 面板留在原地，立刻换成一张新二维码
				qrSession = null;
				clearQrTexture();
				qrStatus = "正在获取二维码…";
				qrCooldown = 0;
				return true;
			}
			
			// 弹层是模态的：点面板外任意处关闭，点面板内不做任何事
			if(!loginPanel().contains(mouseX, mouseY))
				closeLogin();
			
			return true;
		}
		
		if(loginPill().contains(mouseX, mouseY))
		{
			openLogin();
			return true;
		}
		
		if(searchPill().contains(mouseX, mouseY))
		{
			searchFocused = true;
			return true;
		}
		
		searchFocused = false;
		
		if(handlePlayerClick(mouseX, mouseY))
			return true;
		
		int index = frame.navItemAt(mouseX, mouseY);
		
		if(index >= 0)
		{
			activeNav = index;
			
			if(openedPlaylist != null)
				closePlaylistDetail();
			
			ensurePage();
			scrollRows = 0;
			scrollSub = 0;
			hoverChart = -1;
			hoverPageRow = -1;
			hoverPlaylist = -1;
			return true;
		}
		
		return super.mouseClicked(mouseX, mouseY, button);
	}
	
	/**
	 * The player bar: play/pause, previous, next, seeking on the progress bar,
	 * plus the hero's "play all" button. Everything talks to the shared
	 * {@link #PLAYER}, so the queue and the old screens stay consistent.
	 */
	private boolean handlePlayerClick(double mouseX, double mouseY)
	{
		Rect bar = frame.playerBar;
		float coverSize = frame.px(44);
		float coverX = bar.x() + frame.px(13);
		float coverY = bar.centerY() - coverSize / 2F;
		
		if(mouseX >= coverX && mouseX <= coverX + coverSize
			&& mouseY >= coverY && mouseY <= coverY + coverSize)
		{
			immersive = true;
			return true;
		}
		
		if(openedPlaylist != null)
		{
			if(detailPlayButton().contains(mouseX, mouseY))
			{
				playOpenedPlaylist();
				return true;
			}
			
			if(detailBackButton().contains(mouseX, mouseY))
			{
				closePlaylistDetail();
				return true;
			}
		}
		
		if(activeNav == 1 && hoverPlaylist >= 0 && playlists != null
			&& hoverPlaylist < playlists.size())
		{
			openPlaylist(playlists.get(hoverPlaylist));
			return true;
		}
		
		if((activeNav != 0 || searchMode) && hoverPageRow >= 0)
		{
			List<NeteaseSong> songs = pageSongs();
			
			if(songs != null && hoverPageRow < songs.size())
			{
				PLAYER.play(songs, firstListSong(songs) + hoverPageRow);
				return true;
			}
		}
		
		if(hoverChart >= 0 && homeSongs != null
			&& hoverChart < homeSongs.size())
		{
			PLAYER.play(homeSongs, firstChartSong() + hoverChart);
			return true;
		}
		
		Rect play = frame.playButton();
		
		if(play.contains(mouseX, mouseY))
		{
			if(PLAYER.getCurrentSong() == null)
				playHome();
			else
				PLAYER.toggle();
			
			return true;
		}
		
		if(frame.transportButton(-1).contains(mouseX, mouseY))
		{
			PLAYER.playPrevious();
			return true;
		}
		
		if(frame.transportButton(1).contains(mouseX, mouseY))
		{
			PLAYER.playNext();
			return true;
		}
		
		long duration = PLAYER.getDurationMs();
		Rect progress = frame.progressBar(
			TwilightShellLayout.PLAYER_BAR_SIDE_MARGIN);
		
		if(duration > 0 && progress.contains(mouseX, mouseY))
		{
			double ratio = (mouseX - progress.x())
				/ Math.max(1D, progress.width());
			
			PLAYER.seekTo(
				(long)(duration * Math.max(0D, Math.min(1D, ratio))));
			return true;
		}
		
		if(home != null && home.primaryCta.contains(mouseX, mouseY))
		{
			playHome();
			return true;
		}
		
		if(home != null && home.duoCardLeft.contains(mouseX, mouseY))
		{
			playRoaming();
			return true;
		}
		
		if(home != null && home.duoCardRight.contains(mouseX, mouseY))
		{
			activeNav = 1;
			ensurePage();
			scrollRows = 0;
			scrollSub = 0;
			return true;
		}
		
		return false;
	}
	
	@Override
	public boolean keyPressed(int keyCode, int scanCode, int modifiers)
	{
		if(keyCode == GLFW.GLFW_KEY_ESCAPE)
		{
			if(loginOverlay)
			{
				closeLogin();
				return true;
			}
			
			if(immersive)
			{
				immersive = false;
				return true;
			}
			
			if(openedPlaylist != null)
			{
				closePlaylistDetail();
				return true;
			}
			
			if(searchFocused || searchMode)
			{
				searchFocused = false;
				searchMode = false;
				return true;
			}
			
			onClose();
			return true;
		}
		
		if(!searchFocused)
			return super.keyPressed(keyCode, scanCode, modifiers);
		
		if(keyCode == GLFW.GLFW_KEY_ENTER)
		{
			runSearch();
			return true;
		}
		
		if(keyCode == GLFW.GLFW_KEY_BACKSPACE && search.length() > 0)
		{
			search.setLength(search.length() - 1);
			return true;
		}
		
		return super.keyPressed(keyCode, scanCode, modifiers);
	}
	
	@Override
	public boolean isPauseScreen()
	{
		return false;
	}
}

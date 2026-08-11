package net.wurstclient.clickgui2.screens;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletionException;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.EncodeHintType;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.qrcode.QRCodeWriter;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.wurstclient.clickgui2.FlatRenderer;
import net.wurstclient.clickgui2.screens.NeteaseImageCache.Texture;
import net.wurstclient.clickgui2.supersoft.SuperSoftTheme;
import net.wurstclient.clickgui2.supersoft.UiTween;
import net.wurstclient.gui.visual.VisualTheme;
import net.wurstclient.music.LyricLine;
import net.wurstclient.music.LyricParser;
import net.wurstclient.music.MusicAccountManager;
import net.wurstclient.music.MusicProvider;
import net.wurstclient.music.NeteaseCloudApi;
import net.wurstclient.music.NeteaseMusicPlayer;
import net.wurstclient.music.NeteasePlaylist;
import net.wurstclient.music.NeteaseSong;
import net.wurstclient.music.NeteaseUserProfile;
import org.lwjgl.glfw.GLFW;
import com.mojang.math.Axis;

/** Java GuiGraphics music browser styled after Mineradio's player shell. */
public final class NeteaseMusicScreen extends Screen
{
	private static final NeteaseMusicPlayer PLAYER = NeteaseMusicPlayer.INSTANCE;
	private static final int PANEL_WIDTH = 430;
	private static final int PANEL_HEIGHT = 286;
	private static final int SIDEBAR_WIDTH = 76;
	private static final int PLAYER_HEIGHT = 58;
	private static final int SONG_HEIGHT = 26;
	private static final int BACKGROUND = VisualTheme.BACKGROUND;
	private static final int SURFACE = VisualTheme.PANEL_SECONDARY;
	private static final int CARD = VisualTheme.CONTROL;
	private static final int TEXT = VisualTheme.TEXT;
	private static final int MUTED = VisualTheme.TEXT_MUTED;
	private static final ResourceLocation ICON_PLAY = icon("play_fill");
	private static final ResourceLocation ICON_PAUSE = icon("pause");
	private static final ResourceLocation ICON_PREVIOUS = icon("music_last");
	private static final ResourceLocation ICON_NEXT = icon("music_next");
	private static final ResourceLocation ICON_SEARCH = icon("search");
	private static final ResourceLocation ICON_BACK = icon("left");
	private static final ResourceLocation ICON_CLOSE = icon("disable");

	private final Screen parent;
	private NeteaseImageCache images = new NeteaseImageCache();
	private final UiTween screenMotion = new UiTween(0, 300);
	private final UiTween pageMotion = new UiTween(1, 200);
	private final UiTween detailMotion = new UiTween(0, 300);
	private final UiTween queueMotion = new UiTween(0, 220);
	private final UiTween searchMotion = new UiTween(0, 200);
	private final Map<String, UiTween> hoverMotions = new HashMap<>();
	private int accentColor = 0xFFEC4141;
	private int accentTarget = 0xFFEC4141;

	private Page page = Page.HOME;
	private List<NeteaseSong> homeSongs = List.of();
	private List<NeteasePlaylist> playlists = List.of();
	private List<NeteaseSong> searchSongs = List.of();
	private List<NeteaseSong> likedSongs = List.of();
	private List<NeteaseSong> playlistSongs = List.of();
	private NeteasePlaylist selectedPlaylist;
	private String query = "";
	private String message = "";
	private boolean inputFocused;
	private boolean homeLoading;
	private boolean playlistLoading;
	private boolean contentLoading;
	private boolean likedLoadingMore;
	private boolean likedHasMore = true;
	private boolean detailVisible;
	private boolean queueVisible;
	private boolean closing;
	private boolean wasLoggedIn;
	private int scroll;
	private int playlistScroll;
	private int carouselIndex;
	private int previousCarouselIndex;
	private long carouselChangedAt;
	private long requestSequence;
	private final UiTween loginModeMotion = new UiTween(1, 200);
	private LoginMode loginMode = LoginMode.PHONE;
	private MusicProvider loginProvider = MusicProvider.NETEASE;
	private LoginField loginField = LoginField.NONE;
	private String phone = "";
	private String captcha = "";
	private String loginMessage = "";
	private boolean loginLoading;
	private boolean cookieFocused;
	private String cookieInput = "";
	private long captchaAvailableAt;
	private long nextQrPoll;
	private long qrGeneration;
	private NeteaseCloudApi.QrLogin qrLogin;
	private NeteaseCloudApi.QrStatus qrStatus =
		NeteaseCloudApi.QrStatus.WAITING;
	private BitMatrix qrMatrix;
	private boolean draggingDetailProgress;
	private boolean draggingDetailVolume;
	private boolean draggingBottomProgress;
	private boolean draggingBottomVolume;
	private int queueScroll;

	public NeteaseMusicScreen(Screen parent)
	{
		super(Component.literal("网易云音乐"));
		this.parent = parent;
	}

	@Override
	protected void init()
	{
		if(images.isClosed())
			images = new NeteaseImageCache();
		wasLoggedIn = PLAYER.isLoggedIn();
		if(homeSongs.isEmpty() && !homeLoading)
			loadHome();
	}

	@Override
	public void tick()
	{
		NeteaseSong currentSong = PLAYER.getCurrentSong();
		int target = 0xFFEC4141;
		if(currentSong != null)
		{
			NeteaseImageCache.Texture cover = images.get(currentSong.coverUrl());
			if(cover != null)
				target = cover.accent();
		}
		accentTarget = target;
		if(accentColor != accentTarget)
			accentColor = lerpAccent(accentColor, accentTarget, 0.09F);
		if(page == Page.LOGIN && loginMode == LoginMode.QR && !loginLoading
			&& qrLogin != null && (qrStatus == NeteaseCloudApi.QrStatus.WAITING
				|| qrStatus == NeteaseCloudApi.QrStatus.SCANNED)
			&& System.currentTimeMillis() >= nextQrPoll)
			pollQrLogin();
		boolean loggedIn = PLAYER.isLoggedIn();
		if(loggedIn && !wasLoggedIn && page == Page.LOGIN)
			switchPage(Page.HOME);
		wasLoggedIn = loggedIn;
		if(homeSongs.size() > 1 && System.currentTimeMillis() - carouselChangedAt
			>= 3000)
		{
			previousCarouselIndex = carouselIndex;
			carouselIndex = (carouselIndex + 1) % Math.min(5, homeSongs.size());
			carouselChangedAt = System.currentTimeMillis();
		}
	}

	@Override
	public void render(GuiGraphics graphics, int mouseX, int mouseY,
		float partialTicks)
	{
		float open = screenMotion.update(closing ? 0 : 1);
		if(closing && open <= 0.001F)
		{
			minecraft.setScreen(parent);
			return;
		}
		graphics.fill(0, 0, width, height, withAlpha(0xFF000000, open * 0.68F));
		Bounds bounds = bounds();
		float scale = 0.9F + open * 0.1F;
		float centerX = (bounds.left + bounds.right) / 2F;
		float centerY = (bounds.top + bounds.bottom) / 2F;
		graphics.pose().pushPose();
		graphics.pose().translate(centerX, centerY, 0);
		graphics.pose().scale(scale, scale, 1);
		graphics.pose().translate(-centerX, -centerY, 0);
		renderPanel(graphics, bounds, mouseX, mouseY);
		graphics.pose().popPose();
	}

	private void renderPanel(GuiGraphics graphics, Bounds b, int mouseX,
		int mouseY)
	{
		FlatRenderer.fillRoundedRect(graphics, b.left - 5, b.top + 5,
			b.right + 5, b.bottom + 8, 10, 0x66000000);
		FlatRenderer.fillRoundedRect(graphics, b.left, b.top, b.right, b.bottom,
			10, BACKGROUND);
		int mainBottom = b.bottom - PLAYER_HEIGHT;
		graphics.fill(b.left, b.top + 8, b.left + SIDEBAR_WIDTH, mainBottom,
			0xCC000000);
		graphics.fill(b.left + SIDEBAR_WIDTH, b.top,
			b.right, mainBottom, 0x6610141B);
		renderSidebar(graphics, b, mouseX, mouseY);
		renderPage(graphics, b, mouseX, mouseY);
		renderBottomPlayer(graphics, b, mouseX, mouseY);
		renderQueuePopover(graphics, b, mouseX, mouseY);
		renderPlayerDetail(graphics, b, mouseX, mouseY);
	}

	private void renderSidebar(GuiGraphics graphics, Bounds b, int mouseX,
		int mouseY)
	{
		drawCenteredText(graphics, "MINERADIO", 6,
			b.left + SIDEBAR_WIDTH / 2,
			b.top + 10, TEXT, SIDEBAR_WIDTH - 8);
		NeteaseUserProfile profile = PLAYER.getUserProfile();
		if(PLAYER.isLoggedIn() && profile != null)
		{
			drawCover(graphics, profile.avatarUrl(), b.left + 22, b.top + 27,
				b.left + 48, b.top + 53, 1);
			FlatRenderer.drawRoundedOutline(graphics, b.left + 21, b.top + 26,
				b.left + 49, b.top + 54, 14, accentColor);
			drawCenteredText(graphics, profile.nickname(), 5,
				b.left + SIDEBAR_WIDTH / 2, b.top + 57, TEXT, 58);
		}
		int navTop = b.top + (profile != null && PLAYER.isLoggedIn() ? 75 : 34);
		renderNav(graphics, b, Page.HOME, "首页", navTop, mouseX, mouseY);
		renderNav(graphics, b, Page.SEARCH, "搜索", navTop + 24, mouseX,
			mouseY);
		renderNav(graphics, b, Page.LIKE, "喜欢", navTop + 48, mouseX,
			mouseY);

		int accountTop = b.bottom - PLAYER_HEIGHT - 29;
		boolean hovered = contains(mouseX, mouseY, b.left + 4, accountTop,
			b.left + SIDEBAR_WIDTH - 4, accountTop + 22);
		float hover = motion("account").update(hovered ? 1 : 0);
		FlatRenderer.fillRoundedRect(graphics, b.left + 4, accountTop,
			b.left + SIDEBAR_WIDTH - 4, accountTop + 22, 4,
			SuperSoftTheme.mix(0x00000000, withAlpha(accentColor, 0.2F), hover));
		drawCenteredText(graphics, hasAnyAccount() ? "账户" : "登录", 7,
			b.left + SIDEBAR_WIDTH / 2, accountTop + 7,
			PLAYER.isLoggedIn() ? MUTED : accentColor, SIDEBAR_WIDTH - 8);
	}

	private void renderNav(GuiGraphics graphics, Bounds b, Page target,
		String label, int top, int mouseX, int mouseY)
	{
		boolean hovered = contains(mouseX, mouseY, b.left + 4, top,
			b.left + SIDEBAR_WIDTH - 4, top + 22);
		float progress = motion("nav-" + target).update(
			target == page ? 1 : hovered ? 0.5F : 0);
		FlatRenderer.fillRoundedRect(graphics, b.left + 4, top,
			b.left + SIDEBAR_WIDTH - 4, top + 22, 4,
			SuperSoftTheme.mix(0x00000000, withAlpha(accentColor, 0.24F), progress));
		drawCenteredText(graphics, label, 7, b.left + SIDEBAR_WIDTH / 2,
			top + 7, target == page ? accentColor : 0xCCFFFFFF,
			SIDEBAR_WIDTH - 8);
	}

	private void renderPage(GuiGraphics graphics, Bounds b, int mouseX,
		int mouseY)
	{
		int left = b.left + SIDEBAR_WIDTH;
		int right = b.right;
		int bottom = b.bottom - PLAYER_HEIGHT;
		float transition = pageMotion.update(1);
		int offset = Math.round((1 - transition) * (right - left) / 4F);
		graphics.enableScissor(left, b.top, right, bottom);
		graphics.pose().pushPose();
		graphics.pose().translate(offset, 0, 0);
		if(selectedPlaylist != null)
			renderPlaylistDetail(graphics, left, b.top, right, bottom, mouseX,
				mouseY);
		else
			switch(page)
			{
				case HOME -> renderHome(graphics, left, b.top, right, bottom,
					mouseX, mouseY);
				case SEARCH -> renderSearch(graphics, left, b.top, right, bottom,
					mouseX, mouseY);
				case LIKE -> renderLike(graphics, left, b.top, right, bottom,
					mouseX, mouseY);
				case LOGIN -> renderLogin(graphics, left, b.top, right, bottom,
					mouseX, mouseY);
			}
		graphics.pose().popPose();
		if(transition < 0.999F)
			graphics.fill(left, b.top, right, bottom,
				withAlpha(SURFACE, (1 - transition) * 0.3F));
		graphics.disableScissor();
	}

	private void renderHome(GuiGraphics graphics, int left, int top, int right,
		int bottom, int mouseX, int mouseY)
	{
		int x = left + 8;
		int width = right - left - 16;
		int y = top + 8 - scroll;
		drawText(graphics, "热门歌曲", 8, x, y, TEXT, width);
		y += 15;
		if(homeLoading)
			renderLoading(graphics, (left + right) / 2, y + 27);
		else if(homeSongs.isEmpty())
			drawCenteredText(graphics,
				message.isBlank() ? "暂无热门歌曲" : message, 7,
				(left + right) / 2, y + 27, MUTED, width - 12);
		else
			renderCarousel(graphics, x, y, x + width, y + 62, mouseX, mouseY);
		y += 73;
		drawText(graphics, "推荐歌单", 8, x, y, TEXT, width);
		y += 14;
		if(playlistLoading)
			renderLoading(graphics, (left + right) / 2, y + 25);
		else if(playlists.isEmpty())
			drawCenteredText(graphics, "暂无推荐歌单", 7,
				(left + right) / 2, y + 20, MUTED, width - 12);
		else
			for(int index = 0; index < playlists.size(); index++)
			{
				int cardX = x + index * 61 - playlistScroll;
				if(cardX + 55 <= x || cardX >= right)
					continue;
				renderPlaylistCard(graphics, playlists.get(index), index, cardX, y,
					mouseX, mouseY);
			}
		y += 68;
		drawText(graphics, "新歌速递", 8, x, y, TEXT, width);
		y += 13;
		int songStart = Math.min(5, homeSongs.size());
		int songEnd = Math.min(songStart + 6, homeSongs.size());
		renderSongRows(graphics, homeSongs.subList(songStart, songEnd), homeSongs,
			5, false, x, y, x + width, bottom, mouseX, mouseY);
		renderScrollbar(graphics, right - 3, top + 5, bottom - 5,
			Math.max(0, 178 + Math.min(6, Math.max(0, homeSongs.size() - 5))
				* SONG_HEIGHT),
			bottom - top);
	}

	private void renderCarousel(GuiGraphics graphics, int left, int top,
		int right, int bottom, int mouseX, int mouseY)
	{
		int size = Math.min(5, homeSongs.size());
		carouselIndex = Mth.clamp(carouselIndex, 0, size - 1);
		NeteaseSong song = homeSongs.get(carouselIndex);
		float progress = Mth.clamp(
			(System.currentTimeMillis() - carouselChangedAt) / 500F, 0, 1);
		if(previousCarouselIndex != carouselIndex && previousCarouselIndex < size
			&& progress < 1)
		{
			int oldOffset = -Math.round((right - left) * progress);
			drawCover(graphics, homeSongs.get(previousCarouselIndex).coverUrl(),
				left + oldOffset, top, right + oldOffset, bottom, 1 - progress);
		}
		int newOffset = Math.round((right - left) * (1 - progress));
		drawCover(graphics, song.coverUrl(), left + newOffset, top,
			right + newOffset, bottom, 1);
		graphics.fill(left, (top + bottom) / 2, right, bottom, 0x88000000);
		drawText(graphics, song.name(), 8, left + 8, bottom - 24, TEXT,
			right - left - 20);
		drawText(graphics, song.artist(), 6, left + 8, bottom - 13,
			0xBBFFFFFF, right - left - 20);
		boolean hovered = contains(mouseX, mouseY, left, top, right, bottom);
		float hover = motion("carousel").update(hovered ? 1 : 0);
		if(hover > 0.01F)
		{
			int cx = (left + right) / 2;
			int cy = (top + bottom) / 2;
			FlatRenderer.fillRoundedRect(graphics, cx - 12, cy - 12, cx + 12,
				cy + 12, 12, withAlpha(accentColor, hover * 0.92F));
			drawPlay(graphics, cx + 1, cy, withAlpha(TEXT, hover));
		}
		int totalWidth = size * 8 + 4;
		for(int index = 0; index < size; index++)
		{
			int dotX = (left + right - totalWidth) / 2 + index * 8;
			graphics.fill(dotX, bottom - 5, dotX + (index == carouselIndex ? 8 : 4),
				bottom - 2, index == carouselIndex ? accentColor : 0x99FFFFFF);
		}
	}

	private void renderPlaylistCard(GuiGraphics graphics,
		NeteasePlaylist playlist, int index, int left, int top, int mouseX,
		int mouseY)
	{
		boolean hovered = contains(mouseX, mouseY, left, top, left + 52, top + 64);
		float hover = motion("playlist-" + playlist.id()).update(hovered ? 1 : 0);
		int inset = Math.round(hover * -1);
		drawCover(graphics, playlist.coverUrl(), left + inset, top + inset,
			left + 50 - inset, top + 50 - inset, 1);
		FlatRenderer.drawRoundedOutline(graphics, left + inset, top + inset,
			left + 50 - inset, top + 50 - inset, 5,
			SuperSoftTheme.mix(0x33171C24, accentColor, hover));
		String count = formatCount(playlist.playCount());
		graphics.fill(left + 28, top + 2, left + 48, top + 11, 0x99000000);
		drawText(graphics, count, 4, left + 30, top + 3, TEXT, 17);
		drawWrappedCenteredText(graphics, playlist.name(), 5, left + 25,
			top + 53, TEXT, 54, 2);
	}

	private void renderPlaylistDetail(GuiGraphics graphics, int left, int top,
		int right, int bottom, int mouseX, int mouseY)
	{
		int x = left + 8;
		boolean backHovered = contains(mouseX, mouseY, x, top + 8, x + 20,
			top + 28);
		float back = motion("playlist-back").update(backHovered ? 1 : 0);
		FlatRenderer.fillRoundedRect(graphics, x, top + 8, x + 20, top + 28, 10,
			SuperSoftTheme.mix(0x1AFFFFFF, 0x55FFFFFF, back));
		drawIcon(graphics, ICON_BACK, x + 6, top + 14, 8, TEXT, 1);
		drawText(graphics, selectedPlaylist.name(), 8, x + 28, top + 10, TEXT,
			right - left - 55);
		drawText(graphics, playlistSongs.size() + "首歌曲", 5, x + 28,
			top + 21, MUTED, right - left - 55);
		if(contentLoading)
			renderLoading(graphics, (left + right) / 2, top + 70);
		else
		{
			graphics.enableScissor(left, top + 34, right, bottom);
			renderSongRows(graphics, playlistSongs, playlistSongs, 0, true, x,
				top + 36 - scroll, right - 8, bottom, mouseX, mouseY);
			graphics.disableScissor();
		}
		renderScrollbar(graphics, right - 3, top + 34, bottom - 5,
			playlistSongs.size() * SONG_HEIGHT, bottom - top - 36);
	}

	private void renderSearch(GuiGraphics graphics, int left, int top, int right,
		int bottom, int mouseX, int mouseY)
	{
		int inputLeft = left + 8;
		int inputRight = right - 38;
		boolean inputHovered = contains(mouseX, mouseY, inputLeft, top + 8,
			inputRight, top + 30);
		float focus = searchMotion.update(inputFocused || inputHovered ? 1 : 0);
		FlatRenderer.fillRoundedRect(graphics, inputLeft, top + 8, inputRight,
			top + 30, 4, CARD);
		FlatRenderer.drawRoundedOutline(graphics, inputLeft, top + 8, inputRight,
			top + 30, 4, SuperSoftTheme.mix(withAlpha(accentColor, 0.3F), accentColor, focus));
		String display = query.isEmpty() ? "输入歌曲名或歌手..." : query;
		drawText(graphics, display, query.isEmpty() ? 6 : 7, inputLeft + 7,
			top + 15, query.isEmpty() ? 0x669F8997 : TEXT,
			inputRight - inputLeft - 14);
		if(inputFocused && System.currentTimeMillis() / 500 % 2 == 0)
		{
			int cursor = inputLeft + 7 + font.width(font.plainSubstrByWidth(query,
				inputRight - inputLeft - 14));
			graphics.fill(cursor, top + 13, cursor + 1, top + 26, accentColor);
		}
		boolean searchHovered = contains(mouseX, mouseY, right - 32, top + 8,
			right - 8, top + 30);
		float searchHover = motion("search-button").update(searchHovered ? 1 : 0);
		int inset = Math.round(searchHover);
		FlatRenderer.fillRoundedRect(graphics, right - 32 - inset,
			top + 8 - inset, right - 8 + inset, top + 30 + inset, 4, accentColor);
		drawIcon(graphics, ICON_SEARCH, right - 26, top + 13, 12, TEXT, 1);
		if(contentLoading)
			renderLoading(graphics, (left + right) / 2, top + 72);
		else if(searchSongs.isEmpty())
			drawCenteredText(graphics,
				message.isBlank() ? "输入关键词开始搜索" : message, 7,
				(left + right) / 2, top + 66, MUTED, right - left - 24);
		else
		{
			graphics.enableScissor(left, top + 36, right, bottom);
			renderSongRows(graphics, searchSongs, searchSongs, 0, true, left + 8,
				top + 38 - scroll, right - 8, bottom, mouseX, mouseY);
			graphics.disableScissor();
		}
		renderScrollbar(graphics, right - 3, top + 36, bottom - 5,
			searchSongs.size() * SONG_HEIGHT, bottom - top - 38);
	}

	private void renderLike(GuiGraphics graphics, int left, int top, int right,
		int bottom, int mouseX, int mouseY)
	{
		drawText(graphics, "我喜欢的音乐", 8, left + 8, top + 10, TEXT,
			right - left - 16);
		if(!PLAYER.isLoggedIn())
			drawCenteredText(graphics, "请先登录网易云账号", 7,
				(left + right) / 2, (top + bottom) / 2 - 5, MUTED,
				right - left - 24);
		else if(contentLoading)
			renderLoading(graphics, (left + right) / 2, top + 70);
		else if(likedSongs.isEmpty())
			drawCenteredText(graphics, "暂无喜欢的歌曲", 7,
				(left + right) / 2, top + 64, MUTED, right - left - 24);
		else
		{
			graphics.enableScissor(left, top + 26, right, bottom);
			renderSongRows(graphics, likedSongs, likedSongs, 0, false, left + 8,
				top + 28 - scroll, right - 8, bottom, mouseX, mouseY);
			if(likedLoadingMore)
				renderLoading(graphics, (left + right) / 2,
					top + 28 - scroll + likedSongs.size() * SONG_HEIGHT + 10);
			graphics.disableScissor();
		}
		renderScrollbar(graphics, right - 3, top + 26, bottom - 5,
			likedSongs.size() * SONG_HEIGHT, bottom - top - 28);
	}

	private void renderLogin(GuiGraphics graphics, int left, int top, int right,
		int bottom, int mouseX, int mouseY)
	{
		drawCenteredText(graphics, "MUSIC ACCOUNTS", 7, (left + right) / 2,
			top + 9, withAlpha(TEXT, 0.72F), right - left - 20);
		int center = (left + right) / 2;
		int providerLeft = center - 106;
		MusicProvider[] providers = MusicProvider.values();
		for(int index = 0; index < providers.length; index++)
			renderProviderTab(graphics, providers[index], providerLeft + index * 72,
				top + 23, mouseX, mouseY);
		if(loginProvider != MusicProvider.NETEASE)
		{
			renderCookieLogin(graphics, left, top, right, mouseX, mouseY);
			return;
		}
		renderLoginTab(graphics, center - 91, top + 52, "手机号",
			LoginMode.PHONE, mouseX, mouseY);
		renderLoginTab(graphics, center - 28, top + 52, "二维码", LoginMode.QR,
			mouseX, mouseY);
		renderLoginTab(graphics, center + 35, top + 52, "Cookie",
			LoginMode.COOKIE, mouseX, mouseY);
		int slide = Math.round((1 - loginModeMotion.update(1)) * 8);
		graphics.pose().pushPose();
		graphics.pose().translate(slide, 0, 0);
		if(loginMode == LoginMode.PHONE)
			renderInlinePhoneLogin(graphics, left, top, right, mouseX, mouseY);
		else if(loginMode == LoginMode.QR)
			renderInlineQrLogin(graphics, left, top, right, mouseX, mouseY);
		else
			renderCookieLogin(graphics, left, top, right, mouseX, mouseY);
		graphics.pose().popPose();
	}

	private void renderProviderTab(GuiGraphics graphics, MusicProvider provider,
		int left, int top, int mouseX, int mouseY)
	{
		boolean selected = loginProvider == provider;
		boolean hovered = contains(mouseX, mouseY, left, top, left + 68,
			top + 22);
		float progress = motion("provider-" + provider).update(
			selected ? 1 : hovered ? 0.55F : 0);
		FlatRenderer.fillRoundedRect(graphics, left, top, left + 68, top + 22,
			5, SuperSoftTheme.mix(0x16FFFFFF, withAlpha(accentColor, 0.19F), progress));
		drawText(graphics, provider.getShortName(), 6, left + 7, top + 7,
			selected ? accentColor : MUTED, 18);
		drawText(graphics, provider.getDisplayName(), 5, left + 25, top + 8,
			selected ? TEXT : withAlpha(TEXT, 0.65F), 34);
		if(isProviderConnected(provider))
			FlatRenderer.fillRoundedRect(graphics, left + 60, top + 8,
				left + 64, top + 12, 2, 0xFF62D98B);
	}

	private void renderLoginTab(GuiGraphics graphics, int left, int top,
		String label, LoginMode target, int mouseX, int mouseY)
	{
		boolean selected = loginMode == target;
		boolean hovered = contains(mouseX, mouseY, left, top, left + 56,
			top + 22);
		float progress = motion("login-tab-" + target).update(
			selected ? 1 : hovered ? 0.5F : 0);
		FlatRenderer.fillRoundedRect(graphics, left, top, left + 56, top + 22,
			4, withAlpha(accentColor, progress));
		drawCenteredText(graphics, label, 7, left + 28, top + 7,
			selected ? TEXT : MUTED, 48);
	}

	private void renderInlinePhoneLogin(GuiGraphics graphics, int left, int top,
		int right, int mouseX, int mouseY)
	{
		int formLeft = left + 8;
		int formRight = right - 8;
		int phoneTop = top + 86;
		renderLoginInput(graphics, formLeft, phoneTop, formRight, phoneTop + 26,
			phone, "手机号", loginField == LoginField.PHONE, mouseX, mouseY,
			"phone");
		int captchaTop = phoneTop + 34;
		int sendLeft = formRight - 82;
		renderLoginInput(graphics, formLeft, captchaTop, sendLeft - 6,
			captchaTop + 26, captcha, "验证码",
			loginField == LoginField.CAPTCHA, mouseX, mouseY, "captcha");
		boolean canSend = !loginLoading && phone.length() == 11
			&& System.currentTimeMillis() >= captchaAvailableAt;
		boolean sendHovered = contains(mouseX, mouseY, sendLeft, captchaTop,
			formRight, captchaTop + 27);
		float sendHover = motion("inline-send").update(
			canSend && sendHovered ? 1 : 0);
		FlatRenderer.fillRoundedRect(graphics, sendLeft - Math.round(sendHover),
			captchaTop - Math.round(sendHover), formRight + Math.round(sendHover),
			captchaTop + 26 + Math.round(sendHover), 4,
			canSend ? accentColor : CARD);
		long seconds = Math.max(0,
			(captchaAvailableAt - System.currentTimeMillis() + 999) / 1000);
		drawCenteredText(graphics,
			seconds > 0 ? seconds + "s" : "获取验证码", 6,
			(sendLeft + formRight) / 2, captchaTop + 9,
			canSend ? TEXT : MUTED, formRight - sendLeft - 8);
		int loginTop = captchaTop + 34;
		boolean loginHovered = contains(mouseX, mouseY, formLeft, loginTop,
			formRight, loginTop + 29);
		float loginHover = motion("inline-login").update(
			!loginLoading && loginHovered ? 1 : 0);
		FlatRenderer.fillRoundedRect(graphics, formLeft - Math.round(loginHover),
			loginTop - Math.round(loginHover), formRight + Math.round(loginHover),
			loginTop + 29 + Math.round(loginHover), 4,
			loginLoading ? CARD : accentColor);
		drawCenteredText(graphics, loginLoading ? "登录中..." : "登录", 8,
			(formLeft + formRight) / 2, loginTop + 10,
			loginLoading ? MUTED : TEXT, formRight - formLeft - 12);
		String status = loginMessage.isBlank()
			? "验证码由网易云官方 HTTPS 接口发送" : loginMessage;
		drawCenteredText(graphics, status, 6, (left + right) / 2,
			loginTop + 39,
			loginMessage.contains("成功") ? 0xFF75D58A
				: loginMessage.isBlank() ? 0x778F7A88 : accentColor,
			right - left - 30);
	}

	private void renderLoginInput(GuiGraphics graphics, int left, int top,
		int right, int bottom, String value, String placeholder, boolean active,
		int mouseX, int mouseY, String id)
	{
		FlatRenderer.fillRoundedRect(graphics, left, top, right, bottom, 4, CARD);
		boolean hovered = contains(mouseX, mouseY, left, top, right, bottom);
		float border = motion("login-input-" + id).update(
			active || hovered ? 1 : 0);
		FlatRenderer.drawRoundedOutline(graphics, left, top, right, bottom, 4,
			SuperSoftTheme.mix(0x664F3948, accentColor, border));
		String shown = value.isEmpty() ? placeholder : value;
		drawText(graphics, shown, value.isEmpty() ? 6 : 7, left + 7,
			top + 10, value.isEmpty() ? 0x779F8997 : TEXT, right - left - 14);
		if(active && System.currentTimeMillis() / 500 % 2 == 0)
		{
			int cursor = left + 7
				+ font.width(font.plainSubstrByWidth(value, right - left - 14));
			graphics.fill(cursor, top + 7, cursor + 1, bottom - 6, accentColor);
		}
	}

	private void renderInlineQrLogin(GuiGraphics graphics, int left, int top,
		int right, int mouseX, int mouseY)
	{
		int size = 90;
		int qrLeft = (left + right - size) / 2;
		int qrTop = top + 84;
		graphics.fill(qrLeft, qrTop, qrLeft + size, qrTop + size, 0xFFFFFFFF);
		if(qrMatrix == null)
			drawCenteredText(graphics,
				loginLoading ? "正在生成..." : "生成失败", 7,
				qrLeft + size / 2, qrTop + size / 2 - 4, 0xFF4A3A44,
				size - 12);
		else
		{
			int scale = Math.max(1, Math.min(size / qrMatrix.getWidth(),
				size / qrMatrix.getHeight()));
			int offsetX = qrLeft + (size - qrMatrix.getWidth() * scale) / 2;
			int offsetY = qrTop + (size - qrMatrix.getHeight() * scale) / 2;
			for(int y = 0; y < qrMatrix.getHeight(); y++)
				for(int x = 0; x < qrMatrix.getWidth(); x++)
					if(qrMatrix.get(x, y))
						graphics.fill(offsetX + x * scale, offsetY + y * scale,
							offsetX + (x + 1) * scale,
							offsetY + (y + 1) * scale, 0xFF000000);
		}
		String status = switch(qrStatus)
		{
			case WAITING -> "请使用网易云音乐 App 扫码";
			case SCANNED -> "已扫码，请在手机上确认";
			case SUCCESS -> "登录成功";
			case EXPIRED -> "二维码已过期，点击刷新";
			case ERROR -> loginMessage.isBlank() ? "二维码登录失败" : loginMessage;
		};
		drawCenteredText(graphics, status, 6, (left + right) / 2,
			qrTop + size + 10,
			qrStatus == NeteaseCloudApi.QrStatus.ERROR ? accentColor : MUTED,
			right - left - 30);
		drawCenteredText(graphics, "请使用网易云音乐APP扫码登录", 5,
			(left + right) / 2, qrTop + size + 24, withAlpha(TEXT, 0.4F),
			right - left - 30);
	}

	private void renderCookieLogin(GuiGraphics graphics, int left, int top,
		int right, int mouseX, int mouseY)
	{
		MusicAccountManager.AccountStatus status = loginProvider
			== MusicProvider.NETEASE ? null
				: PLAYER.getAccountManager().getStatus(loginProvider);
		boolean connected = isProviderConnected(loginProvider);
		int formLeft = left + 18;
		int formRight = right - 18;
		int fieldTop = top + 86;
		drawCenteredText(graphics,
			loginProvider.getDisplayName() + (connected ? " 已连接" : " 会话导入"),
			8, (left + right) / 2, top + 62,
			connected ? 0xFF62D98B : TEXT, right - left - 24);
		FlatRenderer.fillRoundedRect(graphics, formLeft, fieldTop, formRight,
			fieldTop + 32, 5, CARD);
		boolean hovered = contains(mouseX, mouseY, formLeft, fieldTop,
			formRight, fieldTop + 32);
		float focus = motion("cookie-input").update(
			cookieFocused || hovered ? 1 : 0);
		FlatRenderer.drawRoundedOutline(graphics, formLeft, fieldTop, formRight,
			fieldTop + 32, 5,
			SuperSoftTheme.mix(0x334F5A62, accentColor, focus));
		String inputText = cookieInput.isEmpty() ? cookiePlaceholder()
			: "Cookie 已输入 " + cookieInput.length() + " 字符";
		drawText(graphics, inputText, 6, formLeft + 8, fieldTop + 11,
			cookieInput.isEmpty() ? MUTED : TEXT, formRight - formLeft - 16);
		if(cookieFocused && System.currentTimeMillis() / 500 % 2 == 0)
			graphics.fill(formRight - 9, fieldTop + 8, formRight - 8,
				fieldTop + 24, accentColor);

		int actionTop = fieldTop + 40;
		FlatRenderer.fillRoundedRect(graphics, formLeft, actionTop, formRight,
			actionTop + 27, 5, loginLoading ? CARD : accentColor);
		drawCenteredText(graphics, loginLoading ? "正在验证..." : "保存并连接",
			7, (left + right) / 2, actionTop + 9,
			loginLoading ? MUTED : 0xFF03110F, formRight - formLeft - 12);
		if(connected)
		{
			String account = status == null && PLAYER.getUserProfile() != null
				? PLAYER.getUserProfile().nickname()
				: status == null ? loginProvider.getDisplayName()
					: status.nickname();
			drawText(graphics, account, 5, formLeft, actionTop + 36,
				withAlpha(TEXT, 0.62F), formRight - formLeft - 54);
			FlatRenderer.fillRoundedRect(graphics, formRight - 48, actionTop + 32,
				formRight, actionTop + 51, 5, 0x22FFFFFF);
			drawCenteredText(graphics, "退出", 5, formRight - 24,
				actionTop + 39, MUTED, 40);
		}
		String hint = loginMessage.isBlank()
			? "Cookie 仅保存在 WurstB 本地配置目录" : loginMessage;
		drawCenteredText(graphics, hint, 5, (left + right) / 2,
			actionTop + 58, loginMessage.isBlank() ? withAlpha(TEXT, 0.42F)
				: loginMessage.contains("已保存") ? 0xFF62D98B : accentColor,
			right - left - 28);
	}

	private String cookiePlaceholder()
	{
		return switch(loginProvider)
		{
			case NETEASE -> "MUSIC_U=...; __csrf=...";
			case QQ -> "uin=...; qqmusic_key=...";
			case KUGOU -> "userid=...; token=...";
		};
	}

	private void renderSongRows(GuiGraphics graphics, List<NeteaseSong> rows,
		List<NeteaseSong> playback, int indexOffset, boolean showIndex, int left,
		int top, int right, int bottom, int mouseX, int mouseY)
	{
		NeteaseSong current = PLAYER.getCurrentSong();
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
				SuperSoftTheme.mix(0x1AFFFFFF, withAlpha(accentColor, 0.32F), highlight));
			int coverLeft = left + 3;
			if(showIndex)
			{
				int shownIndex = index + indexOffset + 1;
				String number = Integer.toString(shownIndex);
				drawCenteredText(graphics, number, 6, left + 9, rowTop + 9,
					shownIndex <= 3 ? accentColor : withAlpha(TEXT, 0.5F), 14);
				coverLeft += 14;
			}
			drawCover(graphics, song.coverUrl(), coverLeft, rowTop + 2,
				coverLeft + 20, rowTop + 22, 1);
			FlatRenderer.drawRoundedOutline(graphics, coverLeft, rowTop + 2,
				coverLeft + 20, rowTop + 22, 4, 0x1AFFFFFF);
			int titleLeft = coverLeft + 25;
			drawText(graphics, song.name(), 6, titleLeft, rowTop + 4,
				selected ? accentColor : TEXT, right - titleLeft - 18);
			drawText(graphics, song.artist(), 5, titleLeft, rowTop + 15, MUTED,
				right - titleLeft - 18);
			if(selected && PLAYER.getState()
				== NeteaseMusicPlayer.PlaybackState.PLAYING)
				drawPlaying(graphics, right - 12, rowTop + 13, accentColor);
		}
	}

	private void renderBottomPlayer(GuiGraphics graphics, Bounds b, int mouseX,
		int mouseY)
	{
		int top = b.bottom - PLAYER_HEIGHT;
		FlatRenderer.fillRoundedRect(graphics, b.left + 2, top + 7,
			b.right - 2, b.bottom - 1, 14, 0x66000000);
		FlatRenderer.fillRoundedRect(graphics, b.left + 4, top + 4,
			b.right - 4, b.bottom - 4, 14, 0xE60B0E13);
		FlatRenderer.drawRoundedOutline(graphics, b.left + 4, top + 4,
			b.right - 4, b.bottom - 4, 14, 0x20FFFFFF);
		long duration = PLAYER.getDurationMs();
		float progress = duration <= 0 ? 0
			: Mth.clamp(PLAYER.getPositionMs() / (float)duration, 0, 1);
		int progressLeft = b.left + 10;
		int progressRight = b.right - 10;
		boolean progressHovered = contains(mouseX, mouseY, progressLeft, top,
			progressRight, top + 8);
		float progressHover = motion("bottom-progress").update(
			progressHovered || draggingBottomProgress ? 1 : 0);
		int progressY = top + 4;
		int barHeight = 2 + Math.round(progressHover);
		FlatRenderer.fillRoundedRect(graphics, progressLeft, progressY,
			progressRight, progressY + barHeight, barHeight / 2 + 1,
			0x2AFFFFFF);
		int thumbX = progressLeft
			+ Math.round((progressRight - progressLeft) * progress);
		if(thumbX > progressLeft)
			FlatRenderer.fillRoundedRect(graphics, progressLeft, progressY,
				thumbX, progressY + barHeight, barHeight / 2 + 1, accentColor);
		if(progressHover > 0.02F)
		{
			FlatRenderer.fillRoundedRect(graphics, thumbX - 2, progressY - 2,
				thumbX + 4, progressY + barHeight + 2, 4,
				withAlpha(accentColor, 0.55F * progressHover));
			FlatRenderer.fillRoundedRect(graphics, thumbX - 3, progressY - 3,
				thumbX + 5, progressY + barHeight + 3, 4,
				withAlpha(TEXT, progressHover));
		}

		NeteaseSong song = PLAYER.getCurrentSong();
		if(song != null)
		{
			int coverLeft = b.left + 9;
			int coverTop = top + 10;
			int coverSize = 42;
			FlatRenderer.drawRoundedOutline(graphics, coverLeft - 1, coverTop - 1,
				coverLeft + coverSize + 1, coverTop + coverSize + 1, 9,
				withAlpha(accentColor, 0.4F));
			drawCover(graphics, song.coverUrl(), coverLeft, coverTop,
				coverLeft + coverSize, coverTop + coverSize, 1);
			FlatRenderer.drawRoundedOutline(graphics, coverLeft, coverTop,
				coverLeft + coverSize, coverTop + coverSize, 8, 0x2AFFFFFF);
			int transportX = b.left + b.width() / 2;
			int infoLeft = coverLeft + coverSize + 6;
			int infoWidth = Math.max(42, transportX - 64 - infoLeft);
			drawText(graphics, song.name(), 7, infoLeft, top + 15, TEXT,
				infoWidth);
			drawText(graphics, song.artist(), 5, infoLeft, top + 27, MUTED,
				infoWidth);
			drawText(graphics,
				NeteaseMusicPlayer.formatTime(PLAYER.getPositionMs()) + " / "
					+ NeteaseMusicPlayer.formatTime(duration),
				4, infoLeft, top + 39, withAlpha(TEXT, 0.42F), infoWidth);
		}else
		{
			FlatRenderer.fillRoundedRect(graphics, b.left + 9, top + 10,
				b.left + 51, top + 52, 10, 0x12FFFFFF);
			drawText(graphics, "MINERADIO", 7, b.left + 59, top + 19, TEXT, 90);
			drawText(graphics, "Ready to play", 5, b.left + 59, top + 31,
				MUTED, 90);
		}

		int toggleX = b.left + b.width() / 2;
		int controlY = top + 32;
		int previousX = toggleX - 28;
		int nextX = toggleX + 28;
		renderModeButton(graphics, toggleX - 56, controlY, mouseX, mouseY);
		renderControl(graphics, "previous", previousX, controlY, 10, mouseX,
			mouseY, false);
		renderControl(graphics, "toggle", toggleX, controlY, 15, mouseX, mouseY,
			true);
		renderControl(graphics, "next", nextX, controlY, 10, mouseX,
			mouseY, false);
		renderLyricsButton(graphics, toggleX + 56, controlY, mouseX, mouseY);

		int volumeLeft = b.right - 76;
		int volumeRight = b.right - 34;
		int volumeY = controlY;
		drawVolume(graphics, volumeLeft - 9, volumeY, MUTED,
			PLAYER.getVolume() > 0.55F);
		graphics.fill(volumeLeft, volumeY, volumeRight, volumeY + 1,
			0x2AFFFFFF);
		graphics.fill(volumeLeft, volumeY,
			volumeLeft + Math.round((volumeRight - volumeLeft) * PLAYER.getVolume()),
			volumeY + 1, accentColor);
		boolean queueHovered = contains(mouseX, mouseY, b.right - 30, top + 18,
			b.right - 6, top + 46);
		float queueHover = motion("queue-button").update(
			queueVisible ? 1 : queueHovered ? 0.65F : 0);
		FlatRenderer.fillRoundedRect(graphics, b.right - 30, top + 18,
			b.right - 6, top + 46, 7,
			SuperSoftTheme.mix(0x00FFFFFF, withAlpha(accentColor, 0.14F), queueHover));
		drawQueue(graphics, b.right - 18, controlY,
			SuperSoftTheme.mix(MUTED, TEXT, queueHover));
	}

	private void renderModeButton(GuiGraphics graphics, int x, int y,
		int mouseX, int mouseY)
	{
		boolean hovered = distance(mouseX, mouseY, x, y) <= 12;
		float hover = motion("play-mode").update(hovered ? 1 : 0);
		FlatRenderer.fillRoundedRect(graphics, x - 11, y - 11, x + 11,
			y + 11, 7, SuperSoftTheme.mix(0x00FFFFFF, withAlpha(accentColor, 0.13F), hover));
		drawCenteredText(graphics, playbackModeLabel(), 6, x, y - 3,
			hovered ? TEXT : MUTED, 18);
	}

	private void renderLyricsButton(GuiGraphics graphics, int x, int y,
		int mouseX, int mouseY)
	{
		boolean hovered = distance(mouseX, mouseY, x, y) <= 12;
		float hover = motion("lyrics-button").update(
			detailVisible ? 1 : hovered ? 0.65F : 0);
		FlatRenderer.fillRoundedRect(graphics, x - 11, y - 11, x + 11,
			y + 11, 7,
			SuperSoftTheme.mix(0x00FFFFFF, withAlpha(accentColor, 0.16F), hover));
		drawCenteredText(graphics, "\u8bcd", 7, x, y - 4,
			SuperSoftTheme.mix(MUTED, TEXT, hover), 18);
	}

	private String playbackModeLabel()
	{
		return switch(PLAYER.getPlaybackMode())
		{
			case LOOP_ALL -> "\u5faa";
			case REPEAT_ONE -> "\u5355";
			case SHUFFLE -> "\u968f";
		};
	}

	private void renderQueuePopover(GuiGraphics graphics, Bounds b, int mouseX,
		int mouseY)
	{
		float progress = queueMotion.update(queueVisible ? 1 : 0);
		if(progress <= 0.001F || detailMotion.get() > 0.01F)
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
		List<NeteaseSong> queue = PLAYER.getPlaylist();
		drawTextRight(graphics, queue.size() + " tracks", 5, right - 10,
			top + 10, withAlpha(MUTED, progress), 50);
		FlatRenderer.fillRoundedRect(graphics, left + 8, top + 25, left + 54,
			top + 41, 5, withAlpha(0xFF20272D, progress));
		drawCenteredText(graphics, playbackModeName(), 5, left + 31, top + 30,
			withAlpha(TEXT, progress), 40);
		FlatRenderer.fillRoundedRect(graphics, right - 44, top + 25, right - 8,
			top + 41, 5, withAlpha(0xFF20272D, progress));
		drawCenteredText(graphics, "\u6e05\u7a7a", 5, right - 26, top + 30,
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
			boolean current = index == PLAYER.getCurrentIndex();
			boolean hovered = contains(mouseX, mouseY, left + 6, y, right - 6,
				y + 22);
			float hover = motion("queue-row-" + queuedSong.id()).update(
				current ? 1 : hovered ? 0.55F : 0);
			int rowColor = SuperSoftTheme.mix(TEXT, accentColor, hover);
			FlatRenderer.fillRoundedRect(graphics, left + 6, y, right - 6,
				y + 22, 5,
				withAlpha(rowColor, progress * (0.03F + hover * 0.08F)));
			if(current)
				graphics.fill(left + 6, y + 4, left + 8, y + 18,
					withAlpha(accentColor, progress));
			drawCover(graphics, queuedSong.coverUrl(), left + 11, y + 2,
				left + 29, y + 20, progress);
			drawText(graphics, queuedSong.name(), 6, left + 34, y + 3,
				withAlpha(current ? TEXT : 0xFFD8DFDE, progress), 112);
			drawText(graphics, queuedSong.artist(), 4, left + 34, y + 13,
				withAlpha(MUTED, progress), 112);
			if(current && PLAYER.getState()
				== NeteaseMusicPlayer.PlaybackState.PLAYING)
				drawPlaying(graphics, right - 18, y + 11, accentColor);
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
				thumbTop + thumbHeight, withAlpha(accentColor, progress * 0.55F));
		}
	}

	private String playbackModeName()
	{
		return switch(PLAYER.getPlaybackMode())
		{
			case LOOP_ALL -> "\u987a\u5e8f\u5faa\u73af";
			case REPEAT_ONE -> "\u5355\u66f2\u5faa\u73af";
			case SHUFFLE -> "\u968f\u673a\u64ad\u653e";
		};
	}

	private void renderControl(GuiGraphics graphics, String id, int x, int y,
		int radius, int mouseX, int mouseY, boolean primary)
	{
		boolean hovered = distance(mouseX, mouseY, x, y) <= radius + 2;
		float hover = motion("control-" + id).update(hovered ? 1 : 0);
		if(primary)
			FlatRenderer.fillRoundedRect(graphics, x - radius - Math.round(hover),
				y - radius - Math.round(hover), x + radius + Math.round(hover),
				y + radius + Math.round(hover), radius + 2, accentColor);
		if(id.equals("toggle"))
		{
			if(PLAYER.getState() == NeteaseMusicPlayer.PlaybackState.PLAYING)
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

	private void renderPlayerDetail(GuiGraphics graphics, Bounds b, int mouseX,
		int mouseY)
	{
		float progress = detailMotion.update(detailVisible ? 1 : 0);
		if(progress <= 0.001F)
			return;
		int offset = Math.round((1 - progress) * b.height());
		int top = b.top + offset;
		int panelBottom = b.bottom + offset;
		FlatRenderer.fillRoundedRect(graphics, b.left, top, b.right,
			panelBottom, 10, BACKGROUND);
		NeteaseSong song = PLAYER.getCurrentSong();
		if(song == null)
			return;
		drawCover(graphics, song.coverUrl(), b.left, top, b.right, panelBottom,
			0.16F);
		graphics.fill(b.left, top, b.right, panelBottom, 0xE7080A0E);
		graphics.fill(b.left, top, b.right, top + 1, 0x24FFFFFF);
		graphics.fill(b.left, top, b.left + 2, panelBottom,
			withAlpha(accentColor, 0.45F));

		boolean closeHovered = contains(mouseX, mouseY, b.left + 8, top + 8,
			b.left + 28, top + 28);
		float closeHover = motion("detail-close").update(closeHovered ? 1 : 0);
		FlatRenderer.fillRoundedRect(graphics, b.left + 8, top + 8,
			b.left + 28, top + 28, 10,
			SuperSoftTheme.mix(0x1AFFFFFF, 0x44FFFFFF, closeHover));
		drawIcon(graphics, ICON_CLOSE, b.left + 15, top + 15, 6, TEXT, 1);
		drawText(graphics, "NOW PLAYING", 5, b.left + 36, top + 10,
			withAlpha(accentColor, 0.82F), 100);
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
		if(PLAYER.getState() == NeteaseMusicPlayer.PlaybackState.PLAYING)
		{
			int pulse = Math.round((float)(Math.sin(System.currentTimeMillis()
				/ 420D) * 0.5 + 0.5) * 40);
			FlatRenderer.drawRoundedOutline(graphics, coverLeft - 2, coverTop - 2,
				coverLeft + coverSize + 2, coverTop + coverSize + 2, 9,
				withAlpha(accentColor, (70 + pulse) / 255F));
		}

		int lyricLeft = b.left + 154;
		int lyricRight = b.right - 12;
		drawText(graphics, "LYRICS", 5, lyricLeft + 8, top + 17,
			withAlpha(MUTED, 0.8F), lyricRight - lyricLeft - 16);
		renderLyricTiming(graphics, lyricRight, top, mouseX, mouseY);
		renderDetailLyrics(graphics, lyricLeft, top + 34, lyricRight,
			panelBottom - 66);
		int progressLeft = b.left + 16;
		int progressRight = b.right - 16;
		int progressY = panelBottom - 51;
		long duration = PLAYER.getDurationMs();
		float songProgress = duration <= 0 ? 0
			: Mth.clamp(PLAYER.getPositionMs() / (float)duration, 0, 1);
		graphics.fill(progressLeft, progressY, progressRight, progressY + 2,
			0x26FFFFFF);
		int thumbX = progressLeft
			+ Math.round((progressRight - progressLeft) * songProgress);
		graphics.fill(progressLeft, progressY, thumbX, progressY + 2, accentColor);
		FlatRenderer.fillRoundedRect(graphics, thumbX - 3, progressY - 3,
			thumbX + 4, progressY + 5, 4, TEXT);
		drawText(graphics,
			NeteaseMusicPlayer.formatTime(PLAYER.getPositionMs()), 5,
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
			volumeLeft + Math.round((volumeRight - volumeLeft) * PLAYER.getVolume()),
			controlY + 1, accentColor);
	}

	private void renderDetailLyrics(GuiGraphics graphics, int left, int top,
		int right, int bottom)
	{
		List<LyricLine> lyrics = PLAYER.getLyrics();
		if(lyrics.isEmpty())
		{
			drawCenteredText(graphics, "No lyrics available", 7,
				(left + right) / 2,
				(top + bottom) / 2, MUTED, right - left - 8);
			return;
		}
		int current = LyricParser.findCurrentIndex(lyrics,
			PLAYER.getAdjustedLyricPositionMs());
		int centerY = (top + bottom) / 2;
		float activeMotion = motion("lyric-" + current).update(1);
		for(int offset = -4; offset <= 4; offset++)
		{
			int index = current + offset;
			if(index < 0 || index >= lyrics.size())
				continue;
			float emphasis = offset == 0 ? 1 : Math.max(0.16F,
				0.58F - Math.abs(offset) * 0.1F);
			int y = centerY + offset * 18
				+ (offset == 0 ? Math.round((1 - activeMotion) * 6) : 0);
			if(offset == 0)
				FlatRenderer.fillRoundedRect(graphics, left + 3, y - 1,
					left + 5, y + 10, 1, withAlpha(accentColor, activeMotion));
			drawText(graphics, lyrics.get(index).text(), offset == 0 ? 9 : 6,
				left + 10, y, offset == 0 ? TEXT : withAlpha(TEXT, emphasis),
				right - left - 14);
		}
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
			SuperSoftTheme.mix(0x16FFFFFF, withAlpha(accentColor, 0.19F), resetHover));
		String offset = String.format(java.util.Locale.ROOT, "%+.1fs",
			PLAYER.getLyricOffsetMs() / 1000D);
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
			5, SuperSoftTheme.mix(0x16FFFFFF, withAlpha(accentColor, 0.19F), hover));
		drawCenteredText(graphics, label, 6, x, y - 3,
			hovered ? TEXT : MUTED, 12);
	}

	@Override
	public boolean mouseClicked(double mouseX, double mouseY, int button)
	{
		if(button != 0 || screenMotion.get() < 0.9F)
			return super.mouseClicked(mouseX, mouseY, button);
		Bounds b = bounds();
		if(detailMotion.get() > 0.01F)
			return clickDetail(mouseX, mouseY, b);
		if(queueMotion.get() > 0.01F && clickQueuePopover(mouseX, mouseY, b))
			return true;
		NeteaseUserProfile profile = PLAYER.getUserProfile();
		int navTop = b.top + (profile != null && PLAYER.isLoggedIn() ? 75 : 34);
		Page[] navPages = {Page.HOME, Page.SEARCH, Page.LIKE};
		for(int index = 0; index < navPages.length; index++)
			if(contains(mouseX, mouseY, b.left + 4, navTop + index * 24,
				b.left + SIDEBAR_WIDTH - 4, navTop + index * 24 + 22))
			{
				switchPage(navPages[index]);
				return true;
			}
		int accountTop = b.bottom - PLAYER_HEIGHT - 29;
		if(contains(mouseX, mouseY, b.left + 4, accountTop,
			b.left + SIDEBAR_WIDTH - 4, accountTop + 22))
		{
			switchPage(Page.LOGIN);
			return true;
		}
		if(clickBottomPlayer(mouseX, mouseY, b))
			return true;
		if(queueVisible)
		{
			queueVisible = false;
			return true;
		}
		if(selectedPlaylist != null)
			return clickPlaylistDetail(mouseX, mouseY, b);
		return switch(page)
		{
			case HOME -> clickHome(mouseX, mouseY, b);
			case SEARCH -> clickSearch(mouseX, mouseY, b);
			case LIKE -> mouseY >= b.top + 26
				&& clickSongList(mouseX, mouseY, b, likedSongs,
					b.top + 28 - scroll);
			case LOGIN -> clickLogin(mouseX, mouseY, b);
		};
	}

	private boolean clickHome(double mouseX, double mouseY, Bounds b)
	{
		int left = b.left + SIDEBAR_WIDTH + 8;
		int right = b.right - 8;
		int y = b.top + 23 - scroll;
		if(!homeSongs.isEmpty() && contains(mouseX, mouseY, left, y, right,
			y + 62))
		{
			PLAYER.play(homeSongs, carouselIndex);
			return true;
		}
		y += 87;
		for(int index = 0; index < playlists.size(); index++)
		{
			int cardX = left + index * 61 - playlistScroll;
			if(contains(mouseX, mouseY, cardX, y, cardX + 52, y + 64))
			{
				openPlaylist(playlists.get(index));
				return true;
			}
		}
		int songTop = y + 81;
		int songStart = Math.min(5, homeSongs.size());
		List<NeteaseSong> rows = homeSongs.subList(songStart,
			Math.min(songStart + 6, homeSongs.size()));
		int clicked = songIndex(mouseX, mouseY, left, songTop, right,
			b.bottom - PLAYER_HEIGHT, rows.size());
		if(clicked >= 0)
		{
			PLAYER.play(homeSongs, clicked + 5);
			return true;
		}
		return false;
	}

	private boolean clickSearch(double mouseX, double mouseY, Bounds b)
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
			searchSongs, b.top + 38 - scroll);
	}

	private boolean clickLogin(double mouseX, double mouseY, Bounds b)
	{
		int contentLeft = b.left + SIDEBAR_WIDTH;
		int center = (contentLeft + b.right) / 2;
		int providerLeft = center - 106;
		MusicProvider[] providers = MusicProvider.values();
		for(int index = 0; index < providers.length; index++)
			if(contains(mouseX, mouseY, providerLeft + index * 72, b.top + 23,
				providerLeft + index * 72 + 68, b.top + 45))
			{
				selectLoginProvider(providers[index]);
				return true;
			}
		if(loginProvider != MusicProvider.NETEASE)
			return clickCookieLogin(mouseX, mouseY, b);
		if(contains(mouseX, mouseY, center - 91, b.top + 52, center - 35,
			b.top + 74))
		{
			switchLoginMode(LoginMode.PHONE);
			return true;
		}
		if(contains(mouseX, mouseY, center - 28, b.top + 52, center + 28,
			b.top + 74))
		{
			switchLoginMode(LoginMode.QR);
			return true;
		}
		if(contains(mouseX, mouseY, center + 35, b.top + 52, center + 91,
			b.top + 74))
		{
			switchLoginMode(LoginMode.COOKIE);
			return true;
		}
		if(loginMode == LoginMode.COOKIE)
			return clickCookieLogin(mouseX, mouseY, b);
		if(loginMode == LoginMode.QR)
		{
			if(qrStatus == NeteaseCloudApi.QrStatus.EXPIRED
				|| qrStatus == NeteaseCloudApi.QrStatus.ERROR)
				startQrLogin();
			return true;
		}
		int formLeft = contentLeft + 8;
		int formRight = b.right - 8;
		int phoneTop = b.top + 86;
		int captchaTop = phoneTop + 34;
		int sendLeft = formRight - 82;
		if(contains(mouseX, mouseY, formLeft, phoneTop, formRight,
			phoneTop + 26))
			loginField = LoginField.PHONE;
		else if(contains(mouseX, mouseY, formLeft, captchaTop, sendLeft - 6,
			captchaTop + 26))
			loginField = LoginField.CAPTCHA;
		else if(contains(mouseX, mouseY, sendLeft, captchaTop, formRight,
			captchaTop + 26))
			sendCaptcha();
		else if(contains(mouseX, mouseY, formLeft, captchaTop + 34, formRight,
			captchaTop + 63))
			loginWithCaptcha();
		else
			loginField = LoginField.NONE;
		return true;
	}

	private boolean clickCookieLogin(double mouseX, double mouseY, Bounds b)
	{
		int contentLeft = b.left + SIDEBAR_WIDTH;
		int formLeft = contentLeft + 18;
		int formRight = b.right - 18;
		int fieldTop = b.top + 86;
		int actionTop = fieldTop + 40;
		if(contains(mouseX, mouseY, formLeft, fieldTop, formRight,
			fieldTop + 32))
		{
			cookieFocused = true;
			loginField = LoginField.NONE;
			return true;
		}
		cookieFocused = false;
		if(contains(mouseX, mouseY, formLeft, actionTop, formRight,
			actionTop + 27))
		{
			submitCookieLogin();
			return true;
		}
		if(isProviderConnected(loginProvider)
			&& contains(mouseX, mouseY, formRight - 48, actionTop + 32,
				formRight, actionTop + 51))
		{
			logoutProvider(loginProvider);
			return true;
		}
		return true;
	}

	private boolean clickPlaylistDetail(double mouseX, double mouseY, Bounds b)
	{
		int left = b.left + SIDEBAR_WIDTH + 8;
		if(contains(mouseX, mouseY, left, b.top + 8, left + 20, b.top + 28))
		{
			selectedPlaylist = null;
			playlistSongs = List.of();
			scroll = 0;
			pageMotion.snap(0);
			return true;
		}
		return mouseY >= b.top + 34 && clickSongList(mouseX, mouseY, b,
			playlistSongs, b.top + 36 - scroll);
	}

	private boolean clickSongList(double mouseX, double mouseY, Bounds b,
		List<NeteaseSong> songs, int top)
	{
		int left = b.left + SIDEBAR_WIDTH + 8;
		int index = songIndex(mouseX, mouseY, left, top, b.right - 8,
			b.bottom - PLAYER_HEIGHT, songs.size());
		if(index < 0)
			return false;
		PLAYER.play(songs, index);
		return true;
	}

	private boolean clickBottomPlayer(double mouseX, double mouseY, Bounds b)
	{
		int top = b.bottom - PLAYER_HEIGHT;
		if(!contains(mouseX, mouseY, b.left, top, b.right, b.bottom))
			return false;
		long duration = PLAYER.getDurationMs();
		if(mouseY < top + 9 && duration > 0)
		{
			draggingBottomProgress = true;
			seekBottom(mouseX, b);
			return true;
		}
		int toggleX = b.left + b.width() / 2;
		int controlY = top + 32;
		int previousX = toggleX - 28;
		int nextX = toggleX + 28;
		if(distance(mouseX, mouseY, toggleX - 56, controlY) <= 12)
			PLAYER.cyclePlaybackMode();
		else if(distance(mouseX, mouseY, previousX, controlY) <= 13)
			PLAYER.playPrevious();
		else if(distance(mouseX, mouseY, toggleX, controlY) <= 16)
			PLAYER.toggle();
		else if(distance(mouseX, mouseY, nextX, controlY) <= 13)
			PLAYER.playNext();
		else if(distance(mouseX, mouseY, toggleX + 56, controlY) <= 12
			&& PLAYER.getCurrentSong() != null)
		{
			queueVisible = false;
			detailVisible = true;
		}
		else if(contains(mouseX, mouseY, b.right - 30, top + 18, b.right - 6,
			top + 46))
			queueVisible = !queueVisible;
		else if(contains(mouseX, mouseY, b.right - 78, top + 18,
			b.right - 32, top + 36))
		{
			draggingBottomVolume = true;
			setBottomVolume(mouseX, b);
		}
		else if(PLAYER.getCurrentSong() != null)
		{
			queueVisible = false;
			detailVisible = true;
		}
		return true;
	}

	private boolean clickQueuePopover(double mouseX, double mouseY, Bounds b)
	{
		if(queueMotion.get() < 0.82F)
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
			PLAYER.cyclePlaybackMode();
			return true;
		}
		if(contains(mouseX, mouseY, right - 44, top + 25, right - 8,
			top + 41))
		{
			PLAYER.clearPlaylist();
			queueScroll = 0;
			return true;
		}
		int rowTop = top + 46;
		if(contains(mouseX, mouseY, left + 6, rowTop, right - 6,
			rowTop + 100))
		{
			int visible = (int)((mouseY - rowTop) / 25);
			int index = queueScroll + visible;
			List<NeteaseSong> queue = PLAYER.getPlaylist();
			if(index >= 0 && index < queue.size())
				PLAYER.play(queue, index);
		}
		return true;
	}

	private boolean clickDetail(double mouseX, double mouseY, Bounds b)
	{
		if(detailMotion.get() < 0.9F)
			return true;
		int top = b.top + Math.round((1 - detailMotion.get()) * b.height());
		if(contains(mouseX, mouseY, b.left + 8, top + 8, b.left + 28,
			top + 28))
		{
			detailVisible = false;
			return true;
		}
		int lyricRight = b.right - 12;
		int timingCenter = lyricRight - 43;
		if(contains(mouseX, mouseY, timingCenter - 37, top + 12,
			timingCenter - 21, top + 27))
		{
			PLAYER.adjustLyricOffset(-100);
			return true;
		}
		if(contains(mouseX, mouseY, timingCenter - 19, top + 12,
			timingCenter + 19, top + 27))
		{
			PLAYER.resetLyricOffset();
			return true;
		}
		if(contains(mouseX, mouseY, timingCenter + 21, top + 12,
			timingCenter + 37, top + 27))
		{
			PLAYER.adjustLyricOffset(100);
			return true;
		}
		int progressLeft = b.left + 16;
		int progressRight = b.right - 16;
		int progressY = b.bottom - 51;
		if(contains(mouseX, mouseY, progressLeft, progressY - 6, progressRight,
			progressY + 9) && PLAYER.getDurationMs() > 0)
		{
			draggingDetailProgress = true;
			seekDetail(mouseX, b);
			return true;
		}
		int controlY = b.bottom - 20;
		int toggleX = b.left + b.width() / 2;
		if(distance(mouseX, mouseY, toggleX - 31, controlY) <= 13)
			PLAYER.playPrevious();
		else if(distance(mouseX, mouseY, toggleX, controlY) <= 17)
			PLAYER.toggle();
		else if(distance(mouseX, mouseY, toggleX + 31, controlY) <= 13)
			PLAYER.playNext();
		else if(contains(mouseX, mouseY, b.right - 88, controlY - 8,
			b.right - 24, controlY + 8))
		{
			draggingDetailVolume = true;
			setDetailVolume(mouseX, b);
		}
		return true;
	}

	@Override
	public boolean mouseScrolled(double mouseX, double mouseY, double delta)
	{
		Bounds currentBounds = bounds();
		if(queueVisible)
		{
			int right = currentBounds.right - 8;
			int left = right - 176;
			int bottom = currentBounds.bottom - PLAYER_HEIGHT - 6;
			if(contains(mouseX, mouseY, left, bottom - 154, right, bottom))
			{
				int max = Math.max(0, PLAYER.getPlaylist().size() - 4);
				queueScroll = Mth.clamp(queueScroll + (delta > 0 ? -1 : 1), 0,
					max);
				return true;
			}
		}
		if(detailMotion.get() > 0.01F || selectedPlaylist == null
			&& page == Page.LOGIN)
			return false;
		int playlistTop = currentBounds.top + 110 - scroll;
		if(selectedPlaylist == null && page == Page.HOME
			&& contains(mouseX, mouseY,
				currentBounds.left + SIDEBAR_WIDTH + 8, playlistTop,
				currentBounds.right - 8, playlistTop + 64))
		{
			int available = currentBounds.width() - SIDEBAR_WIDTH - 16;
			int maxPlaylistScroll = Math.max(0, playlists.size() * 61 - 6
				- available);
			playlistScroll = Mth.clamp(playlistScroll
				+ (delta > 0 ? -22 : 22), 0, maxPlaylistScroll);
			return true;
		}
		int contentHeight = contentHeight();
		int viewport = currentBounds.height() - PLAYER_HEIGHT
			- (selectedPlaylist != null ? 36 : page == Page.SEARCH ? 38
				: page == Page.LIKE ? 28 : 0);
		int max = Math.max(0, contentHeight - viewport);
		scroll = Mth.clamp(scroll + (delta > 0 ? -SONG_HEIGHT : SONG_HEIGHT),
			0, max);
		if(page == Page.LIKE && PLAYER.isLoggedIn() && !contentLoading
			&& !likedLoadingMore && likedHasMore
			&& scroll >= max - SONG_HEIGHT && likedSongs.size() >= 50)
			loadMoreLiked();
		return true;
	}

	@Override
	public boolean mouseDragged(double mouseX, double mouseY, int button,
		double dragX, double dragY)
	{
		if(button == 0 && draggingDetailProgress)
		{
			seekDetail(mouseX, bounds());
			return true;
		}
		if(button == 0 && draggingDetailVolume)
		{
			setDetailVolume(mouseX, bounds());
			return true;
		}
		if(button == 0 && draggingBottomProgress)
		{
			seekBottom(mouseX, bounds());
			return true;
		}
		if(button == 0 && draggingBottomVolume)
		{
			setBottomVolume(mouseX, bounds());
			return true;
		}
		return super.mouseDragged(mouseX, mouseY, button, dragX, dragY);
	}

	@Override
	public boolean mouseReleased(double mouseX, double mouseY, int button)
	{
		if(button == 0 && (draggingDetailProgress || draggingDetailVolume
			|| draggingBottomProgress || draggingBottomVolume))
		{
			draggingDetailProgress = false;
			draggingDetailVolume = false;
			draggingBottomProgress = false;
			draggingBottomVolume = false;
			return true;
		}
		return super.mouseReleased(mouseX, mouseY, button);
	}

	private void seekDetail(double mouseX, Bounds b)
	{
		long duration = PLAYER.getDurationMs();
		if(duration <= 0)
			return;
		float value = Mth.clamp((float)((mouseX - (b.left + 16))
			/ (b.width() - 32D)), 0, 1);
		PLAYER.seekTo(Math.round(duration * value));
	}

	private void setDetailVolume(double mouseX, Bounds b)
	{
		PLAYER.setVolume(Mth.clamp((float)((mouseX - (b.right - 84)) / 56D),
			0, 1));
	}

	private void seekBottom(double mouseX, Bounds b)
	{
		long duration = PLAYER.getDurationMs();
		if(duration <= 0)
			return;
		float value = Mth.clamp((float)((mouseX - (b.left + 10))
			/ (b.width() - 20D)), 0, 1);
		PLAYER.seekTo(Math.round(duration * value));
	}

	private void setBottomVolume(double mouseX, Bounds b)
	{
		PLAYER.setVolume(Mth.clamp((float)((mouseX - (b.right - 76)) / 42D),
			0, 1));
	}

	@Override
	public boolean keyPressed(int keyCode, int scanCode, int modifiers)
	{
		if(keyCode == GLFW.GLFW_KEY_ESCAPE)
		{
			if(queueVisible)
				queueVisible = false;
			else if(detailVisible)
				detailVisible = false;
			else
				onClose();
			return true;
		}
		if(!inputFocused && !cookieFocused && !(page == Page.LOGIN
			&& loginMode == LoginMode.PHONE))
		{
			if(keyCode == GLFW.GLFW_KEY_SPACE)
			{
				PLAYER.toggle();
				return true;
			}
			if(keyCode == GLFW.GLFW_KEY_LEFT && PLAYER.getDurationMs() > 0)
			{
				PLAYER.seekTo(PLAYER.getPositionMs() - 5000);
				return true;
			}
			if(keyCode == GLFW.GLFW_KEY_RIGHT && PLAYER.getDurationMs() > 0)
			{
				PLAYER.seekTo(PLAYER.getPositionMs() + 5000);
				return true;
			}
		}
		if(cookieFocused)
		{
			if(keyCode == GLFW.GLFW_KEY_ENTER)
			{
				submitCookieLogin();
				return true;
			}
			if(keyCode == GLFW.GLFW_KEY_BACKSPACE && !cookieInput.isEmpty())
			{
				cookieInput = cookieInput.substring(0, cookieInput.length() - 1);
				return true;
			}
			if(keyCode == GLFW.GLFW_KEY_V && hasControlDown())
			{
				appendCookie(minecraft.keyboardHandler.getClipboard());
				return true;
			}
		}
		if(inputFocused)
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
		}
		if(page == Page.LOGIN && loginMode == LoginMode.PHONE)
		{
			if(keyCode == GLFW.GLFW_KEY_TAB)
			{
				loginField = loginField == LoginField.PHONE ? LoginField.CAPTCHA
					: LoginField.PHONE;
				return true;
			}
			if(keyCode == GLFW.GLFW_KEY_ENTER)
			{
				loginWithCaptcha();
				return true;
			}
			if(keyCode == GLFW.GLFW_KEY_BACKSPACE)
			{
				if(loginField == LoginField.PHONE && !phone.isEmpty())
					phone = phone.substring(0, phone.length() - 1);
				else if(loginField == LoginField.CAPTCHA && !captcha.isEmpty())
					captcha = captcha.substring(0, captcha.length() - 1);
				return true;
			}
			if(keyCode == GLFW.GLFW_KEY_V && hasControlDown())
			{
				appendLoginDigits(minecraft.keyboardHandler.getClipboard());
				return true;
			}
		}
		return super.keyPressed(keyCode, scanCode, modifiers);
	}

	@Override
	public boolean charTyped(char codePoint, int modifiers)
	{
		if(cookieFocused && !Character.isISOControl(codePoint))
		{
			appendCookie(String.valueOf(codePoint));
			return true;
		}
		if(page == Page.LOGIN && loginMode == LoginMode.PHONE
			&& Character.isDigit(codePoint) && loginField != LoginField.NONE)
		{
			appendLoginDigits(String.valueOf(codePoint));
			return true;
		}
		if(!inputFocused || Character.isISOControl(codePoint))
			return super.charTyped(codePoint, modifiers);
		if(query.length() < 80)
			query += codePoint;
		return true;
	}

	private void switchPage(Page target)
	{
		if(page == target && selectedPlaylist == null)
			return;
		page = target;
		cookieFocused = false;
		selectedPlaylist = null;
		inputFocused = false;
		scroll = 0;
		message = "";
		pageMotion.snap(0);
		if(target == Page.LIKE && PLAYER.isLoggedIn() && likedSongs.isEmpty())
			loadLiked();
	}

	private void appendLoginDigits(String value)
	{
		String digits = value.replaceAll("\\D", "");
		if(loginField == LoginField.PHONE)
			phone = (phone + digits).substring(0,
				Math.min(11, phone.length() + digits.length()));
		else if(loginField == LoginField.CAPTCHA)
			captcha = (captcha + digits).substring(0,
				Math.min(6, captcha.length() + digits.length()));
	}

	private void appendCookie(String value)
	{
		if(value == null || value.isEmpty() || cookieInput.length() >= 16_384)
			return;
		String cleaned = value.replace('\r', ' ').replace('\n', ' ');
		int remaining = 16_384 - cookieInput.length();
		cookieInput += cleaned.substring(0, Math.min(remaining, cleaned.length()));
	}

	private void selectLoginProvider(MusicProvider provider)
	{
		if(loginProvider == provider)
			return;
		loginProvider = provider;
		loginMode = LoginMode.PHONE;
		loginField = LoginField.NONE;
		cookieFocused = false;
		cookieInput = "";
		loginMessage = "";
		loginLoading = false;
		qrGeneration++;
		loginModeMotion.snap(0);
	}

	private boolean isProviderConnected(MusicProvider provider)
	{
		if(provider == MusicProvider.NETEASE)
			return PLAYER.isLoggedIn();
		return PLAYER.getAccountManager().isConnected(provider);
	}

	private boolean hasAnyAccount()
	{
		return PLAYER.isLoggedIn()
			|| PLAYER.getAccountManager().isConnected(MusicProvider.QQ)
			|| PLAYER.getAccountManager().isConnected(MusicProvider.KUGOU);
	}

	private void submitCookieLogin()
	{
		if(loginLoading)
			return;
		String submitted = cookieInput.trim();
		if(submitted.isEmpty())
		{
			loginMessage = "请先粘贴 Cookie";
			return;
		}
		loginLoading = true;
		loginMessage = "正在验证会话...";
		if(loginProvider == MusicProvider.NETEASE)
		{
			PLAYER.loginWithCookie(submitted).whenComplete((result, error) ->
				runOnClient(() -> {
					loginLoading = false;
					if(error != null)
						loginMessage = readableMessage(error);
					else
					{
						loginMessage = result.message();
						if(result.success())
							cookieInput = "";
					}
				}));
			return;
		}
		MusicAccountManager.ImportResult result = PLAYER.getAccountManager()
			.importCookie(loginProvider, submitted);
		loginLoading = false;
		loginMessage = result.message();
		if(result.success())
			cookieInput = "";
	}

	private void logoutProvider(MusicProvider provider)
	{
		if(provider == MusicProvider.NETEASE)
		{
			PLAYER.logout();
			likedSongs = List.of();
		}else
			PLAYER.getAccountManager().logout(provider);
		loginMessage = provider.getDisplayName() + " 已退出";
		cookieInput = "";
	}

	private void switchLoginMode(LoginMode target)
	{
		if(loginMode == target)
			return;
		loginMode = target;
		loginField = LoginField.NONE;
		cookieFocused = false;
		loginMessage = "";
		loginModeMotion.snap(0);
		qrGeneration++;
		loginLoading = false;
		if(target == LoginMode.QR)
			startQrLogin();
	}

	private void startQrLogin()
	{
		long generation = ++qrGeneration;
		loginMode = LoginMode.QR;
		loginLoading = true;
		loginMessage = "";
		qrLogin = null;
		qrMatrix = null;
		qrStatus = NeteaseCloudApi.QrStatus.WAITING;
		PLAYER.beginQrLogin().whenComplete((login, error) -> runOnClient(() -> {
			if(generation != qrGeneration)
				return;
			loginLoading = false;
			if(error != null)
			{
				qrStatus = NeteaseCloudApi.QrStatus.ERROR;
				loginMessage = readableMessage(error);
				return;
			}
			try
			{
				qrLogin = login;
				qrMatrix = new QRCodeWriter().encode(login.loginUrl(),
					BarcodeFormat.QR_CODE, 37, 37,
					Map.of(EncodeHintType.MARGIN, 1));
				nextQrPoll = System.currentTimeMillis() + 1000;
			}catch(Exception exception)
			{
				qrStatus = NeteaseCloudApi.QrStatus.ERROR;
				loginMessage = "无法生成二维码";
			}
		}));
	}

	private void pollQrLogin()
	{
		long generation = qrGeneration;
		loginLoading = true;
		nextQrPoll = System.currentTimeMillis() + 2000;
		PLAYER.checkQrLogin(qrLogin.key()).whenComplete((check, error) ->
			runOnClient(() -> {
				if(generation != qrGeneration)
					return;
				loginLoading = false;
				if(error != null)
				{
					qrStatus = NeteaseCloudApi.QrStatus.ERROR;
					loginMessage = readableMessage(error);
					return;
				}
				qrStatus = check.status();
				loginMessage = check.message();
				if(check.status() == NeteaseCloudApi.QrStatus.SUCCESS)
					switchPage(Page.HOME);
			}));
	}

	private void sendCaptcha()
	{
		if(loginLoading || phone.length() != 11
			|| System.currentTimeMillis() < captchaAvailableAt)
			return;
		loginLoading = true;
		loginMessage = "正在发送验证码...";
		PLAYER.sendCaptcha(phone, "86").whenComplete((result, error) ->
			runOnClient(() -> {
				loginLoading = false;
				if(error != null)
					loginMessage = readableMessage(error);
				else
				{
					loginMessage = result.message();
					if(result.success())
						captchaAvailableAt = System.currentTimeMillis() + 60000;
				}
			}));
	}

	private void loginWithCaptcha()
	{
		if(loginLoading || phone.length() != 11 || captcha.length() < 4)
			return;
		loginLoading = true;
		loginMessage = "正在登录...";
		PLAYER.loginWithCaptcha(phone, captcha, "86")
			.whenComplete((result, error) -> runOnClient(() -> {
				loginLoading = false;
				if(error != null)
					loginMessage = readableMessage(error);
				else
				{
					loginMessage = result.message();
					if(result.success())
						switchPage(Page.HOME);
				}
			}));
	}

	private void loadHome()
	{
		homeLoading = true;
		playlistLoading = true;
		carouselChangedAt = System.currentTimeMillis();
		long request = ++requestSequence;
		PLAYER.loadHomeSongs().whenComplete((songs, error) -> runOnClient(() -> {
			if(request != requestSequence)
				return;
			homeLoading = false;
			if(error == null)
				homeSongs = songs;
			else
				message = readableMessage(error);
		}));
		PLAYER.loadRecommendedPlaylists().whenComplete((loaded, error) ->
			runOnClient(() -> {
				playlistLoading = false;
				if(error == null)
					playlists = loaded;
				else if(message.isBlank())
					message = readableMessage(error);
			}));
	}

	private void openPlaylist(NeteasePlaylist playlist)
	{
		selectedPlaylist = playlist;
		playlistSongs = List.of();
		contentLoading = true;
		scroll = 0;
		pageMotion.snap(0);
		long request = ++requestSequence;
		PLAYER.loadPlaylist(playlist).whenComplete((songs, error) ->
			runOnClient(() -> {
				if(request != requestSequence || selectedPlaylist != playlist)
					return;
				contentLoading = false;
				if(error == null)
					playlistSongs = songs;
				else
					message = readableMessage(error);
			}));
	}

	private void runSearch()
	{
		String search = query.trim();
		if(search.isEmpty())
			return;
		contentLoading = true;
		message = "";
		scroll = 0;
		long request = ++requestSequence;
		PLAYER.search(search).whenComplete((songs, error) -> runOnClient(() -> {
			if(request != requestSequence)
				return;
			contentLoading = false;
			if(error == null)
			{
				searchSongs = songs;
				message = songs.isEmpty() ? "没有找到歌曲" : "";
			}else
			{
				searchSongs = List.of();
				message = readableMessage(error);
			}
		}));
	}

	private void loadLiked()
	{
		contentLoading = true;
		likedHasMore = true;
		long request = ++requestSequence;
		PLAYER.loadLikedSongs(0).whenComplete((songs, error) -> runOnClient(() -> {
			if(request != requestSequence || page != Page.LIKE)
				return;
			contentLoading = false;
			if(error == null)
			{
				likedSongs = songs;
				likedHasMore = songs.size() >= 50;
			}
			else
				message = readableMessage(error);
		}));
	}

	private void loadMoreLiked()
	{
		likedLoadingMore = true;
		PLAYER.loadLikedSongs(likedSongs.size()).whenComplete((songs, error) ->
			runOnClient(() -> {
				likedLoadingMore = false;
				if(error == null && !songs.isEmpty())
				{
					java.util.ArrayList<NeteaseSong> combined =
						new java.util.ArrayList<>(likedSongs);
					combined.addAll(songs);
					likedSongs = List.copyOf(combined);
				}
				if(error == null)
					likedHasMore = songs.size() >= 50;
			}));
	}

	private void runOnClient(Runnable action)
	{
		if(minecraft != null)
			minecraft.execute(() -> {
				if(minecraft.screen == this)
					action.run();
			});
	}

	private String readableMessage(Throwable error)
	{
		Throwable current = error;
		while(current instanceof CompletionException && current.getCause() != null)
			current = current.getCause();
		while(current.getCause() != null)
			current = current.getCause();
		return current.getMessage() == null ? "网易云请求失败"
			: current.getMessage();
	}

	private int contentHeight()
	{
		if(selectedPlaylist != null)
			return playlistSongs.size() * SONG_HEIGHT;
		return switch(page)
		{
			case HOME -> 191
				+ Math.min(6, Math.max(0, homeSongs.size() - 5)) * SONG_HEIGHT;
			case SEARCH -> searchSongs.size() * SONG_HEIGHT;
			case LIKE -> likedSongs.size() * SONG_HEIGHT;
			case LOGIN -> 0;
		};
	}

	private void renderScrollbar(GuiGraphics graphics, int x, int top,
		int bottom, int contentHeight, int viewport)
	{
		if(contentHeight <= viewport || viewport <= 0)
			return;
		int height = bottom - top;
		int thumb = Math.max(14, Math.round(height * viewport
			/ (float)contentHeight));
		int maxScroll = contentHeight - viewport;
		int thumbY = top + Math.round((height - thumb) * scroll
			/ (float)Math.max(1, maxScroll));
		graphics.fill(x, top, x + 2, bottom, 0x22FFFFFF);
		graphics.fill(x, thumbY, x + 2, thumbY + thumb,
			withAlpha(accentColor, 0.6F));
	}

	private void renderLoading(GuiGraphics graphics, int centerX, int centerY)
	{
		int phase = (int)(System.currentTimeMillis() / 120 % 8);
		for(int index = 0; index < 8; index++)
		{
			double angle = Math.PI * 2 * index / 8;
			int x = centerX + (int)Math.round(Math.cos(angle) * 8);
			int y = centerY + (int)Math.round(Math.sin(angle) * 8);
			graphics.fill(x, y, x + 2, y + 2,
				withAlpha(accentColor, index == phase ? 1 : 0.22F));
		}
	}

	private void drawCover(GuiGraphics graphics, String url, int left, int top,
		int right, int bottom, float alpha)
	{
		if(right <= left || bottom <= top)
			return;
		Texture texture = images.get(url);
		if(texture == null)
		{
			graphics.fill(left, top, right, bottom, CARD);
			graphics.drawCenteredString(font, "♪", (left + right) / 2,
				(top + bottom - font.lineHeight) / 2, withAlpha(MUTED, alpha));
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

	private void drawCircularCover(GuiGraphics graphics, String url, int left,
		int top, int size, float alpha, float rotation)
	{
		Texture texture = images.get(url);
		if(texture == null)
		{
			FlatRenderer.fillRoundedRect(graphics, left, top, left + size,
				top + size, size / 2, CARD);
			graphics.drawCenteredString(font, "♪", left + size / 2,
				top + (size - font.lineHeight) / 2, withAlpha(MUTED, alpha));
			return;
		}

		int sourceSize = Math.min(texture.width(), texture.height());
		int sourceLeft = (texture.width() - sourceSize) / 2;
		int sourceTop = (texture.height() - sourceSize) / 2;
		float centerX = left + size / 2F;
		float centerY = top + size / 2F;
		graphics.pose().pushPose();
		graphics.pose().translate(centerX, centerY, 0);
		graphics.pose().mulPose(Axis.ZP.rotationDegrees(rotation));
		graphics.pose().translate(-centerX, -centerY, 0);
		graphics.setColor(1, 1, 1, Mth.clamp(alpha, 0, 1));
		double radius = size / 2D;
		for(int row = 0; row < size; row++)
		{
			double dy = row + 0.5 - radius;
			int inset = (int)Math.ceil(radius
				- Math.sqrt(Math.max(0, radius * radius - dy * dy)));
			int rowWidth = size - inset * 2;
			if(rowWidth <= 0)
				continue;
			int u = sourceLeft + inset * sourceSize / size;
			int v = sourceTop + row * sourceSize / size;
			int uWidth = Math.max(1, rowWidth * sourceSize / size);
			int vHeight = Math.max(1, sourceSize / size);
			graphics.blit(texture.location(), left + inset, top + row, rowWidth, 1,
				u, v, uWidth, vHeight, texture.width(), texture.height());
		}
		graphics.setColor(1, 1, 1, 1);
		graphics.pose().popPose();
	}

	private UiTween motion(String id)
	{
		return hoverMotions.computeIfAbsent(id, ignored -> new UiTween(0, 150));
	}

	private static int songIndex(double mouseX, double mouseY, int left,
		int top, int right, int bottom, int size)
	{
		if(!contains(mouseX, mouseY, left, top, right, bottom))
			return -1;
		int index = (int)((mouseY - top) / SONG_HEIGHT);
		return index >= 0 && index < size ? index : -1;
	}

	private void drawText(GuiGraphics graphics, String text, float size, int x,
		int y, int color, int maxWidth)
	{
		float scale = size / font.lineHeight;
		String shown = fitText(text, scale, maxWidth);
		graphics.pose().pushPose();
		graphics.pose().translate(x, y, 0);
		graphics.pose().scale(scale, scale, 1);
		graphics.drawString(font, shown, 0, 0, color, false);
		graphics.pose().popPose();
	}

	private void drawCenteredText(GuiGraphics graphics, String text, float size,
		int centerX, int y, int color, int maxWidth)
	{
		float scale = size / font.lineHeight;
		String shown = fitText(text, scale, maxWidth);
		graphics.pose().pushPose();
		graphics.pose().translate(centerX, y, 0);
		graphics.pose().scale(scale, scale, 1);
		graphics.drawString(font, shown, -font.width(shown) / 2, 0, color,
			false);
		graphics.pose().popPose();
	}

	private void drawTextRight(GuiGraphics graphics, String text, float size,
		int right, int y, int color, int maxWidth)
	{
		float scale = size / font.lineHeight;
		String shown = fitText(text, scale, maxWidth);
		graphics.pose().pushPose();
		graphics.pose().translate(right, y, 0);
		graphics.pose().scale(scale, scale, 1);
		graphics.drawString(font, shown, -font.width(shown), 0, color, false);
		graphics.pose().popPose();
	}

	private void drawWrappedCenteredText(GuiGraphics graphics, String text,
		float size, int centerX, int y, int color, int maxWidth, int maxLines)
	{
		float scale = size / font.lineHeight;
		int baseWidth = Math.max(1, (int)(maxWidth / scale));
		String remaining = text;
		for(int line = 0; line < maxLines && !remaining.isEmpty(); line++)
		{
			String shown = font.plainSubstrByWidth(remaining, baseWidth);
			if(shown.isEmpty())
				break;
			if(line == maxLines - 1 && shown.length() < remaining.length())
				shown = font.plainSubstrByWidth(remaining, baseWidth);
			drawCenteredText(graphics, shown, size, centerX,
				y + Math.round(line * (size + 1)), color, maxWidth);
			remaining = remaining.substring(shown.length()).stripLeading();
		}
	}

	private String fitText(String text, float scale, int maxWidth)
	{
		if(maxWidth <= 0)
			return text;
		return font.plainSubstrByWidth(text,
			Math.max(1, (int)(maxWidth / Math.max(0.01F, scale))));
	}

	private static String formatCount(long count)
	{
		if(count >= 100_000_000)
			return String.format(java.util.Locale.ROOT, "%.1f亿",
				count / 100_000_000D);
		if(count >= 10_000)
			return String.format(java.util.Locale.ROOT, "%.1f万", count / 10_000D);
		return Long.toString(count);
	}

	private static ResourceLocation icon(String name)
	{
		return new ResourceLocation("wurst", "textures/gui/netease/" + name
			+ ".png");
	}

	private static void drawIcon(GuiGraphics graphics, ResourceLocation icon,
		int x, int y, int size, int color, float alpha)
	{
		float red = (color >> 16 & 0xFF) / 255F;
		float green = (color >> 8 & 0xFF) / 255F;
		float blue = (color & 0xFF) / 255F;
		float actualAlpha = (color >>> 24) / 255F * alpha;
		graphics.setColor(red, green, blue, actualAlpha);
		graphics.blit(icon, x, y, size, size, 0, 0, 200, 200, 200, 200);
		graphics.setColor(1, 1, 1, 1);
	}

	private static void drawVolume(GuiGraphics graphics, int x, int y,
		int color, boolean loud)
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

	private static void drawQueue(GuiGraphics graphics, int x, int y, int color)
	{
		for(int row = -5; row <= 5; row += 5)
		{
			graphics.fill(x - 6, y + row, x - 4, y + row + 2, color);
			graphics.fill(x - 2, y + row, x + 6, y + row + 2, color);
		}
	}

	private static void drawPlaying(GuiGraphics graphics, int x, int y,
		int color)
	{
		int phase = (int)(System.currentTimeMillis() / 130 % 4);
		for(int index = 0; index < 3; index++)
		{
			int height = 3 + (phase + index * 2) % 4;
			graphics.fill(x + index * 3, y - height / 2, x + index * 3 + 2,
				y + (height + 1) / 2, color);
		}
	}

	private static void drawPlay(GuiGraphics graphics, int x, int y, int color)
	{
		for(int row = -6; row <= 6; row++)
			graphics.fill(x - 4, y + row, x - 4 + 7 - Math.abs(row), y + row + 1,
				color);
	}

	private static void drawPause(GuiGraphics graphics, int x, int y, int color)
	{
		graphics.fill(x - 4, y - 6, x - 1, y + 6, color);
		graphics.fill(x + 2, y - 6, x + 5, y + 6, color);
	}

	private static void drawPrevious(GuiGraphics graphics, int x, int y,
		int color)
	{
		graphics.fill(x - 6, y - 6, x - 4, y + 6, color);
		for(int row = -5; row <= 5; row++)
			graphics.fill(x - 3 + Math.abs(row), y + row, x + 4, y + row + 1,
				color);
	}

	private static void drawNext(GuiGraphics graphics, int x, int y, int color)
	{
		graphics.fill(x + 4, y - 6, x + 6, y + 6, color);
		for(int row = -5; row <= 5; row++)
			graphics.fill(x - 4, y + row, x + 3 - Math.abs(row), y + row + 1,
				color);
	}

	private static void drawBack(GuiGraphics graphics, int x, int y, int color)
	{
		graphics.fill(x - 4, y - 1, x + 5, y + 1, color);
		graphics.fill(x - 5, y - 1, x - 3, y + 1, color);
		graphics.fill(x - 4, y - 3, x - 2, y - 1, color);
		graphics.fill(x - 4, y + 1, x - 2, y + 3, color);
	}

	private static void drawUp(GuiGraphics graphics, int x, int y, int color)
	{
		graphics.fill(x - 4, y + 1, x - 2, y + 3, color);
		graphics.fill(x - 2, y - 1, x + 2, y + 1, color);
		graphics.fill(x + 2, y + 1, x + 4, y + 3, color);
	}

	private static void drawDown(GuiGraphics graphics, int x, int y, int color)
	{
		graphics.fill(x - 4, y - 3, x - 2, y - 1, color);
		graphics.fill(x - 2, y - 1, x + 2, y + 1, color);
		graphics.fill(x + 2, y - 3, x + 4, y - 1, color);
	}

	@Override
	public void onClose()
	{
		if(!closing)
		{
			qrGeneration++;
			closing = true;
		}
	}

	@Override
	public void removed()
	{
		images.close();
		super.removed();
	}

	@Override
	public boolean isPauseScreen()
	{
		return false;
	}

	private Bounds bounds()
	{
		int panelWidth = Math.min(PANEL_WIDTH, Math.max(300, width - 24));
		int panelHeight = Math.min(PANEL_HEIGHT, Math.max(220, height - 24));
		int left = (width - panelWidth) / 2;
		int top = (height - panelHeight) / 2;
		return new Bounds(left, top, left + panelWidth, top + panelHeight);
	}

	private static boolean contains(double x, double y, int left, int top,
		int right, int bottom)
	{
		return x >= left && x < right && y >= top && y < bottom;
	}

	private static double distance(double x, double y, double targetX,
		double targetY)
	{
		return Math.hypot(x - targetX, y - targetY);
	}

	private static int withAlpha(int color, float alpha)
	{
		return Math.round(Mth.clamp(alpha, 0, 1) * 255) << 24
			| color & 0xFFFFFF;
	}

	private static int lerpAccent(int from, int to, float t)
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

	private enum Page
	{
		HOME,
		SEARCH,
		LIKE,
		LOGIN
	}

	private enum LoginMode
	{
		PHONE,
		QR,
		COOKIE
	}

	private enum LoginField
	{
		NONE,
		PHONE,
		CAPTCHA
	}

	private record Bounds(int left, int top, int right, int bottom)
	{
		private int width()
		{
			return right - left;
		}

		private int height()
		{
			return bottom - top;
		}
	}
}

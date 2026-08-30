package net.wurstclient.clickgui2.screens;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.wurstclient.clickgui2.FlatRenderer;
import net.wurstclient.clickgui2.PingFangFont;
import net.wurstclient.clickgui2.music.BottomPlayerBar;
import net.wurstclient.clickgui2.music.HomePage;
import net.wurstclient.clickgui2.music.LikedPage;
import net.wurstclient.clickgui2.music.LoginPage;
import net.wurstclient.clickgui2.music.MusicContext;
import net.wurstclient.clickgui2.music.MusicContext.Page;
import net.wurstclient.clickgui2.music.MusicRegion;
import net.wurstclient.clickgui2.music.NeteaseImageCache;
import net.wurstclient.clickgui2.music.PlaylistDetailPage;
import net.wurstclient.clickgui2.music.PlayerDetailOverlay;
import net.wurstclient.clickgui2.music.SearchPage;
import net.wurstclient.clickgui2.supersoft.SuperSoftTheme;
import net.wurstclient.clickgui2.supersoft.UiTween;
import net.wurstclient.gui.visual.VisualTheme;
import net.wurstclient.music.NeteaseMusicPlayer;
import net.wurstclient.music.NeteaseSong;
import net.wurstclient.music.NeteaseUserProfile;
import org.lwjgl.glfw.GLFW;

/**
 * Java GuiGraphics music browser styled after Mineradio's player shell.
 *
 * <p>本类只保留外壳：进出场动画、侧边栏导航、面板布局与输入分发。各页面与
 * 播放栏/详情层分别委托给 {@code net.wurstclient.clickgui2.music} 包下的
 * 子组件。</p>
 */
public final class NeteaseMusicScreen extends Screen
{
	private static final NeteaseMusicPlayer PLAYER = NeteaseMusicPlayer.INSTANCE;

	private final Screen parent;
	private final MusicContext ctx;
	private final UiTween screenMotion = new UiTween(0, 300);
	private final HomePage homePage;
	private final SearchPage searchPage;
	private final LikedPage likedPage;
	private final LoginPage loginPage;
	private final PlaylistDetailPage playlistDetailPage;
	private final BottomPlayerBar bottomPlayerBar;
	private final PlayerDetailOverlay playerDetailOverlay;
	private final net.wurstclient.music.PlayerListener playerListener;
	private boolean closing;
	private boolean wasLoggedIn;

	public NeteaseMusicScreen(Screen parent)
	{
		super(Component.literal("网易云音乐"));
		this.parent = parent;
		ctx = new MusicContext(PLAYER);
		ctx.screen = this;
		homePage = new HomePage(ctx);
		searchPage = new SearchPage(ctx);
		likedPage = new LikedPage(ctx);
		loginPage = new LoginPage(ctx);
		playlistDetailPage = new PlaylistDetailPage(ctx);
		bottomPlayerBar = new BottomPlayerBar(ctx);
		playerDetailOverlay = new PlayerDetailOverlay(ctx);
		ctx.onPageSwitched = () -> {
			searchPage.resetFocus();
			loginPage.resetFocus();
		};
		ctx.onLogout = likedPage::clear;
		playerListener = new net.wurstclient.music.PlayerListener()
		{
			@Override
			public void onSongChanged(NeteaseSong song)
			{
				ctx.runOnClient(() -> refreshAccent());
			}

			@Override
			public void onPlaybackStateChanged(
				NeteaseMusicPlayer.PlaybackState state)
			{
				ctx.runOnClient(() -> {
					if(state == NeteaseMusicPlayer.PlaybackState.ERROR
						&& ctx.message.isBlank())
						ctx.message = "播放出错，请重试";
				});
			}
		};
	}

	@Override
	protected void init()
	{
		if(ctx.images.isClosed())
			ctx.images = new NeteaseImageCache();
		wasLoggedIn = PLAYER.isLoggedIn();
		PLAYER.addListener(playerListener);
		homePage.init();
	}

	@Override
	public void tick()
	{
		refreshAccent();
		loginPage.tick();
		boolean loggedIn = PLAYER.isLoggedIn();
		if(loggedIn && !wasLoggedIn && ctx.page == Page.LOGIN)
			ctx.switchPage(Page.HOME);
		wasLoggedIn = loggedIn;
	}

	private void refreshAccent()
	{
		NeteaseSong currentSong = PLAYER.getCurrentSong();
		int target = 0xFF007CFF;
		if(currentSong != null)
		{
			NeteaseImageCache.Texture cover = ctx.images.get(currentSong.coverUrl());
			if(cover != null)
				target = cover.accent();
		}
		ctx.accentTarget = target;
		if(ctx.accentColor != ctx.accentTarget)
			ctx.accentColor = MusicRegion.lerpAccent(ctx.accentColor,
				ctx.accentTarget, 0.09F);
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
		// PVPUtils 音乐 GUI 遮罩：0x111315 68% + 0x07120E 25%
		graphics.fill(0, 0, width, height,
			MusicRegion.withAlpha(0xFF111315, open * 0.68F));
		graphics.fill(0, 0, width, height,
			MusicRegion.withAlpha(0xFF07120E, open * 0.25F));
		MusicContext.Bounds bounds = ctx.bounds();
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

	private void renderPanel(GuiGraphics graphics, MusicContext.Bounds b,
		int mouseX, int mouseY)
	{
		FlatRenderer.fillRoundedRect(graphics, b.left - 5, b.top + 5,
			b.right + 5, b.bottom + 8, 10, 0x66000000);
		FlatRenderer.fillRoundedRect(graphics, b.left, b.top, b.right, b.bottom,
			10, VisualTheme.BACKGROUND);
		int mainBottom = b.bottom - MusicRegion.PLAYER_HEIGHT;
		// PVPUtils 侧栏底色：0x1E1D17 64%
		graphics.fill(b.left, b.top + 8, b.left + MusicRegion.SIDEBAR_WIDTH,
			mainBottom, 0xA31E1D17);
		graphics.fill(b.left + MusicRegion.SIDEBAR_WIDTH, b.top,
			b.right, mainBottom, 0x6610141B);
		NeteaseSong song = PLAYER.getCurrentSong();
		if(song != null)
		{
			NeteaseImageCache.Texture cover = ctx.images.get(song.coverUrl());
			if(cover != null)
				drawCover(graphics, song.coverUrl(), b.left, b.top, b.right,
					mainBottom, 0.05F);
		}
		bottomPlayerBar.renderTopProgressBar(graphics, b, mouseX, mouseY);
		renderSidebar(graphics, b, mouseX, mouseY);
		renderPage(graphics, b, mouseX, mouseY);
		bottomPlayerBar.render(graphics, b, mouseX, mouseY);
		bottomPlayerBar.renderQueuePopover(graphics, b, mouseX, mouseY);
		playerDetailOverlay.render(graphics, b, mouseX, mouseY);
	}

	private void renderSidebar(GuiGraphics graphics, MusicContext.Bounds b,
		int mouseX, int mouseY)
	{
		// PVPUtils 侧栏布局：顶部搜索框 → Home → 歌单 → 底部账户区
		int searchX = b.left + 12;
		int searchY = b.top + 14;
		int searchW = MusicRegion.SIDEBAR_WIDTH - 24;
		int searchH = 22;
		boolean searchHovered = MusicRegion.contains(mouseX, mouseY, searchX,
			searchY, searchX + searchW, searchY + searchH);
		FlatRenderer.fillRoundedRect(graphics, searchX, searchY,
			searchX + searchW, searchY + searchH, 6,
			SuperSoftTheme.mix(0x24FFFFFF, 0x38FFFFFF,
				ctx.motion("sidebar-search").update(searchHovered ? 1 : 0)));
		drawCenteredText(graphics, searchHovered ? "搜索..." : "搜索...", 5,
			searchX + searchW / 2, searchY + 7,
			searchHovered ? VisualTheme.TEXT : VisualTheme.TEXT_MUTED,
			searchW - 16);
		renderNav(graphics, b, Page.HOME, "Home", b.top + 74, mouseX,
			mouseY);
		renderNav(graphics, b, Page.SEARCH, "搜索", b.top + 74 + 28, mouseX,
			mouseY);
		renderNav(graphics, b, Page.LIKE, "喜欢", b.top + 74 + 56, mouseX,
			mouseY);

		// PVPUtils 底部账户区：深色底 + 头像/登录
		int accountAreaTop = b.bottom - MusicRegion.PLAYER_HEIGHT - 50;
		graphics.fill(b.left, accountAreaTop,
			b.left + MusicRegion.SIDEBAR_WIDTH,
			b.bottom - MusicRegion.PLAYER_HEIGHT, 0x8C0D1412);
		NeteaseUserProfile profile = PLAYER.getUserProfile();
		boolean loggedIn = PLAYER.isLoggedIn() && profile != null;
		int avatarY = b.bottom - MusicRegion.PLAYER_HEIGHT - 34;
		if(loggedIn)
		{
			drawCover(graphics, profile.avatarUrl(), b.left + 14, avatarY,
				b.left + 32, avatarY + 18, 1);
			drawCenteredText(graphics, profile.nickname(), 5,
				b.left + 40, avatarY + 6, 0xFFFFFFFF, 120);
		}else
		{
			boolean hovered = MusicRegion.contains(mouseX, mouseY, b.left + 4,
				accountAreaTop, b.left + MusicRegion.SIDEBAR_WIDTH - 4,
				b.bottom - MusicRegion.PLAYER_HEIGHT);
			float hover = ctx.motion("account").update(hovered ? 1 : 0);
			FlatRenderer.fillRoundedRect(graphics, b.left + 10, avatarY,
				b.left + MusicRegion.SIDEBAR_WIDTH - 10, avatarY + 20, 9,
				SuperSoftTheme.mix(0x00FFFFFF,
					MusicRegion.withAlpha(ctx.accentColor, 0.3F), hover));
			drawCenteredText(graphics, "登录", 6,
				b.left + MusicRegion.SIDEBAR_WIDTH / 2, avatarY + 6,
				ctx.accentColor, MusicRegion.SIDEBAR_WIDTH - 20);
		}
	}

	private void renderNav(GuiGraphics graphics, MusicContext.Bounds b,
		Page target, String label, int top, int mouseX, int mouseY)
	{
		boolean hovered = MusicRegion.contains(mouseX, mouseY, b.left + 4, top,
			b.left + MusicRegion.SIDEBAR_WIDTH - 4, top + 22);
		float progress = ctx.motion("nav-" + target).update(
			target == ctx.page ? 1 : hovered ? 0.5F : 0);
		FlatRenderer.fillRoundedRect(graphics, b.left + 4, top,
			b.left + MusicRegion.SIDEBAR_WIDTH - 4, top + 22, 4,
			SuperSoftTheme.mix(0x00000000,
				MusicRegion.withAlpha(ctx.accentColor, 0.24F), progress));
		drawCenteredText(graphics, label, 7, b.left + MusicRegion.SIDEBAR_WIDTH
			/ 2, top + 7, target == ctx.page ? ctx.accentColor : 0xCCFFFFFF,
			MusicRegion.SIDEBAR_WIDTH - 8);
	}

	private void renderPage(GuiGraphics graphics, MusicContext.Bounds b,
		int mouseX, int mouseY)
	{
		int left = b.left + MusicRegion.SIDEBAR_WIDTH;
		int right = b.right;
		int bottom = b.bottom - MusicRegion.PLAYER_HEIGHT;
		float transition = ctx.pageMotion.update(1);
		int offset = Math.round((1 - transition) * (right - left) / 4F);
		graphics.enableScissor(left, b.top, right, bottom);
		graphics.pose().pushPose();
		graphics.pose().translate(offset, 0, 0);
		if(ctx.selectedPlaylist != null)
			playlistDetailPage.render(graphics, left, b.top, right, bottom,
				mouseX, mouseY);
		else
			switch(ctx.page)
			{
				case HOME -> homePage.render(graphics, left, b.top, right,
					bottom, mouseX, mouseY);
				case SEARCH -> searchPage.render(graphics, left, b.top, right,
					bottom, mouseX, mouseY);
				case LIKE -> likedPage.render(graphics, left, b.top, right,
					bottom, mouseX, mouseY);
				case LOGIN -> loginPage.render(graphics, left, b.top, right,
					bottom, mouseX, mouseY);
			}
		graphics.pose().popPose();
		if(transition < 0.999F)
			graphics.fill(left, b.top, right, bottom,
				MusicRegion.withAlpha(VisualTheme.PANEL_SECONDARY,
					(1 - transition) * 0.3F));
		graphics.disableScissor();
	}

	@Override
	public boolean mouseClicked(double mouseX, double mouseY, int button)
	{
		if(button != 0 || screenMotion.get() < 0.9F)
			return super.mouseClicked(mouseX, mouseY, button);
		MusicContext.Bounds b = ctx.bounds();
		if(ctx.detailMotion.get() > 0.01F)
			return playerDetailOverlay.click(mouseX, mouseY, b);
		if(ctx.queueMotion.get() > 0.01F
			&& bottomPlayerBar.clickQueuePopover(mouseX, mouseY, b))
			return true;
		NeteaseUserProfile profile = PLAYER.getUserProfile();
		// PVPUtils 侧栏点击：搜索框 → 搜索页；nav → 页面；账户区 → 登录页
		int searchX = b.left + 12;
		int searchY = b.top + 14;
		if(MusicRegion.contains(mouseX, mouseY, searchX, searchY,
			searchX + MusicRegion.SIDEBAR_WIDTH - 24, searchY + 22))
		{
			ctx.switchPage(Page.SEARCH);
			return true;
		}
		int navTop = b.top + 74;
		Page[] navPages = {Page.HOME, Page.SEARCH, Page.LIKE};
		for(int index = 0; index < navPages.length; index++)
			if(MusicRegion.contains(mouseX, mouseY, b.left + 4,
				navTop + index * 28, b.left + MusicRegion.SIDEBAR_WIDTH - 4,
				navTop + index * 28 + 22))
			{
				ctx.switchPage(navPages[index]);
				return true;
			}
		int accountTop = b.bottom - MusicRegion.PLAYER_HEIGHT - 50;
		if(MusicRegion.contains(mouseX, mouseY, b.left + 4, accountTop,
			b.left + MusicRegion.SIDEBAR_WIDTH - 4,
			b.bottom - MusicRegion.PLAYER_HEIGHT))
		{
			ctx.switchPage(Page.LOGIN);
			return true;
		}
		if(bottomPlayerBar.click(mouseX, mouseY, b))
			return true;
		if(ctx.queueVisible)
		{
			ctx.queueVisible = false;
			return true;
		}
		if(ctx.selectedPlaylist != null)
			return playlistDetailPage.click(mouseX, mouseY, b);
		return switch(ctx.page)
		{
			case HOME -> homePage.click(mouseX, mouseY, b);
			case SEARCH -> searchPage.click(mouseX, mouseY, b);
			case LIKE -> mouseY >= b.top + 26
				&& likedPage.click(mouseX, mouseY, b);
			case LOGIN -> loginPage.click(mouseX, mouseY, b);
		};
	}

	@Override
	public boolean mouseScrolled(double mouseX, double mouseY, double delta)
	{
		MusicContext.Bounds b = ctx.bounds();
		if(ctx.queueVisible && bottomPlayerBar.scrollQueue(delta, mouseX,
			mouseY, b))
			return true;
		if(ctx.detailMotion.get() > 0.01F)
			return playerDetailOverlay.scrollLyrics(mouseX, mouseY, delta, b);
		if(ctx.selectedPlaylist == null && ctx.page == Page.LOGIN)
			return false;
		int playlistTop = b.top + 110 - ctx.scroll;
		if(ctx.selectedPlaylist == null && ctx.page == Page.HOME
			&& MusicRegion.contains(mouseX, mouseY,
				b.left + MusicRegion.SIDEBAR_WIDTH + 8, playlistTop,
				b.right - 8, playlistTop + 64))
			return homePage.scrollPlaylist(delta, b);
		int contentHeight = contentHeight();
		int viewport = b.height() - MusicRegion.PLAYER_HEIGHT
			- (ctx.selectedPlaylist != null ? 36 : ctx.page == Page.SEARCH ? 38
				: ctx.page == Page.LIKE ? 28 : 0);
		int max = Math.max(0, contentHeight - viewport);
		ctx.scroll = Mth.clamp(ctx.scroll
			+ (delta > 0 ? -MusicRegion.SONG_HEIGHT
				: MusicRegion.SONG_HEIGHT), 0, max);
		if(ctx.page == Page.LIKE)
			likedPage.checkLoadMore(max);
		return true;
	}

	@Override
	public boolean mouseDragged(double mouseX, double mouseY, int button,
		double dragX, double dragY)
	{
		if(button == 0 && playerDetailOverlay.drag(mouseX, mouseY, button,
			ctx.bounds()))
			return true;
		if(button == 0 && bottomPlayerBar.drag(mouseX, mouseY, button,
			ctx.bounds()))
			return true;
		return super.mouseDragged(mouseX, mouseY, button, dragX, dragY);
	}

	@Override
	public boolean mouseReleased(double mouseX, double mouseY, int button)
	{
		if(button == 0 && (playerDetailOverlay.release(button)
			|| bottomPlayerBar.release(button)))
			return true;
		return super.mouseReleased(mouseX, mouseY, button);
	}

	@Override
	public boolean keyPressed(int keyCode, int scanCode, int modifiers)
	{
		if(keyCode == GLFW.GLFW_KEY_ESCAPE)
		{
			if(ctx.queueVisible)
				ctx.queueVisible = false;
			else if(ctx.detailVisible)
				ctx.detailVisible = false;
			else
				onClose();
			return true;
		}
		boolean cookieFocused = loginPage.isCookieFocused();
		boolean searchFocused = searchPage.hasInputFocus();
		boolean phoneMode = loginPage.isPhoneMode();
		if(!searchFocused && !cookieFocused && !phoneMode)
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
		if(cookieFocused && loginPage.keyPressed(keyCode, modifiers))
			return true;
		if(searchFocused && searchPage.keyPressed(keyCode))
			return true;
		if(phoneMode && loginPage.keyPressed(keyCode, modifiers))
			return true;
		return super.keyPressed(keyCode, scanCode, modifiers);
	}

	@Override
	public boolean charTyped(char codePoint, int modifiers)
	{
		if(loginPage.charTyped(codePoint))
			return true;
		if(searchPage.charTyped(codePoint))
			return true;
		return super.charTyped(codePoint, modifiers);
	}

	@Override
	public void onClose()
	{
		if(!closing)
		{
			loginPage.invalidateQr();
			closing = true;
		}
	}

	@Override
	public void removed()
	{
		ctx.images.close();
		PLAYER.removeListener(playerListener);
		super.removed();
	}

	@Override
	public boolean isPauseScreen()
	{
		return false;
	}

	private boolean hasAnyAccount()
	{
		return PLAYER.isLoggedIn()
			|| PLAYER.getAccountManager().isConnected(
				net.wurstclient.music.MusicProvider.QQ)
			|| PLAYER.getAccountManager().isConnected(
				net.wurstclient.music.MusicProvider.KUGOU);
	}

	private int contentHeight()
	{
		if(ctx.selectedPlaylist != null)
			return playlistDetailPage.contentHeight();
		return switch(ctx.page)
		{
			case HOME -> homePage.contentHeight();
			case SEARCH -> searchPage.contentHeight();
			case LIKE -> likedPage.contentHeight();
			case LOGIN -> 0;
		};
	}

	private void drawCover(GuiGraphics graphics, String url, int left, int top,
		int right, int bottom, float alpha)
	{
		if(right <= left || bottom <= top)
			return;
		NeteaseImageCache.Texture texture = ctx.images.get(url);
		if(texture == null)
			return;
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

	private void drawCenteredText(GuiGraphics graphics, String text, float size,
		int centerX, int y, int color, int maxWidth)
	{
		float scale = size / font.lineHeight;
		String shown = PingFangFont.trim(font, text,
			Math.max(1, (int)(maxWidth / Math.max(0.01F, scale))));
		graphics.pose().pushPose();
		graphics.pose().translate(centerX, y, 0);
		graphics.pose().scale(scale, scale, 1);
		graphics.drawString(font, PingFangFont.text(shown),
			-PingFangFont.width(font, shown) / 2, 0, color, false);
		graphics.pose().popPose();
	}
}

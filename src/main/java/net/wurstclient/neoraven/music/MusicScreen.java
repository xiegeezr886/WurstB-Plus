/* NeoRaven MusicScreen layout/interaction port; GPL-3.0. See MUSIC-NOTICE.txt. */
package net.wurstclient.neoraven.music;

import java.nio.file.Path;
import java.util.*;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.wurstclient.WurstClient;
import net.wurstclient.clickgui2.NavigatorStyle;
import net.wurstclient.clickgui2.OpaiBackground;
import net.wurstclient.clickgui2.animation.SmoothScroll;
import net.wurstclient.gui.MainMenuBackground;
import net.wurstclient.hud2.nanovg.NanoVGUI;

/** NeoRaven design canvas, screen fitting, controls and lyric panel adapted to Fabric. */
public final class MusicScreen extends Screen {
  private static final float W = MusicViewport.WIDTH,
      H = MusicViewport.HEIGHT,
      CT_X = 70,
      CT_Y = 56,
      CT_W = 750,
      CT_H = H - 140,
      BAR = H - 80;
  private static final int ACCENT = 0xFFB5BEF0;
  private final MusicPalette colors = new MusicPalette();
  private final OpaiBackground background = new OpaiBackground();
  private static final float CARD_RADIUS = 16;
  private final Screen parent;
  private final MusicManager manager = MusicManager.get();
  private final MusicCanvas ui = new MusicCanvas();
  private final MusicPageMotion pageMotion = new MusicPageMotion("推荐");
  private final MusicTransportMotion transportMotion = new MusicTransportMotion();
  private final SmoothScroll scroll = new SmoothScroll(10);
  private final Spring lyricScroll = new Spring(0);
  private final SmoothScroll railSelection = new SmoothScroll(22), playScale = new SmoothScroll(30);
  private float lyricToggle, volumeReveal;
  private int railIndex;
  private long playPressedAt;
  private float seekPreview;
  private final List<Hit> hits = new ArrayList<>();
  private final Map<String, Float> hoverValues = new HashMap<>();
  private final Set<String> visibleHovers = new HashSet<>();
  private float hoverBlend, scrollbarTop, scrollbarHeight, scrollbarThumb, scrollbarGrab;
  private EditBox search;
  private String query = "", page = "推荐", previousPage = "推荐", lyricReturnPage = "推荐";
  private float gx, gy, scale, mouseX, mouseY, maxScroll, clipTop = CT_Y, clipBottom = CT_Y + CT_H;
  private float progressPreview = -1, volumeX, volumeY;
  private float open, closeFrom = 1, originX, originY, originW, originH;
  private boolean closing, qrOpen, volumeOpen;
  private boolean drawingPage;
  private float playHover;
  private int dragging, lyricActive = -1;
  private float lyricOffset;
  private List<LyricLine> lyricSource = List.of();
  private float[] lyricAlphas = new float[0];
  private final Map<String, List<LyricLayout.Run>> lyricRuns = new HashMap<>();
  private long started, lastFrame, typedAt, manualLyricAt;
  private float cachedLyricScale = -1;

  private record Hit(float x, float y, float w, float h, Runnable action) {
    boolean contains(float mx, float my) {
      return mx >= x && mx < x + w && my >= y && my < y + h;
    }
  }

  public MusicScreen(Screen parent) {
    super(Component.literal("音乐播放器"));
    this.parent = parent;
    railSelection.snapTo(34);
    playScale.snapTo(1);
  }

  @Override
  protected void init() {
    manager.boot();
    lyricToggle = manager.lyricAnimation() ? 1 : 0;
    updateViewport();
    ui.x = gx;
    ui.y = gy;
    ui.scale = scale;
    search = new EditBox(font, 0, 0, 400, 20, Component.literal("搜索歌曲、歌手"));
    search.setMaxLength(120);
    search.setValue(query);
    search.setResponder(
        value -> {
          query = value;
          typedAt = System.currentTimeMillis();
        });
    addWidget(search);
    if (started == 0) {
      started = System.nanoTime();
      captureIsland();
    }
  }

  private void updateViewport() {
    var hax = WurstClient.INSTANCE.getHax();
    float multiplier = hax == null ? 1 : hax.musicPlayerHack.guiScale();
    var viewport = MusicViewport.fit(width, height, multiplier);
    scale = viewport.scale();
    gx = viewport.x();
    gy = viewport.y();
  }

  private void captureIsland() {
    originW = 240;
    originH = manager.current() == null ? 23 : 48;
    originX = width / 2F - originW / 2;
    originY = 12;
    var hud = WurstClient.INSTANCE.getHudManager();
    var island = hud == null ? null : hud.getModule("DynamicIsland");
    if (minecraft.level != null && island != null && island.isEnabled()) {
      originX = island.getX();
      originY = island.getY();
      originW = island.getWidth();
      originH = island.getHeight();
    }
  }

  @Override
  public boolean isPauseScreen() {
    return false;
  }

  @Override
  public void tick() {
    search.tick();
    if (search.isFocused() && typedAt > 0 && System.currentTimeMillis() - typedAt > 320)
      searchNow();
  }

  private void searchNow() {
    typedAt = 0;
    page("搜索");
    manager.search(query);
  }

  private void page(String target) {
    pageMotion.request(target);
    String category =
        target.equals("歌词") ? lyricReturnPage : target.equals("歌单") ? previousPage : target;
    if (category.equals("歌单")) category = previousPage;
    railIndex =
        switch (category) {
          case "搜索" -> 1;
          case "我的", "播放队列", "本地音乐" -> 2;
          case "设置" -> 3;
          default -> 0;
        };
    railSelection.setTarget(34 + railIndex * 56);
  }

  private void commitPage(String target) {
    page = target;
    scroll.snapTo(0);
    maxScroll = 0;
    lyricOffset = 0;
    lyricScroll.set(0);
    if (!target.equals("搜索")) search.setFocused(false);
  }

  @Override
  public void onClose() {
    if (closing) return;
    closing = true;
    closeFrom = open;
    started = System.nanoTime();
    captureIsland();
    manager.cancelQrLogin();
  }

  @Override
  public void removed() {
    manager.cancelQrLogin();
    manager.commitVolume();
    background.close();
  }

  @Override
  public void onFilesDrop(List<Path> paths) {
    manager.importLocal(paths);
    page("本地音乐");
  }

  @Override
  public boolean keyPressed(int key, int scan, int modifiers) {
    if (key == 256) {
      if (qrOpen) {
        qrOpen = false;
        manager.cancelQrLogin();
      } else if (page.equals("歌词")) page(lyricReturnPage);
      else if (Set.of("歌单", "播放队列", "本地音乐").contains(page)) page(previousPage);
      else onClose();
      return true;
    }
    if (closing) return true;
    if (search.isFocused()) {
      if (key == 257 || key == 335) {
        searchNow();
        return true;
      }
      return search.keyPressed(key, scan, modifiers);
    }
    if (key == 32) {
      togglePlayback();
      return true;
    }
    return super.keyPressed(key, scan, modifiers);
  }

  @Override
  public boolean charTyped(char c, int modifiers) {
    return !closing && search.isFocused() && search.charTyped(c, modifiers);
  }

  @Override
  public void render(GuiGraphics graphics, int mx, int my, float partialTicks) {
    long now = System.nanoTime();
    float dt = lastFrame == 0 ? 16 : Math.min(100, (now - lastFrame) / 1e6F);
    hoverBlend = 1 - (float) Math.exp(-dt / 100);
    visibleHovers.clear();
    lastFrame = now;
    float time = (now - started) / 1e6F;
    open =
        closing ? closeFrom * (1 - Ease.STANDARD.get(time / 280)) : Ease.EMPHASIZED.get(time / 420);
    if (closing && time >= 280) {
      minecraft.setScreen(parent);
      return;
    }
    updateViewport();
    mouseX = (mx - gx) / scale;
    mouseY = (my - gy) / scale;
    String nextPage = pageMotion.update(dt);
    if (nextPage != null) commitPage(nextPage);
    transportMotion.update(
        progressPreview >= 0 ? progressPreview : MusicView.progress(),
        manager.isPlaying(),
        dt,
        dragging == 1);
    playHover +=
        ((inside(417, BAR + 27, 56, 34) ? 1 : 0) - playHover) * (1 - (float) Math.exp(-dt / 110));
    railSelection.update();
    playScale.setTarget(now - playPressedAt < 75_000_000L ? .97F : 1);
    playScale.update();
    lyricToggle += ((manager.lyricAnimation() ? 1 : 0) - lyricToggle) * hoverBlend;
    volumeReveal += ((volumeOpen ? 1 : 0) - volumeReveal) * hoverBlend;
    scroll.update();
    updateLyrics(dt);
    hits.clear();
    background.render(graphics, partialTicks);
    if (minecraft.level == null)
      NanoVGUI.pass(graphics, () -> MainMenuBackground.render(width, height, 255));
    NanoVGUI.passSmooth(
        graphics,
        () -> {
          float sx = mix(originX, gx, open), sy = mix(originY, gy, open);
          float sw = mix(originW, W * scale, open), sh = mix(originH, H * scale, open);
          float radius = mix(Math.min(originH / 2, 12), MusicViewport.RADIUS * scale, open);
          NavigatorStyle.outerShadow(18, sx, sy, sw, sh, 30, radius + 3, radius);
          float railWidth = sw * 58 / W;
          // Clip adjacent surfaces. No opaque underlay or shadow is drawn behind the category rail.
          NanoVGUI.pushScissor(sx + railWidth, sy, sw - railWidth, sh);
          NanoVGUI.fillRounded(
              sx,
              sy,
              sx + sw,
              sy + sh,
              radius,
              page.equals("歌词") ? 0xFF000000 : NavigatorStyle.BACKGROUND);
          NanoVGUI.popScissor();
          NanoVGUI.pushScissor(sx, sy, railWidth, sh);
          NanoVGUI.fillRounded(
              sx,
              sy,
              sx + sw,
              sy + sh,
              radius,
              page.equals("歌词") ? 0xFF000000 : NavigatorStyle.SIDEBAR);
          NanoVGUI.popScissor();
          ui.x = sx;
          ui.y = sy - (1 - open) * 8;
          ui.scale = scale;
          ui.alpha = Math.max(0, Math.min(1, (open - .15F) / .65F));
          NanoVGUI.pushScissor(sx + 1, sy + 1, Math.max(0, sw - 2), Math.max(0, sh - 2));
          drawRail();
          float pageAlpha = ui.alpha, pageY = ui.y;
          drawingPage = true;
          drawHeader();
          ui.alpha *= pageMotion.alpha();
          ui.y += pageMotion.offset() * scale;
          clipTop = CT_Y;
          clipBottom = CT_Y + CT_H;
          ui.clip(CT_X, CT_Y, CT_W, CT_H);
          drawContent();
          ui.unclip();
          drawingPage = false;
          ui.alpha = pageAlpha;
          ui.y = pageY;
          drawMiniBar();
          if (qrOpen) drawQr();
          NanoVGUI.popScissor();
        });
    hoverValues.keySet().retainAll(visibleHovers);
  }

  private static float mix(float a, float b, float t) {
    return a + (b - a) * t;
  }

  private void drawRail() {
    String[] pages = {"推荐", "搜索", "我的", "设置"}, icons = {"\ue88a", "\ue8b6", "\ue7fd", "\ue8b8"};
    ui.rounded(10, railSelection.getCurrent() - 14, 38, 28, 14, ACCENT);
    for (int i = 0; i < pages.length; i++) {
      String target = pages[i];
      float top = 18 + i * 56, cy = top + 16;
      boolean selected = railIndex == i;
      float hover = hover("rail:" + target, inside(8, top, 42, 56));
      if (!selected && hover > .001F)
        ui.rounded(10, cy - 14, 38, 28, 14, Material.withAlpha(colors.onSurface, .08F * hover));
      ui.icon(
          icons[i],
          29,
          cy,
          19,
          Math.abs(cy - railSelection.getCurrent()) < 14
              ? colors.onSecondaryContainer
              : colors.onSurfaceVariant);
      ui.center(target, 29, top + 40, 9, selected ? colors.onSurface : colors.onSurfaceVariant);
      hit(
          8,
          top,
          42,
          56,
          () -> {
            page(target);
            qrOpen = false;
            if (target.equals("我的")) manager.loadMyPlaylists();
          });
    }
  }

  private boolean showSearch() {
    return !Set.of("歌词", "歌单", "播放队列", "本地音乐").contains(page);
  }

  private void drawHeader() {
    float headerAlpha = ui.alpha;
    ui.alpha *= pageMotion.alpha();
    ui.text(
        ui.trim(page.equals("歌单") ? manager.playlistTitle() : page, 24, showSearch() ? 96 : 600),
        78,
        28,
        24,
        colors.onSurface);
    ui.alpha = headerAlpha;
    if (showSearch()) {
      ui.rounded(
          198,
          8,
          400,
          40,
          20,
          search.isFocused() ? colors.surfaceContainerHighest : colors.surfaceContainerHigh);
      if (search.isFocused()) ui.outline(198, 8, 400, 40, 20, 2, ACCENT);
      ui.icon("\ue8b6", 218, 28, 17, colors.onSurfaceVariant);
      int caret = Math.min(query.length(), search.getCursorPosition()), start = 0;
      while (start < caret && ui.width(query.substring(start, caret), 15) > 342) start++;
      String shown = query.isEmpty() ? "搜索歌曲、歌手" : query.substring(start);
      ui.text(
          ui.trim(shown, 15, 348),
          236,
          28,
          15,
          query.isEmpty() ? colors.onSurfaceVariant : colors.onSurface);
      if (search.isFocused() && (System.currentTimeMillis() / 530) % 2 == 0)
        ui.rect(237 + ui.width(query.substring(start, caret), 15), 20, 1.6F, 16, ACCENT);
      hit(
          198,
          8,
          400,
          40,
          () -> {
            search.setFocused(true);
            setFocused(search);
            search.moveCursorToEnd();
          });
    }
    Profile p = manager.profile();
    ui.rounded(776, 8, 40, 40, 20, colors.surfaceContainerHighest);
    if (p != null && !p.avatarUrl.isEmpty())
      ui.cover(
          p.avatar(80), 776, 8, 40, 20, colors.surfaceContainerHighest, colors.onSurfaceVariant);
    else ui.icon("\ue7fd", 796, 28, 20, colors.onSurfaceVariant);
    hit(
        776,
        8,
        40,
        40,
        () -> {
          page("我的");
          if (manager.profile() == null) openQr();
        });
  }

  private void drawContent() {
    float y = CT_Y - scroll.getCurrent();
    switch (page) {
      case "推荐" -> {
        y = section("推荐歌单", y);
        if (manager.home().isEmpty()) {
          message(
              manager.homeLoading()
                  ? "正在加载…"
                  : manager.libraryError() != null ? manager.libraryError() : "暂无推荐（登录后可看到每日推荐）",
              y);
          y += 40;
          if (!manager.homeLoading()) {
            button("重新加载", 78, y, 104, 32, false, manager::loadHome, true);
            y += 44;
          }
        } else y = grid(manager.home(), y);
        if (!manager.myPlaylists().isEmpty())
          y = grid(manager.myPlaylists(), section("我的歌单", y + 8));
      }
      case "搜索" -> {
        if (manager.searching()) {
          message("正在搜索…", y);
          y += 40;
        } else if (manager.searchResults().isEmpty()) {
          message(
              manager.searchError() != null
                  ? manager.searchError()
                  : manager.searchQuery().isEmpty() ? "输入关键字搜索网易云音乐" : "没有找到结果",
              y);
          y += 40;
        } else {
          ui.text(
              "找到 " + manager.searchResults().size() + " 首  ·  " + manager.searchQuery(),
              78,
              y + 12,
              11,
              colors.onSurfaceVariant);
          y = songList(manager.searchResults(), y + 28);
        }
        if (!manager.searching() && manager.searchError() != null) {
          button("重试搜索", 78, y, 104, 32, false, this::searchNow, true);
          y += 44;
        }
      }
      case "我的" -> y = drawMine(y);
      case "设置" -> y = drawSettings(y);
      case "歌词" -> {
        drawLyrics();
        return;
      }
      default -> {
        transport("\ue5c4", 86, CT_Y + 16, 28, colors.onSurface, () -> page(previousPage));
        List<Song> songs =
            page.equals("歌单")
                ? manager.playlistTracks()
                : page.equals("播放队列") ? manager.queue() : manager.localTracks();
        ui.text(
            page.equals("歌单") && manager.playlistLoading() ? "加载中…" : songs.size() + " 首",
            118,
            CT_Y + 14,
            11,
            colors.onSurfaceVariant);
        clipTop = CT_Y + 38;
        ui.clip(CT_X, clipTop, CT_W, CT_H - 38);
        y = songList(songs, clipTop - scroll.getCurrent());
        ui.unclip();
      }
    }
    maxScroll = Math.max(0, y - (CT_Y - scroll.getCurrent()) - CT_H + 12);
    scroll.setTarget(Math.max(0, Math.min(scroll.getTarget(), maxScroll)));
    if (scroll.getCurrent() > maxScroll) scroll.snapTo(maxScroll);
    if (maxScroll > 0) {
      float vh = clipBottom - clipTop, thumb = Math.max(24, vh * vh / (vh + maxScroll));
      scrollbarTop = clipTop;
      scrollbarHeight = vh;
      scrollbarThumb = thumb;
      float thumbY =
          clipTop + (vh - thumb) * Math.max(0, Math.min(1, scroll.getCurrent() / maxScroll));
      float hover = hover("scrollbar", dragging == 3 || inside(810, clipTop, 14, vh));
      ui.rounded(
          816 - hover,
          thumbY,
          3 + 2 * hover,
          thumb,
          2.5F,
          Material.withAlpha(colors.onSurface, .25F + .35F * hover));
      hit(
          810,
          clipTop,
          14,
          vh,
          () -> {
            scrollbarGrab =
                mouseY >= thumbY && mouseY <= thumbY + thumb ? mouseY - thumbY : thumb / 2;
            dragging = 3;
            dragScrollbar(mouseY);
          });
    }
  }

  private float section(String title, float y) {
    ui.text(title, 78, y + 14, 12, colors.onSurfaceVariant);
    return y + 32;
  }

  private void message(String text, float y) {
    boolean loading = text.startsWith("正在");
    if (loading) {
      float phase = (System.nanoTime() % 1_200_000_000L) / 1_200_000_000F;
      for (int i = 0; i < 3; i++) {
        float pulse = .35F + .65F * (float) (.5 + .5 * Math.sin((phase - i * .18F) * Math.PI * 2));
        ui.rounded(78 + i * 7, y + 14, 4, 4, 2, Material.withAlpha(ACCENT, pulse));
      }
    }
    ui.text(
        ui.trim(text, 12, loading ? 692 : 720),
        loading ? 106 : 78,
        y + 16,
        12,
        colors.onSurfaceVariant);
  }

  private float grid(List<Playlist> lists, float y) {
    float cardW = (CT_W - 16 - 40) / 5, cardH = cardW + 38;
    for (int i = 0; i < lists.size(); i++) {
      Playlist p = lists.get(i);
      float x = 78 + (i % 5) * (cardW + 10), cy = y + (i / 5) * (cardH + 10);
      if (cy + cardH < clipTop || cy > clipBottom) continue;
      float hover = hover("playlist:" + p.id, contentInside(x, cy, cardW, cardH));
      ui.rounded(x, cy, cardW, cardH, CARD_RADIUS, colors.surfaceContainerHigh);
      ui.rounded(
          x, cy, cardW, cardH, CARD_RADIUS, Material.withAlpha(colors.onSurface, .035F * hover));
      ui.cover(
          p.cover(220),
          x + 5,
          cy + 5,
          cardW - 10,
          12,
          colors.primaryContainer,
          colors.onPrimaryContainer);
      ui.text(ui.trim(p.name, 12, cardW - 16), x + 8, cy + cardW + 2, 12, colors.onSurface);
      ui.text(p.trackCount + " 首", x + 8, cy + cardW + 20, 10, colors.onSurfaceVariant);
      contentHit(
          x,
          cy,
          cardW,
          cardH,
          () -> {
            previousPage = page;
            manager.openPlaylist(p.id, p.name);
            page("歌单");
          });
    }
    return y + ((lists.size() + 4) / 5) * (cardH + 10);
  }

  private float songList(List<Song> songs, float y) {
    if (songs.isEmpty()) {
      message(
          page.equals("本地音乐")
              ? "将 MP3 拖入窗口，或放入 wurst/music/local"
              : manager.playlistLoading()
                  ? "正在加载曲目…"
                  : page.equals("歌单") && manager.libraryError() != null
                      ? manager.libraryError()
                      : "这里还没有歌曲",
          y);
      return y + 44;
    }
    for (int i = 0; i < songs.size(); i++) {
      Song song = songs.get(i);
      float row = y + i * 64;
      if (row + 64 < clipTop || row > clipBottom) continue;
      boolean current = manager.current() != null && manager.current().id == song.id;
      float hover = hover("song:" + song.id, contentInside(78, row, 734, 58));
      if (current || hover > .001F)
        ui.rounded(
            78,
            row,
            734,
            58,
            14,
            Material.withAlpha(
                current ? ACCENT : colors.onSurface,
                current ? .08F + .025F * hover : .045F * hover));
      ui.cover(
          song.cover(120), 90, row + 6, 44, 10, colors.primaryContainer, colors.onPrimaryContainer);
      if (hover > .001F) {
        ui.rounded(90, row + 6, 44, 44, 10, Material.withAlpha(0xFF000000, .45F * hover));
        ui.icon(
            current && manager.isPlaying() ? "\ue034" : "\ue037",
            112,
            row + 28,
            21,
            Material.withAlpha(0xFFFFFFFF, hover));
      }
      ui.text(ui.trim(song.name, 14, 530), 150, row + 20, 14, current ? ACCENT : colors.onSurface);
      ui.text(
          ui.trim(
              song.album.isBlank()
                  ? song.artist
                  : song.artist.isBlank() ? song.album : song.artist + "  ·  " + song.album,
              12,
              530),
          150,
          row + 38,
          12,
          colors.onSurfaceVariant);
      ui.right(
          song.durationMs <= 0 ? "--:--" : MusicView.time(song.durationMs),
          796,
          row + 29,
          10,
          colors.onSurfaceVariant);
      contentHit(
          78,
          row,
          734,
          58,
          () -> {
            search.setFocused(false);
            if (manager.current() != null && manager.current().id == song.id) manager.togglePlay();
            else {
              manager.play(song, songs);
              lyricScroll.set(0);
            }
          });
    }
    return y + songs.size() * 64;
  }

  private float drawMine(float y) {
    Profile p = manager.profile();
    if (p == null) {
      ui.rounded(78, y, 734, 132, CARD_RADIUS, colors.surfaceContainerHigh);
      ui.text("登录网易云音乐", 98, y + 34, 20, colors.onSurface);
      ui.text("扫码登录后可同步歌单、查看每日推荐", 98, y + 62, 12, colors.onSurfaceVariant);
      button("扫码登录", 98, y + 86, 132, 36, true, this::openQr, true);
      y += 144;
    } else {
      ui.rounded(78, y, 734, 96, CARD_RADIUS, colors.surfaceContainerHigh);
      ui.cover(
          p.avatar(160), 94, y + 20, 56, 28, colors.primaryContainer, colors.onPrimaryContainer);
      ui.text(ui.trim(p.nickname, 20, 320), 166, y + 38, 20, colors.onSurface);
      ui.text(p.vipType > 0 ? "VIP 会员" : "普通用户", 166, y + 62, 12, colors.onSurfaceVariant);
      button("退出登录", 690, y + 30, 106, 36, false, manager::logout, true);
      y += 108;
    }
    // Extra features stay inside Mine, preserving the upstream four-item rail.
    button(
        "播放队列",
        78,
        y,
        104,
        32,
        false,
        () -> {
          previousPage = "我的";
          page("播放队列");
        },
        true);
    button(
        "本地音乐",
        192,
        y,
        104,
        32,
        false,
        () -> {
          previousPage = "我的";
          manager.loadLocal();
          page("本地音乐");
        },
        true);
    y = section("我的歌单", y + 44);
    if (manager.myPlaylists().isEmpty()) {
      message(manager.myPlaylistsLoading() ? "正在加载…" : "暂无歌单", y);
      return y + 40;
    }
    return grid(manager.myPlaylists(), y);
  }

  private float drawSettings(float y) {
    y = section("播放", y);
    ui.text("音质", 86, y + 20, 14, colors.onSurface);
    String[] labels = {"标准", "较高", "极高"}, values = {"standard", "higher", "exhigh"};
    for (int i = 0; i < 3; i++) {
      String value = values[i];
      button(
          labels[i],
          470 + i * 110,
          y + 3,
          100,
          34,
          manager.streamLevel().equals(value),
          () -> manager.setStreamLevel(value),
          true);
    }
    y += 56;
    ui.text("音频输出", 86, y + 20, 14, colors.onSurface);
    ui.right("Java Sound", 790, y + 20, 12, colors.onSurfaceVariant);
    y = section("外观", y + 56);
    ui.text("歌词字号", 86, y + 20, 14, colors.onSurface);
    button(
        "−",
        670,
        y + 3,
        34,
        34,
        false,
        () -> manager.setLyricScale(manager.lyricScale() - .1F),
        true);
    ui.center(Math.round(manager.lyricScale() * 100) + "%", 730, y + 20, 12, colors.onSurface);
    button(
        "+",
        756,
        y + 3,
        34,
        34,
        false,
        () -> manager.setLyricScale(manager.lyricScale() + .1F),
        true);
    y += 56;
    ui.text("歌词动效", 86, y + 18, 14, colors.onSurface);
    ui.text("逐字高亮与平滑滚动", 86, y + 38, 11, colors.onSurfaceVariant);
    float ty = y + 12;
    ui.rounded(738, ty, 52, 30, 15, colors.surfaceContainerHighest);
    ui.rounded(738, ty, 52, 30, 15, Material.withAlpha(ACCENT, lyricToggle));
    ui.rounded(742 + 20 * lyricToggle, ty + 4, 22, 22, 11, 0xFFFFFFFF);
    contentHit(738, ty, 52, 30, () -> manager.setLyricAnimation(!manager.lyricAnimation()));
    y += 64;
    ui.text("主题色", 86, y + 18, 14, colors.onSurface);
    ui.right("浅紫色", 790, y + 18, 12, ACCENT);
    return y + 50;
  }

  private void updateLyrics(float dt) {
    List<LyricLine> lines = manager.lyrics();
    if (cachedLyricScale != manager.lyricScale()) {
      cachedLyricScale = manager.lyricScale();
      lyricRuns.clear();
    }
    if (lines != lyricSource) {
      lyricSource = lines;
      lyricActive = -1;
      lyricOffset = 0;
      lyricScroll.set(0);
      lyricAlphas = new float[lines.size()];
      Arrays.fill(lyricAlphas, .34F);
      lyricRuns.clear();
    }
    int active = LyricLayout.indexAt(lines, manager.positionMs());
    if (active != lyricActive) {
      lyricActive = active;
      lyricOffset = 0;
    }
    if (System.currentTimeMillis() - manualLyricAt > 2600) lyricOffset = 0;
    lyricScroll.target(active < 0 ? 0 : active + lyricOffset);
    if (manager.lyricAnimation()) lyricScroll.update(dt);
    else lyricScroll.set(lyricScroll.target());
    float rate = 1 - (float) Math.exp(-Math.max(1, dt) / 110);
    for (int i = Math.max(0, active - 8); i < Math.min(lines.size(), active + 9); i++)
      lyricAlphas[i] += ((i == active ? 1 : .34F) - lyricAlphas[i]) * rate;
  }

  private void drawLyrics() {
    ui.rounded(CT_X, CT_Y, CT_W, CT_H, 22, 0xFF000000);
    Song song = manager.current();
    ui.text(
        ui.trim(song == null ? "未在播放" : song.name, 14, CT_W - 120), 94, 82, 14, colors.onSurface);
    ui.text(
        song == null ? "" : ui.trim(song.artist, 10, CT_W - 120),
        94,
        98,
        10,
        colors.onSurfaceVariant);
    transport("\ue5cd", 792, 82, 32, colors.onSurface, () -> page(lyricReturnPage));
    // Keep the same immutable list used to update focus; async loading may publish mid-frame.
    List<LyricLine> lines = lyricSource;
    if (lines.isEmpty()) {
      ui.center(
          manager.lyricsLoading() ? "正在加载歌词…" : "暂无歌词",
          445,
          CT_Y + CT_H / 2,
          12,
          colors.onSurfaceVariant);
      return;
    }
    float top = CT_Y + 56, bottom = CT_Y + CT_H - 8;
    float center = CT_Y + CT_H * .47F, pitch = 46 * manager.lyricScale();
    int anchor = Math.max(0, Math.min(lines.size() - 1, (int) lyricScroll.value()));
    float anchorGap =
        anchor + 1 < lines.size() ? (lyricHeight(anchor) + lyricHeight(anchor + 1)) / 2 : pitch;
    float anchorRow = center - (lyricScroll.value() - anchor) * anchorGap;
    ui.clip(CT_X + 4, top, CT_W - 8, bottom - top);
    for (int i = Math.max(0, (int) lyricScroll.value() - 4);
        i < Math.min(lines.size(), (int) lyricScroll.value() + 5);
        i++) {
      float distance = i - lyricScroll.value(), row = anchorRow;
      for (int j = Math.min(i, anchor); j < Math.max(i, anchor); j++)
        row += (i < anchor ? -1 : 1) * (lyricHeight(j) + lyricHeight(j + 1)) / 2;
      float fade =
          Math.max(
                  0,
                  Math.min(1 - Math.abs(distance) / 3.2F, LyricLayout.edgeFade(row, top, bottom)))
              * lyricAlphas[i];
      if (fade <= .01F) continue;
      drawLyricLine(lines.get(i), i, row, fade, i == lyricActive);
      final long at = lines.get(i).timeMs;
      float hitTop = Math.max(top, row - lyricHeight(i) / 2),
          hitBottom = Math.min(bottom, row + lyricHeight(i) / 2);
      if (hitBottom > hitTop)
        hit(
            CT_X + 12,
            hitTop,
            CT_W - 24,
            hitBottom - hitTop,
            () -> {
              manager.seek(at);
              lyricOffset = 0;
            });
    }
    ui.unclip();
  }

  private List<LyricLayout.Run> lyricRuns(int index, float size) {
    return lyricRuns.computeIfAbsent(
        index + ":" + size,
        ignored ->
            LyricLayout.wrap(lyricSource.get(index), CT_W - 48, text -> ui.width(text, size)));
  }

  private float lyricHeight(int index) {
    float size = 24 * manager.lyricScale();
    LyricLine line = lyricSource.get(index);
    float translation =
        line.translation.isEmpty() && line.romanisation.isEmpty() ? 0 : 20 * manager.lyricScale();
    return Math.max(
        46 * manager.lyricScale(), lyricRuns(index, size).size() * size * 1.3F + translation + 16);
  }

  private void drawLyricLine(LyricLine line, int index, float row, float alpha, boolean active) {
    float zoom = 1;
    float size = 24 * manager.lyricScale();
    List<LyricLayout.Run> runs = lyricRuns(index, size);
    float runHeight = size * 1.3F, blockHeight = runs.size() * runHeight;
    float left = CT_X + 24;
    int base =
        Material.withAlpha(
            active ? colors.onSurface : colors.onSurfaceVariant, alpha * (active ? .42F : .55F));
    for (int p = 0; p < runs.size(); p++) {
      var run = runs.get(p);
      float y = row + (-blockHeight / 2 + p * runHeight + runHeight / 2) * zoom;
      ui.text(run.text(), left, y, size * zoom, base);
      float sung =
          LyricLayout.sungWidth(line, run, manager.positionMs(), text -> ui.width(text, size))
              * zoom;
      if (active && !manager.lyricAnimation()) sung = ui.width(run.text(), size * zoom);
      if (sung > .5F) {
        ui.clip(left - 2, y - runHeight * zoom, sung + 2, runHeight * zoom * 2);
        ui.text(
            run.text(), left, y, size * zoom, active ? Material.withAlpha(ACCENT, alpha) : base);
        ui.unclip();
      }
    }
    String translated = line.translation.isEmpty() ? line.romanisation : line.translation;
    if (!translated.isEmpty())
      ui.text(
          ui.trim(translated, 12 * manager.lyricScale(), CT_W - 48),
          left,
          row + (blockHeight / 2 + 10) * zoom,
          12 * manager.lyricScale() * zoom,
          Material.withAlpha(colors.onSurfaceVariant, alpha * (active ? .85F : .55F)));
  }

  private void drawMiniBar() {
    ui.glass(62, BAR, 766, 76, 18, 0);
    Song song = manager.current();
    String status =
        manager.isLoadingTrack()
            ? "缓冲中… " + (int) (manager.downloadProgress() * 100) + "%"
            : manager.engine().isBuffering()
                ? "解码中…"
                : manager.playbackError() != null
                    ? manager.playbackError()
                    : manager.engine().error() != null ? "音频输出失败，请重试" : "";
    if (song != null) {
      float progress = progressPreview >= 0 ? progressPreview : MusicView.progress();
      ui.wave(
          76,
          BAR + 9,
          634,
          transportMotion.progress(),
          ACCENT,
          colors.secondaryContainer,
          transportMotion.playing(),
          transportMotion.phase());
      ui.right(
          MusicView.time(
                  progressPreview >= 0
                      ? (long) (manager.durationMs() * progress)
                      : manager.positionMs())
              + " / "
              + MusicView.time(manager.durationMs()),
          814,
          BAR + 9,
          10,
          colors.onSurfaceVariant);
      hit(
          76,
          BAR - 3,
          634,
          26,
          () -> {
            dragging = 1;
            adjust(mouseX);
          });
      boolean previewing = !qrOpen && (dragging == 1 || inside(76, BAR - 3, 634, 22));
      float previewAlpha = hover("seek-preview", previewing);
      if (previewing)
        seekPreview =
            dragging == 1 && progressPreview >= 0
                ? progressPreview
                : Math.max(0, Math.min(1, (mouseX - 76) / 634));
      if (previewAlpha > .01F) {
        float savedAlpha = ui.alpha;
        ui.alpha *= previewAlpha;
        float previewY = BAR - 18 + (1 - previewAlpha) * 2;
        float marker = 76 + seekPreview * 634;
        float bubble = Math.max(104, Math.min(682, marker));
        ui.rounded(marker - 3, BAR + 6, 6, 6, 3, ACCENT);
        ui.rounded(bubble - 28, previewY - 11, 56, 22, 11, colors.surfaceContainerHighest);
        ui.center(
            MusicView.time((long) (manager.durationMs() * seekPreview)),
            bubble,
            previewY,
            10,
            colors.onSurface);
        ui.alpha = savedAlpha;
      }
    }
    ui.cover(
        song == null ? "" : song.cover(160),
        76,
        BAR + 18,
        52,
        12,
        colors.surfaceContainerHighest,
        colors.onSurfaceVariant);
    hit(76, BAR + 18, 52, 52, this::toggleLyrics);
    ui.text(
        ui.trim(song == null ? "未在播放" : song.name, 14, 190),
        142,
        BAR + 34,
        14,
        song == null ? colors.onSurfaceVariant : colors.onSurface);
    ui.text(
        ui.trim(!status.isEmpty() ? status : song == null ? "网易云音乐" : song.artist, 12, 190),
        142,
        BAR + 54,
        12,
        manager.playbackError() != null || manager.engine().error() != null
            ? colors.error
            : colors.onSurfaceVariant);
    float cy = BAR + 44;
    transport("\ue045", 361, cy, 34, colors.onSurface, manager::previous);
    float pressScale = playScale.getCurrent();
    ui.rounded(
        445 - 28 * pressScale,
        cy - 17 * pressScale,
        56 * pressScale,
        34 * pressScale,
        17 * pressScale,
        ACCENT);
    ui.rounded(
        445 - 28 * pressScale,
        cy - 17 * pressScale,
        56 * pressScale,
        34 * pressScale,
        17 * pressScale,
        Material.withAlpha(0xFFFFFFFF, playHover * .06F));
    float playing = transportMotion.playing();
    ui.icon(
        "\ue037",
        445,
        cy,
        (17 + 3 * (1 - playing)) * pressScale,
        Material.withAlpha(0xFFFFFFFF, 1 - playing));
    ui.icon(
        "\ue034",
        445,
        cy,
        (17 + 3 * playing) * pressScale,
        Material.withAlpha(0xFFFFFFFF, playing));
    hit(417, cy - 17, 56, 34, this::togglePlayback);
    transport("\ue044", 529, cy, 34, colors.onSurface, manager::next);
    transport("\ue8d2", 794, cy, 30, colors.onSurface, this::toggleLyrics);
    transport(
        manager.repeat() == MusicManager.Repeat.ONE ? "\ue041" : "\ue040",
        752,
        cy,
        30,
        manager.repeat() == MusicManager.Repeat.OFF ? colors.onSurfaceVariant : ACCENT,
        manager::cycleRepeat);
    transport(
        "\ue043",
        710,
        cy,
        30,
        manager.shuffle() ? ACCENT : colors.onSurfaceVariant,
        manager::toggleShuffle);
    transport(
        manager.volume() <= .001F ? "\ue04f" : "\ue050",
        668,
        cy,
        30,
        volumeOpen ? ACCENT : colors.onSurfaceVariant,
        () -> volumeOpen = !volumeOpen);
    if (volumeReveal > .01F) {
      float volumeAlpha = ui.alpha;
      ui.alpha *= volumeReveal;
      volumeX = 556;
      volumeY = cy;
      ui.rounded(volumeX, volumeY - 2, 92, 4, 2, colors.surfaceContainerHighest);
      ui.rounded(volumeX, volumeY - 2, Math.max(4, 92 * manager.volume()), 4, 2, ACCENT);
      ui.rounded(volumeX + 92 * manager.volume() - 6, volumeY - 6, 12, 12, 6, ACCENT);
      ui.center(
          Math.round(manager.volume() * 100) + "%",
          volumeX + 46,
          volumeY + 15,
          10,
          colors.onSurfaceVariant);
      ui.alpha = volumeAlpha;
      if (volumeOpen)
        hit(
            volumeX,
            volumeY - 14,
            92,
            28,
            () -> {
              dragging = 2;
              adjust(mouseX);
            });
    }
  }

  private void toggleLyrics() {
    if (manager.current() == null) return;
    if (page.equals("歌词")) page(lyricReturnPage);
    else {
      lyricReturnPage = page;
      page("歌词");
    }
  }

  private void togglePlayback() {
    playPressedAt = System.nanoTime();
    manager.togglePlay();
  }

  private void openQr() {
    qrOpen = true;
    search.setFocused(false);
    manager.startQrLogin();
  }

  private void drawQr() {
    hits.clear();
    float y = (H - 420) / 2;
    ui.rect(0, 0, W, H, 0x8C000000);
    ui.rounded(250, y, 340, 420, 24, colors.surfaceContainerHigh);
    ui.center("扫码登录网易云", 420, y + 30, 20, colors.onSurface);
    transport(
        "\ue5cd",
        562,
        y + 26,
        28,
        colors.onSurface,
        () -> {
          qrOpen = false;
          manager.cancelQrLogin();
        });
    ui.rounded(300, y + 60, 240, 240, 16, 0xFFFFFFFF);
    boolean[][] matrix = manager.qrMatrix();
    if (matrix != null) {
      float step = 220F / matrix.length;
      for (int row = 0; row < matrix.length; row++)
        for (int col = 0; col < matrix[row].length; col++)
          if (matrix[row][col])
            ui.rect(310 + col * step, y + 70 + row * step, step + .01F, step + .01F, 0xFF000000);
    }
    String state =
        switch (manager.qrState()) {
          case WAITING -> "打开网易云音乐 App 扫描二维码";
          case SCANNED -> "已扫码，请在手机上确认登录";
          case SUCCESS -> "登录成功";
          case EXPIRED -> "二维码已过期，请刷新";
          case ERROR -> manager.qrError() == null ? "登录失败" : manager.qrError();
          default -> "正在获取二维码…";
        };
    ui.center(ui.trim(state, 12, 312), 420, y + 320, 12, colors.onSurfaceVariant);
    ui.center("登录信息仅保存在本地 wurst/music/", 420, y + 340, 10, colors.onSurfaceVariant);
    if (manager.qrState() == MusicManager.QrState.ERROR
        || manager.qrState() == MusicManager.QrState.EXPIRED)
      button("刷新二维码", 360, y + 368, 120, 36, true, manager::startQrLogin, false);
  }

  private void button(
      String label,
      float x,
      float y,
      float w,
      float h,
      boolean filled,
      Runnable action,
      boolean content) {
    float hover =
        hover(
            "button:" + page + ":" + label + ":" + x,
            content ? contentInside(x, y, w, h) : inside(x, y, w, h));
    ui.rounded(
        x,
        y,
        w,
        h,
        h / 2,
        filled ? ACCENT : Material.withAlpha(colors.onSurface, .04F + .06F * hover));
    if (filled) ui.rounded(x, y, w, h, h / 2, Material.withAlpha(0xFFFFFFFF, .12F * hover));
    ui.center(label, x + w / 2, y + h / 2, 11, filled ? colors.onPrimary : colors.onSurface);
    if (content) contentHit(x, y, w, h, action);
    else hit(x, y, w, h, action);
  }

  private void transport(String icon, float cx, float cy, float size, int color, Runnable action) {
    float hover =
        hover("transport:" + cx + ":" + cy, inside(cx - size / 2, cy - size / 2, size, size));
    if (hover > .001F)
      ui.rounded(
          cx - size / 2 - 2,
          cy - size / 2 - 2,
          size + 4,
          size + 4,
          size / 2 + 2,
          Material.withAlpha(colors.onSurface, .08F * hover));
    ui.icon(icon, cx, cy, size * .62F, color);
    hit(cx - size / 2, cy - size / 2, size, size, action);
  }

  private boolean inside(float x, float y, float w, float h) {
    return mouseX >= x && mouseX < x + w && mouseY >= y && mouseY < y + h;
  }

  private boolean contentInside(float x, float y, float w, float h) {
    return pageMotion.ready()
        && !qrOpen
        && mouseY >= clipTop
        && mouseY < clipBottom
        && inside(x, y, w, h);
  }

  private float hover(String key, boolean active) {
    visibleHovers.add(key);
    active &= !closing && (!qrOpen || !drawingPage) && (!drawingPage || pageMotion.ready());
    float value = hoverValues.getOrDefault(key, 0F);
    value += ((active ? 1 : 0) - value) * hoverBlend;
    hoverValues.put(key, value);
    return value;
  }

  private void dragScrollbar(float y) {
    float travel = scrollbarHeight - scrollbarThumb;
    if (travel > 0)
      scroll.snapTo(
          Math.max(
              0, Math.min(maxScroll, (y - scrollbarTop - scrollbarGrab) / travel * maxScroll)));
  }

  private void hit(float x, float y, float w, float h, Runnable action) {
    if (drawingPage && !pageMotion.ready()) return;
    hits.add(new Hit(x, y, w, h, action));
  }

  private void contentHit(float x, float y, float w, float h, Runnable action) {
    float top = Math.max(y, clipTop), bottom = Math.min(y + h, clipBottom);
    if (bottom > top) hit(x, top, w, bottom - top, action);
  }

  @Override
  public boolean mouseClicked(double x, double y, int button) {
    if (button != 0 || closing || open < .995F) return false;
    mouseX = (float) (x - gx) / scale;
    mouseY = (float) (y - gy) / scale;
    for (int i = hits.size() - 1; i >= 0; i--)
      if (hits.get(i).contains(mouseX, mouseY)) {
        hits.get(i).action.run();
        return true;
      }
    search.setFocused(false);
    return qrOpen;
  }

  private void adjust(float x) {
    if (dragging == 1) progressPreview = Math.max(0, Math.min(1, (x - 76) / 634));
    else if (dragging == 2) manager.setVolume(Math.max(0, Math.min(1, (x - volumeX) / 92)));
  }

  @Override
  public boolean mouseDragged(double x, double y, int button, double dx, double dy) {
    if (dragging != 0) {
      if (dragging == 3) dragScrollbar((float) (y - gy) / scale);
      else adjust((float) (x - gx) / scale);
      return true;
    }
    return false;
  }

  @Override
  public boolean mouseReleased(double x, double y, int button) {
    if (dragging == 0) return false;
    if (dragging == 3) dragScrollbar((float) (y - gy) / scale);
    else adjust((float) (x - gx) / scale);
    if (dragging == 1) manager.seek((long) (manager.durationMs() * progressPreview));
    else if (dragging == 2) manager.commitVolume();
    dragging = 0;
    progressPreview = -1;
    return true;
  }

  @Override
  public boolean mouseScrolled(double x, double y, double amount) {
    if (closing || qrOpen || !pageMotion.ready()) return false;
    float localX = (float) (x - gx) / scale, localY = (float) (y - gy) / scale;
    if (localX < CT_X || localX > CT_X + CT_W || localY < CT_Y || localY > CT_Y + CT_H)
      return false;
    if (page.equals("歌词")) {
      lyricOffset = Math.max(-3, Math.min(3, lyricOffset - (float) amount));
      manualLyricAt = System.currentTimeMillis();
    } else
      scroll.setTarget((float) Math.max(0, Math.min(maxScroll, scroll.getTarget() - amount * 48)));
    return true;
  }
}

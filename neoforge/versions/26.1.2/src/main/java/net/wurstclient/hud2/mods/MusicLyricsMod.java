/* OpaiBounce MusicLyrics HUD adaptation; see META-INF/licenses/opai-lyrics. */
package net.wurstclient.hud2.mods;

import java.awt.Color;
import java.util.List;
import net.wurstclient.hud2.HUDMod;
import net.wurstclient.hud2.music.OpaiLyrics;
import net.wurstclient.hud2.nanovg.HudFonts;
import net.wurstclient.neoraven.music.LyricLine;
import net.wurstclient.neoraven.music.MusicManager;
import net.wurstclient.neoraven.music.Song;
import net.wurstclient.settings.CheckboxSetting;

public final class MusicLyricsMod extends HUDMod {
  private static final int WIDTH = 450, HEIGHT = 120;
  private static final List<LyricLine> PREVIEW =
      List.of(
          sample(0, "Opai HUD Preview", "音乐预览"),
          sample(1000, "Let the music carry you", "让音乐带你前行"),
          sample(2000, "A new day is on the way", "新的一天即将到来"));
  private final CheckboxSetting translation =
      new CheckboxSetting("Lyric translation", "Shows translated lyrics when available.", true);
  private final OpaiLyrics motion = new OpaiLyrics();
  private Song song;
  private long lastFrame;
  private boolean placeOnFirstFrame = true;

  public MusicLyricsMod() {
    super("MusicLyrics", "Music Lyrics", "Opai scrolling lyrics for the music player.");
    setTranslationKey("music_lyrics");
    setSize(WIDTH, HEIGHT);
    // CSS pixels to HUD logical units; the editor can freely resize this widget.
    setDefaultScale(.5F);
    int width = MC.getWindow() == null ? 960 : MC.getWindow().getGuiScaledWidth();
    int height = MC.getWindow() == null ? 540 : MC.getWindow().getGuiScaledHeight();
    setDefaultPosition(Math.max(0, width - 245), height / 2);
    setDefaultEnabled(true);
    addSetting(translation);
  }

  @Override
  public void setX(int x) {
    placeOnFirstFrame = false;
    super.setX(x);
  }

  @Override
  public void setY(int y) {
    placeOnFirstFrame = false;
    super.setY(y);
  }

  @Override
  public void clampToScreen(int width, int height) {
    if (placeOnFirstFrame) {
      setDefaultPosition(Math.max(0, width - getWidth() - 20), height / 2);
      placeOnFirstFrame = false;
    }
    super.clampToScreen(width, height);
  }

  private static LyricLine sample(long time, String text, String translated) {
    LyricLine line = LyricLine.plain(time, text);
    line.translation = translated;
    return line;
  }

  @Override
  protected void onDisable() {
    motion.clear();
    lastFrame = 0;
  }

  @Override
  protected void draw() {
    MusicManager music = MusicManager.get();
    Song current = music.current();
    if (current == null && !isEditing()) {
      motion.clear();
      song = null;
      return;
    }
    long now = System.nanoTime() / 1_000_000;
    if (current != song || now - lastFrame > 1000) motion.clear();
    song = current;
    lastFrame = now;
    List<LyricLine> lines = current == null ? PREVIEW : music.lyrics();
    long position = current == null ? 1000 : music.positionMs();
    markDrawn();
    if (lines.isEmpty()) {
      // Opai falls back to the track title, including instrumental tracks.
      text(current == null ? "歌词预览" : current.name, 48, 14, 1);
      return;
    }
    for (OpaiLyrics.Row row : motion.update(lines, position, now)) {
      float y = row.y(now), alpha = row.alpha(now);
      text(row.line.text.isBlank() ? "…" : row.line.text, y, 14, alpha);
      if (translation.isChecked() && !row.line.translation.isBlank())
        text(row.line.translation, y + 18, 9, alpha);
    }
  }

  private void text(String value, float y, float size, float alpha) {
    if (y + size + 2 <= 0 || y >= HEIGHT || alpha <= 0) return;
    String display = trim(value, size);
    float x = WIDTH - getTextWidth(display, size, HudFonts.REGULAR);
    // Render the flat middle once. Only text touching a fading edge needs small
    // clipped bands, preserving the existing antialiased text backend without a framebuffer.
    float top = Math.max(0, y - 2), end = Math.min(HEIGHT, y + size + 3);
    while (top < end) {
      float bottom = top < 18 ? Math.min(18, top + 2) : top < 105.6F ? 105.6F : top + 2;
      bottom = Math.min(end, bottom);
      float opacity = alpha * OpaiLyrics.mask((top + bottom) / 2);
      save();
      try {
        scissor(0, top, WIDTH, bottom - top);
        drawText(
            display,
            x,
            y + 1,
            size,
            HudFonts.REGULAR,
            new Color(0, 0, 0, Math.round(opacity * 187)));
        drawText(
            display,
            x,
            y,
            size,
            HudFonts.REGULAR,
            new Color(255, 255, 255, Math.round(opacity * 255)));
      } finally {
        restore();
      }
      top = bottom;
    }
  }

  private String trim(String text, float size) {
    if (getTextWidth(text, size, HudFonts.REGULAR) <= WIDTH) return text;
    int low = 0, high = text.length();
    while (low < high) {
      int mid = (low + high + 1) >>> 1;
      if (getTextWidth(text.substring(0, mid) + "…", size, HudFonts.REGULAR) <= WIDTH) low = mid;
      else high = mid - 1;
    }
    if (low > 0 && Character.isHighSurrogate(text.charAt(low - 1))) low--;
    return text.substring(0, low) + "…";
  }
}

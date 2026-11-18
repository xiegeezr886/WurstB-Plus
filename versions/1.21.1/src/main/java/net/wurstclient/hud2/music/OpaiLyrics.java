/* OpaiBounce LyricScroller/musicMotion adaptation; see META-INF/licenses/opai-lyrics. */
package net.wurstclient.hud2.music;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import net.wurstclient.neoraven.music.Ease;
import net.wurstclient.neoraven.music.LyricLine;
import net.wurstclient.neoraven.music.MusicView;

/** Keeps only Opai's five cue rows; updates animation targets only when the cue changes. */
public final class OpaiLyrics {
  private final LinkedHashMap<Integer, Row> rows = new LinkedHashMap<>();
  private List<LyricLine> source;
  private int cue = Integer.MIN_VALUE;

  public void clear() {
    source = null;
    cue = Integer.MIN_VALUE;
    rows.clear();
  }

  public Collection<Row> update(List<LyricLine> lines, long position, long now) {
    if (source != lines) {
      clear();
      source = lines;
    }
    int next = MusicView.lyricIndex(lines, position);
    if (next == cue) return rows.values();
    cue = next;
    int first = Math.max(0, cue - 2), end = Math.min(lines.size(), Math.max(0, cue) + 3);
    rows.keySet().removeIf(index -> index < first || index >= end);
    for (int i = first; i < end; i++) {
      float y = 48 + (i - Math.max(0, cue)) * 38;
      float opacity = i == cue ? 1 : Math.abs(i - cue) == 1 ? .35F : .08F;
      Row row = rows.get(i);
      if (row == null) rows.put(i, new Row(lines.get(i), y, opacity, now));
      else row.move(y, opacity, now);
    }
    return rows.values();
  }

  /** CSS mask-image: linear-gradient(transparent, black 15%, black 88%, transparent). */
  public static float mask(float y) {
    return Math.max(0, Math.min(1, Math.min(y / 18F, (120 - y) / 14.4F)));
  }

  public static final class Row {
    public final LyricLine line;
    private float fromY, toY, fromAlpha, toAlpha;
    private long started;

    private Row(LyricLine line, float y, float alpha, long now) {
      this.line = line;
      fromY = toY = y;
      fromAlpha = toAlpha = alpha;
      started = now;
    }

    private float amount(long now) {
      return Ease.CSS_EASE_OUT.get((now - started) / 330F);
    }

    public float y(long now) {
      return fromY + (toY - fromY) * amount(now);
    }

    public float alpha(long now) {
      return fromAlpha + (toAlpha - fromAlpha) * amount(now);
    }

    private void move(float y, float alpha, long now) {
      if (y == toY && alpha == toAlpha) return;
      fromY = y(now);
      fromAlpha = alpha(now);
      toY = y;
      toAlpha = alpha;
      started = now;
    }
  }
}

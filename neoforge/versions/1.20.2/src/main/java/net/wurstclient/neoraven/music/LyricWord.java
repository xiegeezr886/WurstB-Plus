/* Music player adapted from NeoRaven (GPL-3.0). See META-INF/licenses/neoraven/MUSIC-NOTICE.txt. */
package net.wurstclient.neoraven.music;

/** One sung fragment inside a lyric line (a Chinese character or a Latin word). */
public final class LyricWord {

  public final long startMs;
  public final long endMs;
  public final String text;

  /** Filled in by the view once the font is known; kept here so layout is measured once. */
  public float x;

  public float width;

  public LyricWord(long startMs, long endMs, String text) {
    this.startMs = startMs;
    this.endMs = Math.max(endMs, startMs);
    this.text = text == null ? "" : text;
  }

  public long duration() {
    return Math.max(1L, endMs - startMs);
  }

  /** 0..1 sung fraction of this fragment at {@code positionMs}. */
  public float progress(long positionMs) {
    if (positionMs <= startMs) {
      return 0.0F;
    }
    if (positionMs >= endMs) {
      return 1.0F;
    }
    return (float) (positionMs - startMs) / (float) duration();
  }
}

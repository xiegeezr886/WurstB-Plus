/* Music player adapted from NeoRaven (GPL-3.0). See META-INF/licenses/neoraven/MUSIC-NOTICE.txt. */
package net.wurstclient.neoraven.music;

import java.util.ArrayList;
import java.util.List;

/** One displayed lyric line: text, optional translation and the word timings used for the sweep. */
public final class LyricLine {

  public final long timeMs;
  public long endMs;
  public final String text;
  public String translation = "";
  public String romanisation = "";
  public final List<LyricWord> words;

  public LyricLine(long timeMs, long endMs, String text, List<LyricWord> words) {
    this.timeMs = timeMs;
    this.endMs = Math.max(endMs, timeMs + 400L);
    this.text = text == null ? "" : text;
    this.words = words == null ? new ArrayList<LyricWord>() : words;
  }

  public static LyricLine plain(long timeMs, String text) {
    return new LyricLine(timeMs, timeMs + 400L, text, new ArrayList<LyricWord>());
  }

  /** 0..1 progress through the whole line, used for the line-level glow. */
  public float progress(long positionMs) {
    long duration = Math.max(1L, endMs - timeMs);
    if (positionMs <= timeMs) {
      return 0.0F;
    }
    if (positionMs >= endMs) {
      return 1.0F;
    }
    return (float) (positionMs - timeMs) / (float) duration;
  }

  /** Index of the fragment being sung, or -1 before the line starts. */
  public int activeWord(long positionMs) {
    for (int i = 0; i < words.size(); i++) {
      LyricWord word = words.get(i);
      if (positionMs < word.endMs) {
        return positionMs < word.startMs ? -1 : i;
      }
    }
    return words.isEmpty() ? -1 : words.size() - 1;
  }

  public boolean hasWords() {
    return !words.isEmpty();
  }
}

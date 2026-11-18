/* NeoRaven LyricPanel wrapping/focus adaptation; GPL-3.0. See MUSIC-NOTICE.txt. */
package net.wurstclient.neoraven.music;

import java.util.ArrayList;
import java.util.List;
import java.util.function.ToDoubleFunction;

public final class LyricLayout {
  private LyricLayout() {}

  public record Run(String text, int firstWord, int endWord) {}

  /**
   * Original panel follows the first line whose end has not passed, including instrumental gaps.
   */
  public static int indexAt(List<LyricLine> lines, long position) {
    for (int i = 0; i < lines.size(); i++) if (position < lines.get(i).endMs) return i;
    return lines.size() - 1;
  }

  public static List<Run> wrap(LyricLine line, float width, ToDoubleFunction<String> measure) {
    if (measure.applyAsDouble(line.text) <= width || line.words.isEmpty())
      return List.of(new Run(line.text, 0, line.words.size()));
    List<Run> runs = new ArrayList<>();
    StringBuilder current = new StringBuilder();
    int first = 0;
    for (int i = 0; i < line.words.size(); i++) {
      String word = line.words.get(i).text;
      if (current.length() > 0 && measure.applyAsDouble(current.toString() + word) > width) {
        runs.add(new Run(current.toString(), first, i));
        current.setLength(0);
        first = i;
      }
      current.append(word);
    }
    if (current.length() > 0) runs.add(new Run(current.toString(), first, line.words.size()));
    return runs;
  }

  public static float sungWidth(
      LyricLine line, Run run, long position, ToDoubleFunction<String> measure) {
    float width = 0;
    for (int i = run.firstWord(); i < run.endWord(); i++) {
      LyricWord word = line.words.get(i);
      width += (float) measure.applyAsDouble(word.text) * word.progress(position);
      if (position < word.endMs) break;
    }
    return Math.min(width, (float) measure.applyAsDouble(run.text()));
  }

  public static float edgeFade(float y, float top, float bottom) {
    float t = Math.max(0, Math.min(1, Math.min((y - top) / 30, (bottom - y) / 30)));
    return t * t * (3 - 2 * t);
  }
}

/* Music player adapted from NeoRaven (GPL-3.0). See META-INF/licenses/neoraven/MUSIC-NOTICE.txt. */
package net.wurstclient.neoraven.music;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Turns the NetEase lyric payload into timed lines.
 *
 * <p>Two formats matter. {@code yrc} carries per-fragment timing (NetEase's karaoke format, one
 * timing pair per Chinese character or Latin word), and plain {@code lrc} only marks the start of
 * each line. When only the line timing exists the parser still produces fragments: the line's
 * duration is split over the characters (CJK) or words (Latin) by length, which is what makes the
 * sweep effect work for older tracks instead of snapping whole lines at once.
 */
public final class LyricsParser {

  private static final Pattern YRC_LINE = Pattern.compile("^\\[(\\d+),(\\d+)(?:,-?\\d+)?\\]");
  private static final Pattern YRC_WORD = Pattern.compile("\\((\\d+),(\\d+)(?:,-?\\d+)?\\)([^(]*)");
  private static final Pattern LRC_TIME =
      Pattern.compile("\\[(\\d{1,3}):(\\d{1,2})(?:[.:](\\d{1,3}))?\\]");
  private static final Pattern OFFSET =
      Pattern.compile("\\[offset:\\s*([-+]?\\d+)\\s*\\]", Pattern.CASE_INSENSITIVE);

  private LyricsParser() {}

  /** Parses whichever source is available, preferring the word-level one. */
  public static List<LyricLine> parse(
      String yrc, String lrc, String translation, String romanisation) {
    List<LyricLine> lines = parseYrc(yrc);
    if (lines.isEmpty()) {
      long offset = offsetOf(lrc);
      lines = parseLrc(lrc, offset);
    }
    if (lines.isEmpty()) {
      return Collections.emptyList();
    }
    mergeAux(lines, parseLrc(translation, 0L), true);
    mergeAux(lines, parseLrc(romanisation, 0L), false);
    finish(lines);
    return lines;
  }

  // ------------------------------------------------------------------ //
  // yrc
  // ------------------------------------------------------------------ //

  private static List<LyricLine> parseYrc(String yrc) {
    if (yrc == null || yrc.isEmpty()) {
      return new ArrayList<LyricLine>();
    }
    List<LyricLine> lines = new ArrayList<LyricLine>();
    for (String raw : yrc.split("\n")) {
      String line = raw.trim();
      if (line.isEmpty()) {
        continue;
      }
      Matcher header = YRC_LINE.matcher(line);
      if (!header.find()) {
        continue;
      }
      long start;
      long duration;
      try {
        start = Long.parseLong(header.group(1));
        duration = Long.parseLong(header.group(2));
      } catch (NumberFormatException e) {
        continue;
      }
      List<LyricWord> words = new ArrayList<LyricWord>();
      StringBuilder text = new StringBuilder();
      Matcher word = YRC_WORD.matcher(line.substring(header.end()));
      while (word.find()) {
        String part = word.group(3);
        if (part.isEmpty()) {
          continue;
        }
        long wordStart;
        long wordDuration;
        try {
          wordStart = Long.parseLong(word.group(1));
          wordDuration = Long.parseLong(word.group(2));
        } catch (NumberFormatException e) {
          continue;
        }
        words.add(new LyricWord(wordStart, wordStart + Math.max(1L, wordDuration), part));
        text.append(part);
      }
      String plain = text.toString().trim();
      if (plain.isEmpty() && words.isEmpty()) {
        continue;
      }
      if (plain.isEmpty()) {
        plain = joinWords(words);
      }
      lines.add(
          new LyricLine(start, duration > 0L ? start + duration : start + 400L, plain, words));
    }
    return lines;
  }

  // ------------------------------------------------------------------ //
  // lrc
  // ------------------------------------------------------------------ //

  private static long offsetOf(String lrc) {
    if (lrc == null) {
      return 0L;
    }
    Matcher matcher = OFFSET.matcher(lrc);
    if (matcher.find()) {
      try {
        return Long.parseLong(matcher.group(1));
      } catch (NumberFormatException ignored) {
      }
    }
    return 0L;
  }

  private static List<LyricLine> parseLrc(String lrc, long offset) {
    List<LyricLine> lines = new ArrayList<LyricLine>();
    if (lrc == null || lrc.isEmpty()) {
      return lines;
    }
    for (String raw : lrc.split("\n")) {
      String line = raw.trim();
      if (line.isEmpty()) {
        continue;
      }
      Matcher times = LRC_TIME.matcher(line);
      List<Long> stamps = new ArrayList<Long>();
      int end = 0;
      while (times.find()) {
        long minutes;
        long seconds;
        String fraction = times.group(3);
        try {
          minutes = Long.parseLong(times.group(1));
          seconds = Long.parseLong(times.group(2));
        } catch (NumberFormatException e) {
          break;
        }
        long millis = 0L;
        if (fraction != null) {
          millis =
              Long.parseLong(fraction)
                  * (fraction.length() == 1 ? 100L : fraction.length() == 2 ? 10L : 1L);
        }
        stamps.add(minutes * 60000L + seconds * 1000L + millis + offset);
        end = times.end();
      }
      if (stamps.isEmpty()) {
        continue;
      }
      String text = line.substring(end).trim();
      if (text.isEmpty()) {
        continue;
      }
      for (Long stamp : stamps) {
        lines.add(LyricLine.plain(stamp, text));
      }
    }
    lines.sort(
        new Comparator<LyricLine>() {
          @Override
          public int compare(LyricLine a, LyricLine b) {
            return Long.compare(a.timeMs, b.timeMs);
          }
        });
    return lines;
  }

  // ------------------------------------------------------------------ //
  // Post-processing
  // ------------------------------------------------------------------ //

  private static void mergeAux(
      List<LyricLine> lines, List<LyricLine> auxiliary, boolean translation) {
    if (lines.isEmpty() || auxiliary.isEmpty()) {
      return;
    }
    Map<Long, String> byTime = new HashMap<Long, String>();
    for (LyricLine line : auxiliary) {
      if (!line.text.isEmpty()) {
        byTime.put(line.timeMs, line.text);
      }
    }
    for (LyricLine line : lines) {
      String text = byTime.get(line.timeMs);
      if (text == null && byTime.size() == 1) {
        text = byTime.values().iterator().next();
      }
      if (text == null || text.isEmpty() || text.equals(line.text)) {
        continue;
      }
      if (translation) {
        line.translation = text;
      } else {
        line.romanisation = text;
      }
    }
  }

  /**
   * Fills in the line end times and synthesises fragment timings for lines that only have a start
   * stamp {@code duration}.
   */
  private static void finish(List<LyricLine> lines) {
    for (int i = 0; i < lines.size(); i++) {
      LyricLine line = lines.get(i);
      long next = i + 1 < lines.size() ? lines.get(i + 1).timeMs : 0L;
      if (next > line.timeMs) {
        line.endMs = Math.min(next, line.timeMs + 12000L);
      }
      if (!line.hasWords()) {
        line.words.addAll(distribute(line));
      }
    }
  }

  /** Splits a line's duration across its characters/words so a plain LRC still sweeps. */
  private static List<LyricWord> distribute(LyricLine line) {
    List<String> tokens = tokenise(line.text);
    List<LyricWord> words = new ArrayList<LyricWord>();
    if (tokens.isEmpty()) {
      return words;
    }
    int totalWeight = 0;
    int[] weights = new int[tokens.size()];
    for (int i = 0; i < tokens.size(); i++) {
      String token = tokens.get(i);
      int weight = 1;
      for (int c = 0; c < token.length(); c++) {
        if (isWide(token.charAt(c))) {
          weight = 2;
          break;
        }
      }
      weights[i] = weight;
      totalWeight += weight;
    }
    long duration = Math.max(400L, line.endMs - line.timeMs);
    long cursor = line.timeMs;
    for (int i = 0; i < tokens.size(); i++) {
      long span = duration * weights[i] / Math.max(1, totalWeight);
      long end = i == tokens.size() - 1 ? line.timeMs + duration : cursor + span;
      words.add(new LyricWord(cursor, end, tokens.get(i)));
      cursor = end;
    }
    return words;
  }

  /** Splits CJK text per character and Latin text per word, keeping the spaces attached. */
  private static List<String> tokenise(String text) {
    List<String> tokens = new ArrayList<String>();
    if (text == null || text.isEmpty()) {
      return tokens;
    }
    StringBuilder current = new StringBuilder();
    for (int i = 0; i < text.length(); i++) {
      char c = text.charAt(i);
      if (isWide(c)) {
        if (current.length() > 0) {
          tokens.add(current.toString());
          current.setLength(0);
        }
        tokens.add(String.valueOf(c));
      } else if (c == ' ') {
        current.append(c);
        tokens.add(current.toString());
        current.setLength(0);
      } else {
        current.append(c);
      }
    }
    if (current.length() > 0) {
      tokens.add(current.toString());
    }
    List<String> cleaned = new ArrayList<String>(tokens.size());
    for (String token : tokens) {
      if (!token.isEmpty()) {
        cleaned.add(token);
      }
    }
    return cleaned;
  }

  private static boolean isWide(char c) {
    return (c >= 0x2E80 && c <= 0x9FFF)
        || (c >= 0xAC00 && c <= 0xD7AF)
        || (c >= 0xF900 && c <= 0xFAFF)
        || (c >= 0xFF00 && c <= 0xFF60);
  }

  private static String joinWords(List<LyricWord> words) {
    StringBuilder text = new StringBuilder();
    for (LyricWord word : words) {
      text.append(word.text);
    }
    return text.toString().trim();
  }
}

package net.wurstclient.hud2.music;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;
import java.util.stream.IntStream;
import net.wurstclient.neoraven.music.LyricLine;
import org.junit.jupiter.api.Test;

class OpaiLyricsTest {
  private final List<LyricLine> lines =
      IntStream.range(0, 100).mapToObj(i -> LyricLine.plain(i * 1000L, "line " + i)).toList();

  @Test
  void interruptedCueTransitionContinuesFromRenderedPosition() {
    var motion = new OpaiLyrics();
    motion.update(lines, 0, 0);
    var active =
        motion.update(lines, 1000, 100).stream()
            .filter(row -> row.line == lines.get(1))
            .findFirst()
            .orElseThrow();
    assertEquals(86, active.y(100));
    float before = active.y(200), alpha = active.alpha(200);
    assertTrue(before > 48 && before < 86);
    motion.update(lines, 2000, 200);
    assertEquals(before, active.y(200));
    assertEquals(alpha, active.alpha(200));
    assertEquals(10, active.y(530));
    assertEquals(.35F, active.alpha(530), .0001F);
  }

  @Test
  void seekAndTrackReplacementCannotKeepStaleRows() {
    var motion = new OpaiLyrics();
    motion.update(lines, 1000, 0);
    var rows = motion.update(lines, 90000, 100);
    assertEquals(5, rows.size());
    assertTrue(rows.stream().allMatch(row -> row.line.timeMs >= 88000));
    var other = List.of(LyricLine.plain(0, "other track"));
    var newRows = motion.update(other, 0, 110);
    assertEquals(1, newRows.size());
    assertSame(other.get(0), newRows.iterator().next().line);
    assertEquals(48, newRows.iterator().next().y(110));
  }

  @Test
  void preludeAndEdgeMaskMatchOpaiVisibility() {
    var motion = new OpaiLyrics();
    var rows = motion.update(List.of(LyricLine.plain(5000, "first")), 0, 0);
    assertEquals(.35F, rows.iterator().next().alpha(0));
    assertEquals(0, OpaiLyrics.mask(0));
    assertEquals(.5F, OpaiLyrics.mask(9));
    assertEquals(1, OpaiLyrics.mask(48));
    assertEquals(0, OpaiLyrics.mask(120));
  }
}

package net.wurstclient.neoraven.music;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;
import org.junit.jupiter.api.Test;

class LyricLayoutTest {
  @Test
  void wrappedKaraokeDoesNotSpillIntoNextRun() {
    var line =
        new LyricLine(
            0,
            4000,
            "onetwothreefour",
            List.of(
                new LyricWord(0, 1000, "one"), new LyricWord(1000, 2000, "two"),
                new LyricWord(2000, 3000, "three"), new LyricWord(3000, 4000, "four")));
    var runs = LyricLayout.wrap(line, 6, String::length);
    assertEquals(
        List.of("onetwo", "three", "four"), runs.stream().map(LyricLayout.Run::text).toList());
    assertEquals(6, LyricLayout.sungWidth(line, runs.get(0), 2500, String::length));
    assertEquals(2.5F, LyricLayout.sungWidth(line, runs.get(1), 2500, String::length));
    assertEquals(0, LyricLayout.sungWidth(line, runs.get(2), 2500, String::length));
  }

  @Test
  void originalPanelSelectsNextLineDuringGapAndBeforeFirstCue() {
    var lines =
        List.of(
            new LyricLine(1000, 2000, "first", null), new LyricLine(4000, 5000, "second", null));
    assertEquals(0, LyricLayout.indexAt(lines, 0));
    assertEquals(1, LyricLayout.indexAt(lines, 2500));
    assertEquals(1, LyricLayout.indexAt(lines, 6000));
    assertEquals(-1, LyricLayout.indexAt(List.of(), 0));
  }
}

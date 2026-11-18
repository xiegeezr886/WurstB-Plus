package net.wurstclient.neoraven.music;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;
import org.junit.jupiter.api.Test;

class MusicViewTest {
  @Test
  void seekingBackwardsAndBeforeFirstLineDoesNotKeepStaleLyrics() {
    var lines = LyricsParser.parse("", "[00:02.00]First\n[00:06.50]Second\n[00:10.0]Third", "", "");
    assertEquals(-1, MusicView.lyricIndex(lines, 1999));
    assertEquals(0, MusicView.lyricIndex(lines, 2000));
    assertEquals(2, MusicView.lyricIndex(lines, 15000));
    assertEquals(1, MusicView.lyricIndex(lines, 6500));
    assertEquals(0, MusicView.lyricIndex(lines, 3000));
    assertEquals(-1, MusicView.lyricIndex(List.of(), 5000));
  }

  @Test
  void wordTimingsAndTranslationSurvivePort() {
    var lines =
        LyricsParser.parse("[1000,2000](1000,700,0)你(1700,1300,0)好", "", "[00:01.00]Hello", "");
    assertEquals(1, lines.size());
    assertEquals("你好", lines.get(0).text);
    assertEquals("Hello", lines.get(0).translation);
    assertEquals(0, lines.get(0).activeWord(1699));
    assertEquals(1, lines.get(0).activeWord(1700));
    assertEquals(0, lines.get(0).progress(0));
    assertEquals(1, lines.get(0).progress(5000));
  }

  @Test
  void invalidLyricsAndTimeFormatting() {
    assertTrue(LyricsParser.parse("garbage", "[ar:Unknown]\nno timestamps", "", "").isEmpty());
    assertEquals("0:00", MusicView.time(-1));
    assertEquals("3:07", MusicView.time(187999));
    assertEquals("60:00", MusicView.time(3600000));
  }
}

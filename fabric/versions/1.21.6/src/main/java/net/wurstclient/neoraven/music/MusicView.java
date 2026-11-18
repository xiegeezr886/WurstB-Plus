/* Adapted from NeoRaven MusicIsland; GPL-3.0. See MUSIC-NOTICE.txt. */
package net.wurstclient.neoraven.music;

import java.util.List;

public final class MusicView {
  private MusicView() {}

  public static int lyricIndex(List<LyricLine> lines, long position) {
    int low = 0, high = lines.size() - 1, result = -1;
    while (low <= high) {
      int middle = (low + high) >>> 1;
      if (lines.get(middle).timeMs <= position) {
        result = middle;
        low = middle + 1;
      } else high = middle - 1;
    }
    return result;
  }

  public static String lyric() {
    MusicManager m = MusicManager.get();
    if (m.isLoadingTrack()) return "正在加载 " + (int) (m.downloadProgress() * 100) + "%";
    if (m.playbackError() != null) return m.playbackError();
    if (m.engine().error() != null) return "播放失败，请重试";
    List<LyricLine> lines = m.lyrics();
    int index = lyricIndex(lines, m.positionMs());
    return index >= 0
        ? lines.get(index).text
        : m.lyricsLoading() ? "正在加载歌词…" : m.current() == null ? "尚未播放音乐" : m.current().artist;
  }

  public static float progress() {
    MusicManager m = MusicManager.get();
    return m.durationMs() <= 0
        ? 0
        : Math.min(1, Math.max(0, (float) m.positionMs() / m.durationMs()));
  }

  public static String time(long millis) {
    long seconds = Math.max(0, millis) / 1000;
    return seconds / 60 + ":" + (seconds % 60 < 10 ? "0" : "") + seconds % 60;
  }
}

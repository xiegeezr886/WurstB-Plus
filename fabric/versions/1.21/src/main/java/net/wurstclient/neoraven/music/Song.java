/* Music player adapted from NeoRaven (GPL-3.0). See META-INF/licenses/neoraven/MUSIC-NOTICE.txt. */
package net.wurstclient.neoraven.music;

/** One track as the NetEase web API describes it. */
public final class Song {

  public final long id;
  public final String name;
  public final String artist;
  public final String album;
  public final String coverUrl;
  public final long durationMs;

  public Song(long id, String name, String artist, String album, String coverUrl, long durationMs) {
    this.id = id;
    this.name = name == null ? "" : name;
    this.artist = artist == null ? "" : artist;
    this.album = album == null ? "" : album;
    this.coverUrl = coverUrl == null ? "" : coverUrl;
    this.durationMs = durationMs;
  }

  /** Cover URL for a square thumbnail of {@code size} pixels. */
  public String cover(int size) {
    if (coverUrl.isEmpty()) {
      return "";
    }
    return coverUrl + "?param=" + size + "y" + size;
  }

  public String label() {
    return artist.isEmpty() ? name : name;
  }

  @Override
  public String toString() {
    return name;
  }
}

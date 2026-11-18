/* Music player adapted from NeoRaven (GPL-3.0). See META-INF/licenses/neoraven/MUSIC-NOTICE.txt. */
package net.wurstclient.neoraven.music;

/** A NetEase playlist row (recommendations, the user's own playlists, search results). */
public final class Playlist {

  public final long id;
  public final String name;
  public final int trackCount;
  public final String coverUrl;

  public Playlist(long id, String name, int trackCount, String coverUrl) {
    this.id = id;
    this.name = name == null ? "" : name;
    this.trackCount = trackCount;
    this.coverUrl = coverUrl == null ? "" : coverUrl;
  }

  public String cover(int size) {
    return coverUrl.isEmpty() ? "" : coverUrl + "?param=" + size + "y" + size;
  }
}

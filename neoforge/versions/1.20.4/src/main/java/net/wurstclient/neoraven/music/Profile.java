/* Music player adapted from NeoRaven (GPL-3.0). See META-INF/licenses/neoraven/MUSIC-NOTICE.txt. */
package net.wurstclient.neoraven.music;

/** The signed-in NetEase account, as shown on the account page. */
public final class Profile {

  public final long userId;
  public final String nickname;
  public final String avatarUrl;
  public final int vipType;

  public Profile(long userId, String nickname, String avatarUrl, int vipType) {
    this.userId = userId;
    this.nickname = nickname == null ? "" : nickname;
    this.avatarUrl = avatarUrl == null ? "" : avatarUrl;
    this.vipType = vipType;
  }

  public String avatar(int size) {
    return avatarUrl.isEmpty() ? "" : avatarUrl + "?param=" + size + "y" + size;
  }
}

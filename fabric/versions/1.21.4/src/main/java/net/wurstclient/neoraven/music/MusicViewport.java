/* NeoRaven MusicScreen.fitScale adaptation; GPL-3.0. */
package net.wurstclient.neoraven.music;

/** The original player canvas and screen-fitting rules, independent of Navigator. */
public record MusicViewport(float x, float y, float scale) {
  public static final float WIDTH = 840, HEIGHT = 520, RADIUS = 28;

  public static MusicViewport fit(int width, int height, float guiScale) {
    float fit = Math.min(width * .92F / WIDTH, height * .92F / HEIGHT);
    // The upstream shared Material scale defaults to 1; this client has no Onyx GUI setting.
    float base = Math.min(1, fit);
    float scale = Math.max(.1F, Math.min(base * guiScale, fit));
    return new MusicViewport((width - WIDTH * scale) / 2, (height - HEIGHT * scale) / 2, scale);
  }
}

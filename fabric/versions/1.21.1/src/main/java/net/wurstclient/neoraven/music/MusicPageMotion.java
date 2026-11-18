package net.wurstclient.neoraven.music;

/** Fade out before changing layout; quick repeated navigation always lands on the latest page. */
public final class MusicPageMotion {
  private String current, pending;
  private float alpha = 1;
  private float direction = -1;
  private boolean leaving;

  public MusicPageMotion(String page) {
    current = page;
  }

  public void request(String page) {
    if (page.equals(current)) {
      pending = null;
      leaving = false;
      return;
    }
    if (alpha >= .999F) direction = -1;
    pending = page;
    leaving = true;
  }

  public String update(float millis) {
    float step = Math.max(0, Math.min(100, millis)) / 140;
    if (leaving) {
      alpha = Math.max(0, alpha - step);
      if (alpha == 0) {
        current = pending;
        pending = null;
        leaving = false;
        direction = 1;
        return current;
      }
    } else alpha = Math.min(1, alpha + step);
    return null;
  }

  public float alpha() {
    return alpha * alpha * (3 - 2 * alpha);
  }

  public float offset() {
    return direction * (1 - alpha()) * 10;
  }

  public boolean ready() {
    return !leaving && alpha >= .999F;
  }
}

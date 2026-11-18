/* NeoRaven Onyx adaptation (GPL-3.0), see MUSIC-NOTICE.txt. */
package net.wurstclient.neoraven.music;

/**
 * Cubic-bezier easing, ported from Onyx's {@code render.misc.Util}.
 *
 * <p>Onyx solved the bezier numerically (Newton, then bisection) instead of using sampled tables;
 * that keeps every animation curve frame-accurate, so the same solver is used here.
 */
public final class Ease {

  /** Linear. */
  public static final Ease LINEAR = new Ease(0.0, 0.0, 1.0, 1.0);

  /** CSS ease-out, used by Opai's 330 ms lyric transitions. */
  public static final Ease CSS_EASE_OUT = new Ease(0.0, 0.0, 0.58, 1.0);

  /** Material 3 emphasized decelerate (Onyx's {@code IFACE2}/{@code IFACE6}). */
  public static final Ease EMPHASIZED_DECELERATE = new Ease(0.2, 0.0, 0.0, 1.0);

  /** Decelerate (Onyx's {@code IFACE5}). */
  public static final Ease DECELERATE = new Ease(0.0, 0.0, 0.0, 1.0);

  /** Material 3 emphasized (Onyx's {@code IFACE7}). */
  public static final Ease EMPHASIZED = new Ease(0.05, 0.7, 0.1, 1.0);

  /** Material 3 standard (Onyx's {@code IFACE3}). */
  public static final Ease STANDARD = new Ease(0.4, 0.0, 0.2, 1.0);

  /** Onyx's {@code IFACE4}. */
  public static final Ease EMPHASIZED_ACCELERATE = new Ease(0.3, 0.0, 1.0, 1.0);

  /** Onyx notification exit (render.misc.Util.IFACE). */
  public static final Ease NOTIFICATION_EXIT = new Ease(0.3, 0.0, 0.8, 0.15);

  private final double x1;
  private final double y1;
  private final double x2;
  private final double y2;

  private Ease(double x1, double y1, double x2, double y2) {
    this.x1 = x1;
    this.y1 = y1;
    this.x2 = x2;
    this.y2 = y2;
  }

  /** Evaluates the curve for a normalized time in 0..1. */
  public float get(float t) {
    if (t <= 0.0F) {
      return 0.0F;
    }
    if (t >= 1.0F) {
      return 1.0F;
    }
    return (float) bezierY(solveX(t));
  }

  private double solveX(double x) {
    double current = x;
    for (int i = 0; i < 8; i++) {
      double error = bezierX(current) - x;
      if (Math.abs(error) < 1.0E-6) {
        return current;
      }
      double derivative = bezierDX(current);
      if (Math.abs(derivative) < 1.0E-6) {
        break;
      }
      current -= error / derivative;
    }

    double lo = 0.0;
    double hi = 1.0;
    current = x;
    while (lo < hi) {
      double error = bezierX(current) - x;
      if (Math.abs(error) < 1.0E-6) {
        return current;
      }
      if (error < 0.0) {
        lo = current;
      } else {
        hi = current;
      }
      double next = (lo + hi) / 2.0;
      if (Math.abs(next - current) < 1.0E-9) {
        return current;
      }
      current = next;
    }
    return current;
  }

  private double bezierX(double t) {
    double inv = 1.0 - t;
    return 3.0 * inv * inv * t * x1 + 3.0 * inv * t * t * x2 + t * t * t;
  }

  private double bezierY(double t) {
    double inv = 1.0 - t;
    return 3.0 * inv * inv * t * y1 + 3.0 * inv * t * t * y2 + t * t * t;
  }

  private double bezierDX(double t) {
    double inv = 1.0 - t;
    return 3.0 * inv * inv * x1 + 6.0 * inv * t * (x2 - x1) + 3.0 * t * t * (1.0 - x2);
  }
}

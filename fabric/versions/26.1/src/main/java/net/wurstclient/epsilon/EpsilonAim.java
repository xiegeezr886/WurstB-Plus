/* Epsilon AimBot projectile and aim stepping equations. GPL-3.0. */
package net.wurstclient.epsilon;

import net.minecraft.util.Mth;
import net.wurstclient.util.Rotation;

public final class EpsilonAim {
  private EpsilonAim() {}

  public static Rotation step(Rotation from, Rotation to, double speed) {
    float yaw = Mth.wrapDegrees(to.yaw() - from.yaw()), pitch = to.pitch() - from.pitch();
    double scale = Math.min(1, Math.max(0, speed) / Math.max(1E-9, Math.hypot(yaw, pitch)));
    return new Rotation(
        from.yaw() + (float) (yaw * scale),
        Mth.clamp(from.pitch() + (float) (pitch * scale), -90, 90));
  }

  public static float projectilePitch(double horizontal, double vertical, double velocity) {
    if (horizontal < 1E-6 || velocity <= 0) return Float.NaN;
    double v2 = velocity * velocity, g = .05;
    double discriminant = v2 * v2 - g * (g * horizontal * horizontal + 2 * vertical * v2);
    return discriminant < 0
        ? Float.NaN
        : (float) -Math.toDegrees(Math.atan((v2 - Math.sqrt(discriminant)) / (g * horizontal)));
  }
}

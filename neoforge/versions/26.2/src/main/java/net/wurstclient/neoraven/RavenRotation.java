/* NeoRaven RotationUtils, AimAssistModule and KillAuraModule adaptation.
 * GPL-3.0; see META-INF/licenses/neoraven/LICENSE. */
package net.wurstclient.neoraven;

import net.minecraft.util.Mth;
import net.wurstclient.util.Rotation;

public final class RavenRotation {
  private RavenRotation() {}

  public static Rotation smooth(Rotation from, Rotation to, double smoothing) {
    float keep = (float) (1 - Math.min(9.9, Math.max(0, smoothing)) / 10);
    float yaw = Mth.wrapDegrees(to.yaw() - from.yaw());
    float pitch = to.pitch() - from.pitch();
    if (Math.abs(yaw) > 7) yaw = Math.signum(yaw) * ((Math.abs(yaw) - 7) * keep + 7);
    if (Math.abs(pitch) > 3) pitch = Math.signum(pitch) * ((Math.abs(pitch) - 3) * keep + 3);
    return new Rotation(from.yaw() + yaw, Mth.clamp(from.pitch() + pitch, -90, 90));
  }

  public static Rotation assist(
      Rotation from, Rotation to, double speed, double fov, double random) {
    if (speed >= 100) return to;
    double yaw = Mth.wrapDegrees(to.yaw() - from.yaw());
    double pitch = to.pitch() - from.pitch();
    double rate = 0.3 + 14.7 * Math.pow(speed / 100, 2);
    double near = Math.min(1, Math.abs(yaw) / Math.max(10, fov));
    double step = rate * (0.3 + (5 + (int) (3 * random)) * near);
    double k = Math.max(1, Math.max(Math.abs(yaw), Math.abs(pitch)) / Math.max(1E-6, step));
    return new Rotation(
        (float) (from.yaw() + yaw / k), Mth.clamp((float) (from.pitch() + pitch / k), -90, 90));
  }

  public static Rotation quantize(Rotation from, Rotation to, double sensitivity) {
    double f = sensitivity * 0.6 + 0.2;
    double step = f * f * f * 8 * 0.15;
    float yaw =
        (float) (from.yaw() + Math.round(Mth.wrapDegrees(to.yaw() - from.yaw()) / step) * step);
    float pitch = (float) (from.pitch() + Math.round((to.pitch() - from.pitch()) / step) * step);
    return new Rotation(yaw, Mth.clamp(pitch, -90, 90));
  }
}

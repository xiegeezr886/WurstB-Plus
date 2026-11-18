/* NeoRaven Onyx adaptation (GPL-3.0), see MUSIC-NOTICE.txt. */
package net.wurstclient.neoraven.music;

/**
 * Critically-ish damped spring, the time-based motion model Material 3 Expressive uses for
 * "spatial" transitions (a keycap popping under a finger, a chip settling after a toggle).
 *
 * <p>The fixed-duration animation interpolates between two values on a fixed curve, so it can never
 * overshoot and come back. A spring keeps a velocity, which is what gives the small bounce that
 * reads as physical without adding a hand-tuned keyframe list. The integrator is a semi-implicit
 * Euler with a fixed 8ms sub-step, so a frame spike (the client occasionally stalls for 80ms) can
 * never make it explode.
 */
public final class Spring {

  private static final float STEP_SECONDS = 0.008F;
  private static final float MAX_SECONDS = 0.05F;

  private float value;
  private float velocity;
  private float target;

  public Spring(float initial) {
    this.value = initial;
    this.target = initial;
  }

  public float value() {
    return value;
  }

  public float target() {
    return target;
  }

  /** Moves towards {@code value}; the spring then settles there with a damped bounce. */
  public Spring target(float value) {
    this.target = value;
    return this;
  }

  /** Adds velocity (units per second) for an impulse-style pop without moving the value. */
  public Spring impulse(float velocity) {
    this.velocity += velocity;
    return this;
  }

  /** Sets both value and target, dropping any velocity. */
  public void set(float value) {
    this.value = value;
    this.target = value;
    this.velocity = 0.0F;
  }

  /** Advances the simulation; {@code deltaMs} is the frame delta in milliseconds. */
  public void update(float deltaMs) {
    float seconds = Math.min(Math.max(deltaMs, 0.0F) / 1000.0F, MAX_SECONDS);
    if (seconds <= 0.0F) {
      return;
    }
    int steps = Math.max(1, (int) Math.ceil(seconds / STEP_SECONDS));
    float step = seconds / steps;
    for (int i = 0; i < steps; i++) {
      float acceleration = -STIFFNESS * (value - target) - DAMPING * velocity;
      velocity += acceleration * step;
      value += velocity * step;
    }
  }

  /** Whether the spring is close enough to be considered at rest. */
  public boolean settled() {
    return Math.abs(value - target) < 0.0005F && Math.abs(velocity) < 0.01F;
  }

  // Under-damped on purpose: this is the "expressive" overshoot, roughly 8% of the distance.
  private static final float STIFFNESS = 260.0F;
  private static final float DAMPING = 18.0F;
}

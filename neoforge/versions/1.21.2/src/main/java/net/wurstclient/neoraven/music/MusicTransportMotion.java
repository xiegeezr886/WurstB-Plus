package net.wurstclient.neoraven.music;

/** Frame-rate independent, non-overshooting playback and seek feedback. */
public final class MusicTransportMotion {
  private float progress, playing, phase;
  private boolean initialized;

  public void update(float target, boolean active, float millis, boolean dragging) {
    float dt = Math.max(0, Math.min(100, millis));
    target = Math.max(0, Math.min(1, target));
    if (!initialized) {
      progress = target;
      playing = active ? 1 : 0;
      initialized = true;
    }
    playing += ((active ? 1 : 0) - playing) * (1 - (float) Math.exp(-dt / 85));
    if (Math.abs(playing - (active ? 1 : 0)) < .001F) playing = active ? 1 : 0;
    progress =
        dragging ? target : progress + (target - progress) * (1 - (float) Math.exp(-dt / 90));
    if (Math.abs(target - progress) < .00005F) progress = target;
    phase = (phase + dt * .028F * playing) % 40;
  }

  public float progress() {
    return progress;
  }

  public float playing() {
    return playing;
  }

  public float phase() {
    return phase;
  }
}

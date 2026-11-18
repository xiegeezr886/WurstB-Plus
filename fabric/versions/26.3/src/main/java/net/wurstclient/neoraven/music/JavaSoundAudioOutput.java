/* Music player adapted from NeoRaven (GPL-3.0). See META-INF/licenses/neoraven/MUSIC-NOTICE.txt. */
package net.wurstclient.neoraven.music;

import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.DataLine;
import javax.sound.sampled.FloatControl;
import javax.sound.sampled.Line;
import javax.sound.sampled.Mixer;
import javax.sound.sampled.SourceDataLine;

public final class JavaSoundAudioOutput implements AudioOutput {

  private static final int LINE_BUFFER_FRAMES = 8192;

  private final SourceDataLine line;
  private final FloatControl gain;

  private volatile float softwareVolume = 1.0F;
  private byte[] scaled = new byte[0];

  private JavaSoundAudioOutput(SourceDataLine line, FloatControl gain) {
    this.line = line;
    this.gain = gain;
  }

  public static JavaSoundAudioOutput open(int sampleRate, int channels) {
    AudioFormat format = new AudioFormat(sampleRate, 16, channels, true, false);
    SourceDataLine line = openLine(format, LINE_BUFFER_FRAMES * channels * 2);
    if (line == null) {
      return null;
    }
    FloatControl control = null;
    try {
      control = (FloatControl) line.getControl(FloatControl.Type.MASTER_GAIN);
    } catch (Throwable ignored) {
      control = null;
    }
    return new JavaSoundAudioOutput(line, control);
  }

  private static SourceDataLine openLine(AudioFormat format, int bufferBytes) {
    try {
      SourceDataLine line = AudioSystem.getSourceDataLine(format);
      try {
        line.open(format, bufferBytes);
      } catch (Throwable first) {
        line.open(format);
      }
      line.start();
      return line;
    } catch (Throwable failed) {
      System.out.println("[neoraven] default audio line failed: " + failed);
    }
    DataLine.Info info = new DataLine.Info(SourceDataLine.class, format);
    for (Mixer.Info mixerInfo : AudioSystem.getMixerInfo()) {
      try {
        Mixer mixer = AudioSystem.getMixer(mixerInfo);
        if (!mixer.isLineSupported(info)) {
          continue;
        }
        Line candidate = mixer.getLine(info);
        if (!(candidate instanceof SourceDataLine)) {
          continue;
        }
        SourceDataLine line = (SourceDataLine) candidate;
        line.open(format);
        line.start();
        System.out.println("[neoraven] using audio device: " + mixerInfo.getName());
        return line;
      } catch (Throwable ignored) {
      }
    }
    return null;
  }

  @Override
  public void write(byte[] data, int offset, int length) {
    float volume = softwareVolume;
    if (volume == 1.0F) {
      line.write(data, offset, length);
      return;
    }
    int count = length - length % 2;
    if (scaled.length < count) {
      scaled = new byte[count];
    }
    for (int i = 0; i < count; i += 2) {
      int sample = (data[offset + i] & 0xFF) | (data[offset + i + 1] << 8);
      int adjusted = (int) (sample * volume);
      if (adjusted > 32767) {
        adjusted = 32767;
      } else if (adjusted < -32768) {
        adjusted = -32768;
      }
      scaled[i] = (byte) (adjusted & 0xFF);
      scaled[i + 1] = (byte) ((adjusted >> 8) & 0xFF);
    }
    line.write(scaled, 0, count);
  }

  @Override
  public void setGain(float gain) {
    if (this.gain == null) {
      return;
    }
    float requested =
        Float.isNaN(gain) || Float.isInfinite(gain) ? 0.0F : Math.max(0.0F, Math.min(1.0F, gain));
    try {
      float db =
          requested <= 0.0001F ? this.gain.getMinimum() : (float) (20.0D * Math.log10(requested));
      this.gain.setValue(Math.max(this.gain.getMinimum(), Math.min(this.gain.getMaximum(), db)));
      softwareVolume = 1.0F;
    } catch (Throwable ignored) {
      softwareVolume = requested;
    }
  }

  @Override
  public boolean hasGain() {
    return gain != null;
  }

  @Override
  public void flush() {
    line.flush();
  }

  @Override
  public void drain() {
    line.drain();
  }

  @Override
  public void close() {
    try {
      line.stop();
      line.flush();
      line.close();
    } catch (Throwable ignored) {
    }
  }

  @Override
  public boolean isLost() {
    try {
      return !line.isOpen();
    } catch (Throwable ignored) {
      return true;
    }
  }
}

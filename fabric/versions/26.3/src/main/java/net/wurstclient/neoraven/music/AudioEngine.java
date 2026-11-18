/* Music player adapted from NeoRaven (GPL-3.0). See META-INF/licenses/neoraven/MUSIC-NOTICE.txt. */
package net.wurstclient.neoraven.music;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import javazoom.jl.decoder.Bitstream;
import javazoom.jl.decoder.Decoder;
import javazoom.jl.decoder.Header;
import javazoom.jl.decoder.Obuffer;
import javazoom.jl.decoder.SampleBuffer;

public final class AudioEngine {

  /** Notified when a track reaches its end without being stopped or replaced. */
  public interface Listener {
    void onTrackEnded();
  }

  public static final int OUTPUT_AUTO = 0;
  public static final int OUTPUT_JAVA_SOUND = 1;
  public static final int OUTPUT_GAME = 2;

  private final Object lock = new Object();

  private volatile int outputMode = OUTPUT_AUTO;
  private volatile String activeOutput = "";
  private volatile boolean disposed;
  private volatile File file;
  private volatile boolean wantPlay;
  private volatile long seekRequestMs = -1L;
  private volatile float volume = 0.8F;
  private volatile float gain;
  private volatile long positionMs;
  private volatile long durationMs;
  private volatile float level;
  private volatile float peak;
  private volatile String error;
  private volatile boolean buffering;
  private volatile Listener listener;

  private Thread thread;
  private volatile AudioOutput line;
  private volatile long trackSerial;

  /**
   * Serial of a track that already played to its end. The worker must not restart the same file
   * after EOF: the next track is fetched on another thread, and restarting the old one played a few
   * seconds of the previous song before the switch.
   */
  private volatile long endedSerial = -1L;

  public void setListener(Listener value) {
    this.listener = value;
  }

  public void setVolume(float value) {
    this.volume = Float.isFinite(value) ? clamp(value, 0.0F, 1.0F) : 0;
    AudioOutput current = this.line;
    if (current != null && current.hasGain()) {
      current.setGain(this.volume);
    }
  }

  public float volume() {
    return volume;
  }

  public void setOutputMode(int value) {
    int mode = value == OUTPUT_JAVA_SOUND || value == OUTPUT_GAME ? value : OUTPUT_AUTO;
    if (mode == this.outputMode) {
      return;
    }
    synchronized (lock) {
      this.outputMode = mode;
      if (file != null) {
        if (wantPlay) {
          this.seekRequestMs = Math.max(0L, positionMs);
        }
        this.trackSerial++;
        lock.notifyAll();
      }
    }
  }

  public int outputMode() {
    return outputMode;
  }

  public String activeOutput() {
    return activeOutput;
  }

  public void setDurationMs(long value) {
    this.durationMs = Math.max(0L, value);
  }

  public long durationMs() {
    return durationMs;
  }

  public long positionMs() {
    return positionMs;
  }

  /** Smoothed RMS of the most recent chunk (0..1), used to pulse the lyric view. */
  public float level() {
    return level;
  }

  /** Instantaneous peak of the most recent chunk (0..1), decays over ~200ms. */
  public float peak() {
    return peak;
  }

  public boolean isBuffering() {
    return buffering;
  }

  public String error() {
    return error;
  }

  public boolean isPlaying() {
    return wantPlay && file != null;
  }

  public boolean hasTrack() {
    return file != null;
  }

  public boolean isAtEnd() {
    return file != null && endedSerial == trackSerial;
  }

  /** Starts (or replaces) the current track at {@code startAtMs}. */
  public void play(File target, long startAtMs) {
    if (disposed) return;
    if (target == null || !target.isFile()) {
      this.error = "missing audio file";
      return;
    }
    synchronized (lock) {
      this.file = target;
      this.seekRequestMs = startAtMs > 0L ? startAtMs : -1L;
      this.positionMs = Math.max(0L, startAtMs);
      this.error = null;
      this.buffering = true;
      this.wantPlay = true;
      this.gain = 0.0F;
      this.endedSerial = -1L;
      this.trackSerial++;
      ensureThread();
      lock.notifyAll();
    }
  }

  public void pause() {
    this.wantPlay = false;
  }

  public void resume() {
    synchronized (lock) {
      error = null;
      lock.notifyAll();
      if (file != null) {
        if (endedSerial == trackSerial) {
          // Pressing play after the song finished restarts it instead of doing nothing.
          this.seekRequestMs = 0L;
          this.positionMs = 0L;
        }
        this.wantPlay = true;
      }
    }
  }

  public void stop() {
    synchronized (lock) {
      this.wantPlay = false;
      this.file = null;
      this.buffering = false;
      this.positionMs = 0L;
      this.level = 0.0F;
      this.peak = 0.0F;
      this.endedSerial = -1L;
      this.trackSerial++;
      lock.notifyAll();
    }
  }

  public void seek(long ms) {
    if (file == null) return;
    long target =
        Math.max(0L, Math.min(ms, durationMs > 0 ? Math.max(0, durationMs - 100) : Long.MAX_VALUE));
    this.seekRequestMs = target;
    // The worker has to decode-skip to the target; reflect it immediately so the bar does not
    // snap back to the old position while that happens.
    this.positionMs = target;
  }

  public void dispose() {
    disposed = true;
    stop();
    closeLine();
    Thread worker = thread;
    if (worker != null) {
      worker.interrupt();
    }
  }

  private void ensureThread() {
    if (thread != null && thread.isAlive()) {
      return;
    }
    Thread worker =
        new Thread(
            new Runnable() {
              @Override
              public void run() {
                loop();
              }
            },
            "neoraven-music");
    worker.setDaemon(true);
    worker.setPriority(Thread.NORM_PRIORITY + 1);
    thread = worker;
    worker.start();
  }

  private void loop() {
    while (!disposed) {
      File target;
      long serial;
      synchronized (lock) {
        while (!disposed && (file == null || (!wantPlay && error != null))) {
          try {
            lock.wait(200L);
          } catch (InterruptedException e) {
            if (disposed) {
              return;
            }
          }
        }
        target = file;
        serial = trackSerial;
      }
      if (disposed || target == null) {
        continue;
      }
      if (serial == endedSerial && seekRequestMs < 0L) {
        // This track is over and nothing has replaced it yet (the next song is still being
        // fetched): stay silent instead of looping the finished file.
        sleep(120L);
        continue;
      }
      try {
        runTrack(target, serial);
      } catch (Throwable t) {
        if (serial == trackSerial) {
          this.error =
              t.getClass().getSimpleName() + (t.getMessage() == null ? "" : ": " + t.getMessage());
          this.buffering = false;
          this.wantPlay = false;
          closeLine();
        }
        sleep(160L);
      }
    }
    closeLine();
  }

  private void runTrack(File target, long serial) throws Exception {
    this.buffering = true;
    this.error = null;
    this.level = 0.0F;
    this.peak = 0.0F;

    Bitstream stream = null;
    AudioOutput output = null;
    try {
      stream = new Bitstream(new BufferedInputStream(new FileInputStream(target), 256 * 1024));
      Header header = stream.readFrame();
      if (header == null) {
        throw new IOException("no mp3 frame");
      }
      Decoder decoder = new Decoder();
      SampleBuffer buffer = decode(decoder, header, stream);

      int frequency = decoder.getOutputFrequency() > 0 ? decoder.getOutputFrequency() : 44100;
      int channels = Math.max(1, Math.min(2, decoder.getOutputChannels()));
      long baseMs = 0L;

      long seek = this.seekRequestMs;
      this.seekRequestMs = -1L;
      if (seek > 0L) {
        long[] aligned = new long[1];
        Header targetHeader = skipTo(stream, frequency, seek, aligned);
        baseMs = aligned[0];
        if (targetHeader == null) {
          return;
        }
        buffer = decode(decoder, targetHeader, stream);
        if (buffer == null) {
          return;
        }
      }

      output = openOutput(frequency, channels);
      if (output == null) {
        throw new IOException("no audio output available");
      }
      this.line = output;
      if (output.hasGain()) {
        output.setGain(volume);
      }
      this.buffering = false;
      this.positionMs = baseMs;

      long playedFrames = 0L;
      byte[] pcm = new byte[1152 * 2 * 2];

      while (!disposed) {
        if (serial != trackSerial) {
          return;
        }
        if (output.isLost()) {
          long resumeAt = Math.max(0L, this.positionMs);
          AudioOutput reopened = reopenOutput(output, serial, frequency, channels);
          if (reopened == null) {
            this.buffering = false;
            return;
          }
          if (reopened.isLost()) {
            close(reopened);
            this.buffering = false;
            return;
          }
          output = reopened;
          this.line = output;
          if (output.hasGain()) {
            output.setGain(volume);
          }
          this.seekRequestMs = resumeAt;
          this.positionMs = resumeAt;
          this.buffering = true;
          continue;
        }
        long pendingSeek = this.seekRequestMs;
        if (pendingSeek >= 0L) {
          this.seekRequestMs = -1L;
          try {
            output.flush();
          } catch (Throwable lost) {
            AudioOutput reopened = reopenOutput(output, serial, frequency, channels);
            if (reopened == null) {
              this.buffering = false;
              return;
            }
            output = reopened;
            this.line = output;
            if (output.hasGain()) {
              output.setGain(volume);
            }
          }
          // Re-open the file: skipTo() counts frames from wherever the stream currently
          // is, so skipping on the live stream made every mid-song seek land at a
          // position that drifted further the longer the song had played.
          Bitstream fresh =
              new Bitstream(new BufferedInputStream(new FileInputStream(target), 256 * 1024));
          Header freshHeader = fresh.readFrame();
          if (freshHeader == null) {
            fresh.close();
            this.buffering = false;
            return;
          }
          decoder = new Decoder();
          buffer = decode(decoder, freshHeader, fresh);
          if (buffer == null) {
            fresh.close();
            this.buffering = false;
            return;
          }
          int freshChannels = Math.max(1, Math.min(2, decoder.getOutputChannels()));
          if (freshChannels != channels) {
            // A different channel layout cannot be written to the open line; let the
            // track restart with its own format instead of feeding mismatched PCM.
            fresh.close();
            this.buffering = false;
            return;
          }
          closeQuietly(stream);
          stream = fresh;
          long[] aligned = new long[1];
          Header targetHeader = skipTo(stream, frequency, pendingSeek, aligned);
          baseMs = aligned[0];
          playedFrames = 0L;
          this.positionMs = baseMs;
          this.gain = 0.0F;
          if (targetHeader == null) {
            this.buffering = false;
            return;
          }
          buffer = decode(decoder, targetHeader, stream);
          if (buffer == null) {
            this.buffering = false;
            return;
          }
          this.buffering = false;
          continue;
        }
        if (!wantPlay) {
          sleep(25L);
          this.level *= 0.70F;
          this.peak *= 0.70F;
          continue;
        }

        // JLayer's getBufferLength() counts *interleaved* samples: bufferp advances by
        // `channels` for every frame. Treating it as a frame count made the position run
        // at 2x on stereo, which is what put the progress bar far ahead of the audio.
        int interleaved = buffer.getBufferLength();
        int frames = Math.max(0, interleaved / Math.max(1, channels));
        float fadeStep = frames / Math.max(1.0F, frequency * 0.08F);
        this.gain = Math.min(1.0F, this.gain + fadeStep);
        boolean hardwareGain = output.hasGain();
        if (hardwareGain) {
          output.setGain(volume);
        }
        float appliedGain = hardwareGain ? this.gain : this.gain * volume;
        int bytes = toPcm16(buffer.getBuffer(), interleaved, channels, appliedGain, pcm);
        if (bytes > 0) {
          try {
            output.write(pcm, 0, bytes);
          } catch (Throwable failure) {
            long resumeAt = Math.max(0L, this.positionMs);
            AudioOutput reopened = reopenOutput(output, serial, frequency, channels);
            if (reopened == null) {
              throw failure;
            }
            output = reopened;
            this.line = output;
            if (output.hasGain()) {
              output.setGain(volume);
            }
            this.seekRequestMs = resumeAt;
            this.positionMs = resumeAt;
            this.buffering = true;
            continue;
          }
        }
        playedFrames += frames;
        this.positionMs = baseMs + playedFrames * 1000L / frequency;
        updateLevel(buffer.getBuffer(), interleaved, channels);

        Header next = stream.readFrame();
        if (next == null) {
          break;
        }
        buffer = decode(decoder, next, stream);
        if (buffer == null) {
          break;
        }
      }
      try {
        output.drain();
      } catch (Throwable lost) {
        AudioOutput reopened = reopenOutput(output, serial, frequency, channels);
        if (reopened != null) {
          output = reopened;
          this.line = output;
          try {
            output.drain();
          } catch (Throwable ignored) {
          }
        }
      }
    } finally {
      closeQuietly(stream);
      if (this.line == output) {
        this.line = null;
      }
      close(output);
    }

    boolean ended;
    synchronized (lock) {
      ended = !disposed && serial == trackSerial && wantPlay;
      if (ended) {
        this.positionMs = durationMs > 0L ? durationMs : this.positionMs;
        this.buffering = false;
        this.wantPlay = false;
        this.endedSerial = serial;
      }
    }
    // Never call the manager while holding the audio lock: UI controls acquire them in reverse.
    if (ended) {
      Listener callback = listener;
      if (callback != null) {
        try {
          callback.onTrackEnded();
        } catch (Throwable ignored) {
        }
      }
    }
  }

  // Use a separate desktop output; never change Minecraft's OpenAL context.
  private AudioOutput openOutput(int frequency, int channels) {
    AudioOutput output = openJavaSound(frequency, channels);
    activeOutput = output == null ? "" : "Java Sound";
    return output;
  }

  private AudioOutput reopenOutput(AudioOutput broken, long serial, int frequency, int channels) {
    if (!broken.isLost() || disposed || serial != trackSerial) return null;
    close(broken);
    return openOutput(frequency, channels);
  }

  private AudioOutput openJavaSound(int frequency, int channels) {
    try {
      return JavaSoundAudioOutput.open(frequency, channels);
    } catch (Throwable ignored) {
      return null;
    }
  }

  private static SampleBuffer decode(Decoder decoder, Header header, Bitstream stream)
      throws Exception {
    if (header == null) {
      return null;
    }
    Obuffer decoded = decoder.decodeFrame(header, stream);
    stream.closeFrame();
    return decoded instanceof SampleBuffer ? (SampleBuffer) decoded : null;
  }

  /**
   * Reads and throws away frames until {@code targetMs}, leaving the bitstream on the first frame
   * that belongs to the target position. The returned header has <em>not</em> been decoded yet, so
   * the caller decodes it as the first frame to play.
   *
   * @param aligned receives the position the stream was actually aligned to
   */
  private static Header skipTo(Bitstream stream, int frequency, long targetMs, long[] aligned)
      throws Exception {
    long frames = 0L;
    Header header = stream.readFrame();
    while (header != null) {
      long position = frames * 1000L / Math.max(1, frequency);
      if (position >= targetMs) {
        aligned[0] = position;
        return header;
      }
      frames += header.version() == Header.MPEG1 ? 1152 : 576;
      stream.closeFrame();
      header = stream.readFrame();
    }
    aligned[0] = frames * 1000L / Math.max(1, frequency);
    return null;
  }

  private static int toPcm16(
      short[] source, int interleaved, int channels, float gain, byte[] out) {
    int count = interleaved;
    if (count > source.length) {
      count = source.length;
    }
    if (count * 2 > out.length) {
      count = out.length / 2;
    }
    for (int i = 0; i < count; i++) {
      int value = (int) (source[i] * gain);
      if (value > 32767) {
        value = 32767;
      } else if (value < -32768) {
        value = -32768;
      }
      out[i * 2] = (byte) (value & 0xFF);
      out[i * 2 + 1] = (byte) ((value >> 8) & 0xFF);
    }
    return count * 2;
  }

  private void updateLevel(short[] source, int interleaved, int channels) {
    int count = Math.min(interleaved, source.length);
    if (count <= 0) {
      return;
    }
    double sum = 0.0D;
    int peakValue = 0;
    for (int i = 0; i < count; i++) {
      int value = source[i];
      sum += (double) value * (double) value;
      int absolute = value < 0 ? -value : value;
      if (absolute > peakValue) {
        peakValue = absolute;
      }
    }
    float rms = (float) Math.sqrt(sum / count) / 32768.0F;
    this.level += (rms - this.level) * 0.35F;
    this.peak = Math.max(peakValue / 32768.0F, this.peak * 0.82F);
  }

  private void closeLine() {
    AudioOutput current = this.line;
    this.line = null;
    close(current);
  }

  private static void close(AudioOutput output) {
    if (output != null) {
      output.close();
    }
  }

  private static void closeQuietly(Bitstream stream) {
    if (stream != null) {
      try {
        stream.close();
      } catch (Throwable ignored) {
      }
    }
  }

  private static void sleep(long ms) {
    try {
      Thread.sleep(ms);
    } catch (InterruptedException ignored) {
    }
  }

  private static float clamp(float value, float min, float max) {
    return value < min ? min : (value > max ? max : value);
  }
}

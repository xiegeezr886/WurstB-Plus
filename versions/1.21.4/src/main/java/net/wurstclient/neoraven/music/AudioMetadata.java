package net.wurstclient.neoraven.music;

import java.io.*;
import javazoom.jl.decoder.*;

/** Reads frame durations on the IO worker, without opening an audio device. */
public final class AudioMetadata {
  private AudioMetadata() {}

  public static long duration(File file) {
    try (var in = new BufferedInputStream(new FileInputStream(file))) {
      Bitstream stream = new Bitstream(in);
      double ms = 0;
      Header header;
      while (!Thread.currentThread().isInterrupted() && (header = stream.readFrame()) != null) {
        ms += header.ms_per_frame();
        stream.closeFrame();
      }
      stream.close();
      return Math.round(ms);
    } catch (Exception e) {
      return 0;
    }
  }
}

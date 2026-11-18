/* Music player adapted from NeoRaven (GPL-3.0). See META-INF/licenses/neoraven/MUSIC-NOTICE.txt. */
package net.wurstclient.neoraven.music;

public interface AudioOutput {
  void write(byte[] data, int offset, int length);

  void setGain(float gain);

  boolean hasGain();

  void flush();

  void drain();

  void close();

  boolean isLost();
}

/* Alpha helpers adapted from NeoRaven Onyx Material (GPL-3.0). */
package net.wurstclient.neoraven.music;

final class Material {
  private Material() {}

  static int withAlpha(int argb, float alpha) {
    return (argb & 0xFFFFFF) | Math.max(0, Math.min(255, Math.round(alpha * 255))) << 24;
  }

  static int scaleAlpha(int argb, float factor) {
    return (argb & 0xFFFFFF) | Math.round((argb >>> 24) * Math.max(0, Math.min(1, factor))) << 24;
  }
}

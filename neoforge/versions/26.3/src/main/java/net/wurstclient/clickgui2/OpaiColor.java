/* OpaiBounce ColorPicker adaptation. See assets/wurstpenguin/opai/NOTICE.txt. */
package net.wurstclient.clickgui2;

import java.awt.Color;

/** Keeps the user's HSV position even when an RGB value cannot represent its hue. */
final class OpaiColor
{
    private float hue, saturation, brightness;
    private int last;
    private boolean initialized;

    float hue() { return hue; }
    float saturation() { return saturation; }
    float brightness() { return brightness; }
    void sync(int rgb)
    {
        if(initialized && last == rgb) return;
        float[] hsv = Color.RGBtoHSB(rgb >> 16 & 255, rgb >> 8 & 255, rgb & 255, null);
        if(hsv[1] > 0) hue = hsv[0];
        saturation = hsv[1]; brightness = hsv[2];
        last = rgb; initialized = true;
    }
    void hue(double value) { hue = clamp(value); }
    void plane(double s, double v) { saturation = clamp(s); brightness = clamp(v); }
    int rgb()
    {
        last = Color.HSBtoRGB(hue, saturation, brightness);
        initialized = true;
        return last;
    }
    private static float clamp(double value) { return (float)Math.max(0, Math.min(1, value)); }
}

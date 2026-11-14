/* OpaiBounce theme adaptation (GPL-3.0-or-later). See assets/wurstpenguin/opai/NOTICE.txt. */
package net.wurstclient.clickgui2;

import net.wurstclient.hud2.nanovg.NanoVGUI;
import org.lwjgl.nanovg.NanoVG;

final class OpaiStyle
{
    // The user's Opai screenshot supplies the cool palette; layout/motion use OpaiBounce.
    static final int HEADER = 0xF513161F, BODY = 0xB0101016;
    static final int SETTINGS = 0xFF1C1F2B, FIELD = 0xFF282D40;
    static final int TEXT = 0xFFF0F0F5, MUTED = 0xFFC1C5D6;
    static final int ACCENT = 0xFFB5BEF0, ENABLED = 0xFFB5BEF0, INK = 0xFF0C185C;
    static final int TRACK = 0xFF454C70, OUTLINE = 0xFF9097B5;
    static final int HOVER = 0x0CF0F0F5, OPTION = 0xFF3C435F;
    static final int TOOLTIP = 0xEB20232F, TOOLTIP_BORDER = 0x66707899;
    static final int SCROLLBAR = 0x669097B5, DIVIDER = 0x28353E77;
    static final float FONT = 8.3F;
    static final long SWITCH_MILLIS = 180;

    private OpaiStyle() {}

    static void text(String text, float x, float y, float width, int color)
    {
        NanoVGUI.text(NanoVGUI.substrByWidth(NanoVGUI.stripCodes(text), width, FONT), x, y, color, FONT);
    }

    static void field(float x, float y, float width, float height, boolean active)
    {
        NanoVGUI.fillRounded(x, y, x + width, y + height, 2, FIELD);
        NanoVGUI.fill(x, y + height - 1, x + width, y + height, active ? ACCENT : MUTED);
    }

    static void toggle(float x, float y, float amount)
    {
        NanoVGUI.fillRounded(x, y, x + 26, y + 16, 8, mix(FIELD, ACCENT, amount));
        NanoVGUI.outlineRounded(x, y, x + 26, y + 16, 8, 1, mix(OUTLINE, ACCENT, amount));
        float center = x + 8 + amount * 10;
        NanoVGUI.circle(center, y + 8, 6, mix(MUTED, INK, amount));
        if(amount > 0) check(center - 3, y + 5, 6, mix(ACCENT & 0xFFFFFF, ACCENT, amount));
    }

    // Opai VectorIcon paths, in their original 24-unit coordinate system.
    static void check(float x, float y, float size, int color)
    {
        float s = size / 24;
        long vg = NanoVGUI.context().getHandle();
        NanoVGUI.push();
        NanoVG.nvgBeginPath(vg);
        NanoVG.nvgMoveTo(vg, x + 2 * s, y + 13 * s);
        NanoVG.nvgLineTo(vg, x + 9 * s, y + 21 * s);
        NanoVG.nvgLineTo(vg, x + 23 * s, y + 2 * s);
        NanoVG.nvgStrokeWidth(vg, Math.max(0.65F, 1.3F * s));
        NanoVG.nvgLineCap(vg, NanoVG.NVG_ROUND);
        NanoVG.nvgLineJoin(vg, NanoVG.NVG_ROUND);
        NanoVG.nvgStrokeColor(vg, NanoVGUI.context().getColor(color));
        NanoVG.nvgStroke(vg);
        NanoVGUI.pop();
    }

    static void chevron(float x, float y, float amount)
    {
        double angle = Math.toRadians(-90 + amount * 180);
        float c = (float)Math.cos(angle), s = (float)Math.sin(angle);
        float[] px = {1, -2, 1}, py = {-3, 0, 3};
        for(int i = 0; i < 2; i++) NanoVGUI.line(x + px[i] * c - py[i] * s, y + px[i] * s + py[i] * c,
            x + px[i + 1] * c - py[i + 1] * s, y + px[i + 1] * s + py[i + 1] * c, 0.87F, MUTED);
    }

    private static int mix(int from, int to, float amount)
    {
        int result = 0;
        for(int shift = 0; shift <= 24; shift += 8)
            result |= Math.round(((from >>> shift) & 255) * (1 - amount) + ((to >>> shift) & 255) * amount) << shift;
        return result;
    }

    static float openingScale(long elapsed)
    {
        double seconds = Math.max(0, elapsed) / 1000.0;
        return seconds >= 0.32 ? 1 : (float)(1 - 0.42 * Math.exp(-17 * seconds) * Math.cos(23 * seconds));
    }
}

/* OpaiBounce theme adaptation (GPL-3.0-or-later). See assets/wurstpenguin/opai/NOTICE.txt. */
package net.wurstclient.clickgui2;

import net.wurstclient.hud2.nanovg.NanoVGUI;

final class OpaiStyle
{
    static final int HEADER = 0xFF161110, BODY = 0xB01A1413;
    static final int SETTINGS = 0xFF211B1A, FIELD = 0xFF2E2625;
    static final int TEXT = 0xFFEDE0DE, MUTED = 0xFFCDB7B4;
    static final int ACCENT = 0xFFFFB4AA, ENABLED = 0xFFDF9D95, INK = 0xFF5F150F;
    static final int TRACK = 0xFF56413E;
    static final float FONT = 8.3F;

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

    static void toggle(float x, float y, boolean checked)
    {
        NanoVGUI.fillRounded(x, y, x + 26, y + 16, 8, checked ? ACCENT : FIELD);
        if(!checked) NanoVGUI.outlineRounded(x, y, x + 26, y + 16, 8, 1, 0xFF9E8D8B);
        float center = x + (checked ? 18 : 8);
        NanoVGUI.circle(center, y + 8, 6, checked ? INK : MUTED);
        if(checked)
        {
            NanoVGUI.line(center - 2, y + 8, center - 0.5F, y + 9.5F, 0.8F, ACCENT);
            NanoVGUI.line(center - 0.5F, y + 9.5F, center + 2.5F, y + 6.5F, 0.8F, ACCENT);
        }
    }

    static float openingScale(long elapsed)
    {
        double seconds = Math.max(0, elapsed) / 1000.0;
        return seconds >= 0.32 ? 1 : (float)(1 - 0.42 * Math.exp(-17 * seconds) * Math.cos(23 * seconds));
    }
}

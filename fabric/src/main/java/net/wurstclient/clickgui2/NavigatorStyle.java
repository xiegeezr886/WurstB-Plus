package net.wurstclient.clickgui2;

import net.wurstclient.hud2.nanovg.NanoVGUI;
import org.lwjgl.nanovg.NVGPaint;
import org.lwjgl.nanovg.NanoVG;
import org.lwjgl.system.MemoryStack;

/** Opai palette with a translucent category sidebar and solid module area. */
final class NavigatorStyle
{
    static final int BACKGROUND = 0xFF000000 | (OpaiStyle.BODY & 0xFFFFFF);
    static final int SIDEBAR = (OpaiStyle.BODY & 0xFF000000) | (OpaiStyle.HEADER & 0xFFFFFF);
    static final int CARD = 0x32000000 | (OpaiStyle.SETTINGS & 0xFFFFFF);
    static final int AMBIENT = 0x01000000 | (OpaiStyle.ACCENT & 0xFFFFFF);
    static final int VALUE_TEXT = 0xDC000000 | (OpaiStyle.TEXT & 0xFFFFFF);
    static final int LABEL = 0xC8000000 | (OpaiStyle.MUTED & 0xFFFFFF);
    static final int DESCRIPTION = 0x46000000 | (OpaiStyle.MUTED & 0xFFFFFF);
    static final int CATEGORY_HINT = 0x40000000 | (OpaiStyle.MUTED & 0xFFFFFF);

    private NavigatorStyle() {}

    static void roundedGradient(float x, float y, float width, float height,
        float radius, int first, int second)
    {
        roundedGradient(x, y, width, height, radius, first, second, false);
    }

    static void roundedGradient(float x, float y, float width, float height,
        float radius, int first, int second, boolean vertical)
    {
        var context = NanoVGUI.context();
        long vg = context.getHandle();
        try(MemoryStack stack = MemoryStack.stackPush())
        {
            NVGPaint paint = NVGPaint.malloc(stack);
            NanoVG.nvgLinearGradient(vg, x, y, vertical ? x : x + width,
                vertical ? y + height : y, context.getColor(first), context.getColor(second), paint);
            NanoVG.nvgBeginPath(vg);
            NanoVG.nvgRoundedRect(vg, x, y, width, height, radius);
            NanoVG.nvgFillPaint(vg, paint);
            NanoVG.nvgFill(vg);
        }
    }

    // RenderUtil.dropShadow's original layer spacing and alpha falloff.
    static void dropShadow(int layers, float x, float y, float width,
        float height, float strength, float radius)
    {
        for(float spread = 0; spread <= layers / 2F; spread += 0.5F)
            NanoVGUI.fillRounded(x - spread / 2, y - spread / 2,
                x + width + spread / 2, y + height + spread / 2, radius,
                ((int)Math.max(0.5, (strength - spread * 1.2) / 5.5)) << 24);
    }

    /** Keep the shadow outside the window so it cannot darken translucent panels. */
    static void outerShadow(int layers, float x, float y, float width,
        float height, float strength, float radius, float cutoutRadius)
    {
        var context = NanoVGUI.context();
        long vg = context.getHandle();
        for(float spread = 0; spread <= layers / 2F; spread += 0.5F)
        {
            float outerRadius = Math.min(radius, cutoutRadius + spread / 2);
            NanoVG.nvgBeginPath(vg);
            NanoVG.nvgRoundedRect(vg, x - spread / 2, y - spread / 2,
                width + spread, height + spread, outerRadius);
            NanoVG.nvgRoundedRect(vg, x, y, width, height, cutoutRadius);
            NanoVG.nvgPathWinding(vg, NanoVG.NVG_HOLE);
            int alpha = (int)Math.max(0.5, (strength - spread * 1.2) / 5.5);
            NanoVG.nvgFillColor(vg, context.getColor(alpha << 24));
            NanoVG.nvgFill(vg);
        }
    }
}

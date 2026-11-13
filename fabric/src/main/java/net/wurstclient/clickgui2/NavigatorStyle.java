package net.wurstclient.clickgui2;

import net.wurstclient.hud2.nanovg.NanoVGUI;
import org.lwjgl.nanovg.NVGPaint;
import org.lwjgl.nanovg.NanoVG;
import org.lwjgl.system.MemoryStack;

/** Small rendering bridge for the shapes used by Rise's value controls. */
final class NavigatorStyle
{
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
}

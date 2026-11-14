package net.wurstclient.clickgui2;

import java.util.function.IntToDoubleFunction;

/** One measurement shared by panel painting, scrolling and pointer hit tests. */
final class OpaiPanelLayout
{
    private final float[] starts;
    private final float[] heights;
    private final float total;

    OpaiPanelLayout(int count, IntToDoubleFunction measure)
    {
        starts = new float[count];
        heights = new float[count];
        float offset = 0;
        for(int i = 0; i < count; i++)
        {
            starts[i] = offset;
            heights[i] = Math.max(0, (float)measure.applyAsDouble(i));
            offset += heights[i];
        }
        total = offset + 7;
    }

    float start(int index) { return starts[index]; }
    float height(int index) { return heights[index]; }
    float total() { return total; }
    float clampScroll(float offset, float viewport)
    {
        return Math.max(0, Math.min(offset, Math.max(0, total - viewport)));
    }

    int hit(double position, float scroll, float viewport)
    {
        if(position < 0 || position >= viewport) return -1;
        double content = position + scroll;
        for(int i = 0; i < heights.length; i++)
            if(content >= starts[i] && content < starts[i] + heights[i]) return i;
        return -1;
    }
}

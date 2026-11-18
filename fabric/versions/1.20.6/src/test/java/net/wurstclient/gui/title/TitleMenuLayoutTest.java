package net.wurstclient.gui.title;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

final class TitleMenuLayoutTest
{
    @Test void restoresOriginalButtonSizesAndSpacing()
    {
        var l = TitleMenuLayout.of(480, 270);
        assertEquals(64, l.size());
        assertEquals(8, l.gap());
        assertEquals(16, l.margin());
        assertEquals(166, l.rowY());
        assertEquals(136, l.x(0, 3));
        assertEquals(28, l.x(0, 6));
    }
    @Test void bothPagesFitAtSupportedGuiSizes()
    {
        for(int[] size : new int[][]{{320,180},{480,270},{640,360},{960,540},{3440,1440}})
            for(int count : new int[]{3,6,7})
            {
                var l = TitleMenuLayout.of(size[0], size[1]);
                for(int i = 0; i < count; i++)
                {
                    assertTrue(l.x(i,count) >= l.margin());
                    assertTrue(l.x(i,count) + l.size() <= size[0] - l.margin());
                    assertTrue(l.y(i,count) >= l.margin());
                    assertTrue(l.y(i,count) + l.size() < size[1] - 15);
                    if(i > 0 && l.y(i,count) == l.y(i-1,count))
                        assertEquals(l.size() + l.gap(), l.x(i,count) - l.x(i-1,count));
                }
                if(count == 7)
                {
                    assertEquals(l.y(3,7), l.y(4,7), "ClickGUI and Navigator share a row");
                    assertEquals(l.size() + l.gap(), l.x(4,7) - l.x(3,7));
                }
            }
    }
    @Test void flyEasingHasExpectedEndpointsAndOvershoot()
    {
        assertEquals(0, TitleMenuLayout.backIn(0), .00001);
        assertEquals(1, TitleMenuLayout.backIn(1), .00001);
        assertEquals(0, TitleMenuLayout.backOut(0), .00001);
        assertEquals(1, TitleMenuLayout.backOut(1), .00001);
        assertTrue(TitleMenuLayout.backIn(.25F) < 0);
        assertTrue(TitleMenuLayout.backOut(.75F) > 1);
    }
}

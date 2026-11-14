package net.wurstclient.clickgui2;

import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;

final class OpaiPanelLayoutTest
{
    @Test
    void reusedMeasurementsKeepHitTargetsCurrentThroughExpansionAndRowChanges()
    {
        OpaiPanelLayout layout = new OpaiPanelLayout(3, i -> 19.6);
        layout.measure(3, i -> i == 0 ? 99.6 : 19.6);
        assertEquals(99.6F, layout.start(1), 0.001F);
        assertEquals(0, layout.hit(50, 0, 100));
        layout.measure(1, i -> 19.6);
        assertEquals(-1, layout.hit(20, 0, 100));
        assertEquals(0, layout.clampScroll(100, 100));
        layout.measure(0, i -> { throw new AssertionError("empty panel measured a row"); });
        assertEquals(-1, layout.hit(0, 0, 100));
        layout.measure(2, i -> 20);
        assertEquals(1, layout.hit(20, 0, 100));
    }

    @Test
    void expandedSettingsMoveFollowingRowsAndTheirHitTargetsTogether()
    {
        float[] heights = {19.6F, 99.6F, 19.6F};
        OpaiPanelLayout layout = new OpaiPanelLayout(3, i -> heights[i]);
        assertEquals(119.2F, layout.start(2), 0.001F);
        assertEquals(1, layout.hit(65, 0, 140));
        assertEquals(2, layout.hit(120, 0, 140));
        assertEquals(2, layout.hit(20, 100, 40));
        assertEquals(-1, layout.hit(40, 100, 40));
        assertEquals(-1, layout.hit(-1, 100, 40));
    }

    @Test
    void partiallyRevealedSettingsHaveOnlyTheirVisibleHitArea()
    {
        OpaiPanelLayout layout = new OpaiPanelLayout(2, i -> i == 0 ? 59.6 : 19.6);
        assertEquals(0, layout.hit(59, 0, 60));
        assertEquals(1, layout.hit(59.6, 0, 60));
        assertEquals(-1, layout.hit(60, 0, 60));
        assertEquals(-1, layout.hit(80, 0, 100)); // bottom padding
    }

    @Test
    void collapsingContentClampsOldScrollWithoutLeavingBlankSpace()
    {
        OpaiPanelLayout expanded = new OpaiPanelLayout(10, i -> 100);
        assertEquals(807, expanded.clampScroll(900, 200));
        OpaiPanelLayout collapsed = new OpaiPanelLayout(2, i -> 19.6);
        assertEquals(0, collapsed.clampScroll(807, 200));
        assertEquals(0, collapsed.clampScroll(-5, 20));
    }

    @Test
    void fractionalWheelOffsetsAndEmptyPanelsAreSupported()
    {
        OpaiPanelLayout layout = new OpaiPanelLayout(20, i -> 19.6);
        assertEquals(0.75F, layout.clampScroll(0.75F, 100));
        assertEquals(1, layout.hit(19, 0.75F, 100));
        OpaiPanelLayout empty = new OpaiPanelLayout(0, i -> 0);
        assertEquals(-1, empty.hit(2, 0, 7));
        assertEquals(0, empty.clampScroll(100, 100));
    }
}

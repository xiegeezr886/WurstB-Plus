package net.wurstclient.clickgui2;

import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;

final class GuiReferenceViewportTest
{
    @Test
    void matchesMeasuredReferencePixelsAfterRemovingVideoBorders()
    {
        GuiReferenceViewport opai = GuiReferenceViewport.fit(2512, 1440, GuiReferenceViewport.OPAI_HEIGHT);
        assertEquals(64, opai.screen(19.6), 0.01);
        assertEquals(496, opai.screen(152), 1);
        GuiReferenceViewport rise = GuiReferenceViewport.fit(810, 653, GuiReferenceViewport.RISE_HEIGHT);
        assertEquals(640, rise.screen(400), 0.01);
        assertEquals(480, rise.screen(300), 0.01);
        assertEquals(160, rise.screen(100), 0.01);
    }

    @Test
    void minecraftGuiScaleDoesNotChangeThePhysicalControlSize()
    {
        GuiReferenceViewport scaleTwo = GuiReferenceViewport.fit(960, 540, GuiReferenceViewport.OPAI_HEIGHT);
        GuiReferenceViewport scaleThree = GuiReferenceViewport.fit(640, 360, GuiReferenceViewport.OPAI_HEIGHT);
        assertEquals(scaleTwo.screen(152) * 2, scaleThree.screen(152) * 3, 0.001);
        assertEquals(48, scaleThree.screen(19.6) * 3, 0.01);
        assertEquals(scaleTwo.width(), scaleThree.width());
    }

    @Test
    void transformedPointerStillSelectsTheSameExpandedAndScrolledRow()
    {
        GuiReferenceViewport viewport = GuiReferenceViewport.fit(640, 360, GuiReferenceViewport.OPAI_HEIGHT);
        OpaiPanelLayout layout = new OpaiPanelLayout(3, i -> i == 1 ? 119.6 : 19.6);
        float scroll = 50, panelY = 45, header = 21;
        double localY = panelY + header + layout.start(1) + 70 - scroll;
        double pointer = viewport.screen(localY);
        assertEquals(localY, viewport.local(pointer), 0.001);
        assertEquals(1, layout.hit(viewport.local(pointer) - panelY - header, scroll, 200));
        assertEquals(-1, layout.hit(viewport.local(viewport.screen(panelY + header + 200)) - panelY - header, scroll, 200));
    }
}

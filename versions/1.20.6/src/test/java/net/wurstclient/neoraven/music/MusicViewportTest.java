package net.wurstclient.neoraven.music;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

class MusicViewportTest {
  @Test
  void fitsBothAxesAndPreservesOriginalAspectRatio() {
    for (int[] screen : new int[][] {{500, 350}, {427, 249}, {300, 600}, {1280, 360}}) {
      var layout = MusicViewport.fit(screen[0], screen[1], 1);
      assertTrue(840 * layout.scale() <= screen[0] * .92F + .001F);
      assertTrue(520 * layout.scale() <= screen[1] * .92F + .001F);
      assertEquals(screen[0] / 2F, layout.x() + 420 * layout.scale(), .001F);
      assertEquals(screen[1] / 2F, layout.y() + 260 * layout.scale(), .001F);
    }
  }

  @Test
  void moduleScaleShrinksAndCapsGrowthAtFit() {
    var base = MusicViewport.fit(500, 350, 1);
    assertEquals(base.scale() / 2, MusicViewport.fit(500, 350, .5F).scale(), .0001F);
    assertEquals(base.scale(), MusicViewport.fit(500, 350, 2).scale(), .0001F);
    assertEquals(1, MusicViewport.fit(1920, 1080, 1).scale());
    assertTrue(MusicViewport.fit(1920, 1080, 2).scale() > 1);
  }
}

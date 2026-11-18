package net.wurstclient.neoraven.music;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

class MusicPageMotionTest {
  @Test
  void changesPageOnlyAfterFadeAndHonorsLatestRequest() {
    var motion = new MusicPageMotion("推荐");
    motion.request("搜索");
    assertNull(motion.update(70));
    assertEquals(.5F, motion.alpha(), .001F);
    motion.request("设置");
    assertEquals("设置", motion.update(70));
    assertEquals(0, motion.alpha());
    assertFalse(motion.ready());
    motion.update(100);
    motion.update(40);
    assertTrue(motion.ready());
  }

  @Test
  void returningToCurrentPageReversesWithoutJumping() {
    var motion = new MusicPageMotion("推荐");
    motion.request("搜索");
    motion.update(50);
    float before = motion.alpha();
    float offset = motion.offset();
    motion.request("推荐");
    assertEquals(before, motion.alpha());
    assertEquals(offset, motion.offset());
    assertNull(motion.update(100));
    assertTrue(motion.ready());
  }
}

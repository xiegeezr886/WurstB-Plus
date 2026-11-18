package net.wurstclient.neoraven.music;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

class MusicTransportMotionTest {
  @Test
  void seekInterpolatesWithoutOvershootAndDraggingFollowsPointer() {
    var motion = new MusicTransportMotion();
    motion.update(.2F, true, 16, false);
    motion.update(.8F, true, 16, false);
    assertTrue(motion.progress() > .2F && motion.progress() < .8F);
    for (int i = 0; i < 80; i++) motion.update(.8F, true, 16, false);
    assertEquals(.8F, motion.progress(), .0001F);
    motion.update(.1F, false, 16, true);
    assertEquals(.1F, motion.progress());
  }

  @Test
  void pauseSettlesWaveWithoutResettingItsPhase() {
    var motion = new MusicTransportMotion();
    motion.update(.2F, true, 16, false);
    motion.update(.2F, false, 16, false);
    assertTrue(motion.playing() > 0 && motion.playing() < 1);
    for (int i = 0; i < 80; i++) motion.update(.2F, false, 16, false);
    float phase = motion.phase();
    motion.update(.2F, false, 16, false);
    assertEquals(phase, motion.phase());
    motion.update(.2F, true, 16, false);
    assertTrue(motion.playing() > 0);
  }
}

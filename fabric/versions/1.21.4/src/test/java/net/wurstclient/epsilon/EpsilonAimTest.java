package net.wurstclient.epsilon;

import static org.junit.jupiter.api.Assertions.*;

import net.wurstclient.util.Rotation;
import org.junit.jupiter.api.Test;

final class EpsilonAimTest {
  @Test
  void takesShortestYawAcrossWrap() {
    var r = EpsilonAim.step(new Rotation(179, 0), new Rotation(-179, 0), 1);
    assertEquals(180, r.yaw(), .001);
  }

  @Test
  void angularStepIsBoundedAndDoesNotOvershoot() {
    var r = EpsilonAim.step(new Rotation(0, 0), new Rotation(30, 40), 10);
    assertEquals(10, Math.hypot(r.yaw(), r.pitch()), .001);
    var end = EpsilonAim.step(r, new Rotation(30, 40), 100);
    assertEquals(30, end.yaw());
    assertEquals(40, end.pitch());
  }

  @Test
  void unreachableAndVerticalShotsHaveNoBallisticSolution() {
    assertTrue(Float.isNaN(EpsilonAim.projectilePitch(0, 10, 3)));
    assertTrue(Float.isNaN(EpsilonAim.projectilePitch(1000, 0, 1)));
  }

  @Test
  void bowPitchAccountsForGravity() {
    assertTrue(EpsilonAim.projectilePitch(20, 0, 3) < 0);
    assertTrue(EpsilonAim.projectilePitch(20, 5, 3) < EpsilonAim.projectilePitch(20, 0, 3));
  }
}

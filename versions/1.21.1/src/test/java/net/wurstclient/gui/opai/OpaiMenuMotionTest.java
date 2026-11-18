package net.wurstclient.gui.opai;

import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;

final class OpaiMenuMotionTest
{
 @Test void slowFrameHandsOffAtTheEdgeAndResumesWithoutMirroring() {
  OpaiMenuMotion outgoing = new OpaiMenuMotion(true,0,0,1000);
  assertTrue(outgoing.crossed(1400));
  outgoing.handoff(1400);
  OpaiMenuMotion incoming = OpaiMenuMotion.enter(true,1800);
  assertEquals(-90,incoming.rotation(1800),.5);
  assertEquals(-180,incoming.rotation(2800),.0001);
  assertEquals(1,incoming.phase(2800),.0001);
  assertTrue(incoming.finished(2800));
  for(int angle=-180;angle<=180;angle++)assertTrue(OpaiMenuMotion.projection(angle,1)[0]>=0);
  assertEquals(0,OpaiMenuMotion.projection(90,1)[0],.0001);
 }
 @Test void reverseExitRetainsCurrentPoseAndUsesLongerPhase() {
  OpaiMenuMotion incoming = new OpaiMenuMotion(true,-88,.8,0);
  OpaiMenuMotion outgoing = new OpaiMenuMotion(false,incoming.rotation(120),incoming.phase(120),120);
  assertEquals(incoming.rotation(120),outgoing.rotation(120),.0001);
  assertEquals(incoming.phase(120),outgoing.phase(120),.0001);
  assertFalse(outgoing.finished(959));
  assertTrue(outgoing.finished(960));
  assertEquals(0,outgoing.rotation(960),.0001);
  assertEquals(0,outgoing.fade(960),.0001);
 }
 @Test void enteringWithoutHandoffStartsEdgeOnAndControlsFadeLate() {
  OpaiMenuMotion motion = OpaiMenuMotion.enter(true,10000);
  assertEquals(-90,motion.rotation(10000),.0001);
  assertTrue(motion.fade(10000)<.05);
  assertFalse(motion.finished(10699));
  assertTrue(motion.finished(10700));
 }

 @Test void titleFullyFadesBeforeStartingAFreshFlipEvenAfterASlowFrame() {
  OpaiMenuFade fade = new OpaiMenuFade(false,1000);
  assertEquals(1,fade.opacity(1000));
  assertEquals(.5,fade.opacity(1120),.0001);
  assertFalse(fade.finished(1239));
  assertTrue(fade.finished(1600));
  assertEquals(0,fade.opacity(1600));
  OpaiMenuMotion incoming = OpaiMenuMotion.enter(true,1600);
  assertEquals(-90,incoming.rotation(1600),.0001);
  assertEquals(0,incoming.fade(1600));
  assertFalse(incoming.finished(1600));
  assertTrue(incoming.finished(2300));
 }

 @Test void returningTitleFadesInWithoutOvershoot() {
  OpaiMenuFade fade = new OpaiMenuFade(true,1000);
  assertEquals(0,fade.opacity(900));
  assertEquals(0,fade.opacity(1000));
  assertEquals(.5,fade.opacity(1120),.0001);
  assertEquals(1,fade.opacity(1240));
  assertEquals(1,fade.opacity(2000));
 }
}

/* OpenOpal VelocityMode KillAura integration defaults; GPL-3.0. */
package net.wurstclient.opal;
public interface VelocityMode {
    default boolean isAttacking() { return false; }
    default int getHitSelectSkips() { return 0; }
    default boolean consumeHitSelectSkip() { return false; }
}

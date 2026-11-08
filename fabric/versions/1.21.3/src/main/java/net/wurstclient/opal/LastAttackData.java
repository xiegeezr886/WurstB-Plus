/*
 * Ported directly from OpenOpal: client/feature/module/impl/combat/killaura/target/LastAttackData.java
 * Original source: GPL-3.0; see META-INF/licenses/openopal/LICENSE.
 * Minecraft names and host integration adapted for WurstB+ Plus.
 */
package net.wurstclient.opal;


public final class LastAttackData {
    private final Stopwatch stopwatch = new Stopwatch();
    private double damage;

    public LastAttackData(double damage) {
        this.reset(false, damage);
    }

    public void reset(boolean reset, double damage) {
        if (reset) {
            this.stopwatch.reset();
        }
        this.damage = damage;
    }

    public long getTime() {
        return this.stopwatch.getTime();
    }

    public double getDamage() {
        return damage;
    }
}

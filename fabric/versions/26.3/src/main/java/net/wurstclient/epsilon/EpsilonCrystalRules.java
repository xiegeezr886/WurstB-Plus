/* Epsilon CrystalCombatRules.kt, GPL-3.0. */
package net.wurstclient.epsilon;

public record EpsilonCrystalRules(
    double minimum,
    double maximumSelf,
    double ratio,
    boolean safety,
    boolean safetyOverride,
    boolean force,
    double forceHealth,
    double forceDamage,
    boolean armorBreaker,
    int armorScale,
    double lethalMultiplier) {
  public record Target(double health, double minArmor, double maxArmor) {}

  public boolean unsafe(double damage, double health, boolean creative) {
    return !creative && ((safety && damage >= health - .5) || damage > maximumSelf);
  }

  public boolean accepts(
      double damage, double self, double health, boolean creative, Target target) {
    if (!Double.isFinite(damage) || !Double.isFinite(self) || damage < 0 || self < 0) return false;
    boolean unsafe = unsafe(self, health, creative);
    if (unsafe && !safetyOverride) return false;
    if (force
        && target.health <= forceHealth
        && !(armorBreaker && target.maxArmor > armorScale)
        && safetyOverride
        && unsafe
        && damage < target.health + .5) return false;
    return (safetyOverride || self == 0 || damage / self >= ratio)
        && damage >= minimum(target, damage);
  }

  public double minimum(Target target, double damage) {
    if (damage * (1 + lethalMultiplier) >= target.health + .5) return 1.5;
    if (force && target.health <= forceHealth) return forceDamage;
    if (armorBreaker && target.minArmor < armorScale) return 1.5;
    return minimum;
  }
}

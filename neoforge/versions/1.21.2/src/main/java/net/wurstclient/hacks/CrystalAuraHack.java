/* Epsilon CrystalAura / CrystalCombatRules / CrystalTiming, GPL-3.0.
 * Native Fabric 1.20.1 placement and shared motion arbitration adapter. */
package net.wurstclient.hacks;

import java.util.*;
import net.minecraft.core.*;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.boss.enderdragon.EndCrystal;
import net.minecraft.world.item.*;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.*;
import net.wurstclient.Category;
import net.wurstclient.epsilon.*;
import net.wurstclient.events.*;
import net.wurstclient.hack.*;
import net.wurstclient.settings.*;
import net.wurstclient.settings.SliderSetting.ValueDisplay;
import net.wurstclient.settings.filterlists.*;
import net.wurstclient.util.*;

public final class CrystalAuraHack extends Hack
    implements UpdateListener, PreMotionListener, PostMotionListener {
  private final SliderSetting range = number("Range", 4.5, 1, 6, .1);
  private final SliderSetting wallRange = number("Wall range", 3, 0, 6, .1);
  private final SliderSetting searchRange = number("Search range", 8, 1, 12, .5);
  private final CheckboxSetting autoPlace = new CheckboxSetting("Auto-place crystals", true);
  private final CheckboxSetting rotate = new CheckboxSetting("Rotate", true);
  private final SliderSetting breakDelay = number("Break delay", 50, 0, 1000, 10);
  private final SliderSetting placeDelay = number("Place delay", 100, 0, 1000, 10);
  private final SliderSetting minAge = number("Min crystal age", 0, 0, 20, 1);
  private final SliderSetting attempts = number("Attack attempts", 5, 1, 20, 1);
  private final SliderSetting minDamage = number("Min damage", 4, 0, 36, .5);
  private final SliderSetting maxSelf = number("Max self damage", 8, 0, 36, .5);
  private final SliderSetting ratio = number("Damage ratio", 1.2, 0, 5, .1);
  private final CheckboxSetting safety = new CheckboxSetting("Anti suicide", true);
  private final CheckboxSetting override = new CheckboxSetting("Safety override", false);
  private final CheckboxSetting force = new CheckboxSetting("Force low health", true);
  private final SliderSetting forceHealth = number("Force health", 8, 0, 36, .5);
  private final SliderSetting forceDamage = number("Force damage", 2, 0, 36, .5);
  private final CheckboxSetting armorBreaker = new CheckboxSetting("Armor breaker", true);
  private final SliderSetting armorScale = number("Armor scale", 5, 1, 20, 1);
  private final SliderSetting lethal = number("Lethal multiplier", 1.5, 0, 4, .1);
  private final EntityFilterList filters = CrystalAuraFilterList.create();
  private final MotionActionQueue<Plan> actions = new MotionActionQueue<>();
  private final Map<Integer, Integer> attacked = new HashMap<>();
  private final Map<BlockPos, Long> placed = new HashMap<>();
  private RotationQueue rotations;
  private Plan pending;
  private Object player, world;
  private long nextBreak, nextPlace;

  private record Plan(
      EndCrystal crystal, BlockPos base, LivingEntity target, Vec3 aim, double damage) {}

  private static SliderSetting number(
      String name, double value, double min, double max, double step) {
    return new SliderSetting(name, value, min, max, step, ValueDisplay.DECIMAL);
  }

  private static long now() {
    return System.nanoTime() / 1_000_000;
  }

  public CrystalAuraHack() {
    super("CrystalAura");
    setCategory(Category.COMBAT);
    addConflictGroup(HackConflictGroup.COMBAT_TARGETING);
    for (Setting s :
        new Setting[] {
          range,
          wallRange,
          searchRange,
          autoPlace,
          rotate,
          breakDelay,
          placeDelay,
          minAge,
          attempts,
          minDamage,
          maxSelf,
          ratio,
          safety,
          override,
          force,
          forceHealth,
          forceDamage,
          armorBreaker,
          armorScale,
          lethal
        }) addSetting(s);
    filters.forEach(this::addSetting);
  }

  protected void onEnable() {
    reset();
    rotations = new RotationQueue(RotationQueue.Priority.COMBAT);
    rotations.start();
    EVENTS.add(UpdateListener.class, this);
    EVENTS.add(PreMotionListener.class, this);
    EVENTS.add(PostMotionListener.class, this);
  }

  protected void onDisable() {
    EVENTS.remove(UpdateListener.class, this);
    EVENTS.remove(PreMotionListener.class, this);
    EVENTS.remove(PostMotionListener.class, this);
    rotations.stop();
    reset();
  }

  private void reset() {
    actions.clear();
    pending = null;
    attacked.clear();
    placed.clear();
    nextBreak = nextPlace = 0;
    player = MC.player;
    world = MC.level;
  }

  private boolean ready() {
    return EpsilonTargets.ready()
        && MC.screen == null
        && !MC.player.isUsingItem()
        && !WURST.getHax().scaffoldWalkHack.isEnabled();
  }

  public void onUpdate() {
    if (player != MC.player || world != MC.level) {
      reset();
      rotations.clear();
    }
    pending = null;
    actions.clear();
    if (!ready()) {
      rotations.clear();
      return;
    }
    long time = now();
    attacked.keySet().removeIf(id -> !(MC.level.getEntity(id) instanceof EndCrystal));
    placed.entrySet().removeIf(e -> time - e.getValue() > 500);
    List<LivingEntity> targets =
        filters
            .applyTo(
                EpsilonTargets.acquire(searchRange.getValue(), 360, 15, true, true).stream()
                    .map(e -> (Entity) e))
            .map(e -> (LivingEntity) e)
            .limit(8)
            .toList();
    if (targets.isEmpty()) {
      rotations.clear();
      return;
    }
    if (time >= nextBreak)
      for (Entity entity :
          MC.level.getEntities(
              MC.player,
              MC.player.getBoundingBox().inflate(range.getValue()),
              e -> e instanceof EndCrystal)) {
        EndCrystal crystal = (EndCrystal) entity;
        if (crystal.tickCount < minAge.getValueI()
            || attacked.getOrDefault(crystal.getId(), 0) >= attempts.getValueI()
            || !reachable(crystal.getBoundingBox().getCenter())) continue;
        Plan plan =
            evaluate(
                crystal, null, crystal.position(), crystal.getBoundingBox().getCenter(), targets);
        if (plan != null && (pending == null || plan.damage > pending.damage)) pending = plan;
      }
    if (pending == null && autoPlace.isChecked() && time >= nextPlace && crystalSlot() != -1) {
      BlockPos center = MC.player.blockPosition();
      int r = (int) Math.ceil(range.getValue());
      for (BlockPos base :
          BlockPos.betweenClosed(center.offset(-r, -r, -r), center.offset(r, r, r))) {
        if (!canPlace(base) || placed.containsKey(base)) continue;
        Vec3 aim = Vec3.atBottomCenterOf(base.above());
        if (!reachable(aim)) continue;
        Plan plan = evaluate(null, base.immutable(), aim, aim, targets);
        if (plan != null && (pending == null || plan.damage > pending.damage)) pending = plan;
      }
    }
    if (pending == null) rotations.clear();
  }

  private EpsilonCrystalRules rules() {
    return new EpsilonCrystalRules(
        minDamage.getValue(),
        maxSelf.getValue(),
        ratio.getValue(),
        safety.isChecked(),
        override.isChecked(),
        force.isChecked(),
        forceHealth.getValue(),
        forceDamage.getValue(),
        armorBreaker.isChecked(),
        armorScale.getValueI(),
        lethal.getValue());
  }

  private EpsilonCrystalRules.Target snapshot(LivingEntity target) {
    double min = 100, max = 0;
    boolean any = false;
    for (ItemStack stack : target.getArmorSlots())
      if (!stack.isEmpty() && stack.isDamageableItem()) {
        double durability =
            (stack.getMaxDamage() - stack.getDamageValue()) * 100.0 / stack.getMaxDamage();
        min = Math.min(min, durability);
        max = Math.max(max, durability);
        any = true;
      }
    return new EpsilonCrystalRules.Target(
        target.getHealth() + target.getAbsorptionAmount(), any ? min : 0, max);
  }

  private Plan evaluate(
      EndCrystal crystal, BlockPos base, Vec3 explosion, Vec3 aim, List<LivingEntity> targets) {
    double self = DamageUtils.calculateDamage(explosion, MC.player),
        health = MC.player.getHealth() + MC.player.getAbsorptionAmount();
    EpsilonCrystalRules rules = rules();
    Plan best = null;
    if (rules.unsafe(self, health, MC.player.isCreative()) && !override.isChecked()) return null;
    for (LivingEntity target : targets) {
      double damage = DamageUtils.calculateDamage(explosion, target);
      if (rules.accepts(damage, self, health, MC.player.isCreative(), snapshot(target))
          && (best == null || damage > best.damage))
        best = new Plan(crystal, base, target, aim, damage);
    }
    return best;
  }

  public void onPreMotion() {
    if (pending == null || !ready()) return;
    actions.begin(MC.player, MC.level, MC.player.tickCount);
    actions.schedule(pending);
    if (rotate.isChecked()) rotations.setRotation(RotationUtils.getNeededRotations(pending.aim));
  }

  public void onPostMotion() {
    if (MC.player == null) return;
    Plan plan = actions.consume(MC.player, MC.level, MC.player.tickCount);
    pending = null;
    if (plan == null
        || !ready()
        || rotate.isChecked() && !rotations.wasApplied()
        || !EpsilonTargets.valid(plan.target)
        || !reachable(plan.aim)) return;
    Vec3 explosion =
        plan.crystal != null ? plan.crystal.position() : Vec3.atBottomCenterOf(plan.base.above());
    if (evaluate(plan.crystal, plan.base, explosion, plan.aim, List.of(plan.target)) == null)
      return;
    if (plan.crystal != null) {
      if (plan.crystal.isRemoved() || MC.level.getEntity(plan.crystal.getId()) != plan.crystal)
        return;
      MC.gameMode.attack(MC.player, plan.crystal);
      MC.player.swing(InteractionHand.MAIN_HAND);
      attacked.merge(plan.crystal.getId(), 1, Integer::sum);
      nextBreak = now() + breakDelay.getValueI();
      return;
    }
    if (!canPlace(plan.base)) return;
    int slot = crystalSlot();
    if (slot == -1) return;
    int previous = MC.player.getInventory().selected;
    InteractionHand hand = slot == 40 ? InteractionHand.OFF_HAND : InteractionHand.MAIN_HAND;
    try {
      if (slot != 40) MC.player.getInventory().selected = slot;
      var result =
          MC.gameMode.useItemOn(
              MC.player, hand, new BlockHitResult(plan.aim, Direction.UP, plan.base, false));
      if (result.consumesAction()) {
        MC.player.swing(hand);
        placed.put(plan.base, now());
        nextPlace = now() + placeDelay.getValueI();
      }
    } finally {
      MC.player.getInventory().selected = previous;
    }
  }

  private boolean reachable(Vec3 point) {
    Vec3 eye = MC.player.getEyePosition();
    BlockHitResult hit =
        MC.level.clip(
            new ClipContext(
                eye, point, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, MC.player));
    boolean visible =
        hit.getType() == HitResult.Type.MISS || hit.getLocation().distanceToSqr(point) < .01;
    return eye.distanceTo(point)
        <= (visible ? range.getValue() : Math.min(range.getValue(), wallRange.getValue()));
  }

  private boolean canPlace(BlockPos base) {
    var block = MC.level.getBlockState(base);
    if (!block.is(Blocks.OBSIDIAN) && !block.is(Blocks.BEDROCK)) return false;
    if (!MC.level.isEmptyBlock(base.above()) || !MC.level.getWorldBorder().isWithinBounds(base))
      return false;
    return MC.level
        .getEntities((Entity) null, new AABB(base.above()).expandTowards(0, 1, 0))
        .isEmpty();
  }

  private int crystalSlot() {
    if (MC.player.getOffhandItem().is(Items.END_CRYSTAL)) return 40;
    for (int i = 0; i < 9; i++)
      if (MC.player.getInventory().getItem(i).is(Items.END_CRYSTAL)) return i;
    return -1;
  }
}

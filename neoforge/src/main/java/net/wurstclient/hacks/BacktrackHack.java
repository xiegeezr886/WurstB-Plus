/* Epsilon Backtrack.kt port, GPL-3.0. Inbound-only queue with world ownership. */
package net.wurstclient.hacks;

import java.util.concurrent.ThreadLocalRandom;
import net.minecraft.network.protocol.BundlePacket;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.*;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.*;
import net.wurstclient.Category;
import net.wurstclient.epsilon.*;
import net.wurstclient.events.*;
import net.wurstclient.hack.Hack;
import net.wurstclient.settings.*;
import net.wurstclient.settings.SliderSetting.ValueDisplay;

public final class BacktrackHack extends Hack
    implements UpdateListener, PacketInputListener, PlayerAttacksEntityListener {
  private enum TargetMode {
    ATTACK,
    RANGE
  }

  private final EnumSetting<TargetMode> mode =
      new EnumSetting<>("Target mode", TargetMode.values(), TargetMode.ATTACK);
  private final SliderSetting minRange = number("Min range", 2.5, 0, 10, .1),
      maxRange = number("Max range", 5, 0, 10, .1);
  private final SliderSetting minDelay = number("Min delay", 100, 0, 1000, 10),
      maxDelay = number("Max delay", 150, 0, 1000, 10);
  private final SliderSetting nextMin = number("Next backtrack min", 0, 0, 2000, 10),
      nextMax = number("Next backtrack max", 10, 0, 2000, 10);
  private final SliderSetting buffer = number("Tracking buffer", 500, 0, 2000, 10),
      lastAttack = number("Last attack time", 1000, 0, 5000, 50);
  private final SliderSetting chance = number("Chance", 50, 0, 100, 1),
      hurt = number("Hurt time", 3, 0, 10, 1);
  private final CheckboxSetting pauseHurt = new CheckboxSetting("Pause on hurt time", false);
  private final CheckboxSetting players = new CheckboxSetting("Players", true),
      mobs = new CheckboxSetting("Mobs", false),
      animals = new CheckboxSetting("Animals", false),
      villagers = new CheckboxSetting("Villagers", false),
      ambient = new CheckboxSetting("Ambient", false),
      water = new CheckboxSetting("Water", false),
      others = new CheckboxSetting("Others", false),
      invisible = new CheckboxSetting("Invisible", true);
  private final EpsilonPacketQueue packets = new EpsilonPacketQueue();
  private LivingEntity target;
  private Vec3 tracked = Vec3.ZERO;
  private Object world, player, connection;
  private long attackedAt = -10000, inRangeAt = -10000, nextAllowed;
  private int delay;
  private boolean rolled, tracking;
  private TargetMode previousMode;

  private static long now() {
    return System.nanoTime() / 1_000_000;
  }

  private static SliderSetting number(String n, double v, double lo, double hi, double step) {
    return new SliderSetting(n, v, lo, hi, step, ValueDisplay.DECIMAL);
  }

  private static int random(SliderSetting a, SliderSetting b) {
    return ThreadLocalRandom.current()
        .nextInt(
            Math.min(a.getValueI(), b.getValueI()), Math.max(a.getValueI(), b.getValueI()) + 1);
  }

  public BacktrackHack() {
    super("Backtrack");
    setCategory(Category.COMBAT);
    for (Setting s :
        new Setting[] {
          mode,
          minRange,
          maxRange,
          minDelay,
          maxDelay,
          nextMin,
          nextMax,
          buffer,
          lastAttack,
          chance,
          pauseHurt,
          hurt
        }) addSetting(s);
    for (Setting s :
        new Setting[] {players, mobs, animals, villagers, ambient, water, others, invisible}) {
      s.visibleWhen(() -> mode.getSelected() == TargetMode.RANGE);
      addSetting(s);
    }
  }

  protected synchronized void onEnable() {
    clear();
    EVENTS.add(UpdateListener.class, this);
    EVENTS.add(PacketInputListener.class, this);
    EVENTS.add(PlayerAttacksEntityListener.class, this);
  }

  protected synchronized void onDisable() {
    EVENTS.remove(UpdateListener.class, this);
    EVENTS.remove(PacketInputListener.class, this);
    EVENTS.remove(PlayerAttacksEntityListener.class, this);
    packets.flush(Long.MAX_VALUE);
    clear();
  }

  private void clear() {
    packets.clear();
    target = null;
    tracking = false;
    rolled = false;
    previousMode = mode.getSelected();
    attackedAt = inRangeAt = -10000;
    nextAllowed = 0;
    world = MC.level;
    player = MC.player;
    connection = MC.getConnection();
  }

  private boolean sameSession() {
    return world == MC.level && player == MC.player && connection == MC.getConnection();
  }

  private void finish() {
    packets.flush(Long.MAX_VALUE);
    if (target != null) nextAllowed = now() + random(nextMin, nextMax);
    target = null;
    tracking = false;
    inRangeAt = -10000;
  }

  public synchronized void onPlayerAttacksEntity(Entity entity) {
    if (!sameSession()) clear();
    if (!EpsilonTargets.valid(entity)) return;
    attackedAt = now();
    rolled = ThreadLocalRandom.current().nextInt(100) < chance.getValueI();
    if (mode.getSelected() == TargetMode.ATTACK) select((LivingEntity) entity);
  }

  private void select(LivingEntity candidate) {
    if (candidate == target) return;
    packets.flush(Long.MAX_VALUE);
    target = candidate;
    tracking = false;
    tracked = candidate.position();
    inRangeAt = -10000;
    delay = random(minDelay, maxDelay);
  }

  public synchronized void onUpdate() {
    if (!sameSession()) clear();
    if (previousMode != mode.getSelected()) {
      finish();
      previousMode = mode.getSelected();
    }
    if (!EpsilonTargets.ready() || MC.screen != null) {
      finish();
      return;
    }
    if (mode.getSelected() == TargetMode.RANGE) {
      Entity candidate =
          RangeTargetSelector.nearest(
              MC.level.entitiesForRendering(),
              this::allowedRangeTarget,
              entity -> distance(entity.getBoundingBox(), MC.player.getEyePosition()),
              minRange.getValue(),
              maxRange.getValue());
      if (candidate != null) select((LivingEntity) candidate);
      else {
        finish();
        return;
      }
    }
    long time = now();
    tracking = valid(time);
    if (tracking) packets.flush(time - delay);
    else if (packets.size() > 0 || target != null && !EpsilonTargets.valid(target)) finish();
  }

  private boolean allowedRangeTarget(Entity entity) {
    if (!EpsilonTargets.valid(entity)
        || entity instanceof ArmorStand
        || MC.player.isAlliedTo(entity)) return false;
    if (entity instanceof Player)
      return players.isChecked() && (invisible.isChecked() || !entity.isInvisible());
    if (entity instanceof Villager) return villagers.isChecked();
    if (entity instanceof Monster) return mobs.isChecked();
    if (entity instanceof Animal) return animals.isChecked();
    return switch (entity.getType().getCategory()) {
      case AMBIENT, WATER_AMBIENT -> ambient.isChecked();
      case WATER_CREATURE, AXOLOTLS, UNDERGROUND_WATER_CREATURE -> water.isChecked();
      default -> others.isChecked();
    };
  }

  private boolean valid(long time) {
    if (!EpsilonTargets.valid(target) || MC.player.isAlliedTo(target)) return false;
    double distance = EpsilonTargets.distance(target);
    boolean in =
        distance >= Math.min(minRange.getValue(), maxRange.getValue())
            && distance <= Math.max(minRange.getValue(), maxRange.getValue());
    if (in) inRangeAt = time;
    return (in || time - inRangeAt <= buffer.getValueI())
        && MC.player.tickCount > 10
        && rolled
        && time >= nextAllowed
        && time - attackedAt <= lastAttack.getValueI()
        && !WURST.getHax().antiKnockbackHack.blocksBacktrack()
        && (!pauseHurt.isChecked() || target.hurtTime < hurt.getValueI());
  }

  public synchronized void onReceivedPacket(PacketInputEvent event) {
    if (!sameSession()) {
      clear();
      return;
    }
    Packet<?> packet = event.getPacket();
    if (packet instanceof ClientboundDisconnectPacket
        || packet instanceof ClientboundRespawnPacket
        || packet instanceof ClientboundLoginPacket) {
      clear();
      return;
    }
    if (packet instanceof ClientboundPlayerPositionPacket
        || packet instanceof ClientboundSetHealthPacket health && health.getHealth() <= 0) {
      finish();
      return;
    }
    if (WURST.getHax().antiKnockbackHack.blocksBacktrack()) {
      finish();
      return;
    }
    if (packet instanceof ClientboundKeepAlivePacket
        || packet instanceof ClientboundPingPacket
        || packet instanceof ClientboundSystemChatPacket
        || packet instanceof ClientboundPlayerChatPacket) return;
    if (!tracking && packets.size() == 0) return;
    Vec3 position = track(packet);
    if (position != null && MC.player != null && target != null) {
      AABB old = target.getBoundingBox(), actual = old.move(position.subtract(target.position()));
      if (distance(actual, MC.player.getEyePosition())
          < distance(old, MC.player.getEyePosition())) {
        packets.flush(Long.MAX_VALUE);
        return;
      }
    }
    packets.offer(packet, now());
    event.cancel();
  }

  private Vec3 track(Packet<?> packet) {
    if (target == null) return null;
    if (packet instanceof BundlePacket<?> bundle) {
      Vec3 result = null;
      for (Packet<?> child : bundle.subPackets()) {
        Vec3 next = track(child);
        if (next != null) result = next;
      }
      return result;
    }
    if (packet instanceof ClientboundMoveEntityPacket move
        && move.getEntity(MC.level) == target
        && move.hasPosition()) {
      tracked =
          new Vec3(
              decode(tracked.x, move.getXa()),
              decode(tracked.y, move.getYa()),
              decode(tracked.z, move.getZa()));
      return tracked;
    }
    if (packet instanceof ClientboundTeleportEntityPacket tp && tp.getId() == target.getId()) {
      tracked = new Vec3(tp.getX(), tp.getY(), tp.getZ());
      return tracked;
    }
    return null;
  }

  private static double decode(double base, short delta) {
    return delta == 0 ? base : (Math.round(base * 4096) + delta) / 4096.0;
  }

  private static double distance(AABB b, Vec3 p) {
    double x = Math.max(0, Math.max(b.minX - p.x, p.x - b.maxX)),
        y = Math.max(0, Math.max(b.minY - p.y, p.y - b.maxY)),
        z = Math.max(0, Math.max(b.minZ - p.z, p.z - b.maxZ));
    return x * x + y * y + z * z;
  }

  public synchronized String getRenderName() {
    return getName() + (tracking ? " [" + delay + "ms]" : "");
  }
}

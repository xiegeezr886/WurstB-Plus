/* Epsilon TargetManager/TargetRequest adaptation, GPL-3.0.
 * Host friends and AntiBot remain authoritative. */
package net.wurstclient.epsilon;

import static net.wurstclient.WurstClient.MC;

import java.util.Comparator;
import java.util.List;
import net.minecraft.tags.ItemTags;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.phys.*;
import net.wurstclient.WurstClient;
import net.wurstclient.hacks.aura.OpenAura;
import net.wurstclient.util.FakePlayerEntity;
import net.wurstclient.util.Rotation;

public final class EpsilonTargets {
  private EpsilonTargets() {}

  public static boolean ready() {
    return MC.player != null
        && MC.level != null
        && MC.gameMode != null
        && MC.player.isAlive()
        && !MC.player.isSpectator()
        && MC.gui.overlay() == null;
  }

  public static boolean valid(Entity entity) {
    return ready()
        && entity instanceof LivingEntity
        && entity != MC.player
        && entity.isAlive()
        && !entity.isSpectator()
        && entity.isPickable()
        && MC.level.getEntity(entity.getId()) == entity
        && !(entity instanceof FakePlayerEntity)
        && !WurstClient.INSTANCE.getFriends().isFriend(entity)
        && (!(entity instanceof Player p) || !WurstClient.INSTANCE.getHax().antiBotHack.isBot(p));
  }

  public static List<LivingEntity> acquire(
      double range, double fov, int flags, boolean invisible, boolean walls) {
    if (!ready()) return List.of();
    return MC
        .level
        .getEntitiesOfClass(LivingEntity.class, MC.player.getBoundingBox().inflate(range))
        .stream()
        .filter(EpsilonTargets::valid)
        .filter(e -> invisible || !e.isInvisible())
        .filter(
            e ->
                (flags
                        & (e instanceof Player
                            ? 1
                            : e instanceof Enemy ? 2 : e instanceof Animal ? 4 : 8))
                    != 0)
        .filter(e -> distance(e) <= range && yaw(e) <= fov / 2)
        .filter(e -> walls || MC.player.hasLineOfSight(e))
        .sorted(Comparator.comparingDouble(MC.player::distanceToSqr))
        .limit(64)
        .toList();
  }

  public static double distance(Entity e) {
    Vec3 eye = MC.player.getEyePosition();
    return eye.distanceTo(OpenAura.closestPoint(eye, e.getBoundingBox()));
  }

  public static double yaw(Entity e) {
    return Math.abs(
        Mth.wrapDegrees(
            OpenAura.rotationTo(MC.player.getEyePosition(), e.getBoundingBox().getCenter()).yaw()
                - MC.player.getYRot()));
  }

  public static boolean weapon() {
    var stack = MC.player.getMainHandItem();
    return stack.is(ItemTags.SWORDS) || stack.is(ItemTags.AXES) || stack.is(ItemTags.PICKAXES);
  }

  public static EntityHitResult ray(double range, Rotation rotation, boolean walls) {
    Vec3 eyes = MC.player.getEyePosition(), end = eyes.add(rotation.toLookVec().scale(range));
    EntityHitResult hit =
        ProjectileUtil.getEntityHitResult(
            MC.player,
            eyes,
            end,
            new AABB(eyes, end).inflate(1),
            e -> e != MC.player && !e.isSpectator() && e.isPickable(),
            range * range);
    return hit != null && (walls || OpenAura.hasLineOfSight(eyes, hit.getLocation())) ? hit : null;
  }
}

/* NeoRaven ScaffoldModule / TowerModule Fabric 1.20.1 adaptation.
 * GPL-3.0; see META-INF/licenses/neoraven/LICENSE. */
package net.wurstclient.hacks;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.protocol.game.ServerboundSetCarriedItemPacket;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.block.FallingBlock;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.wurstclient.Category;
import net.wurstclient.SearchTags;
import net.wurstclient.events.*;
import net.wurstclient.hack.Hack;
import net.wurstclient.hacks.aura.OpenAura;
import net.wurstclient.neoraven.RavenRotation;
import net.wurstclient.neoraven.RavenScaffoldSearch;
import net.wurstclient.settings.*;
import net.wurstclient.settings.SliderSetting.ValueDisplay;
import net.wurstclient.util.MotionActionQueue;
import net.wurstclient.util.Rotation;
import net.wurstclient.util.RotationQueue;

@SearchTags({"scaffold", "bridge", "tower", "NeoRaven"})
public final class ScaffoldWalkHack extends Hack
    implements UpdateListener, PreMotionListener, PostMotionListener, MoveInputListener {
  private final EnumSetting<RotationMode> rotation =
      new EnumSetting<>("Rotation", RotationMode.values(), RotationMode.STRICT);
  private final EnumSetting<FastMode> fast =
      new EnumSetting<>("Fast scaffold", FastMode.values(), FastMode.DISABLED);
  private final SliderSetting motion =
      new SliderSetting("Motion", 1, .1, 1, .01, ValueDisplay.PERCENTAGE);
  private final CheckboxSetting autoSwap = new CheckboxSetting("Auto swap", true);
  private final CheckboxSetting safeWalk = new CheckboxSetting("SafeWalk", true);
  private final CheckboxSetting tower = new CheckboxSetting("Tower", false);
  private final CheckboxSetting silentSwing = new CheckboxSetting("Silent swing", false);
  private final SliderSetting sneakEvery =
      new SliderSetting("Sneak every", 0, 0, 10, 1, ValueDisplay.INTEGER);
  private final MotionActionQueue<BlockHitResult> actions = new MotionActionQueue<>();
  private RotationQueue rotations;
  private Rotation lastRotation;
  private Object player, world;
  private int originalSlot = -1, ownedSlot = -1, keptY, placed, sneakTicks;
  private boolean active;

  public ScaffoldWalkHack() {
    super("ScaffoldWalk");
    setCategory(Category.MOVEMENT);
    addSetting(rotation);
    addSetting(fast);
    addSetting(motion);
    addSetting(autoSwap);
    addSetting(safeWalk);
    addSetting(tower);
    addSetting(silentSwing);
    addSetting(sneakEvery);
  }

  @Override
  protected void onEnable() {
    reset();
    if (MC.player != null) keptY = Mth.floor(MC.player.getY()) - 1;
    rotations = new RotationQueue(RotationQueue.Priority.BLOCK_PLACEMENT);
    rotations.start();
    EVENTS.add(PreMotionListener.class, this);
    EVENTS.add(PostMotionListener.class, this);
    EVENTS.add(MoveInputListener.class, this);
  }

  @Override
  protected void onDisable() {
    EVENTS.remove(PreMotionListener.class, this);
    EVENTS.remove(PostMotionListener.class, this);
    EVENTS.remove(MoveInputListener.class, this);
    reset();
    rotations.stop();
    rotations = null;
  }

  // Called once at Minecraft.tick HEAD, before input and movement.
  @Override
  public void onUpdate() {
    if (player != MC.player || world != MC.level) {
      reset();
      player = MC.player;
      world = MC.level;
      if (MC.player != null) keptY = Mth.floor(MC.player.getY()) - 1;
    }
    actions.clear();
    if (!canRun()) {
      reset();
      return;
    }
    if (!selectBlocks()) {
      reset();
      return;
    }
    active = true;
    boolean moving =
        MC.options.keyUp.isDown()
            || MC.options.keyDown.isDown()
            || MC.options.keyLeft.isDown()
            || MC.options.keyRight.isDown();
    if (MC.player.onGround()) {
      if (fast.getSelected() != FastMode.KEEP_Y) keptY = Mth.floor(MC.player.getY()) - 1;
      if (tower.isChecked() && MC.options.keyJump.isDown()) MC.player.jumpFromGround();
      else if (moving
          && (fast.getSelected() == FastMode.JUMP || fast.getSelected() == FastMode.KEEP_Y))
        MC.player.jumpFromGround();
      if (moving && fast.getSelected() == FastMode.CONSTANT_A) strafe(.153);
      if (moving && fast.getSelected() == FastMode.CONSTANT_B) strafe(.181);
    }
    Vec3 v = MC.player.getDeltaMovement();
    MC.player.setDeltaMovement(v.x * motion.getValue(), v.y, v.z * motion.getValue());
    if (fast.getSelected() == FastMode.VANILLA && moving) MC.player.setSprinting(true);
    else if (fast.getSelected() != FastMode.VANILLA) MC.player.setSprinting(false);
    actions.begin(MC.player, MC.level, MC.player.tickCount);
  }

  @Override
  public void onPreMotion() {
    if (!active || !canRun() || !usable(MC.player.getMainHandItem())) {
      reset();
      return;
    }
    // Look just beyond the feet so SafeWalk can place before stopping at an edge.
    Vec3 feet = placementFeet();
    if (!MC.level.getBlockState(BlockPos.containing(feet.x, feet.y - 1, feet.z)).canBeReplaced()) {
      rotations.clear();
      return;
    }
    BlockHitResult hit = findPlacement();
    if (hit == null) {
      rotations.clear();
      return;
    }
    Rotation base =
        lastRotation == null
            ? new Rotation(MC.player.getYRot(), MC.player.getXRot())
            : lastRotation;
    Rotation aim = OpenAura.rotationTo(MC.player.getEyePosition(), hit.getLocation());
    if (rotation.getSelected() == RotationMode.NONE)
      aim = new Rotation(MC.player.getYRot(), MC.player.getXRot());
    else if (rotation.getSelected() == RotationMode.SMOOTH)
      aim =
          new Rotation(
              base.yaw() + Mth.clamp(Mth.wrapDegrees(aim.yaw() - base.yaw()), -35, 35),
              aim.pitch());
    else if (rotation.getSelected() == RotationMode.LAZY && hits(base, hit)) aim = base;
    aim = RavenRotation.quantize(base, aim, MC.options.sensitivity().get());
    lastRotation = aim;
    if (rotation.getSelected() != RotationMode.NONE) rotations.setRotation(aim);
    actions.schedule(hit);
  }

  @Override
  public void onPostMotion() {
    if (!active || !canRun()) {
      reset();
      return;
    }
    BlockHitResult planned = actions.consume(MC.player, MC.level, MC.player.tickCount);
    if (planned == null) return;
    Rotation aim =
        rotation.getSelected() == RotationMode.NONE
            ? new Rotation(MC.player.getYRot(), MC.player.getXRot())
            : rotations.getLastSent();
    if (rotation.getSelected() != RotationMode.NONE && !rotations.wasApplied()) return;
    if (!hits(aim, planned)) return;
    BlockHitResult actual = ray(aim);
    BlockPlaceContext context =
        new BlockPlaceContext(
            MC.player, InteractionHand.MAIN_HAND, MC.player.getMainHandItem(), actual);
    BlockPos target = actual.getBlockPos().relative(actual.getDirection());
    if (!context.canPlace()
        || !MC.level.getWorldBorder().isWithinBounds(target)
        || !MC.level.noCollision(new AABB(target))) return;
    var result = MC.gameMode.useItemOn(MC.player, InteractionHand.MAIN_HAND, actual);
    if (result.consumesAction()) {
      if (silentSwing.isChecked())
        SwingHandSetting.SwingHand.SERVER.swing(InteractionHand.MAIN_HAND);
      else MC.player.swing(InteractionHand.MAIN_HAND);
      placed++;
      if (sneakEvery.getValueI() > 0 && placed % sneakEvery.getValueI() == 0) sneakTicks = 2;
    }
  }

  private BlockHitResult findPlacement() {
    Integer height = fast.getSelected() == FastMode.KEEP_Y ? keptY : null;
    for (BlockPos target :
        RavenScaffoldSearch.candidates(
            placementFeet(), height, pos -> MC.level.getBlockState(pos).canBeReplaced())) {
      Direction face = RavenScaffoldSearch.faceFor(target, MC.player.position(), this::support);
      if (face == null) continue;
      BlockPos against = target.relative(face.getOpposite());
      Vec3 center = Vec3.atCenterOf(against).add(Vec3.atLowerCornerOf(face.getUnitVec3i()).scale(.5));
      if (MC.player.getEyePosition().distanceToSqr(center) > 20.25) continue;
      Rotation aim = OpenAura.rotationTo(MC.player.getEyePosition(), center);
      BlockHitResult hit = ray(aim);
      if (hit.getType() == HitResult.Type.BLOCK
          && hit.getBlockPos().equals(against)
          && hit.getDirection() == face) return hit;
    }
    return null;
  }

  private Vec3 placementFeet() {
    Vec3 pos = MC.player.position();
    float forward = (MC.options.keyUp.isDown() ? 1 : 0) - (MC.options.keyDown.isDown() ? 1 : 0);
    float side = (MC.options.keyLeft.isDown() ? 1 : 0) - (MC.options.keyRight.isDown() ? 1 : 0);
    double length = Math.hypot(forward, side), yaw = Math.toRadians(MC.player.getYRot());
    if (length == 0) return pos;
    return pos.add(
        (side * Math.cos(yaw) - forward * Math.sin(yaw)) * .4 / length,
        0,
        (forward * Math.cos(yaw) + side * Math.sin(yaw)) * .4 / length);
  }

  private boolean support(BlockPos pos) {
    var state = MC.level.getBlockState(pos);
    return !state.canBeReplaced()
        && state.isCollisionShapeFullBlock(MC.level, pos)
        && !state.hasBlockEntity();
  }

  private BlockHitResult ray(Rotation aim) {
    Vec3 eyes = MC.player.getEyePosition();
    return MC.level.clip(
        new ClipContext(
            eyes,
            eyes.add(aim.toLookVec().scale(4.5)),
            ClipContext.Block.OUTLINE,
            ClipContext.Fluid.NONE,
            MC.player));
  }

  private boolean hits(Rotation aim, BlockHitResult planned) {
    if (aim == null) return false;
    BlockHitResult actual = ray(aim);
    return actual.getType() == HitResult.Type.BLOCK
        && actual.getBlockPos().equals(planned.getBlockPos())
        && actual.getDirection() == planned.getDirection();
  }

  private boolean canRun() {
    return MC.player != null
        && MC.level != null
        && MC.gameMode != null
        && MC.gui.screen() == null
        && MC.gui.overlay() == null
        && MC.player.isAlive()
        && !MC.player.isSpectator()
        && !MC.player.isPassenger()
        && !MC.player.isUsingItem()
        && !MC.gameMode.isDestroying();
  }

  private boolean usable(ItemStack stack) {
    if (stack.isEmpty()
        || !(stack.getItem() instanceof BlockItem item)
        || item.getBlock() instanceof FallingBlock) return false;
    var state = item.getBlock().defaultBlockState();
    return state.isCollisionShapeFullBlock(MC.level, BlockPos.ZERO) && !state.hasBlockEntity();
  }

  private boolean selectBlocks() {
    if (ownedSlot >= 0 && MC.player.getInventory().getSelectedSlot() != ownedSlot)
      originalSlot = ownedSlot = -1;
    if (usable(MC.player.getMainHandItem())) return true;
    if (!autoSwap.isChecked()) return false;
    int best = -1;
    for (int i = 0; i < 9; i++)
      if (usable(MC.player.getInventory().getItem(i))
          && (best < 0
              || MC.player.getInventory().getItem(i).getCount()
                  > MC.player.getInventory().getItem(best).getCount())) best = i;
    if (best < 0) return false;
    if (originalSlot < 0) originalSlot = MC.player.getInventory().getSelectedSlot();
    ownedSlot = best;
    MC.player.getInventory().setSelectedSlot(best);
    MC.player.connection.send(new ServerboundSetCarriedItemPacket(best));
    return true;
  }

  private void reset() {
    if (originalSlot >= 0
        && player == MC.player
        && world == MC.level
        && MC.player != null
        && MC.player.getInventory().getSelectedSlot() == ownedSlot) {
      MC.player.getInventory().setSelectedSlot(originalSlot);
      MC.player.connection.send(new ServerboundSetCarriedItemPacket(originalSlot));
    }
    originalSlot = ownedSlot = -1;
    active = false;
    lastRotation = null;
    sneakTicks = placed = 0;
    actions.clear();
    if (rotations != null) rotations.clear();
  }

  private void strafe(double speed) {
    double yaw = Math.toRadians(MC.player.getYRot());
    Vec3 v = MC.player.getDeltaMovement();
    double length = Math.hypot(v.x, v.z);
    if (length > 1E-5) MC.player.setDeltaMovement(v.x / length * speed, v.y, v.z / length * speed);
    else MC.player.setDeltaMovement(-Math.sin(yaw) * speed, v.y, Math.cos(yaw) * speed);
  }

  @Override
  public void onMoveInput(MoveInputEvent event) {
    if (active && sneakTicks > 0) {
      event.setSneak(true);
      sneakTicks--;
    }
  }

  public boolean shouldSafeWalk() {
    return isEnabled() && active && safeWalk.isChecked();
  }

  public int getRemainingBlockCount() {
    if (MC.player == null || MC.level == null) return 0;
    int count = 0;
    for (int i = 0; i < 9; i++)
      if (usable(MC.player.getInventory().getItem(i)))
        count += MC.player.getInventory().getItem(i).getCount();
    return count;
  }

  public enum RotationMode {
    NONE,
    STRICT,
    SMOOTH,
    LAZY
  }

  public enum FastMode {
    DISABLED,
    VANILLA,
    CONSTANT_A,
    CONSTANT_B,
    JUMP,
    KEEP_Y
  }
}

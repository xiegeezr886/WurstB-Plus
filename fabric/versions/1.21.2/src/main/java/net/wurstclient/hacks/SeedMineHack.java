package net.wurstclient.hacks;

import java.util.*;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Block;
import net.wurstclient.Category;
import net.wurstclient.SearchTags;
import net.wurstclient.events.RenderListener;
import net.wurstclient.events.UpdateListener;
import net.wurstclient.events.WorldChangeListener;
import net.wurstclient.hack.DontSaveState;
import net.wurstclient.hack.Hack;
import net.wurstclient.hack.HackConflictGroup;
import net.wurstclient.seed.SeedCrackerBridge;
import net.wurstclient.seed.SeedOrePredictor;
import net.wurstclient.settings.CheckboxSetting;
import net.wurstclient.settings.SliderSetting;
import net.wurstclient.settings.TextFieldSetting;
import net.wurstclient.util.BaritoneUtils;
import net.wurstclient.util.ChatUtils;

@DontSaveState
@SearchTags({"openseed", "seed mine", "种子挖矿"})
public final class SeedMineHack extends Hack
    implements UpdateListener, WorldChangeListener, RenderListener {
  private final TextFieldSetting manualSeed =
      new TextFieldSetting(
          "World seed",
          "Optional signed world seed. Must match this world's hash. Leave empty to wait for"
              + " SeedCrackerX.",
          "");
  private final SliderSetting radius =
      new SliderSetting(
          "Chunk radius",
          "Prediction radius in loaded chunks.",
          2,
          1,
          4,
          1,
          SliderSetting.ValueDisplay.INTEGER);
  private final CheckboxSetting preview =
      new CheckboxSetting("Preview only", "Predict targets without moving or digging.", false);
  private final Set<BlockPos> visited = new HashSet<>();
  private SeedOrePredictor predictor;
  private BlockPos target, scanCenter;
  private Set<Block> ores = Set.of();
  private long resultSeed;
  private int targetTicks, rescanTicks;
  private boolean ownsPath;
  private String status = "Waiting for seed";

  public SeedMineHack() {
    super("SeedMine");
    setCategory(Category.WORLD);
    addConflictGroup(HackConflictGroup.BARITONE_CONTROL);
    addSetting(manualSeed);
    addSetting(radius);
    addSetting(preview);
  }

  @Override
  protected boolean canEnable() {
    return MC.player != null && MC.level != null && BaritoneUtils.IS_AVAILABLE;
  }

  @Override
  protected void onEnable() {
    predictor = null;
    target = null;
    visited.clear();
    ores = Set.of();
    ownsPath = false;
    status = "Waiting for seed";
    String raw = manualSeed.getValue().trim();
    if (!raw.isEmpty()) {
      try {
        if (!SeedCrackerBridge.SESSION.accept(
            SeedCrackerBridge.SESSION.generation(), Long.parseLong(raw)))
          throw new IllegalArgumentException("Seed does not match this world");
      } catch (IllegalArgumentException e) {
        ChatUtils.error("SeedMine: invalid or mismatched world seed.");
        setEnabled(false);
        return;
      }
    }
    EVENTS.add(UpdateListener.class, this);
    EVENTS.add(WorldChangeListener.class, this);
    EVENTS.add(RenderListener.class, this);
  }

  @Override
  protected void onDisable() {
    EVENTS.remove(UpdateListener.class, this);
    EVENTS.remove(WorldChangeListener.class, this);
    EVENTS.remove(RenderListener.class, this);
    stopPath();
    predictor = null;
    visited.clear();
    target = null;
    status = "Stopped";
  }

  private void stopPath() {
    if (ownsPath) BaritoneUtils.stop();
    ownsPath = false;
  }

  @Override
  public void onWorldChange(ClientLevel world) {
    setEnabled(false);
  }

  @Override
  public void onUpdate() {
    if (MC.player == null || MC.level == null) {
      setEnabled(false);
      return;
    }
    var found = SeedCrackerBridge.SESSION.seed();
    if (found.isEmpty()) {
      stopPath();
      status = "Waiting for seed";
      return;
    }
    if (MC.player.getInventory().getFreeSlot() < 0) {
      ChatUtils.message("SeedMine: inventory full.");
      setEnabled(false);
      return;
    }
    Set<Block> selected = new HashSet<>(WURST.getHax().baritoneMineHack.getOreBlocks());
    if (selected.isEmpty()) {
      status = "No ores selected";
      stopPath();
      return;
    }
    if (predictor == null || resultSeed != found.getAsLong() || !ores.equals(selected)) {
      stopPath();
      target = null;
      visited.clear();
      resultSeed = found.getAsLong();
      ores = Set.copyOf(selected);
      beginScan();
    }
    predictor.tick();
    if (predictor.error() != null) {
      ChatUtils.error("SeedMine: " + predictor.error());
      setEnabled(false);
      return;
    }
    if (preview.isChecked() || MC.screen != null) {
      stopPath();
      target = null;
      status = "Preview " + predictor.results().size();
      return;
    }
    if (target != null) {
      targetTicks++;
      if (MC.level.getBlockState(target).isAir()
          || targetTicks > 600
          || unsafe(target)) {
        visited.add(target);
        target = null;
        stopPath();
      } else if (targetTicks > 40 && !BaritoneUtils.isPathing()) {
        visited.add(target);
        target = null;
        stopPath();
      }
    }
    if (target == null) {
      target =
          predictor.results().stream()
              .map(SeedOrePredictor.Ore::pos)
              .filter(
                  p ->
                      !visited.contains(p)
                          && MC.level.hasChunkAt(p)
                          && !MC.level.getBlockState(p).isAir()
                          && !unsafe(p))
              .min(Comparator.<BlockPos>comparingInt(
                      p -> ores.contains(MC.level.getBlockState(p).getBlock()) ? 0 : 1)
                  .thenComparingDouble(p -> p.distSqr(MC.player.blockPosition())))
              .orElse(null);
      if (target != null) {
        BaritoneUtils.walkTo(target);
        ownsPath = true;
        targetTicks = 0;
      }
    }
    status = target != null ? "Mining" : predictor.finished() ? "No targets" : "Predicting";
    if (target == null
        && predictor.finished()
        && ++rescanTicks > 100
        && !scanCenter.closerThan(MC.player.blockPosition(), 16)) beginScan();
  }

  private boolean unsafe(BlockPos pos) {
    if (!MC.level.hasChunkAt(pos) || MC.level.getBlockState(pos).getDestroySpeed(MC.level, pos) < 0)
      return true;
    for (Direction direction : Direction.values()) {
      BlockPos adjacent = pos.relative(direction);
      if (!MC.level.hasChunkAt(adjacent) || !MC.level.getFluidState(adjacent).isEmpty())
        return true;
    }
    return !MC.level.getFluidState(pos).isEmpty();
  }

  private void beginScan() {
    scanCenter = MC.player.blockPosition();
    rescanTicks = 0;
    predictor = new SeedOrePredictor(MC.level, resultSeed, ores, scanCenter, radius.getValueI());
  }

  public String status() {
    return status;
  }

  @Override
  public void onRender(com.mojang.blaze3d.vertex.PoseStack stack, float partialTicks) {
    if (predictor == null || MC.player == null) return;
    int count = 0;
    for (var ore : predictor.results()) {
      if (visited.contains(ore.pos()) || !ore.pos().closerThan(MC.player.blockPosition(), 64))
        continue;
      net.wurstclient.util.RenderUtils.drawOutlinedBox(
          stack,
          new net.minecraft.world.phys.AABB(ore.pos()),
          ore.pos().equals(target) ? 0xFFFFFFFF : 0xAAB5BEF0,
          false);
      if (++count >= 256) break;
    }
  }

  public List<SeedOrePredictor.Ore> predictions() {
    return predictor == null ? List.of() : predictor.results();
  }

  @Override
  public String getRenderName() {
    return getName() + " [" + status + "]";
  }
}

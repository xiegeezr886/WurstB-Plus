/* AutoTorch ported from MrBZBZ/LeavesHack 13daf96. SPDX-License-Identifier: AGPL-3.0-or-later */
package net.wurstclient.hacks;

import com.mojang.blaze3d.vertex.PoseStack;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.TorchBlock;
import net.minecraft.world.phys.Vec3;
import net.wurstclient.Category;
import net.wurstclient.events.*;
import net.wurstclient.hack.Hack;
import net.wurstclient.leaves.LeavesUtils;
import net.wurstclient.settings.*;
import net.wurstclient.settings.SliderSetting.ValueDisplay;
import net.wurstclient.util.*;

public final class AutoTorchHack extends Hack implements UpdateListener, RenderListener {
  private final SliderSetting delay =
      new SliderSetting("Delay", 50, 0, 10000, 1, ValueDisplay.INTEGER);
  private final SliderSetting renderRange =
      new SliderSetting("RenderRange", 10, 0, 10, 1, ValueDisplay.INTEGER);
  private final SliderSetting range = new SliderSetting("Range", 5, 0, 6, 1, ValueDisplay.INTEGER);
  private final CheckboxSetting onlyRender = new CheckboxSetting("OnlyRender", true);
  private final CheckboxSetting throughWall = new CheckboxSetting("ThroughWall", false);
  private final SliderSetting light =
      new SliderSetting("CheckLightLevel", 7, 0, 15, 1, ValueDisplay.INTEGER);
  private List<BlockPos> dark = List.of();
  private Object world;
  private long next;

  public AutoTorchHack() {
    super("AutoTorch");
    setCategory(Category.WORLD);
    for (Setting s : new Setting[] {delay, renderRange, range, onlyRender, throughWall, light})
      addSetting(s);
  }

  @Override
  protected void onEnable() {
    dark = List.of();
    next = 0;
    world = MC.level;
    EVENTS.add(UpdateListener.class, this);
    EVENTS.add(RenderListener.class, this);
  }

  @Override
  protected void onDisable() {
    EVENTS.remove(UpdateListener.class, this);
    EVENTS.remove(RenderListener.class, this);
    dark = List.of();
  }

  @Override
  public void onUpdate() {
    if (world != MC.level) {
      dark = List.of();
      next = 0;
      world = MC.level;
    }
    if (MC.level == null || MC.player == null) return;
    dark =
        BlockUtils.getAllInBoxStream(MC.player.blockPosition(), renderRange.getValueI())
            .filter(p -> MC.level.hasChunkAt(p) && MC.level.isInWorldBounds(p))
            .filter(
                p ->
                    MC.player.getEyePosition().distanceToSqr(Vec3.atCenterOf(p))
                        <= renderRange.getValueSq())
            .filter(
                p ->
                    MC.level.getBlockState(p).canBeReplaced()
                        && MC.level.getBrightness(LightLayer.BLOCK, p) <= light.getValueI()
                        && BlockPlacer.getBlockPlacingParams(p) != null)
            .map(BlockPos::immutable)
            .toList();
    if (onlyRender.isChecked() || !LeavesUtils.ready() || System.nanoTime() < next) return;
    int slot =
        InventoryUtils.indexOf(
            s -> s.getItem() instanceof BlockItem b && b.getBlock() instanceof TorchBlock, 9);
    if (slot < 0) return;
    var item = MC.player.getInventory().getItem(slot).getItem();
    for (BlockPos p : BlockUtils.getAllInBox(MC.player.blockPosition(), range.getValueI())) {
      if (!MC.level.isEmptyBlock(p)
          || MC.level.getBrightness(LightLayer.BLOCK, p) > light.getValueI()
          || !Blocks.TORCH.defaultBlockState().canSurvive(MC.level, p)) continue;
      if (!throughWall.isChecked()
          && !BlockUtils.hasLineOfSight(MC.player.getEyePosition(), Vec3.atCenterOf(p))) continue;
      if (LeavesUtils.place(p, item, range.getValue(), false, true)) break;
    }
    next = System.nanoTime() + delay.getValueI() * 1_000_000L;
  }

  @Override
  public void onRender(PoseStack matrices, float partialTicks) {
    if (MC.level != world) return;
    for (BlockPos p : dark) {
      Vec3 start = new Vec3(p.getX(), p.getY() + 0.01, p.getZ());
      RenderUtils.drawLine(matrices, start, start.add(1, 0, 1), 0xFFFF0000, true);
      RenderUtils.drawLine(matrices, start.add(1, 0, 0), start.add(0, 0, 1), 0xFFFF0000, true);
    }
  }
}

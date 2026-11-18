/* AutoWeb ported from MrBZBZ/LeavesHack 13daf96. SPDX-License-Identifier: AGPL-3.0-or-later */
package net.wurstclient.hacks;

import com.mojang.blaze3d.vertex.PoseStack;
import java.awt.Color;
import net.minecraft.core.BlockPos;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.wurstclient.Category;
import net.wurstclient.events.*;
import net.wurstclient.hack.Hack;
import net.wurstclient.leaves.LeavesColorSetting;
import net.wurstclient.leaves.LeavesUtils;
import net.wurstclient.settings.*;
import net.wurstclient.settings.SliderSetting.ValueDisplay;
import net.wurstclient.util.RenderUtils;

public final class AutoWebHack extends Hack implements UpdateListener, RenderListener {
  private final SliderSetting targetRange =
      new SliderSetting("TargetRange", 4, 1, 8, 0.1, ValueDisplay.DECIMAL);
  private final SliderSetting placeRange =
      new SliderSetting("PlaceRange", 4.5, 1, 6, 0.1, ValueDisplay.DECIMAL);
  private final SliderSetting placeDelay =
      new SliderSetting("PlaceDelay", 50, 0, 500, 1, ValueDisplay.INTEGER);
  private final CheckboxSetting inventory = new CheckboxSetting("InventorySwap", true);
  private final CheckboxSetting rotate = new CheckboxSetting("Rotate", true);
  private final EnumSetting<Mode> mode = new EnumSetting<>("PlaceMode", Mode.values(), Mode.Feet);
  private final CheckboxSetting predict = new CheckboxSetting("Predict", false);
  private final SliderSetting ticks =
      new SliderSetting("PredictTicks", 2, 1, 10, 1, ValueDisplay.INTEGER);
  private final CheckboxSetting skip = new CheckboxSetting("SkipWebbed", true);
  private final CheckboxSetting pause = new CheckboxSetting("UsingPause", true);
  private final CheckboxSetting onlyMain = new CheckboxSetting("OnlyMain", true);
  private final CheckboxSetting render = new CheckboxSetting("Render", true);
  private final EnumSetting<PrinterHack.Shape> shape =
      new EnumSetting<>("ShapeMode", PrinterHack.Shape.values(), PrinterHack.Shape.BOTH);
  private final LeavesColorSetting side =
      new LeavesColorSetting("SideColor", new Color(255, 255, 255, 50));
  private final LeavesColorSetting line = new LeavesColorSetting("LineColor", Color.WHITE);
  private final SliderSetting speed =
      new SliderSetting("RenderSpeed", 0.15, 0.01, 0.5, 0.01, ValueDisplay.DECIMAL);
  private BlockPos webPos;
  private Vec3 rendered;
  private long next;
  private Object world;

  public AutoWebHack() {
    super("AutoWeb");
    setCategory(Category.WORLD);
    for (Setting s :
        new Setting[] {
          targetRange,
          placeRange,
          placeDelay,
          inventory,
          rotate,
          mode,
          predict,
          ticks,
          skip,
          pause,
          onlyMain,
          render,
          shape,
          side,
          line,
          speed
        }) addSetting(s);
  }

  @Override
  protected void onEnable() {
    clear();
    EVENTS.add(UpdateListener.class, this);
    EVENTS.add(RenderListener.class, this);
  }

  @Override
  protected void onDisable() {
    EVENTS.remove(UpdateListener.class, this);
    EVENTS.remove(RenderListener.class, this);
    clear();
  }

  private void clear() {
    webPos = null;
    rendered = null;
    next = 0;
    world = MC.level;
  }

  public BlockPos getWebPos() {
    return isEnabled() ? webPos : null;
  }

  @Override
  public void onUpdate() {
    if (world != MC.level) clear();
    if (!LeavesUtils.ready()) {
      webPos = null;
      return;
    }
    var target = LeavesUtils.enemy(targetRange.getValue());
    if (target == null) {
      webPos = null;
      return;
    }
    if (pause.isChecked() && LeavesUtils.using(onlyMain.isChecked())) return;
    Vec3 pos = target.position();
    if (predict.isChecked())
      pos =
          pos.add(
              (target.getX() - target.xo) * ticks.getValue(),
              (target.getY() - target.yo) * ticks.getValue(),
              (target.getZ() - target.zo) * ticks.getValue());
    pos =
        pos.add(
            0,
            mode.getSelected() == Mode.Head
                ? target.getEyeHeight()
                : mode.getSelected() == Mode.Body ? 0.5 : 0,
            0);
    webPos = BlockPos.containing(pos);
    if (skip.isChecked() && MC.level.getBlockState(webPos).is(Blocks.COBWEB)) return;
    if (MC.player.distanceToSqr(Vec3.atCenterOf(webPos))
        > placeRange.getValue() * placeRange.getValue()) {
      webPos = null;
      return;
    }
    if (System.nanoTime() < next) return;
    if (MC.level.isEmptyBlock(webPos))
      LeavesUtils.place(
          webPos, Items.COBWEB, placeRange.getValue(), inventory.isChecked(), rotate.isChecked());
    next = System.nanoTime() + placeDelay.getValueI() * 1_000_000L;
  }

  @Override
  public void onRender(PoseStack matrices, float partialTicks) {
    if (!render.isChecked() || webPos == null || MC.level == null) {
      rendered = null;
      return;
    }
    Vec3 to = Vec3.atLowerCornerOf(webPos);
    rendered = rendered == null ? to : rendered.add(to.subtract(rendered).scale(speed.getValue()));
    AABB box = new AABB(rendered, rendered.add(1, 1, 1));
    if (shape.getSelected() != PrinterHack.Shape.LINES)
      RenderUtils.drawSolidBox(matrices, box, side.getColorI(), false);
    if (shape.getSelected() != PrinterHack.Shape.SIDES)
      RenderUtils.drawOutlinedBox(matrices, box, line.getColorI(), false);
  }

  public enum Mode {
    Feet,
    Head,
    Body
  }
}

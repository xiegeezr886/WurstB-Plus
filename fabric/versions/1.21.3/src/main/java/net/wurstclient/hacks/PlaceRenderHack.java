/* PlaceRender ported from MrBZBZ/LeavesHack 13daf96. SPDX-License-Identifier: AGPL-3.0-or-later */
package net.wurstclient.hacks;

import com.mojang.blaze3d.vertex.PoseStack;
import java.awt.Color;
import java.util.LinkedHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.*;
import net.wurstclient.Category;
import net.wurstclient.events.RenderListener;
import net.wurstclient.hack.Hack;
import net.wurstclient.leaves.LeavesColorSetting;
import net.wurstclient.leaves.LeavesRender;
import net.wurstclient.settings.*;
import net.wurstclient.settings.SliderSetting.ValueDisplay;
import net.wurstclient.util.RenderUtils;

public final class PlaceRenderHack extends Hack implements RenderListener {
  private final EnumSetting<PrinterHack.Shape> shape =
      new EnumSetting<>("ShapeMode", PrinterHack.Shape.values(), PrinterHack.Shape.BOTH);
  private final SliderSetting speed =
      new SliderSetting("Speed", 4, 1, 100, 1, ValueDisplay.INTEGER);
  private final CheckboxSetting client = new CheckboxSetting("RenderClientPlace", true);
  private final SliderSetting exponent =
      new SliderSetting("Animation Exponent", 3, 0, 10, 0.1, ValueDisplay.DECIMAL);
  private final LeavesColorSetting sideStart =
      new LeavesColorSetting("SideStart", new Color(140, 142, 255, 50));
  private final LeavesColorSetting sideEnd =
      new LeavesColorSetting("SideEnd", new Color(140, 142, 255, 50));
  private final LeavesColorSetting lineStart =
      new LeavesColorSetting("LineStart", new Color(140, 142, 255, 255));
  private final LeavesColorSetting lineEnd =
      new LeavesColorSetting("LineEnd", new Color(140, 142, 255, 255));
  private final LinkedHashMap<BlockPos, Double> entries = new LinkedHashMap<>();
  private Object world;

  public PlaceRenderHack() {
    super("PlaceRender");
    setCategory(Category.RENDER);
    for (Setting s :
        new Setting[] {shape, speed, client, exponent, sideStart, sideEnd, lineStart, lineEnd})
      addSetting(s);
  }

  @Override
  protected void onEnable() {
    entries.clear();
    world = MC.level;
    EVENTS.add(RenderListener.class, this);
  }

  @Override
  protected void onDisable() {
    EVENTS.remove(RenderListener.class, this);
    entries.clear();
  }

  public void record(BlockPos pos, boolean clientPlacement) {
    if (!isEnabled() || clientPlacement && !client.isChecked()) return;
    if (world != MC.level) {
      entries.clear();
      world = MC.level;
    }
    entries.putIfAbsent(pos.immutable(), 1D);
    while (entries.size() > 512) entries.remove(entries.keySet().iterator().next());
  }

  @Override
  public void onRender(PoseStack matrices, float partialTicks) {
    if (world != MC.level) {
      entries.clear();
      world = MC.level;
    }
    if (MC.level == null) return;
    var iterator = entries.entrySet().iterator();
    while (iterator.hasNext()) {
      var e = iterator.next();
      if (e.getValue() <= 0) {
        iterator.remove();
        continue;
      }
      double p = 1 - Math.pow(1 - Math.min(1, e.getValue()), exponent.getValue()), size = p / 2;
      Vec3 center = Vec3.atCenterOf(e.getKey());
      AABB box = new AABB(center.subtract(size, size, size), center.add(size, size, size));
      if (shape.getSelected() != PrinterHack.Shape.LINES)
        RenderUtils.drawSolidBox(
            matrices, box, LeavesRender.lerp(sideStart.getColorI(), sideEnd.getColorI(), p), false);
      if (shape.getSelected() != PrinterHack.Shape.SIDES)
        RenderUtils.drawOutlinedBox(
            matrices, box, LeavesRender.lerp(lineStart.getColorI(), lineEnd.getColorI(), p), false);
      e.setValue(e.getValue() - speed.getValue() * 0.01);
    }
  }
}

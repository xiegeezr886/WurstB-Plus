/* BreakESP adapted from LeavesHack 13daf96.
 * SPDX-License-Identifier: AGPL-3.0-or-later */
package net.wurstclient.hacks;

import com.mojang.blaze3d.vertex.PoseStack;
import java.awt.Color;
import java.util.Locale;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.wurstclient.Category;
import net.wurstclient.events.RenderListener;
import net.wurstclient.hack.Hack;
import net.wurstclient.leaves.*;
import net.wurstclient.leaves.LeavesColorSetting;
import net.wurstclient.settings.*;
import net.wurstclient.settings.SliderSetting.ValueDisplay;
import net.wurstclient.util.RenderUtils;

public final class BreakEspHack extends Hack implements RenderListener {
  private final CheckboxSetting progress = new CheckboxSetting("Progress", true),
      second = new CheckboxSetting("Second", true);
  private final SliderSetting
      damage = new SliderSetting("Damage", 1, 0, 2, 0.05, ValueDisplay.DECIMAL),
      textScale = new SliderSetting("TextScale", 1, 0.5, 3, 0.05, ValueDisplay.DECIMAL);
  private final EnumSetting<PrinterHack.Shape> shape =
      new EnumSetting<>("ShapeMode", PrinterHack.Shape.values(), PrinterHack.Shape.BOTH);
  private final LeavesColorSetting box = color("Box", 255),
      fill = color("Fill", 50),
      friendBox = color("FriendBox", 255),
      friendFill = color("FriendFill", 50),
      secondBox = color("SecondBox", 255),
      secondFill = color("SecondFill", 50),
      nameColor = color("NameColor", 255),
      progressColor = color("ProgressColor", 255),
      secondName = color("SecondNameColor", 255),
      secondProgress = color("SecondProgressColor", 255);

  private static LeavesColorSetting color(String name, int alpha) {
    return new LeavesColorSetting(name, new Color(140, 142, 255, alpha));
  }

  public BreakEspHack() {
    super("BreakESP");
    setCategory(Category.RENDER);
    for (Setting s :
        new Setting[] {
          progress,
          damage,
          shape,
          box,
          fill,
          friendBox,
          friendFill,
          second,
          secondBox,
          secondFill,
          nameColor,
          progressColor,
          secondName,
          secondProgress,
          textScale
        }) addSetting(s);
  }

  @Override
  protected void onEnable() {
    EVENTS.add(RenderListener.class, this);
  }

  @Override
  protected void onDisable() {
    EVENTS.remove(RenderListener.class, this);
  }

  @Override
  public void onRender(PoseStack matrices, float partialTicks) {
    if (MC.player == null || MC.level == null) return;
    for (var e : BreakTracker.INSTANCE.primary()) render(matrices, e, false);
    if (second.isChecked())
      for (var e : BreakTracker.INSTANCE.secondary()) {
        var main = BreakTracker.INSTANCE.primary(e.id);
        if (!MC.level.isEmptyBlock(e.pos) && (main == null || !main.pos.equals(e.pos)))
          render(matrices, e, true);
      }
  }

  private void render(PoseStack matrices, BreakTracker.Entry e, boolean secondary) {
    var player = e.player();
    if (player == null) return;
    boolean friend = WURST.getFriends().contains(player.getScoreboardName());
    int edge = (secondary ? secondBox : friend ? friendBox : box).getColorI(),
        inside = (secondary ? secondFill : friend ? friendFill : fill).getColorI();
    AABB bounds = new AABB(e.pos);
    if (shape.getSelected() != PrinterHack.Shape.LINES)
      RenderUtils.drawSolidBox(matrices, bounds, inside, false);
    if (shape.getSelected() != PrinterHack.Shape.SIDES)
      RenderUtils.drawOutlinedBox(matrices, bounds, edge, false);
    Vec3 center = Vec3.atCenterOf(e.pos);
    LeavesRender.text(
        matrices,
        center.add(0, 0.15, 0),
        player.getScoreboardName(),
        (secondary ? secondName : nameColor).getColorI(),
        textScale.getValue());
    if (progress.isChecked() || secondary) {
      String text =
          secondary
              ? "Double"
              : e.failed
                  ? "Failed"
                  : e.complete
                      ? "Broke"
                      : String.format(Locale.ROOT, "%.1f%%", e.progress(damage.getValue()) * 100);
      LeavesRender.text(
          matrices,
          center.add(0, -0.3, 0),
          text,
          (secondary ? secondProgress : progressColor).getColorI(),
          textScale.getValue());
    }
  }
}

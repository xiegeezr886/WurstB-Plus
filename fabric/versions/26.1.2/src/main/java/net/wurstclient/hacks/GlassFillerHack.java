/* GlassFiller ported from MrBZBZ/LeavesHack 13daf96. SPDX-License-Identifier: AGPL-3.0-or-later */
package net.wurstclient.hacks;

import java.util.LinkedHashSet;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.AABB;
import net.wurstclient.Category;
import net.wurstclient.events.UpdateListener;
import net.wurstclient.hack.Hack;
import net.wurstclient.leaves.LeavesUtils;
import net.wurstclient.settings.*;
import net.wurstclient.settings.SliderSetting.ValueDisplay;

public final class GlassFillerHack extends Hack implements UpdateListener {
  private final SliderSetting targetRange =
      new SliderSetting("TargetRange", 6, 0, 8, 1, ValueDisplay.INTEGER);
  private final SliderSetting delay =
      new SliderSetting("PlaceDelay", 50, 0, 500, 1, ValueDisplay.INTEGER);
  private final SliderSetting range =
      new SliderSetting("PlaceRange", 4.5, 0, 6, 0.1, ValueDisplay.DECIMAL);
  private final CheckboxSetting pause = new CheckboxSetting("UsingPause", true);
  private final CheckboxSetting onlyMain = new CheckboxSetting("OnlyMain", true);
  private final CheckboxSetting skipPhased = new CheckboxSetting("SkipPhased", true);
  private final CheckboxSetting rotate = new CheckboxSetting("Rotate", true);
  private final CheckboxSetting inventory = new CheckboxSetting("InventorySwap", true);
  private long next;

  public GlassFillerHack() {
    super("GlassFiller");
    setCategory(Category.WORLD);
    for (Setting s :
        new Setting[] {targetRange, delay, range, pause, onlyMain, skipPhased, rotate, inventory})
      addSetting(s);
  }

  @Override
  protected void onEnable() {
    next = 0;
    EVENTS.add(UpdateListener.class, this);
  }

  @Override
  protected void onDisable() {
    EVENTS.remove(UpdateListener.class, this);
  }

  @Override
  public void onUpdate() {
    if (WURST.getHax().crystalAuraPlusHack.isBusy() || WURST.getHax().autoAnchorHack.isBusy())
      return;
    if (!LeavesUtils.ready()
        || System.nanoTime() < next
        || pause.isChecked() && LeavesUtils.using(onlyMain.isChecked())) return;
    var target = LeavesUtils.enemy(targetRange.getValue());
    if (target == null
        || skipPhased.isChecked() && !MC.level.noCollision(target, target.getBoundingBox())) return;
    AABB box = target.getBoundingBox().deflate(0.01, 0.1, 0.01);
    var candidates = new LinkedHashSet<BlockPos>();
    for (int x = (int) Math.floor(box.minX); x <= Math.floor(box.maxX); x++)
      for (int z = (int) Math.floor(box.minZ); z <= Math.floor(box.maxZ); z++)
        for (Direction dir : Direction.Plane.HORIZONTAL)
          candidates.add(new BlockPos(x, target.blockPosition().getY(), z).relative(dir));
    for (BlockPos pos : candidates) {
      if (!MC.level
          .getEntities(
              (net.minecraft.world.entity.Entity) null,
              new AABB(pos),
              e ->
                  e.isAlive()
                      && !(e instanceof net.minecraft.world.entity.item.ItemEntity)
                      && !(e instanceof net.minecraft.world.entity.ExperienceOrb))
          .isEmpty()) continue;
      if (LeavesUtils.place(
          pos, Items.GLASS, range.getValue(), inventory.isChecked(), rotate.isChecked()))
        next = System.nanoTime() + delay.getValueI() * 1_000_000L;
    }
  }
}

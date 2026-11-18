/* AutoPlaceBlock ported from LeavesHack. SPDX-License-Identifier: AGPL-3.0-or-later */
package net.wurstclient.hacks;

import com.mojang.blaze3d.vertex.PoseStack;
import net.wurstclient.Category;
import net.wurstclient.events.*;
import net.wurstclient.hack.Hack;
import net.wurstclient.hacks.autoplace.DirectionalPaver;
import net.wurstclient.settings.*;
import net.wurstclient.settings.SliderSetting.ValueDisplay;

public final class AutoPlaceBlockHack extends Hack implements UpdateListener, RenderListener {
  private final BlockSetting block =
      new BlockSetting("Block", "Block to place.", "minecraft:obsidian", false);
  private final SliderSetting range = new SliderSetting("Range", 3, 0, 6, 1, ValueDisplay.INTEGER);
  private final DirectionalPaver paver = new DirectionalPaver();

  public AutoPlaceBlockHack() {
    super("AutoPlaceBlock");
    setCategory(Category.WORLD);
    addSetting(block);
    addSetting(range);
    for (var setting : paver.settings()) addSetting(setting);
  }

  @Override
  protected void onEnable() {
    paver.reset();
    EVENTS.add(UpdateListener.class, this);
    EVENTS.add(RenderListener.class, this);
  }

  @Override
  protected void onDisable() {
    EVENTS.remove(UpdateListener.class, this);
    EVENTS.remove(RenderListener.class, this);
    paver.reset();
  }

  @Override
  public void onUpdate() {
    paver.tick(block.getBlock(), range.getValue());
  }

  @Override
  public void onRender(PoseStack matrices, float partialTicks) {
    paver.render(matrices);
  }
}

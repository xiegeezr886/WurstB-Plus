/* AutoTree ported from LeavesHack. SPDX-License-Identifier: AGPL-3.0-or-later */
package net.wurstclient.hacks;

import com.mojang.blaze3d.vertex.PoseStack;
import net.wurstclient.Category;
import net.wurstclient.events.*;
import net.wurstclient.hack.Hack;
import net.wurstclient.hacks.treebot.SaplingFarm;

public final class TreeBotHack extends Hack
    implements UpdateListener, RenderListener, LeftClickListener, HandleBlockBreakingListener {
  private final SaplingFarm farm = new SaplingFarm();

  public TreeBotHack() {
    super("TreeBot");
    setCategory(Category.WORLD);
    for (var setting : farm.settings()) addSetting(setting);
  }

  @Override
  protected void onEnable() {
    farm.reset();
    EVENTS.add(UpdateListener.class, this);
    EVENTS.add(RenderListener.class, this);
    EVENTS.add(LeftClickListener.class, this);
    EVENTS.add(HandleBlockBreakingListener.class, this);
  }

  @Override
  protected void onDisable() {
    EVENTS.remove(UpdateListener.class, this);
    EVENTS.remove(RenderListener.class, this);
    EVENTS.remove(LeftClickListener.class, this);
    EVENTS.remove(HandleBlockBreakingListener.class, this);
    farm.reset();
  }

  @Override
  public void onUpdate() {
    farm.tick(6);
  }

  @Override
  public void onRender(PoseStack matrices, float partialTicks) {
    farm.render(matrices);
  }

  @Override
  public void onLeftClick(LeftClickEvent event) {
    if (farm.select()) event.cancel();
  }

  @Override
  public void onHandleBlockBreaking(HandleBlockBreakingEvent event) {
    event.cancel();
  }
}

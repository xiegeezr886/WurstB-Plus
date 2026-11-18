/* Legacy ModuleList controls retained with the RedFix ArrayList renderer.
 * SPDX-License-Identifier: GPL-3.0-or-later */
package net.wurstclient.hacks;

import net.wurstclient.util.render.GuiGraphicsExtractor;
import net.wurstclient.Category;
import net.wurstclient.events.GUIRenderListener;
import net.wurstclient.hack.Hack;
import net.wurstclient.hud2.nanovg.NanoVGUI;
import net.wurstclient.hud2.redfix.RedFixArrayList;
import net.wurstclient.hud2.mods.ArrayListMod.Order;
import net.wurstclient.settings.*;
import net.wurstclient.settings.SliderSetting.ValueDisplay;

public final class ModuleListHack extends Hack implements GUIRenderListener
{
    private final SliderSetting x = new SliderSetting("X", 1910, 0, 3840, 1, ValueDisplay.INTEGER);
    private final SliderSetting y = new SliderSetting("Y", 10, 0, 2160, 1, ValueDisplay.INTEGER);
    private final CheckboxSetting info = new CheckboxSetting("Additional info", true);
    private final CheckboxSetting onlyBind = new CheckboxSetting("OnlyBind", false);
    private final CheckboxSetting spaced = new CheckboxSetting("SpaceSeparated", false);
    private final EnumSetting<Order> order = new EnumSetting<>("Order", Order.values(), Order.DESCENDING);
    private final RedFixArrayList list = new RedFixArrayList();

    public ModuleListHack()
    {
        super("ModuleList");
        setCategory(Category.RENDER);
        for(Setting setting : new Setting[]{x, y, info, onlyBind, spaced, order}) addSetting(setting);
    }
    @Override protected void onEnable()
    {
        list.clear();
        EVENTS.add(GUIRenderListener.class, this);
    }
    @Override protected void onDisable()
    {
        EVENTS.remove(GUIRenderListener.class, this);
        list.clear();
    }
    @Override public void onRenderGUI(GuiGraphicsExtractor graphics, float partialTicks)
    {
        if(MC.player == null || MC.options.hideGui) return;
        NanoVGUI.pass(graphics, () -> {
            list.update(NanoVGUI.context(), info.isChecked(), spaced.isChecked(),
                order.getSelected() == Order.ASCENDING, true, onlyBind.isChecked(), false);
            float edge = Math.min(x.getValueI(), MC.getWindow().getGuiScaledWidth() - 10);
            float top = Math.min(y.getValueI(), Math.max(0, MC.getWindow().getGuiScaledHeight() - list.height()));
            list.draw(NanoVGUI.context(), edge, top, 1);
        });
    }
}

/* RedFix/LiquidBounce ArrayList adapter. SPDX-License-Identifier: GPL-3.0-or-later */
package net.wurstclient.hud2.mods;

import net.wurstclient.hud2.HUDMod;
import net.wurstclient.hud2.redfix.RedFixArrayList;
import net.wurstclient.settings.CheckboxSetting;
import net.wurstclient.settings.EnumSetting;

public final class ArrayListMod extends HUDMod
{
    public enum Alignment { LEFT, RIGHT }
    public enum Order { ASCENDING, DESCENDING }
    private final CheckboxSetting tags = new CheckboxSetting("ShowTags", true);
    private final CheckboxSetting spaced = new CheckboxSetting("SpaceSeparated", false);
    private final EnumSetting<Alignment> alignment = new EnumSetting<>("ItemAlignment",
        Alignment.values(), Alignment.RIGHT);
    private final EnumSetting<Order> order = new EnumSetting<>("Order", Order.values(), Order.DESCENDING);
    private final RedFixArrayList list = new RedFixArrayList();

    public ArrayListMod()
    {
        super("ArrayList", "ArrayList", "Lists enabled modules in the RedFix style.");
        setTranslationKey("array_list");
        setDefaultPosition(700, 10);
        setSize(100, 13);
        addSetting(tags); addSetting(spaced); addSetting(alignment); addSetting(order);
    }

    @Override protected void onDisable() { list.clear(); }

    @Override protected void draw()
    {
        if(WURST.getHax() != null && WURST.getHax().moduleListHack.isEnabled())
        {
            list.clear();
            return;
        }
        boolean right = alignment.getSelected() == Alignment.RIGHT;
        list.update(nvg, tags.isChecked(), spaced.isChecked(),
            order.getSelected() == Order.ASCENDING, right, false, isEditing());
        int width = (int)Math.ceil(list.width()), height = (int)Math.ceil(list.height());
        float edge = right ? getX() + getUnscaledWidth() * getScale() : getX();
        if(right) setX(Math.round(edge - width * getScale()));
        setSize(width, height);
        list.draw(nvg, edge, getY(), getScale());
        if(list.hasRows()) markDrawn();
    }
}

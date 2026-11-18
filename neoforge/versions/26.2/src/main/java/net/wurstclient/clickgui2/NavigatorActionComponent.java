package net.wurstclient.clickgui2;

import net.wurstclient.util.render.GuiGraphicsExtractor;
import net.wurstclient.hud2.nanovg.NanoVGUI;
import net.wurstclient.settings.Setting;
import net.wurstclient.settings.BlockSetting;
import net.wurstclient.settings.BlockListSetting;
import net.wurstclient.settings.ItemListSetting;
import net.wurstclient.settings.FileSetting;
import net.wurstclient.settings.BookOffersSetting;
import net.wurstclient.clickgui2.screens.EditBlockScreen;
import net.wurstclient.clickgui2.screens.EditBlockListScreen;
import net.wurstclient.clickgui2.screens.EditItemListScreen;
import net.wurstclient.clickgui2.screens.EditBookOffersScreen;
import net.wurstclient.clickgui2.screens.SelectFileScreen;

/** Rise-like compact action row for settings that open a dedicated editor. */
final class NavigatorActionComponent extends Component
{
    private final Setting setting;
    private float actionX;

    NavigatorActionComponent(Setting setting)
    {
        this.setting = setting;
        setHeight(getDefaultHeight());
    }

    public void extractRenderState(net.minecraft.client.gui.GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTicks) { render(new GuiGraphicsExtractor(graphics), mouseX, mouseY, partialTicks); }
@Override public void render(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTicks)
    {
        float x = getX(), y = getY();
        String name = setting.getTranslatedName() + ":";
        NanoVGUI.text(name, x, y, NavigatorStyle.VALUE_TEXT, 8);
        actionX = x + NanoVGUI.width(name, 8) + 2;
        String label = setting instanceof FileSetting file ? file.getSelectedFileName()
            : setting instanceof BlockSetting block ? block.getBlockName() : "编辑...";
        NanoVGUI.text(NanoVGUI.plainSubstrByWidth(label, getX() + getWidth() - actionX, 8),
            actionX, y, NavigatorStyle.VALUE_TEXT, 8);
    }

    @Override
    public void handleMouseClick(double mouseX, double mouseY, int mouseButton)
    {
        if(mouseX < actionX) return;
        if(mouseButton == 1 && setting instanceof BlockSetting block)
            block.resetToDefault();
        if(mouseButton != 0) return;
        if(setting instanceof BlockSetting block)
            MC.setScreenAndShow(new EditBlockScreen(MC.gui.screen(), block));
        else if(setting instanceof BlockListSetting blocks)
            MC.setScreenAndShow(new EditBlockListScreen(MC.gui.screen(), blocks));
        else if(setting instanceof ItemListSetting items)
            MC.setScreenAndShow(new EditItemListScreen(MC.gui.screen(), items));
        else if(setting instanceof BookOffersSetting books)
            MC.setScreenAndShow(new EditBookOffersScreen(MC.gui.screen(), books));
        else if(setting instanceof FileSetting file)
            MC.setScreenAndShow(new SelectFileScreen(MC.gui.screen(), file));
    }

    @Override public int getDefaultWidth() { return 271; }
    @Override public int getDefaultHeight() { return 14; }
}

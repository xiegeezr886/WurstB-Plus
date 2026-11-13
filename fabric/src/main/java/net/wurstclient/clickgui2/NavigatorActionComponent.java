package net.wurstclient.clickgui2;

import net.minecraft.client.gui.GuiGraphics;
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

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTicks)
    {
        float x = getX(), y = getY();
        String name = setting.getTranslatedName() + ":";
        NanoVGUI.text(name, x, y, 0xDCFFFFFF, 8);
        actionX = x + NanoVGUI.width(name, 8) + 2;
        String label = setting instanceof FileSetting file ? file.getSelectedFileName()
            : setting instanceof BlockSetting block ? block.getBlockName() : "编辑...";
        NanoVGUI.text(NanoVGUI.plainSubstrByWidth(label, getX() + getWidth() - actionX, 8),
            actionX, y, 0xDCFFFFFF, 8);
    }

    @Override
    public void handleMouseClick(double mouseX, double mouseY, int mouseButton)
    {
        if(mouseX < actionX) return;
        if(mouseButton == 1 && setting instanceof BlockSetting block)
            block.resetToDefault();
        if(mouseButton != 0) return;
        if(setting instanceof BlockSetting block)
            MC.setScreen(new EditBlockScreen(MC.screen, block));
        else if(setting instanceof BlockListSetting blocks)
            MC.setScreen(new EditBlockListScreen(MC.screen, blocks));
        else if(setting instanceof ItemListSetting items)
            MC.setScreen(new EditItemListScreen(MC.screen, items));
        else if(setting instanceof BookOffersSetting books)
            MC.setScreen(new EditBookOffersScreen(MC.screen, books));
        else if(setting instanceof FileSetting file)
            MC.setScreen(new SelectFileScreen(MC.screen, file));
    }

    @Override public int getDefaultWidth() { return 271; }
    @Override public int getDefaultHeight() { return 14; }
}

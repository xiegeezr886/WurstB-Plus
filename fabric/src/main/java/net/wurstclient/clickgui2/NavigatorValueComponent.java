package net.wurstclient.clickgui2;

import java.awt.Color;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.util.Mth;
import net.wurstclient.hud2.nanovg.NanoVGUI;
import net.wurstclient.settings.CheckboxSetting;
import net.wurstclient.settings.ColorSetting;
import net.wurstclient.settings.EnumSetting;
import net.wurstclient.settings.Setting;
import net.wurstclient.settings.SliderSetting;
import net.wurstclient.settings.TextFieldSetting;
import org.lwjgl.glfw.GLFW;

/** Rise standard value components, using the existing font and Fabric setting model. */
final class NavigatorValueComponent extends Component
{
    private final Setting setting;
    private final NavigatorTextBox editor;
    private boolean dragging;
    private boolean colorOpen;
    private int colorDrag;
    private float hue, saturation, brightness;
    private float sliderX, colorX, knob, booleanSize;
    private long lastFrame;

    static Component create(Setting setting)
    {
        if(setting instanceof CheckboxSetting || setting instanceof EnumSetting<?>
            || setting instanceof SliderSetting || setting instanceof TextFieldSetting
            || setting instanceof ColorSetting)
            return new NavigatorValueComponent(setting);
        return new NavigatorActionComponent(setting);
    }

    private NavigatorValueComponent(Setting setting)
    {
        this.setting = setting;
        if(setting instanceof SliderSetting)
        {
            editor = new NavigatorTextBox("1234567890.-", null);
            syncNumber();
        }else if(setting instanceof TextFieldSetting text)
        {
            editor = new NavigatorTextBox(null, text::setValue);
            editor.set(text.getValue());
            editor.end();
        }else editor = null;
        setHeight(getDefaultHeight());
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTicks)
    {
        long now = System.currentTimeMillis();
        long elapsed = lastFrame == 0 ? 16 : Math.min(500, Math.max(0, now - lastFrame));
        lastFrame = now;
        float x = getX(), y = getY();
        String name = setting.getTranslatedName();
        float nameWidth = NanoVGUI.width(name, 8);
        if(setting instanceof EnumSetting<?> mode)
        {
            NanoVGUI.text(name + ":", x, y, 0xDCFFFFFF, 8);
            NanoVGUI.text(mode.getSelected().toString(), x + NanoVGUI.width(name + ":", 8) + 2, y, 0xDCFFFFFF, 8);
        }else
        {
            NanoVGUI.text(name, x, y, 0xFFFFFFFF, 8);
            if(setting instanceof CheckboxSetting checkbox)
            {
                float dotX = x + nameWidth + 5.5F;
                booleanSize = Mth.clamp(booleanSize + (checkbox.isChecked() ? 1 : -1) * elapsed / 20F, 0, 5);
                NanoVGUI.fillRounded(dotX, y, dotX + 5, y + 5, 2.5F, 0xFF171A21);
                if(booleanSize > 0)
                    NanoVGUI.circle(dotX + 2.5F, y + 2.5F, booleanSize / 2, accent());
                if(setting.hasChildren())
                    NanoVGUI.text(setting.isExpanded() ? "−" : "+", x + getWidth() - 9, y, 0x82FFFFFF, 8);
            }else if(setting instanceof SliderSetting slider)
            {
                sliderX = x + nameWidth + 7;
                if(dragging) setSlider(mouseX);
                if(!editor.focused()) syncNumber();
                editor.render(sliderX + 105, y, 20, 8, 0xDCFFFFFF, false, number(slider.getDefaultValue()), now);
                NanoVGUI.fillRounded(sliderX, y + 1.5F, sliderX + 100, y + 3.5F, 1, 0xFF171A21);
                knob += ((float)slider.getPercentage() - knob) * (1 - (float)Math.pow(29.0 / 30.0, elapsed + 1));
                NanoVGUI.circle(sliderX + knob * 100, y + 2.5F, 2.5F, accent());
            }else if(setting instanceof TextFieldSetting text)
            {
                if(!editor.focused()) editor.set(text.getValue());
                editor.render(x, y + 14, 230.5F, 8, 0xFFFFFFFF, false, "", now);
            }else if(setting instanceof ColorSetting color)
            {
                float afterName = x + nameWidth + 4;
                NanoVGUI.fillRounded(afterName, y, afterName + 15, y + 7, 2.5F, color.getColorI());
                colorX = afterName + 18.5F;
                if(colorOpen) renderColor(color, mouseX, mouseY, y);
            }
        }
    }

    private void renderColor(ColorSetting color, int mouseX, int mouseY, float y)
    {
        if(colorDrag == 0)
        {
            float[] hsv = Color.RGBtoHSB(color.getRed(), color.getGreen(), color.getBlue(), null);
            if(hsv[1] > 0 && hsv[2] > 0) hue = hsv[0];
            saturation = hsv[1];
            brightness = hsv[2];
        }else updateColor(mouseX, mouseY);
        float top = y + 0.5F;
        NavigatorStyle.dropShadow(10, colorX, top, 105, 105, 40, 24);
        NanoVGUI.fillRounded(colorX - 0.5F, y, colorX + 104.5F, y + 105, 9, 0xFF121419);
        NanoVGUI.fillRounded(colorX, top, colorX + 104, top + 104, 9, 0xFE171A21);
        NavigatorStyle.roundedGradient(colorX, top, 104, 66, 7, 0xFFFFFFFF, Color.HSBtoRGB(hue, 1, 1));
        NavigatorStyle.roundedGradient(colorX - 0.5F, top, 105, 66.5F,
            0.5F, 0x00000000, 0xFF000000, true);
        float hueX = colorX + 8.5F, hueY = top + 72;
        for(int i = 0; i < 88; i++)
            NanoVGUI.fill(hueX + i, hueY, hueX + i + 1, hueY + 7, Color.HSBtoRGB(i / 88F, 1, 1));
        float marker = hueX + hue * 88;
        NanoVGUI.fillRounded(marker - 5.5F, hueY - 2, marker + 5.5F, hueY + 9, 5, Color.HSBtoRGB(hue, 1, 1));
        NanoVGUI.outlineRounded(marker - 5.5F, hueY - 2, marker + 5.5F, hueY + 9, 3.4F, 1, 0xFF000000);
        float selectionX = colorX + saturation * 105, selectionY = top + (1 - brightness) * 66;
        NanoVGUI.circle(selectionX, selectionY, 3.5F, 0xFFFFFFFF);
        NanoVGUI.circle(selectionX, selectionY, 3, 0xFF000000);
        NanoVGUI.circle(selectionX, selectionY, 2.5F, color.getColorI());
        float valueY = top + 84;
        NavigatorStyle.dropShadow(30, hueX, valueY, 15, 15.5F, 40, 6);
        NanoVGUI.fillRounded(hueX, valueY, hueX + 15, valueY + 15.5F, 3.5F, color.getColorI());
        float textX = colorX + 32;
        NanoVGUI.centeredText(Integer.toString(color.getRed()), textX + 8.5F, valueY, 0xDCFFFFFF, 8.5F);
        NanoVGUI.centeredText(Integer.toString(color.getGreen()), textX + 30, valueY, 0xDCFFFFFF, 8.5F);
        NanoVGUI.centeredText(Integer.toString(color.getBlue()), textX + 51, valueY, 0xDCFFFFFF, 8.5F);
        NanoVGUI.text(hex(color), textX, valueY + 13, 0xFF373B3D, 6.5F);
    }

    @Override
    public void handleMouseClick(double mouseX, double mouseY, int button)
    {
        if(setting instanceof CheckboxSetting checkbox)
        {
            if(setting.hasChildren() && mouseX >= getX() + getWidth() - 12)
                setting.setExpanded(!setting.isExpanded());
            else checkbox.setChecked(!checkbox.isChecked());
        }else if(setting instanceof EnumSetting<?> mode)
        {
            if(button == 0) mode.selectNext();
            else if(button == 1) mode.selectPrev();
        }else if(setting instanceof SliderSetting slider)
        {
            if(slider.isDisabled() || slider.isLocked()) return;
            if(button == 0 && mouseX >= sliderX - 5 && mouseX < sliderX + 105)
            {
                editor.focus(false);
                dragging = true;
                setSlider(mouseX);
            }else editor.click(mouseX, mouseY, button);
        }else if(setting instanceof TextFieldSetting)
            editor.click(mouseX, mouseY, button);
        else if(setting instanceof ColorSetting color)
        {
            float y = getY() + 0.5F;
            if(colorOpen && mouseX >= colorX && mouseX < colorX + 105 && mouseY >= y && mouseY < y + 105)
            {
                if(mouseY < y + 66) colorDrag = 1;
                else if(mouseY < y + 86) colorDrag = 2;
                else MC.keyboardHandler.setClipboard(mouseY < y + 97
                    ? color.getRed() + ", " + color.getGreen() + ", " + color.getBlue() : hex(color));
                updateColor(mouseX, mouseY);
            }else colorOpen = !colorOpen;
            setHeight(getDefaultHeight());
        }
    }

    private void setSlider(double mouseX)
    {
        SliderSetting slider = (SliderSetting)setting;
        slider.setValue(slider.getMinimum() + slider.getRange() * Mth.clamp((mouseX - sliderX) / 100, 0, 1));
    }

    private void updateColor(double mouseX, double mouseY)
    {
        if(colorDrag == 0) return;
        if(colorDrag == 1)
        {
            saturation = (float)Mth.clamp((mouseX - colorX) / 105, 0, 1);
            brightness = 1 - (float)Mth.clamp((mouseY - getY() - 0.5) / 66, 0, 1);
        }else hue = (float)Mth.clamp((mouseX - colorX - 8.5) / 88, 0, 1);
        ((ColorSetting)setting).setColor(Color.getHSBColor(hue, saturation, brightness));
    }

    void release() { dragging = false; colorDrag = 0; }
    void blur()
    {
        release();
        if(editor != null) editor.focus(false);
        colorOpen = false;
        setHeight(getDefaultHeight());
    }
    boolean isEditing() { return editor != null && editor.focused(); }
    void retainFocusWithin(int originY, int top, int bottom)
    {
        int editorTop = originY + getY() + (setting instanceof TextFieldSetting ? 14 : 0);
        if(isEditing() && (editorTop + 8 <= top || editorTop >= bottom)) editor.focus(false);
    }
    boolean keyPressed(int key, int scan, int modifiers)
    {
        if(!isEditing()) return false;
        if(setting instanceof SliderSetting && (key == GLFW.GLFW_KEY_ENTER || key == GLFW.GLFW_KEY_KP_ENTER))
        {
            commitNumber();
            editor.focus(false);
            return true;
        }
        return editor.key(key, modifiers);
    }
    boolean charTyped(char value, int modifiers) { return isEditing() && editor.character(value); }
    private void syncNumber() { if(setting instanceof SliderSetting slider) editor.set(number(slider.getValue())); }
    private void commitNumber()
    {
        SliderSetting slider = (SliderSetting)setting;
        try
        {
            double value = editor.value().isEmpty() ? slider.getDefaultValue() : Double.parseDouble(editor.value());
            if(Double.isFinite(value)) slider.setValue(value);
        }catch(NumberFormatException ignored) {}
        syncNumber();
    }
    private static String number(double value) { return value == Math.rint(value) ? Long.toString((long)value) : Double.toString(value); }
    private static String hex(ColorSetting color) { return String.format("#%02X%02X%02X", color.getRed(), color.getGreen(), color.getBlue()); }
    @Override public int getDefaultWidth() { return 271; }
    @Override public int getDefaultHeight() { return setting instanceof TextFieldSetting ? 28 : setting instanceof ColorSetting ? colorOpen ? 110 : 15 : 14; }
    private static int accent() { return NavigatorPreferences.theme.primary(); }
}

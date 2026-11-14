/* OpaiBounce settings adaptation (GPL-3.0-or-later). See assets/wurstpenguin/opai/NOTICE.txt. */
package net.wurstclient.clickgui2;

import java.awt.Color;
import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.client.gui.GuiGraphics;
import net.wurstclient.Feature;
import net.wurstclient.WurstClient;
import net.wurstclient.hud2.nanovg.NanoVGUI;
import net.wurstclient.settings.*;
import net.wurstclient.clickgui2.screens.*;
import org.lwjgl.glfw.GLFW;

/** Inline controls measured in GUI pixels (one half of OpaiBounce's CSS units). */
final class OpaiSettings
{
    private final Feature feature;
    private final Map<Setting, Value> values = new IdentityHashMap<>();
    private List<Value> visible = List.of();
    private float x, y, width;
    private float clipTop, clipBottom;

    OpaiSettings(Feature feature) { this.feature = feature; }

    float height()
    {
        List<Setting> settings = SettingTreeLayout.flatten(feature.getSettings().values());
        List<Value> next = new ArrayList<>();
        for(Setting setting : settings) next.add(values.computeIfAbsent(setting, Value::new));
        for(Value value : visible) if(!next.contains(value)) value.blur();
        visible = next;
        return (float)visible.stream().mapToDouble(Value::height).sum() + 4;
    }

    void render(GuiGraphics graphics, float x, float y, float width, float top, float bottom, int mouseX, int mouseY)
    {
        this.x = x; this.y = y; this.width = width; clipTop = top; clipBottom = bottom;
        float offset = y;
        for(Value value : visible)
        {
            float indent = value.setting.getDepth() * 5;
            value.x = x + indent; value.y = offset; value.width = width - indent;
            if(offset + value.height() > top && offset < bottom) value.render(mouseX, mouseY);
            else if(value.editing()) value.blur();
            offset += value.height();
        }
    }

    boolean click(double mouseX, double mouseY, int button)
    {
        if(mouseY < clipTop || mouseY >= clipBottom || mouseX < x || mouseX >= x + width) return false;
        for(Value value : visible)
            if(mouseY >= value.y && mouseY < value.y + value.height())
            {
                for(Value other : visible) if(other != value) other.blur();
                value.click(mouseX, mouseY, button);
                return true;
            }
        return false;
    }

    boolean editing() { return visible.stream().anyMatch(Value::editing); }
    void blur() { values.values().forEach(Value::blur); }
    void release() { values.values().forEach(value -> value.drag = 0); }
    boolean key(int key, int modifiers)
    {
        for(Value value : visible) if(value.editing()) return value.key(key, modifiers);
        return false;
    }
    boolean character(char c)
    {
        for(Value value : visible) if(value.editing()) return value.editor.character(c);
        return false;
    }

    private static final class Value
    {
        final Setting setting;
        final NavigatorTextBox editor;
        float x, y, width;
        boolean open;
        int drag;
        float hue, saturation, brightness;

        Value(Setting setting)
        {
            this.setting = setting;
            editor = setting instanceof SliderSetting ? new NavigatorTextBox("0123456789.-", null)
                : setting instanceof TextFieldSetting text ? new NavigatorTextBox(null, text::setValue) : null;
        }

        float height()
        {
            if(setting instanceof CheckboxSetting) return 19.6F;
            if(setting instanceof SliderSetting) return 20;
            if(setting instanceof EnumSetting<?> mode) return 40 + (open ? mode.getValues().length * 20 : 0);
            if(setting instanceof ColorSetting) return open ? 110 : 40;
            return 40;
        }

        boolean editing() { return editor != null && editor.focused(); }

        void render(int mouseX, int mouseY)
        {
            long now = System.currentTimeMillis();
            String name = setting.getTranslatedName();
            if(setting instanceof CheckboxSetting checkbox)
            {
                OpaiStyle.text(name, x, y + 5, width - 30 - (setting.hasChildren() ? 11 : 0), OpaiStyle.TEXT);
                OpaiStyle.toggle(x + width - 26 - (setting.hasChildren() ? 11 : 0), y + 1.8F, checkbox.isChecked());
                if(setting.hasChildren()) NanoVGUI.text(setting.isExpanded() ? "−" : "+", x + width - 7, y + 5, OpaiStyle.MUTED, 8);
                return;
            }
            if(setting instanceof SliderSetting slider)
            {
                OpaiStyle.text(name, x, y + 2, width - 34, OpaiStyle.TEXT);
                if(!editing()) editor.set(number(slider.getValue()));
                editor.render(x + width - 29, y + 2, 29, OpaiStyle.FONT, OpaiStyle.TEXT, false, "", now);
                float fraction = (float)Math.max(0, Math.min(1, slider.getPercentage()));
                NanoVGUI.fillRounded(x, y + 15, x + width, y + 17.5F, 2, OpaiStyle.TRACK);
                NanoVGUI.fillRounded(x, y + 15, x + width * fraction, y + 17.5F, 2, OpaiStyle.ACCENT);
                NanoVGUI.circle(x + width * fraction, y + 16.25F, 4, OpaiStyle.ACCENT);
                if(drag != 0) updateSlider(mouseX);
                return;
            }
            OpaiStyle.text(name, x, y + 3, width, OpaiStyle.TEXT);
            OpaiStyle.field(x, y + 16, width, 21, open || editing());
            if(setting instanceof TextFieldSetting text)
            {
                if(!editing()) editor.set(text.getValue());
                editor.render(x + 6, y + 22, width - 12, OpaiStyle.FONT, OpaiStyle.TEXT, false, "", now);
            }else if(setting instanceof EnumSetting<?> mode)
            {
                OpaiStyle.text(mode.getSelected().toString(), x + 6, y + 22, width - 18, OpaiStyle.TEXT);
                NanoVGUI.text(open ? "−" : "+", x + width - 10, y + 22, OpaiStyle.MUTED, 8);
                if(open)
                    for(int i = 0; i < mode.getValues().length; i++)
                    {
                        float top = y + 40 + i * 20;
                        String label = mode.getValues()[i].toString();
                        boolean chosen = mode.getSelected() == mode.getValues()[i];
                        NanoVGUI.fill(x, top, x + width, top + 20, chosen ? 0xFF483A37 : OpaiStyle.FIELD);
                        OpaiStyle.text((chosen ? "✓ " : "") + label, x + 6, top + 6, width - 12, OpaiStyle.TEXT);
                    }
            }else if(setting instanceof ColorSetting color)
            {
                NanoVGUI.fillRounded(x + 5, y + 20, x + 19, y + 32, 2, color.getColorI());
                OpaiStyle.text(String.format("#%06X", color.getColorI() & 0xFFFFFF), x + 25, y + 22, width - 30, OpaiStyle.TEXT);
                if(open) renderColor(color, mouseX, mouseY);
            }else
            {
                String label = setting instanceof FileSetting file ? file.getSelectedFileName()
                    : setting instanceof BlockSetting block ? block.getBlockName() : "编辑…";
                OpaiStyle.text(label, x + 6, y + 22, width - 12, OpaiStyle.TEXT);
            }
        }

        void renderColor(ColorSetting color, int mouseX, int mouseY)
        {
            if(drag == 0)
            {
                float[] hsv = Color.RGBtoHSB(color.getRed(), color.getGreen(), color.getBlue(), null);
                if(hsv[1] > 0 && hsv[2] > 0) hue = hsv[0];
                saturation = hsv[1]; brightness = hsv[2];
            }else updateColor(mouseX, mouseY);
            NavigatorStyle.roundedGradient(x, y + 41, width, 50, 2, 0xFFFFFFFF, Color.HSBtoRGB(hue, 1, 1));
            NavigatorStyle.roundedGradient(x, y + 41, width, 50, 2, 0, 0xFF000000, true);
            for(int i = 0; i < (int)width; i++) NanoVGUI.fill(x + i, y + 98, x + i + 1, y + 103, Color.HSBtoRGB(i / width, 1, 1));
            NanoVGUI.circle(x + saturation * width, y + 41 + (1 - brightness) * 50, 2.5F, OpaiStyle.TEXT);
            NanoVGUI.circle(x + saturation * width, y + 41 + (1 - brightness) * 50, 1.5F, color.getColorI());
            NanoVGUI.circle(x + hue * width, y + 100.5F, 3.5F, OpaiStyle.TEXT);
        }

        void click(double mx, double my, int button)
        {
            if(button == 1 && setting.hasChildren()) {
                setting.setExpanded(!setting.isExpanded()); return;
            }
            if(setting instanceof CheckboxSetting checkbox)
            {
                if(setting.hasChildren() && (button == 1 || mx > x + width - 11)) setting.setExpanded(!setting.isExpanded());
                else if(button == 0) checkbox.setChecked(!checkbox.isChecked());
            }else if(setting instanceof SliderSetting slider)
            {
                if(slider.isLocked() || slider.isDisabled()) return;
                if(button == 0 && my >= y + 12) { editor.focus(false); drag = 1; updateSlider(mx); }
                else editor.click(mx, my, button);
            }else if(setting instanceof TextFieldSetting) {
                if(button == 0 && my >= y + 16 && my < y + 37) { editor.focus(true); editor.end(); }
            }
            else if(setting instanceof EnumSetting<?> mode)
            {
                if(button == 0 && open && my >= y + 40)
                {
                    int index = (int)((my - y - 40) / 20);
                    if(index >= 0 && index < mode.getValues().length) mode.setSelected(mode.getValues()[index].toString());
                    open = false;
                }else if(button == 0 && my >= y + 16) open = !open;
            }else if(setting instanceof ColorSetting)
            {
                if(button != 0) return;
                if(open && my >= y + 41) { drag = my < y + 94 ? 1 : 2; updateColor(mx, my); }
                else if(my >= y + 16) open = !open;
            }else if(button == 0 && my >= y + 16) editSpecial();
            else if(button == 1 && setting.hasChildren()) setting.setExpanded(!setting.isExpanded());
        }

        void editSpecial()
        {
            var mc = WurstClient.MC;
            if(setting instanceof BlockSetting value) mc.setScreen(new EditBlockScreen(mc.screen, value));
            else if(setting instanceof BlockListSetting value) mc.setScreen(new EditBlockListScreen(mc.screen, value));
            else if(setting instanceof ItemListSetting value) mc.setScreen(new EditItemListScreen(mc.screen, value));
            else if(setting instanceof BookOffersSetting value) mc.setScreen(new EditBookOffersScreen(mc.screen, value));
            else if(setting instanceof FileSetting value) mc.setScreen(new SelectFileScreen(mc.screen, value));
        }

        void updateSlider(double mx)
        {
            SliderSetting slider = (SliderSetting)setting;
            slider.setValue(slider.getMinimum() + slider.getRange() * Math.max(0, Math.min(1, (mx - x) / width)));
        }

        void updateColor(double mx, double my)
        {
            if(drag == 1) { saturation = (float)Math.max(0, Math.min(1, (mx - x) / width)); brightness = 1 - (float)Math.max(0, Math.min(1, (my - y - 41) / 50)); }
            else hue = (float)Math.max(0, Math.min(1, (mx - x) / width));
            ((ColorSetting)setting).setColor(Color.getHSBColor(hue, saturation, brightness));
        }

        boolean key(int key, int modifiers)
        {
            if(key == GLFW.GLFW_KEY_ENTER || key == GLFW.GLFW_KEY_KP_ENTER || key == GLFW.GLFW_KEY_ESCAPE)
            { blur(); return true; }
            return editor.key(key, modifiers);
        }

        void blur()
        {
            if(editing() && setting instanceof SliderSetting slider)
                try { double value = Double.parseDouble(editor.value()); if(Double.isFinite(value)) slider.setValue(value); }
                catch(NumberFormatException ignored) {}
            if(editor != null) editor.focus(false);
            drag = 0;
        }

        static String number(double value) { return java.math.BigDecimal.valueOf(value).stripTrailingZeros().toPlainString(); }
    }
}

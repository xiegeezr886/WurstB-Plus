/* OpaiBounce settings adaptation (GPL-3.0-or-later). See assets/wurstpenguin/opai/NOTICE.txt. */
package net.wurstclient.clickgui2;

import java.awt.Color;
import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BooleanSupplier;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.wurstclient.Feature;
import net.wurstclient.WurstClient;
import net.wurstclient.hud2.nanovg.NanoVGUI;
import net.wurstclient.keybinds.KeyAction;
import net.wurstclient.settings.*;
import net.wurstclient.clickgui2.screens.*;
import org.lwjgl.glfw.GLFW;

/** Inline controls in Opai's reference coordinate system. */
final class OpaiSettings
{
    private static final float LABEL = 16, FIELD = 21, VALUE = 40;
    private static final float BIND_ACTIONS = 22.6F, BIND_BOTTOM = 42.6F;
    private static final float BIND_RADIUS = 5;
    private final Feature feature;
    private final Map<Setting, Value> values = new IdentityHashMap<>();
    private final OpaiBinding binding;
    private final Runnable captureBinding;
    private final BooleanSupplier waiting;
    private List<Value> visible = List.of();
    private float x, width = 132, bindingY;
    private float clipTop, clipBottom;
    private boolean rendered;
    private boolean measured;
    private float measuredHeight;

    OpaiSettings(Feature feature, Runnable captureBinding, BooleanSupplier waiting)
    {
        this.feature = feature;
        this.captureBinding = captureBinding;
        this.waiting = waiting;
        String command = OpaiBinding.commandFor(feature);
        binding = command == null ? null : new OpaiBinding(WurstClient.INSTANCE.getKeybinds(), command);
    }

    OpaiBinding binding() { return binding; }
    void beginFrame() { rendered = measured = false; }
    void endFrame() { if(!rendered) { blur(); clipTop = clipBottom = 0; } }
    void advance(float seconds) { values.values().forEach(value -> value.advance(seconds)); }

    float height()
    {
        if(measured) return measuredHeight;
        List<Setting> settings = SettingTreeLayout.flatten(feature.getSettings().values());
        List<Value> next = new ArrayList<>();
        for(Setting setting : settings) next.add(values.computeIfAbsent(setting, Value::new));
        for(Value value : visible) if(!next.contains(value)) value.blur();
        visible = next;
        measuredHeight = binding == null ? 4 : 46.6F;
        for(Value value : visible) measuredHeight += value.height(width);
        measured = true;
        return measuredHeight;
    }

    void render(GuiGraphics graphics, float x, float y, float width, float top, float bottom, int mouseX, int mouseY)
    {
        rendered = true;
        this.x = x; this.width = width; clipTop = top; clipBottom = bottom;
        float offset = y;
        for(Value value : visible)
        {
            // Opai's nested controls retain the same left edge as their parent.
            value.x = x; value.y = offset; value.width = width;
            if(offset + value.height(width) > top && offset < bottom) value.render(mouseX, mouseY);
            else if(value.editing()) value.blur();
            offset += value.height(width);
        }
        bindingY = offset;
        if(binding != null && bindingY < bottom && bindingY + BIND_BOTTOM > top) renderBinding();
    }

    private void renderBinding()
    {
        net.wurstclient.gui.IslandButtonStyle.draw(x, bindingY, width, 19.6F,
            true, 0, waiting.getAsBoolean(), OpaiStyle.ACCENT);
        String key = binding.key();
        String label = waiting.getAsBoolean() ? "按下按键…" : key == null ? "无" :
            key.replace("key.keyboard.", "").replace("key.mouse.", "mouse.");
        float keyWidth = Math.min(width * 0.65F, NanoVGUI.width(label, OpaiStyle.FONT));
        OpaiStyle.text("按键", x + 6, bindingY + 5.5F, width - keyWidth - 14, OpaiStyle.TEXT);
        OpaiStyle.text(label, x + width - keyWidth - 6, bindingY + 5.5F, keyWidth, OpaiStyle.TEXT);
        String[] labels = {"Toggle", "Hold", "Smart"};
        for(int i = 0; i < labels.length; i++)
        {
            net.wurstclient.gui.IslandButtonStyle.draw(x + i * width / 3,
                bindingY + BIND_ACTIONS, width / 3 - 1, BIND_BOTTOM - BIND_ACTIONS,
                true, 0, binding.action().ordinal() == i, OpaiStyle.ACCENT);
            float labelWidth = NanoVGUI.width(labels[i], OpaiStyle.FONT);
            NanoVGUI.text(labels[i], x + (i + 0.5F) * width / 3 - labelWidth / 2, bindingY + 28.5F,
                binding.action().ordinal() == i ? OpaiStyle.ACCENT : OpaiStyle.TEXT, OpaiStyle.FONT);
        }
    }

    boolean click(double mouseX, double mouseY, int button)
    {
        if(mouseY < clipTop || mouseY >= clipBottom || mouseX < x - 2 || mouseX >= x + width + 2) return false;
        for(Value value : visible)
            if(mouseY >= value.y && mouseY < value.y + value.height(width))
            {
                for(Value other : visible) if(other != value) other.blur();
                value.click(mouseX, mouseY, button);
                return true;
            }
        blur();
        if(binding != null && button == 0)
        {
            if(mouseY >= bindingY && mouseY < bindingY + 19.6F) captureBinding.run();
            else if(mouseY >= bindingY + BIND_ACTIONS && mouseY < bindingY + BIND_BOTTOM)
                binding.action(KeyAction.values()[Math.max(0, Math.min(2, (int)((mouseX - x) / (width / 3))))]);
        }
        return true;
    }

    boolean scroll(double mouseX, double mouseY, double delta)
    {
        if(mouseY < clipTop || mouseY >= clipBottom || mouseX < x - 2 || mouseX >= x + width + 2) return false;
        for(Value value : visible)
            if(value.dropdown != null && value.dropdown.open() && mouseY >= value.y + VALUE && mouseY < value.y + value.height(width))
            { value.dropdown.wheel(delta); return true; }
        return false;
    }

    boolean drag(double mouseX, double mouseY)
    {
        boolean captured = false;
        for(Value value : values.values())
        {
            if(value.drag != 0)
            {
                if(value.setting instanceof SliderSetting) value.updateSlider(mouseX);
                else value.updateColor(mouseX, mouseY);
                captured = true;
            }
            if(value.editor != null) captured |= value.editor.drag(mouseX);
        }
        return captured;
    }
    boolean editing() { return visible.stream().anyMatch(Value::editing); }
    void blur() { values.values().forEach(Value::blur); }
    void release() { values.values().forEach(value -> { value.drag = 0; if(value.editor != null) value.editor.release(); }); }
    boolean key(int key, int modifiers)
    {
        for(Value value : visible) if(value.editing()) return value.key(key, modifiers);
        return false;
    }
    boolean character(char c)
    {
        for(Value value : visible) if(value.editing()) return value.editor != null && value.editor.character(c);
        return false;
    }

    private static final class Value
    {
        final Setting setting;
        final OpaiTextBox editor;
        final OpaiDropdown dropdown;
        float x, y, width;
        boolean colorFocused;
        int drag;
        final OpaiColor picker = new OpaiColor();
        final NavigatorAnimation checked;
        final NavigatorAnimation sliderProgress;
        float chevron;

        Value(Setting setting)
        {
            this.setting = setting;
            editor = setting instanceof SliderSetting ? new OpaiTextBox("0123456789.-+eE")
                : setting instanceof TextFieldSetting ? new OpaiTextBox(null) : null;
            dropdown = setting instanceof EnumSetting<?> mode ? new OpaiDropdown(mode.getValues().length) : null;
            checked = new NavigatorAnimation(setting instanceof CheckboxSetting checkbox && checkbox.isChecked() ? 1 : 0);
            sliderProgress = new NavigatorAnimation(setting instanceof SliderSetting slider ? clamp(slider.getPercentage()) : 0);
            chevron = setting.isExpanded() ? 1 : 0;
        }

        void advance(float seconds)
        {
            if(dropdown != null) dropdown.advance(seconds);
            chevron += ((setting.isExpanded() ? 1 : 0) - chevron) * Math.min(1, seconds / 0.16F);
        }

        float height(float width)
        {
            if(setting instanceof CheckboxSetting) return 19.6F;
            if(setting instanceof SliderSetting) return 20;
            if(dropdown != null) return VALUE + dropdown.height();
            if(setting instanceof ColorSetting) return 21.5F + (width - 10.5F) / 2.08F;
            return VALUE;
        }

        boolean editing() { return colorFocused || editor != null && editor.focused(); }

        void render(int mouseX, int mouseY)
        {
            long now = System.currentTimeMillis();
            String name = setting.getTranslatedName();
            if(setting instanceof CheckboxSetting checkbox)
            {
                checked.animate(checkbox.isChecked() ? 1 : 0, OpaiStyle.SWITCH_MILLIS, now,
                    NavigatorAnimation.Easing.LINEAR);
                OpaiStyle.text(name, x, y + 5, width - 30 - (setting.hasChildren() ? 11 : 0), OpaiStyle.TEXT);
                OpaiStyle.toggle(x + width - 26 - (setting.hasChildren() ? 11 : 0), y + 1.8F, checked.value(now));
                if(setting.hasChildren()) OpaiStyle.chevron(x + width - 6, y + 9.8F, chevron);
                return;
            }
            if(setting instanceof SliderSetting slider)
            {
                OpaiStyle.text(name, x, y + 2, width - 34, OpaiStyle.TEXT);
                if(!editing()) editor.set(number(slider.getValue()));
                editor.render(x + width - 29, y + 2, 29, OpaiStyle.TEXT, now, true);
                float fraction = clamp(slider.getPercentage());
                // Moon: typed/external changes ease into place; dragging follows the pointer directly.
                if(drag != 0) sliderProgress.set(fraction);
                else sliderProgress.animate(fraction, 180, now, NavigatorAnimation.Easing.OUT_QUINT);
                fraction = sliderProgress.value(now);
                NanoVGUI.fillRounded(x, y + 15, x + width, y + 17.5F, 2, OpaiStyle.TRACK);
                NanoVGUI.fillRounded(x, y + 15, x + width * fraction, y + 17.5F, 2, OpaiStyle.ACCENT);
                float thumbX = Math.max(x, Math.min(x + width - 8, x + width * fraction - 4));
                if(mouseX >= x && mouseX < x + width && mouseY >= y + 12 && mouseY < y + 20)
                    NanoVGUI.fillRounded(thumbX - 2, y + 10.25F, thumbX + 10, y + 22.25F, 4, 0x18B5BEF0);
                NanoVGUI.fillRounded(thumbX, y + 12.25F, thumbX + 8, y + 20.25F, 3, OpaiStyle.ACCENT);
                return;
            }
            OpaiStyle.text(name, x, y + 3, width, OpaiStyle.TEXT);
            if(setting instanceof ColorSetting color) { renderColor(color); return; }
            boolean choice = dropdown != null;
            float fieldX = choice ? x - 2 : x, fieldWidth = choice ? width + 4 : width;
            if(setting instanceof TextFieldSetting)
                OpaiStyle.field(fieldX, y + LABEL, fieldWidth, FIELD, editing());
            else
                net.wurstclient.gui.IslandButtonStyle.draw(fieldX, y + LABEL, fieldWidth, FIELD,
                    true, mouseX >= fieldX && mouseX < fieldX + fieldWidth
                        && mouseY >= y + LABEL && mouseY < y + LABEL + FIELD ? 1 : 0,
                    choice && dropdown.open(), OpaiStyle.ACCENT);
            if(setting instanceof TextFieldSetting text)
            {
                if(!editing()) editor.set(text.getValue());
                editor.render(x + 6, y + 22, width - 12, OpaiStyle.TEXT, now);
            }else if(setting instanceof EnumSetting<?> mode)
            {
                OpaiStyle.text(mode.getSelected().toString(), fieldX + 6, y + 22, fieldWidth - 12, OpaiStyle.TEXT);
                if(dropdown.height() > 0)
                {
                    NanoVGUI.pushScissor(fieldX, y + VALUE, fieldWidth, dropdown.height());
                    NanoVGUI.fill(fieldX, y + VALUE, fieldX + fieldWidth, y + VALUE + dropdown.height(), OpaiStyle.FIELD);
                    for(int i = 0; i < mode.getValues().length; i++)
                    {
                        float top = y + VALUE + i * OpaiDropdown.ROW - dropdown.scroll();
                        if(top + OpaiDropdown.ROW <= y + VALUE || top >= y + VALUE + dropdown.height()) continue;
                        boolean chosen = mode.getSelected() == mode.getValues()[i];
                        boolean hover = mouseX >= fieldX && mouseX < fieldX + fieldWidth && mouseY >= top && mouseY < top + OpaiDropdown.ROW;
                        if(chosen || hover) NanoVGUI.fill(fieldX, top, fieldX + fieldWidth, top + OpaiDropdown.ROW, chosen ? OpaiStyle.OPTION : 0x803C435F);
                        if(chosen) OpaiStyle.check(fieldX + 6, top + 6.5F, 6.5F, OpaiStyle.TEXT);
                        OpaiStyle.text(mode.getValues()[i].toString(), fieldX + (chosen ? 16.5F : 6), top + 6, fieldWidth - (chosen ? 22.5F : 12), OpaiStyle.TEXT);
                    }
                    NanoVGUI.popScissor();
                    if(mode.getValues().length * OpaiDropdown.ROW > dropdown.fullHeight())
                    {
                        float thumb = dropdown.fullHeight() * dropdown.fullHeight() / (mode.getValues().length * OpaiDropdown.ROW);
                        float top = y + VALUE + dropdown.scroll() / (mode.getValues().length * OpaiDropdown.ROW) * dropdown.fullHeight();
                        NanoVGUI.pushScissor(fieldX, y + VALUE, fieldWidth, dropdown.height());
                        NanoVGUI.fillRounded(fieldX + fieldWidth - 1.5F, top, fieldX + fieldWidth - 0.5F, top + thumb, 0.5F, OpaiStyle.SCROLLBAR);
                        NanoVGUI.popScissor();
                    }
                }
            }else
            {
                String label = setting instanceof FileSetting file ? file.getSelectedFileName()
                    : setting instanceof BlockSetting block ? block.getBlockName()
                    : setting instanceof BlockListSetting list ? list.size() + " 个方块"
                    : setting instanceof ItemListSetting list ? list.getItemNames().size() + " 个物品"
                    : setting instanceof BookOffersSetting list ? list.getOffers().size() + " 项交易" : "编辑…";
                OpaiStyle.text(label, x + 6, y + 22, width - 12, OpaiStyle.TEXT);
            }
        }

        float planeWidth() { return width - 10.5F; }
        float planeHeight() { return planeWidth() / 2.08F; }
        void syncColor(ColorSetting color)
        {
            picker.sync(color.getColorI());
        }
        void renderColor(ColorSetting color)
        {
            if(drag == 0) syncColor(color);
            float top = y + 17.5F, pw = planeWidth(), ph = planeHeight();
            NavigatorStyle.roundedGradient(x, top, pw, ph, 0, 0xFFFFFFFF, Color.HSBtoRGB(picker.hue(), 1, 1));
            NavigatorStyle.roundedGradient(x, top, pw, ph, 0, 0, 0xFF000000, true);
            // Source hue range is vertical: red, yellow, green, cyan, blue, magenta, red.
            for(int i = 0; i < 6; i++) NavigatorStyle.roundedGradient(x + width - 8, top + ph * i / 6, 8, ph / 6 + 0.1F, 0,
                Color.HSBtoRGB(i / 6F, 1, 1), Color.HSBtoRGB((i + 1) / 6F, 1, 1), true);
            NanoVGUI.line(x + width - 9, top + picker.hue() * ph, x + width + 1, top + picker.hue() * ph, 1, OpaiStyle.TEXT);
            float px = x + picker.saturation() * pw, py = top + (1 - picker.brightness()) * ph;
            NanoVGUI.circle(px, py, 2.5F, 0x80000000);
            NanoVGUI.circle(px, py, 1.75F, OpaiStyle.TEXT);
            NanoVGUI.circle(px, py, 1, color.getColorI());
            if(colorFocused) NanoVGUI.outlineRounded(x - 1, top - 1, x + pw + 1, top + ph + 1, 0, 0.5F, OpaiStyle.ACCENT);
        }

        void click(double mx, double my, int button)
        {
            if(button == 1 && setting.hasChildren()) { blur(); setting.setExpanded(!setting.isExpanded()); return; }
            if(setting instanceof CheckboxSetting checkbox)
            {
                if(setting.hasChildren() && mx > x + width - 11 && button == 0) setting.setExpanded(!setting.isExpanded());
                else if(button == 0) checkbox.setChecked(!checkbox.isChecked());
            }else if(setting instanceof SliderSetting slider)
            {
                if(slider.isLocked() || slider.isDisabled() || button != 0) return;
                if(my >= y + 12) { blur(); drag = 1; updateSlider(mx); }
                else if(mx >= x + width - 29) editor.click(mx, Screen.hasShiftDown());
                else blur();
            }else if(setting instanceof TextFieldSetting)
            {
                if(button == 0 && my >= y + LABEL && my < y + LABEL + FIELD) editor.click(mx, Screen.hasShiftDown());
                else blur();
            }else if(setting instanceof EnumSetting<?> mode)
            {
                if(button != 0) return;
                if(my >= y + VALUE)
                {
                    int index = dropdown.hit(my - y - VALUE);
                    if(index >= 0) { mode.setSelected(mode.getValues()[index].toString()); dropdown.close(); }
                }else if(my >= y + LABEL && my < y + LABEL + FIELD) dropdown.toggle(java.util.Arrays.asList(mode.getValues()).indexOf(mode.getSelected()));
            }else if(setting instanceof ColorSetting color)
            {
                if(button != 0 || my < y + 17.5F || my > y + 17.5F + planeHeight()) { colorFocused = false; return; }
                syncColor(color);
                if(mx >= x && mx <= x + planeWidth()) drag = 1;
                else if(mx >= x + width - 8 && mx <= x + width) drag = 2;
                colorFocused = drag == 1;
                if(drag != 0) updateColor(mx, my);
            }else if(button == 0 && my >= y + LABEL && my < y + LABEL + FIELD) editSpecial();
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
            slider.setValue(slider.getMinimum() + slider.getRange() * clamp((mx - x) / width));
        }
        void updateColor(double mx, double my)
        {
            if(drag == 1) picker.plane((mx - x) / planeWidth(), 1 - clamp((my - y - 17.5F) / planeHeight()));
            else picker.hue((my - y - 17.5F) / planeHeight());
            applyColor();
        }
        void applyColor() { ((ColorSetting)setting).setColor(new Color(picker.rgb())); }

        boolean key(int key, int modifiers)
        {
            if(key == GLFW.GLFW_KEY_ESCAPE)
            {
                // Discard the draft without changing the setting.
                if(editor != null) editor.focus(false);
                colorFocused = false; drag = 0;
                return true;
            }
            if(key == GLFW.GLFW_KEY_ENTER || key == GLFW.GLFW_KEY_KP_ENTER || key == GLFW.GLFW_KEY_TAB) { blur(); return true; }
            if(colorFocused)
            {
                float step = (modifiers & GLFW.GLFW_MOD_SHIFT) != 0 ? 0.1F : 0.01F;
                float saturation = picker.saturation(), brightness = picker.brightness();
                if(key == GLFW.GLFW_KEY_LEFT) saturation -= step;
                else if(key == GLFW.GLFW_KEY_RIGHT) saturation += step;
                else if(key == GLFW.GLFW_KEY_UP) brightness += step;
                else if(key == GLFW.GLFW_KEY_DOWN) brightness -= step;
                else if(key == GLFW.GLFW_KEY_HOME) saturation = 0;
                else if(key == GLFW.GLFW_KEY_END) saturation = 1;
                else return true;
                picker.plane(saturation, brightness);
                applyColor(); return true;
            }
            return editor != null && editor.key(key, modifiers);
        }
        void blur()
        {
            if(editor != null && editor.focused())
            {
                if(setting instanceof SliderSetting slider)
                    try { double value = Double.parseDouble(editor.value()); if(Double.isFinite(value)) slider.setValue(value); }
                    catch(NumberFormatException ignored) {}
                else if(setting instanceof TextFieldSetting text) text.setValue(editor.value());
                editor.focus(false);
            }
            if(dropdown != null) dropdown.close();
            colorFocused = false; drag = 0;
        }
        static float clamp(double value) { return (float)Math.max(0, Math.min(1, value)); }
        static String number(double value) { return java.math.BigDecimal.valueOf(value).stripTrailingZeros().toPlainString(); }
    }
}

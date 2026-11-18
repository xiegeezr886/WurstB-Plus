/* OpaiBounce field adaptation. See assets/wurstpenguin/opai/NOTICE.txt. */
package net.wurstclient.clickgui2;

import net.wurstclient.WurstClient;
import net.wurstclient.hud2.nanovg.NanoVGUI;
import org.lwjgl.glfw.GLFW;

/** A draft editor; settings commit explicitly on Enter or blur. */
final class OpaiTextBox
{
    private final String allowed;
    private String text = "";
    private int cursor, anchor;
    private boolean focused, dragging;
    private float left, scroll;

    OpaiTextBox(String allowed) { this.allowed = allowed; }
    String value() { return text; }
    boolean focused() { return focused; }
    int cursor() { return cursor; }
    String selection() { return text.substring(Math.min(cursor, anchor), Math.max(cursor, anchor)); }
    void focus(boolean value) { focused = value; if(!value) dragging = false; }
    void release() { dragging = false; }
    void set(String value)
    {
        if(text.equals(value)) return;
        text = value;
        cursor = anchor = text.length();
        scroll = 0;
    }
    void selectAll() { anchor = 0; cursor = text.length(); }

    void render(float x, float y, float width, int color, long now)
    {
        render(x, y, width, color, now, false);
    }
    void render(float x, float y, float width, int color, long now, boolean rightAligned)
    {
        left = x + (rightAligned ? Math.max(0, width - measure(text.length()) - 1) : 0);
        float caret = measure(cursor);
        if(focused) scroll = Math.max(caret - width + 1, Math.min(scroll, caret));
        else scroll = 0;
        NanoVGUI.pushScissor(x, y - 1, width, OpaiStyle.FONT + 4);
        if(focused && cursor != anchor)
            NanoVGUI.fill(left + measure(Math.min(cursor, anchor)) - scroll, y - 1,
                left + measure(Math.max(cursor, anchor)) - scroll, y + OpaiStyle.FONT + 2, 0x667078B5);
        NanoVGUI.text(text, left - scroll, y, color, OpaiStyle.FONT);
        if(focused && now % 1000 < 600)
            NanoVGUI.fill(left + caret - scroll, y, left + caret - scroll + 0.6F, y + OpaiStyle.FONT + 1, color);
        NanoVGUI.popScissor();
    }

    void click(double mouseX, boolean shift)
    {
        focus(true);
        move(hit(mouseX), shift);
        dragging = true;
    }
    boolean drag(double mouseX)
    {
        if(!dragging) return false;
        move(hit(mouseX), true);
        return true;
    }
    private int hit(double mouseX)
    {
        double local = mouseX - left + scroll;
        int previous = 0;
        for(int next = 0; next < text.length();)
        {
            next = text.offsetByCodePoints(next, 1);
            if(local < (measure(previous) + measure(next)) / 2) return previous;
            previous = next;
        }
        return text.length();
    }
    private float measure(int index) { return NanoVGUI.width(text.substring(0, index), OpaiStyle.FONT); }

    boolean character(char c)
    {
        if(!focused) return false;
        insert(String.valueOf(c));
        return true;
    }
    void insert(String value)
    {
        StringBuilder accepted = new StringBuilder();
        for(char c : value.toCharArray())
            if(!Character.isISOControl(c) && c != '\u00a7' && (allowed == null || allowed.indexOf(c) >= 0)) accepted.append(c);
        // Invalid input must not erase a selected value.
        if(accepted.length() == 0) return;
        int start = Math.min(cursor, anchor), end = Math.max(cursor, anchor);
        int limit = Math.max(0, 32767 - (text.length() - end + start));
        if(accepted.length() > limit) accepted.setLength(limit);
        if(accepted.length() == 0) return;
        text = text.substring(0, start) + accepted + text.substring(end);
        cursor = anchor = start + accepted.length();
    }
    void move(int index, boolean selecting)
    {
        cursor = Math.max(0, Math.min(text.length(), index));
        if(!selecting) anchor = cursor;
    }
    private int word(int direction)
    {
        int index = cursor;
        if(direction < 0)
        {
            while(index > 0 && Character.isWhitespace(text.codePointBefore(index))) index = text.offsetByCodePoints(index, -1);
            while(index > 0 && !Character.isWhitespace(text.codePointBefore(index))) index = text.offsetByCodePoints(index, -1);
        }else
        {
            while(index < text.length() && !Character.isWhitespace(text.codePointAt(index))) index = text.offsetByCodePoints(index, 1);
            while(index < text.length() && Character.isWhitespace(text.codePointAt(index))) index = text.offsetByCodePoints(index, 1);
        }
        return index;
    }
    private void delete(int direction, boolean control)
    {
        if(cursor == anchor)
        {
            int next = control ? word(direction) : text.offsetByCodePoints(cursor,
                direction < 0 ? (cursor > 0 ? -1 : 0) : (cursor < text.length() ? 1 : 0));
            anchor = next;
        }
        int start = Math.min(cursor, anchor), end = Math.max(cursor, anchor);
        text = text.substring(0, start) + text.substring(end);
        cursor = anchor = start;
    }
    boolean key(int key, int modifiers)
    {
        if(!focused) return false;
        boolean control = (modifiers & (GLFW.GLFW_MOD_CONTROL | GLFW.GLFW_MOD_SUPER)) != 0;
        boolean shift = (modifiers & GLFW.GLFW_MOD_SHIFT) != 0;
        if(control && key == GLFW.GLFW_KEY_A) selectAll();
        else if(control && (key == GLFW.GLFW_KEY_C || key == GLFW.GLFW_KEY_X))
        {
            if(cursor != anchor) WurstClient.MC.keyboardHandler.setClipboard(selection());
            if(key == GLFW.GLFW_KEY_X && cursor != anchor) delete(-1, false);
        }
        else if(control && key == GLFW.GLFW_KEY_V) insert(WurstClient.MC.keyboardHandler.getClipboard());
        else if(key == GLFW.GLFW_KEY_BACKSPACE) delete(-1, control);
        else if(key == GLFW.GLFW_KEY_DELETE) delete(1, control);
        else if(key == GLFW.GLFW_KEY_HOME) move(0, shift);
        else if(key == GLFW.GLFW_KEY_END) move(text.length(), shift);
        else if(key == GLFW.GLFW_KEY_LEFT || key == GLFW.GLFW_KEY_RIGHT)
        {
            int direction = key == GLFW.GLFW_KEY_LEFT ? -1 : 1;
            int next = control ? word(direction) : !shift && cursor != anchor
                ? (direction < 0 ? Math.min(cursor, anchor) : Math.max(cursor, anchor))
                : text.offsetByCodePoints(cursor, direction < 0 ? (cursor > 0 ? -1 : 0) : (cursor < text.length() ? 1 : 0));
            move(next, shift);
        }
        return true;
    }
}

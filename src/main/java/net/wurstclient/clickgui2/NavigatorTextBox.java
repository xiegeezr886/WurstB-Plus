package net.wurstclient.clickgui2;

import java.util.function.Consumer;
import net.wurstclient.WurstClient;
import net.wurstclient.hud2.nanovg.NanoVGUI;
import org.lwjgl.glfw.GLFW;

/** Rise TextBox's scrolling text, centered easing, end-on-click cursor and clipboard behavior. */
final class NavigatorTextBox
{
	private String text = "";
	private final String allowed;
	private final Consumer<String> changed;
	private int cursor;
	private int visibleStart;
	private boolean selected;
	private float left, top, width, size, drawnX, caretX;
	private long lastFrame;

	NavigatorTextBox(String allowed, Consumer<String> changed)
	{
		this.allowed = allowed;
		this.changed = changed;
	}
	String value() { return text; }
	boolean focused() { return selected; }
	void focus(boolean value) { selected = value; }
	void end() { cursor = text.length(); }
	void set(String value)
	{
		if(text.equals(value)) return;
		text = value;
		cursor = Math.min(cursor, text.length());
		if(changed != null) changed.accept(text);
	}

	void render(float x, float y, float width, float size, int color, boolean centered, String hint, long now)
	{
		this.left = centered ? x - width / 2 : x;
		this.top = y;
		this.width = width;
		this.size = size;
		cursor = Math.max(0, Math.min(cursor, text.length()));
		boolean empty = text.isBlank();
		visibleStart = 0;
		if(!empty && NanoVGUI.width(text, size) > width)
		{
			visibleStart = cursor;
			while(visibleStart > 0 && NanoVGUI.width(text.substring(visibleStart - 1, cursor), size) < width)
				visibleStart--;
		}
		String visible = empty ? hint : text.substring(visibleStart);
		while(!empty && NanoVGUI.width(visible, size) > width && !visible.isEmpty())
			visible = visible.substring(0, visible.length() - 1);
		long elapsed = lastFrame == 0 ? 500 : Math.min(500, Math.max(0, now - lastFrame));
		lastFrame = now;
		float blend = 1 - (float)Math.pow(0.95, elapsed);
		float target = centered ? x - NanoVGUI.width(visible, size) / 2 : x;
		drawnX = centered ? drawnX + (target - drawnX) * blend : x;
		int alpha = color >>> 24;
		NanoVGUI.text(visible, drawnX, y, empty ? withAlpha(color, Math.round(alpha * (selected ? 0.3F : 0.2F))) : color, size);
		if(selected)
		{
			float caret = NanoVGUI.width(text.substring(visibleStart, Math.max(visibleStart, cursor)), size) - 2;
			caretX += (caret - caretX) * blend;
			int blink = Math.round((float)((Math.sin(now / 150.0) + 1) / 2) * alpha);
			NanoVGUI.text("|", drawnX + caretX + 1, y - 1, withAlpha(color, blink), size);
		}
	}

	boolean click(double x, double y, int button)
	{
		selected = button == 0 && x >= left && x < left + width && y >= top && y < top + size + 3;
		if(selected) end();
		return selected;
	}

	boolean key(int key, int modifiers)
	{
		if(!selected) return false;
		boolean control = (modifiers & (GLFW.GLFW_MOD_CONTROL | GLFW.GLFW_MOD_SUPER)) != 0;
		if(control && key == GLFW.GLFW_KEY_C)
			WurstClient.MC.keyboardHandler.setClipboard(text);
		else if(control && key == GLFW.GLFW_KEY_V)
			insert(WurstClient.MC.keyboardHandler.getClipboard());
		else if(key == GLFW.GLFW_KEY_BACKSPACE && cursor > 0)
		{
			int start = control ? 0 : cursor - 1;
			set(text.substring(0, start) + text.substring(cursor));
			cursor = start;
		}else if(key == GLFW.GLFW_KEY_DELETE && cursor < text.length())
			set(text.substring(0, cursor) + text.substring(cursor + 1));
		else if(key == GLFW.GLFW_KEY_LEFT)
			cursor = control ? 0 : Math.max(0, cursor - 1);
		else if(key == GLFW.GLFW_KEY_RIGHT)
			cursor = control ? text.length() : Math.min(text.length(), cursor + 1);
		return true;
	}

	boolean character(char value)
	{
		if(!selected || Character.isISOControl(value) || value == '\u00a7') return false;
		insert(String.valueOf(value));
		return true;
	}

	private void insert(String value)
	{
		StringBuilder accepted = new StringBuilder();
		for(char c : value.toCharArray())
			if(!Character.isISOControl(c) && c != '\u00a7' && (allowed == null || allowed.indexOf(c) >= 0)) accepted.append(c);
		if(text.length() + accepted.length() > 32767) return;
		int next = cursor + accepted.length();
		set(text.substring(0, cursor) + accepted + text.substring(cursor));
		cursor = next;
	}

	static int withAlpha(int color, int alpha) { return (color & 0xFFFFFF) | (Math.max(0, Math.min(255, alpha)) << 24); }
}

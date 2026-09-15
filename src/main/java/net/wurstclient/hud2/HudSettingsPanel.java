package net.wurstclient.hud2;

import java.util.ArrayList;
import java.util.List;

import com.google.gson.JsonElement;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.wurstclient.clickgui2.FlatRenderer;
import net.wurstclient.gui.visual.VisualTheme;
import net.wurstclient.settings.CheckboxSetting;
import net.wurstclient.settings.EnumSetting;
import net.wurstclient.settings.Setting;

/**
 * HUD 编辑器右下角的「逐元素设置」面板。
 *
 * <p>
 * 只支持两种可交互设置——{@link CheckboxSetting}（点击切换）和
 * {@link EnumSetting}（点击循环到下一个值）。其余类型（滑条、颜色、物品列表、
 * 文本框……）<b>照常列出但只读</b>，用灰色显示当前值：悄悄不显示它们比显示成
 * 只读更糟，用户会以为设置丢了。滑条拖拽与颜色选择器是后续工作。
 *
 * <p>
 * 面板出现规则是「悬停即显示、且不清空」：悬停到有设置的元素就把面板切过去，
 * 光标移进面板后不再切换（否则鼠标一挪过去面板就消失、永远点不到）。所以它
 * 不会自己消失，这是有意的。
 */
final class HudSettingsPanel
{
	private static final int WIDTH = 196;
	private static final int ROW_HEIGHT = 20;
	private static final int TITLE_HEIGHT = 14;
	private static final int PADDING = 6;
	private static final int MARGIN = 6;
	private static final int BOX_SIZE = 10;

	private String elementId;
	// 只存顺序，行的纵向范围一律由 rowIndexAt() 从 rowsTop 算出来——存一份
	// 就可能和算出来的那份不一致，而那种不一致表现为「点这行改了那行」。
	private final List<Setting> rows = new ArrayList<>();
	private boolean changed;
	private int rowsTop;
	private int x1;
	private int y1;
	private int x2;
	private int y2;

	/**
	 * 命中的是第几行；点不到任何行时返回 -1。
	 *
	 * <p>
	 * 区间取法与每行的绘制一致：{@code top} 含、{@code bottom} 不含。抽成纯函数
	 * 是为了能单测边界——差一行就会变成「点这个开关改了上面那个」，
	 * 是那种一眼看不出来的错。
	 */
	static int rowIndexAt(double mouseY, int rowsTop, int rowCount)
	{
		if(rowCount <= 0 || mouseY < rowsTop)
			return -1;

		int index = (int)((mouseY - rowsTop) / ROW_HEIGHT);
		return index < 0 || index >= rowCount ? -1 : index;
	}

	String getElementId()
	{
		return elementId;
	}

	void show(HudElement element)
	{
		elementId = element == null ? null : element.getId();
	}

	/**
	 * 光标是否落在面板上。用的是上一次 render 记下的矩形——面板位置只由屏幕
	 * 尺寸和行数决定，所以这一帧和上一帧的差别只有行数变化时的一点点。
	 */
	boolean contains(double mouseX, double mouseY)
	{
		return elementId != null && mouseX >= x1 && mouseX < x2
			&& mouseY >= y1 && mouseY < y2;
	}

	/** 本次点击是否真的改了某个设置（改了就由调用方存盘）。 */
	boolean consumeChanged()
	{
		boolean result = changed;
		changed = false;
		return result;
	}

	/**
	 * 画出面板。元素没有可见设置时返回 false，并把面板收起来。
	 */
	boolean render(GuiGraphics graphics, Font font, HudElement element,
		int screenWidth, int screenHeight, int toolbarHeight)
	{
		rows.clear();

		if(element == null)
		{
			elementId = null;
			return false;
		}

		List<Setting> visible = new ArrayList<>();
		for(Setting setting : element.getSettings().values())
			if(setting.isVisible())
				visible.add(setting);

		if(visible.isEmpty())
		{
			elementId = null;
			return false;
		}

		int height =
			PADDING * 2 + TITLE_HEIGHT + visible.size() * ROW_HEIGHT;
		x2 = screenWidth - MARGIN;
		y2 = screenHeight - toolbarHeight - MARGIN;
		x1 = Math.max(MARGIN, x2 - WIDTH);
		y1 = Math.max(MARGIN, y2 - height);

		FlatRenderer.drawPanel(graphics, x1, y1, x2, y2, 4,
			VisualTheme.PANEL, VisualTheme.BORDER);

		String title = font.plainSubstrByWidth(element.getName(),
			WIDTH - PADDING * 2);
		graphics.drawString(font, title, x1 + PADDING, y1 + PADDING,
			VisualTheme.TEXT, false);

		int rowTop = y1 + PADDING + TITLE_HEIGHT;
		rowsTop = rowTop;
		for(Setting setting : visible)
		{
			rows.add(setting);
			drawRow(graphics, font, setting, rowTop);
			rowTop += ROW_HEIGHT;
		}
		return true;
	}

	private void drawRow(GuiGraphics graphics, Font font, Setting setting,
		int rowTop)
	{
		int textY = rowTop + (ROW_HEIGHT - font.lineHeight) / 2 + 1;

		if(setting instanceof CheckboxSetting checkbox)
		{
			int boxX = x2 - PADDING - BOX_SIZE;
			int boxY = rowTop + (ROW_HEIGHT - BOX_SIZE) / 2;
			FlatRenderer.fillRoundedRect(graphics, boxX, boxY,
				boxX + BOX_SIZE, boxY + BOX_SIZE, 2,
				checkbox.isChecked() ? VisualTheme.ACCENT
					: VisualTheme.CONTROL);
			String label = font.plainSubstrByWidth(setting.getName(),
				WIDTH - PADDING * 3 - BOX_SIZE);
			graphics.drawString(font, label, x1 + PADDING, textY,
				VisualTheme.TEXT, false);
			return;
		}

		String value = valueText(setting);
		int valueWidth = font.width(value);
		String label = font.plainSubstrByWidth(setting.getName(),
			Math.max(0, WIDTH - PADDING * 2 - valueWidth - 8));

		graphics.drawString(font, label, x1 + PADDING, textY,
			VisualTheme.TEXT, false);
		// 可点的用强调色、只读的用灰：一眼能看出哪些行点得动
		graphics.drawString(font, value, x2 - PADDING - valueWidth, textY,
			setting instanceof EnumSetting ? VisualTheme.ACCENT
				: VisualTheme.TEXT_DISABLED,
			false);
	}

	private static String valueText(Setting setting)
	{
		if(setting instanceof EnumSetting<?> enumSetting)
			return String.valueOf(enumSetting.getSelected());

		JsonElement json = setting.toJson();
		if(json == null || json.isJsonNull())
			return "";
		// 复合值（颜色、物品列表……）在面板里只示意，等做了对应控件再显示
		if(json.isJsonPrimitive())
			return json.getAsString();
		return "\u2026";
	}

	/**
	 * 处理面板上的点击。返回 true 表示点击被面板吃掉——包括点在只读行或空白处，
	 * 否则会穿透下去开关或拖动底下的元素，那种误操作很难理解。
	 */
	boolean mouseClicked(double mouseX, double mouseY)
	{
		if(elementId == null || !contains(mouseX, mouseY))
			return false;

		int index = rowIndexAt(mouseY, rowsTop, rows.size());
		if(index < 0)
			return true;

		Setting setting = rows.get(index);
		if(setting instanceof CheckboxSetting checkbox)
		{
			checkbox.setChecked(!checkbox.isChecked());
			changed = true;
		}else if(setting instanceof EnumSetting<?> enumSetting)
		{
			enumSetting.selectNext();
			changed = true;
		}
		return true;
	}
}

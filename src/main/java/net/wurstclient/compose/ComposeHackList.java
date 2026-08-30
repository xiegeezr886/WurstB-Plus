package net.wurstclient.compose;

import java.util.List;

import net.minecraft.client.gui.GuiGraphics;
import net.wurstclient.WurstClient;
import net.wurstclient.clickgui2.FlatRenderer;

/**
 * SuperSoftClient {@code Hud.renderCompose()} 模块列表部分的等价物：
 * 启用模块以 LazyColumn 式垂直列表呈现，每项文字带背景圆角、阴影和
 * 逐项颜色（由 {@link ModuleColors} 计算），条目进出场带进度动画。
 *
 * <p>数据源保留在 {@code HackListHUD}（排序/模式/动画进度），本类只负责
 * 声明式组件树的构建与渲染。</p>
 */
public final class ComposeHackList
{
	private static final float ENTRY_HEIGHT = 11;
	private static final int ROW_GAP = 1;

	private ComposeHackList()
	{}

	/** 一条模块条目：文字 + 动画进度 + 颜色。 */
	public static final class Entry
	{
		public final String name;
		public float progress = 1;
		public int color = 0xFFFFFFFF;
		public float backgroundAlpha = 0.41F;

		public Entry(String name)
		{
			this.name = name;
		}
	}

	/**
	 * 在 (x, y) 处渲染条目列表。rightAligned 决定行右对齐。每行高度
	 * ENTRY_HEIGHT，按 progress 滑入/淡出。
	 */
	public static void render(GuiGraphics graphics, List<Entry> entries,
		float x, float y, boolean rightAligned, float partialTicks)
	{
		if(entries.isEmpty())
			return;
		int containerWidth = widestEntry(entries);
		float posY = y;
		for(Entry entry : entries)
		{
			renderEntry(graphics, entry, x, posY, containerWidth, rightAligned);
			posY += ENTRY_HEIGHT * entry.progress + ROW_GAP;
		}
	}

	/** 供 HackListHUD 复用：按进度渲染单条。 */
	public static void renderEntry(GuiGraphics graphics, Entry entry,
		float baseX, float posY, int containerWidth, boolean rightAligned)
	{
		if(entry.progress <= 0.001F)
			return;
		int textWidth = WurstClient.MC.font.width(entry.name);
		float slide = (textWidth + 12) * (1 - entry.progress);
		float x1;
		float x2;
		if(!rightAligned)
		{
			x1 = baseX - slide;
			x2 = x1 + textWidth + 11;
		}else
		{
			x2 = baseX + containerWidth + slide;
			x1 = x2 - textWidth - 11;
		}
		int left = Math.round(x1);
		int top = Math.round(posY);
		int right = Math.round(x2);
		int bottom = top + 11;
		// 背景 + 流动渐变描边
		FlatRenderer.fillRoundedRect(graphics, left, top, right, bottom, 3,
			withAlpha(0x070B10, Math.round(104 * entry.progress)));
		FlatRenderer.drawGradientOutline(graphics, left, top, right, bottom, 3,
			FlowingGradient.flowing(left, right - left,
				200 * entry.progress));
		// 左侧强调条
		int accentX = rightAligned ? right - 3 : left + 1;
		FlatRenderer.fillRoundedRect(graphics, accentX, top + 2, accentX + 2,
			bottom - 2, 1, withAlpha(entry.color,
				Math.round(220 * entry.progress)));
		// 文字 + 阴影（对应 TextStyle shadow）
		int textX = rightAligned ? left + 4 : left + 6;
		int textY = top + 2;
		graphics.drawString(WurstClient.MC.font, entry.name, textX + 1,
			textY + 1, withAlpha(0, Math.round(145 * entry.progress)), false);
		graphics.drawString(WurstClient.MC.font, entry.name, textX, textY,
			withAlpha(entry.color, Math.round(255 * entry.progress)), false);
	}

	private static int widestEntry(List<Entry> entries)
	{
		int widest = 90;
		for(Entry entry : entries)
			widest = Math.max(widest,
				WurstClient.MC.font.width(entry.name));
		return widest + 11;
	}

	private static int withAlpha(int color, int alpha)
	{
		return Math.max(0, Math.min(255, alpha)) << 24 | color & 0xFFFFFF;
	}
}

package net.wurstclient.compose;

import java.util.List;

import net.minecraft.client.gui.GuiGraphics;
import net.wurstclient.WurstClient;
import net.wurstclient.clickgui2.FlatRenderer;
import net.wurstclient.other_features.HackListOtf;

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
		render(graphics, entries, x, y, rightAligned, partialTicks,
			HackListOtf.BarMode.OUTER);
	}

	public static void render(GuiGraphics graphics, List<Entry> entries,
		float x, float y, boolean rightAligned, float partialTicks,
		HackListOtf.BarMode barMode)
	{
		if(entries.isEmpty())
			return;
		int containerWidth = widestEntry(entries);
		float posY = y;
		for(Entry entry : entries)
		{
			renderEntry(graphics, entry, x, posY, containerWidth, rightAligned,
				barMode);
			posY += ENTRY_HEIGHT * entry.progress + ROW_GAP;
		}
	}

	/** 供 HackListHUD 复用：按进度渲染单条。 */
	public static void renderEntry(GuiGraphics graphics, Entry entry,
		float baseX, float posY, int containerWidth, boolean rightAligned)
	{
		renderEntry(graphics, entry, baseX, posY, containerWidth, rightAligned,
			HackListOtf.BarMode.OUTER);
	}

	/**
	 * 供 HackListHUD 复用：按进度渲染单条，并按 {@code barMode} 放置竖条。
	 *
	 * <p>
	 * {@code OUTER} 的取值与本次改动之前逐像素一致（右对齐贴右边、左对齐贴
	 * 左边），所以默认观感不变；{@code LEFT}/{@code RIGHT} 是参考
	 * {@code ToggledSettings.BarMode} 的固定侧语义。
	 */
	public static void renderEntry(GuiGraphics graphics, Entry entry,
		float baseX, float posY, int containerWidth, boolean rightAligned,
		HackListOtf.BarMode barMode)
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
		// 强调条位置：OUTER 保持原来的「外缘」画法，LEFT/RIGHT 是固定侧
		Integer accentX = accentBarX(barMode, left, right, rightAligned);
		if(accentX != null)
			FlatRenderer.fillRoundedRect(graphics, accentX, top + 2,
				accentX + BAR_WIDTH, bottom - 2, 1,
				withAlpha(entry.color, Math.round(220 * entry.progress)));
		// 文字 + 阴影（对应 TextStyle shadow）
		int textX = rightAligned ? left + 4 : left + 6;
		int textY = top + 2;
		graphics.drawString(WurstClient.MC.font, entry.name, textX + 1,
			textY + 1, withAlpha(0, Math.round(145 * entry.progress)), false);
		graphics.drawString(WurstClient.MC.font, entry.name, textX, textY,
			withAlpha(entry.color, Math.round(255 * entry.progress)), false);
	}

	/** 竖条宽度。 */
	static final int BAR_WIDTH = 2;

	/**
	 * 竖条左边缘；{@code BarMode.NONE} 时返回 {@code null}，表示不画。
	 *
	 * <p>
	 * 抽成纯函数是为了能单测。这里有一个必须钉住的性质：{@code OUTER} 的取值
	 * 必须与加这个开关<b>之前</b>的那行
	 * {@code rightAligned ? right - 3 : left + 1} 完全一致，否则默认观感就变了。
	 * 注意左右两侧的内缩并不对称（左 +1、右 -3），这是既有画法，保持原样。
	 */
	static Integer accentBarX(HackListOtf.BarMode barMode, int left, int right,
		boolean rightAligned)
	{
		return switch(barMode)
		{
			case NONE -> null;
			case LEFT -> left + 1;
			case RIGHT -> right - 3;
			case OUTER -> rightAligned ? right - 3 : left + 1;
		};
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

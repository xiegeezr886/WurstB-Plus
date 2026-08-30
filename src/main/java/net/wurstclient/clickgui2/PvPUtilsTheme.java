package net.wurstclient.clickgui2;

/**
 * PVPUtils 浅色 ClickGUI 主题（参考 PVPUtils 设置面板的 Default 浅色外观）：
 * 近白卡片 + 白侧栏 + 宝蓝强调色。导航器默认（非 Rise mode）使用。
 */
final class PvPUtilsTheme
{
	/** 外层卡片底色。 */
	static final int CARD = 0xFFF5F5F7;
	/** 侧栏纯白底。 */
	static final int SIDEBAR = 0xFFFFFFFF;
	/** 侧栏 1px 分隔线。 */
	static final int DIVIDER = 0xFFEEEEEE;
	/** 模块卡片底。 */
	static final int MODULE = 0xFFFFFFFF;
	/** 子设置行底。 */
	static final int SUB = 0xFFF8F8FF;
	/** 宝蓝强调色（页签/开关/滑轨填充/选中文字）= 统一客户端色调 #4677FF。 */
	static final int ACCENT = 0xFF007CFF;
	/** 选中页签药丸底。 */
	static final int ACCENT_PILL = 0xFFE3ECFF;
	/** 悬停页签药丸底。 */
	static final int HOVER_PILL = 0xFFF0F4FF;
	/** 主文字。 */
	static final int TEXT = 0xFF111111;
	/** 次文字（副标题/描述）。 */
	static final int TEXT_MUTED = 0xFFAAAAAA;
	/** 行/设置项文字。 */
	static final int TEXT_ROW = 0xFF333333;
	/** 未选中图标。 */
	static final int TEXT_ICON = 0xFF888888;
	/** 滑轨/开关关态轨道。 */
	static final int TRACK = 0xFFE0E0E0;
	/** 开关关态轨道。 */
	static final int TRACK_OFF = 0xFFCCCCCC;
	/** 开关/滑轨旋钮。 */
	static final int THUMB = 0xFFFFFFFF;
	/** 滚动条轨道。 */
	static final int SCROLL_TRACK = 0xFFE0E0E0;
	/** 滚动条滑块。 */
	static final int SCROLL_THUMB = 0xFFBBBBBB;
	/** 面板阴影。 */
	static final int SHADOW = 0x50000000;
	/** 颜色选择弹层边框。 */
	static final int PICKER_BG = 0xFFE0E0E0;
	/** 搜索框底（普通/聚焦）。 */
	static final int SEARCH_BG = 0xFFF1F2F5;
	static final int SEARCH_BG_F = 0xFFE9EEFF;
	/** 搜索框光标/下划线。 */
	static final int SEARCH_CURS = 0xFF007CFF;
	/** 搜索框文字。 */
	static final int SEARCH_TEXT = 0xFF343842;
	/** 搜索框占位符。 */
	static final int SEARCH_HINT = 0xFF9BA1AE;

	private PvPUtilsTheme()
	{}

	static int mix(int first, int second, float progress)
	{
		float amount = Math.max(0, Math.min(1, progress));
		int a = mixChannel(first >>> 24, second >>> 24, amount);
		int r = mixChannel(first >>> 16 & 0xFF, second >>> 16 & 0xFF, amount);
		int g = mixChannel(first >>> 8 & 0xFF, second >>> 8 & 0xFF, amount);
		int b = mixChannel(first & 0xFF, second & 0xFF, amount);
		return a << 24 | r << 16 | g << 8 | b;
	}

	static int withAlpha(int color, float alpha)
	{
		return Math.max(0, Math.min(255,
			Math.round(Math.max(0, Math.min(1, alpha)) * 255))) << 24
			| color & 0xFFFFFF;
	}

	private static int mixChannel(int first, int second, float progress)
	{
		return Math.round(first + (second - first) * progress);
	}
}

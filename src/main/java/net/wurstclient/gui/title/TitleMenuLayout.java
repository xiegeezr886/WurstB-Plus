/*
 * Copyright (c) 2014-2025 Wurst-Imperium and contributors.
 *
 * This source code is subject to the terms of the GNU General Public
 * License, version 3. If a copy of the GPL was not distributed with this
 * file, You can obtain one at: https://www.gnu.org/licenses/gpl-3.0.txt
 */
package net.wurstclient.gui.title;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 标题主界面的几何。
 *
 * <p>
 * 每个尺寸都是「屏幕尺寸 × 参考帧比例」，参考帧是用户给的参考截图
 * （{@value #REFERENCE_WIDTH}×{@value #REFERENCE_HEIGHT} 物理像素）。用比例
 * 而不是固定像素，是因为屏幕上的物理尺寸 = 比例 × 逻辑尺寸 × guiScale，而
 * guiScale = 物理高度 / 逻辑高度，两者相乘正好把 guiScale 约掉：同一套比例在
 * 任何分辨率、任何 GUI scale 下都落在同一个物理位置。</p>
 *
 * <p>
 * 唯一的例外是文字：Minecraft 的字体固定 9 逻辑像素高，不随屏幕缩放。所以
 * 各处的 {@code *_MIN} 钳制是按「9 像素的字还放得下」定的，而不是按参考图
 * 定的；{@link #TEXT_HEIGHT} 就是那个 9。</p>
 *
 * <p>
 * 参考图上量到的关键数字（物理像素）：账号胶囊 16,15 起、218×70；齿轮
 * 36×36、右边距 16；动作条 x 20..920（宽 904）、y 579..646，里面 4 个
 * 208×48 的按钮、间隔 16；logo 底边距动作条 32、左边缩进 12。</p>
 */
public final class TitleMenuLayout
{
	/** 量取比例用的参考帧（用户给的参考主界面截图）。 */
	public static final int REFERENCE_WIDTH = 1296;
	public static final int REFERENCE_HEIGHT = 672;

	/** Minecraft 字体固定的逻辑行高。 */
	public static final int TEXT_HEIGHT = 9;

	// ----------------------------------------------------------------
	// 外边距
	// ----------------------------------------------------------------

	public static final float MARGIN_X_RATIO = 16F / REFERENCE_WIDTH;
	public static final float MARGIN_Y_RATIO = 15F / REFERENCE_HEIGHT;
	public static final int MARGIN_MIN = 3;
	public static final int MARGIN_MAX = 18;

	// ----------------------------------------------------------------
	// 左上角账号胶囊
	// ----------------------------------------------------------------

	public static final float CHIP_HEIGHT_RATIO = 70F / REFERENCE_HEIGHT;
	public static final int CHIP_HEIGHT_MIN = 22;
	/**
	 * 上限是给 GUI scale 1 那种「逻辑画布跟屏幕一样大」的场合定的：那里比例算
	 * 出来的胶囊有 112 逻辑像素高，而字还是 9 像素，胶囊会空得像块板子。
	 */
	public static final int CHIP_HEIGHT_MAX = 36;
	/** 内边距、头像、文字间距都以胶囊高度为基准。 */
	public static final float CHIP_PAD_RATIO = 10F / 70F;
	public static final int CHIP_PAD_MIN = 3;
	public static final float CHIP_AVATAR_RATIO = 42F / 70F;
	public static final int CHIP_AVATAR_MIN = 12;
	public static final float CHIP_TEXT_GAP_RATIO = 20F / 70F;
	public static final float CHIP_RADIUS_RATIO = 10F / 70F;
	public static final float CHIP_WIDTH_MAX_RATIO = 0.44F;

	// ----------------------------------------------------------------
	// 右上角齿轮
	// ----------------------------------------------------------------

	public static final float GEAR_RATIO = 36F / REFERENCE_HEIGHT;
	public static final int GEAR_MIN = 14;
	public static final int GEAR_MAX = 26;
	public static final float GEAR_ICON_RATIO = 0.5F;
	public static final float GEAR_RADIUS_RATIO = 10F / 36F;

	// ----------------------------------------------------------------
	// 底部动作条
	// ----------------------------------------------------------------

	public static final int ACTION_COUNT = 4;
	public static final float RAIL_HEIGHT_RATIO = 67F / REFERENCE_HEIGHT;
	public static final int RAIL_HEIGHT_MIN = 18;
	/** 上限的理由同 {@link #CHIP_HEIGHT_MAX}。 */
	public static final int RAIL_HEIGHT_MAX = 36;
	/** 参考图里动作条只有屏宽的 70%，右边留给壁纸。 */
	public static final float RAIL_WIDTH_RATIO = 904F / REFERENCE_WIDTH;
	public static final float RAIL_PAD_X_RATIO = 12F / 67F;
	public static final float RAIL_PAD_Y_RATIO = 9F / 67F;
	public static final float RAIL_RADIUS_RATIO = 15F / 67F;
	public static final float BOTTOM_MARGIN_RATIO = 25.5F / REFERENCE_HEIGHT;
	public static final int BOTTOM_MARGIN_MIN = 4;
	public static final int BOTTOM_MARGIN_MAX = 20;
	public static final float BUTTON_GAP_RATIO = 16F / REFERENCE_WIDTH;
	public static final int BUTTON_GAP_MIN = 3;
	public static final int BUTTON_GAP_MAX = 14;
	public static final float BUTTON_RADIUS_RATIO = 12F / 48F;
	public static final float BUTTON_ICON_RATIO = 20F / 48F;
	/** 再窄也要放得下「单人游戏 + 图标」。 */
	public static final int MIN_BUTTON_WIDTH = 46;

	// ----------------------------------------------------------------
	// 左下角 logo
	// ----------------------------------------------------------------

	/** wurstb_logo_white.png 的宽高比（原分辨率嵌入，2101x660）。 */
	public static final float LOGO_ASPECT = 768F / 229F;
	/**
	 * 参考图的标题宽 185/1296 = 14.3%，我们的字标取 26%（337/1296）——手写体的
	 * 笔画细，按参考图那个尺寸缩下去发丝只剩 1 像素，整条字标会糊成一团。
	 */
	public static final float LOGO_WIDTH_RATIO = 337F / REFERENCE_WIDTH;
	/** 屏宽再窄也留一条能认出来的字标。 */
	public static final int LOGO_WIDTH_MIN = 40;
	public static final float LOGO_INDENT_RATIO = 12F / REFERENCE_WIDTH;
	public static final float LOGO_GAP_RATIO = 32F / REFERENCE_HEIGHT;
	public static final int LOGO_GAP_MIN = 2;

	// ----------------------------------------------------------------
	// 齿轮弹出菜单
	// ----------------------------------------------------------------

	public static final int MENU_COUNT = 3;
	public static final float MENU_WIDTH_RATIO = 150F / REFERENCE_WIDTH;
	public static final int MENU_WIDTH_MIN = 88;
	public static final int MENU_WIDTH_MAX = 180;
	public static final float MENU_ROW_RATIO = 37F / REFERENCE_HEIGHT;
	public static final int MENU_ROW_MIN = 14;
	public static final int MENU_ROW_MAX = 26;
	public static final float MENU_PAD_RATIO = 6F / REFERENCE_HEIGHT;
	public static final float MENU_GAP_RATIO = 8F / REFERENCE_HEIGHT;
	public static final float MENU_ICON_RATIO = 0.5F;

	private final int width;
	private final int height;
	private final int margin;

	private final Rect chip;
	private final Rect avatar;
	private final Rect gear;
	private final Rect logo;
	private final Rect rail;
	private final Rect menu;
	private final List<Rect> actions;
	private final List<Rect> menuRows;

	private final int chipRadius;
	private final int chipTextX;
	private final int gearRadius;
	private final int railRadius;
	private final int buttonRadius;
	private final int menuRadius;
	private final int actionIconSize;
	private final int gearIconSize;
	private final int menuIconSize;

	/**
	 * @param chipTextWidth
	 *            账号名与「欢迎回来」中较宽的那一行的像素宽度，胶囊宽度由它决定
	 */
	public TitleMenuLayout(int screenWidth, int screenHeight, int chipTextWidth)
	{
		width = Math.max(1, screenWidth);
		height = Math.max(1, screenHeight);

		margin = clamp(round(Math.min(width * MARGIN_X_RATIO,
			height * MARGIN_Y_RATIO)), MARGIN_MIN, MARGIN_MAX);

		// ---- 账号胶囊 ----
		int chipHeight = clamp(round(height * CHIP_HEIGHT_RATIO),
			CHIP_HEIGHT_MIN, CHIP_HEIGHT_MAX);
		int chipPad = Math.max(CHIP_PAD_MIN,
			round(chipHeight * CHIP_PAD_RATIO));
		int avatarSize = Math.max(CHIP_AVATAR_MIN,
			round(chipHeight * CHIP_AVATAR_RATIO));
		int textGap = Math.max(CHIP_PAD_MIN,
			round(chipHeight * CHIP_TEXT_GAP_RATIO));
		int chipWidth = chipPad * 2 + avatarSize + textGap
			+ Math.max(0, chipTextWidth);
		chipWidth = Math.max(1,
			Math.min(chipWidth, round(width * CHIP_WIDTH_MAX_RATIO)));
		chip = new Rect(margin, margin, chipWidth, chipHeight);
		avatar = new Rect(chip.x + chipPad,
			chip.y + (chipHeight - avatarSize) / 2, avatarSize, avatarSize);
		chipTextX = avatar.right() + textGap;
		chipRadius = Math.max(2, round(chipHeight * CHIP_RADIUS_RATIO));

		// ---- 齿轮 ----
		int gearSize = clamp(round(height * GEAR_RATIO), GEAR_MIN, GEAR_MAX);
		gearSize = Math.max(1, Math.min(gearSize, chipHeight));
		gear = new Rect(width - margin - gearSize, margin, gearSize, gearSize);
		gearIconSize = Math.max(6, round(gearSize * GEAR_ICON_RATIO));
		gearRadius = Math.max(2, round(gearSize * GEAR_RADIUS_RATIO));

		// ---- 底部动作条 ----
		int bottomMargin = clamp(round(height * BOTTOM_MARGIN_RATIO),
			BOTTOM_MARGIN_MIN, BOTTOM_MARGIN_MAX);
		int railHeight = clamp(round(height * RAIL_HEIGHT_RATIO),
			RAIL_HEIGHT_MIN, RAIL_HEIGHT_MAX);
		int railPadX = Math.max(2, round(railHeight * RAIL_PAD_X_RATIO));
		int railPadY = Math.max(2, round(railHeight * RAIL_PAD_Y_RATIO));
		int gap = clamp(round(width * BUTTON_GAP_RATIO), BUTTON_GAP_MIN,
			BUTTON_GAP_MAX);
		int minRail = ACTION_COUNT * MIN_BUTTON_WIDTH
			+ (ACTION_COUNT - 1) * gap + railPadX * 2;
		int railWidth = Math.max(1, clamp(round(width * RAIL_WIDTH_RATIO),
			minRail, width - margin * 2));
		int buttonHeight = Math.max(1, railHeight - railPadY * 2);
		int buttonWidth = Math.max(1, (railWidth - railPadX * 2
			- (ACTION_COUNT - 1) * gap) / ACTION_COUNT);
		rail = new Rect(margin, height - bottomMargin - railHeight, railWidth,
			railHeight);

		actions = new ArrayList<>(ACTION_COUNT);
		for(int i = 0; i < ACTION_COUNT; i++)
			actions.add(new Rect(rail.x + railPadX + i * (buttonWidth + gap),
				rail.y + railPadY, buttonWidth, buttonHeight));

		railRadius = Math.max(2, round(railHeight * RAIL_RADIUS_RATIO));
		buttonRadius = Math.max(2, round(buttonHeight * BUTTON_RADIUS_RATIO));
		actionIconSize = Math.max(6, round(buttonHeight * BUTTON_ICON_RATIO));

		// ---- 左下角 logo ----
		int logoGap = Math.max(LOGO_GAP_MIN, round(height * LOGO_GAP_RATIO));
		int logoIndent = round(width * LOGO_INDENT_RATIO);
		int logoWidth = Math.max(1, clamp(round(width * LOGO_WIDTH_RATIO),
			LOGO_WIDTH_MIN, width - margin * 2 - logoIndent));
		int logoHeight = Math.max(1, round(logoWidth / LOGO_ASPECT));
		// 屏幕太矮时先压字标，免得它顶到胶囊上
		int logoRoom = rail.y - logoGap - chip.bottom() - 2;

		if(logoHeight > logoRoom)
		{
			logoHeight = Math.max(1, logoRoom);
			logoWidth = Math.max(1, round(logoHeight * LOGO_ASPECT));
		}

		logo = new Rect(margin + logoIndent, rail.y - logoGap - logoHeight,
			logoWidth, logoHeight);

		// ---- 齿轮菜单 ----
		int menuWidth = clamp(round(width * MENU_WIDTH_RATIO), MENU_WIDTH_MIN,
			MENU_WIDTH_MAX);
		int menuPad = Math.max(2, round(height * MENU_PAD_RATIO));
		int menuGap = Math.max(2, round(height * MENU_GAP_RATIO));
		int menuRowHeight = clamp(round(height * MENU_ROW_RATIO),
			MENU_ROW_MIN, MENU_ROW_MAX);
		int menuY = gear.bottom() + menuGap;
		int menuRoom = rail.y - menuGap - menuY - menuPad * 2;
		if(menuRoom > 0)
			menuRowHeight = Math.min(menuRowHeight,
				Math.max(MENU_ROW_MIN / 2, menuRoom / MENU_COUNT));
		menuRowHeight = Math.max(1, menuRowHeight);
		int menuHeight = menuPad * 2 + MENU_COUNT * menuRowHeight;
		menu = new Rect(gear.right() - menuWidth, menuY, menuWidth, menuHeight);
		menuRadius = Math.max(2, round(menuRowHeight * 0.3F));
		menuIconSize = Math.max(6, round(menuRowHeight * MENU_ICON_RATIO));

		menuRows = new ArrayList<>(MENU_COUNT);
		for(int i = 0; i < MENU_COUNT; i++)
			menuRows.add(new Rect(menu.x + menuPad,
				menu.y + menuPad + i * menuRowHeight, menuWidth - menuPad * 2,
				menuRowHeight));
	}

	// ----------------------------------------------------------------
	// 取值
	// ----------------------------------------------------------------

	public int width()
	{
		return width;
	}

	public int height()
	{
		return height;
	}

	public int margin()
	{
		return margin;
	}

	public Rect chip()
	{
		return chip;
	}

	public Rect avatar()
	{
		return avatar;
	}

	public Rect gear()
	{
		return gear;
	}

	public Rect logo()
	{
		return logo;
	}

	public Rect rail()
	{
		return rail;
	}

	public Rect menu()
	{
		return menu;
	}

	public List<Rect> actions()
	{
		return Collections.unmodifiableList(actions);
	}

	public Rect action(int index)
	{
		return actions.get(index);
	}

	public List<Rect> menuRows()
	{
		return Collections.unmodifiableList(menuRows);
	}

	public int chipRadius()
	{
		return chipRadius;
	}

	/** 账号名与「欢迎回来」共用的一列：头像右边 + 间距。 */
	public int chipTextX()
	{
		return chipTextX;
	}

	public int gearRadius()
	{
		return gearRadius;
	}

	public int railRadius()
	{
		return railRadius;
	}

	public int buttonRadius()
	{
		return buttonRadius;
	}

	public int menuRadius()
	{
		return menuRadius;
	}

	public int actionIconSize()
	{
		return actionIconSize;
	}

	public int gearIconSize()
	{
		return gearIconSize;
	}

	public int menuIconSize()
	{
		return menuIconSize;
	}

	/** 账号名那一行的顶边：整块文字在胶囊里垂直居中。 */
	public int chipNameY()
	{
		return chip.centerY() - TEXT_HEIGHT;
	}

	/** 「欢迎回来」那一行的顶边，紧跟账号名。 */
	public int chipSubtitleY()
	{
		return chip.centerY() + 1;
	}

	/** 一行文字在给定高度里垂直居中时的顶边。 */
	public static int centerTextY(int top, int height)
	{
		return top + (height - TEXT_HEIGHT) / 2 + 1;
	}

	// ----------------------------------------------------------------
	// 工具
	// ----------------------------------------------------------------

	private static int round(float value)
	{
		return Math.round(value);
	}

	/**
	 * 夹取；当上限小于下限（屏幕小到比例本身就放不下）时退化成上限，绝不返回
	 * 负数尺寸。
	 */
	private static int clamp(int value, int min, int max)
	{
		if(max < min)
			return Math.max(0, max);

		return Math.max(min, Math.min(max, value));
	}

	/** 屏幕空间里的一个矩形。 */
	public record Rect(int x, int y, int width, int height)
	{
		public int right()
		{
			return x + width;
		}

		public int bottom()
		{
			return y + height;
		}

		public int centerX()
		{
			return x + width / 2;
		}

		public int centerY()
		{
			return y + height / 2;
		}

		public boolean contains(double pointX, double pointY)
		{
			return pointX >= x && pointX < right() && pointY >= y
				&& pointY < bottom();
		}

		public boolean intersects(Rect other)
		{
			return x < other.right() && other.x < right() && y < other.bottom()
				&& other.y < bottom();
		}
	}
}

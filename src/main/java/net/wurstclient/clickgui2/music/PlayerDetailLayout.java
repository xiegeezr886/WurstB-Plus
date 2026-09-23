/*
 * Copyright (c) 2014-2025 Wurst-Imperium and contributors.
 *
 * This source code is subject to the terms of the GNU General Public
 * License, version 3. If a copy of the GPL was not distributed with this
 * file, You can obtain one at: https://www.gnu.org/licenses/gpl-3.0.txt
 */
package net.wurstclient.clickgui2.music;

/**
 * 播放详情层（{@link PlayerDetailOverlay}）的几何：左栏自上而下是封面 → 歌名 →
 * 歌手，面板底部自下而上是音量 → 控件 → 进度条，右栏是歌词。
 *
 * <p>
 * 底部三行锚在面板底部（间距固定），封面大小则由「剩下的空间」反推，所以面板
 * 再矮也不会出现文字压到进度条/控件上的情况——封面会先缩小。这条不变量由
 * {@code PlayerDetailLayoutTest} 在 300~740 宽、220~500 高的整个范围内断言。
 *
 * <p>
 * 故意不含 Minecraft 依赖（连矩形都是拆成 int 传的），渲染与命中测试共用同一套
 * 数字，避免两处各写一遍导致漂移。
 */
public final class PlayerDetailLayout
{
	/** 左栏左边距。 */
	public static final int LEFT_PADDING = 28;
	/** 左栏与歌词栏的间距。 */
	public static final int COLUMN_GAP = 32;
	/** 右栏右边距。 */
	public static final int RIGHT_PADDING = 12;
	/** 面板顶到封面顶。 */
	public static final int COVER_TOP_PADDING = 44;
	/** 封面底到歌名顶。 */
	public static final int TITLE_GAP = 14;
	/** 歌名字号。 */
	public static final int TITLE_SIZE = 12;
	/** 歌名底到歌手顶。 */
	public static final int ARTIST_GAP = 6;
	/** 歌手字号。 */
	public static final int ARTIST_SIZE = 7;
	/** 歌手底到进度条。 */
	public static final int TEXT_PROGRESS_GAP = 8;
	/** 进度条刻度文字的字号（画在进度条下方）。 */
	public static final int PROGRESS_LABEL_SIZE = 5;
	/** 进度条下方留给刻度文字的高度。 */
	public static final int PROGRESS_LABEL_HEIGHT = 12;
	/** 播放键半径；悬停时 {@code renderControl} 会再外扩 1。 */
	public static final int PLAY_BUTTON_RADIUS = 15;
	/** 进度条/控件/音量三行到面板底的距离。 */
	public static final int PROGRESS_BOTTOM_OFFSET = 130;
	public static final int CONTROL_BOTTOM_OFFSET = 84;
	public static final int VOLUME_BOTTOM_OFFSET = 36;
	/** 音量条左侧让给音量图标的宽度（图标左边对齐封面左边）。 */
	public static final int VOLUME_INSET = 24;
	/** 音量图标自身的半径。 */
	public static final int VOLUME_ICON_RADIUS = 6;
	/** 歌词栏上下边距。 */
	public static final int LYRICS_TOP_PADDING = 34;
	public static final int LYRICS_BOTTOM_PADDING = 66;
	/** 封面边长上限，以及再挤也不会低于的下限。 */
	public static final int COVER_SIZE_MAX = 190;
	public static final int COVER_SIZE_MIN = 40;
	/** 歌词栏至少留这么宽，窄面板靠缩小封面来让位。 */
	public static final int LYRICS_MIN_WIDTH = 120;
	/** 歌名顶到进度条顶的净高：歌名 + 歌手 + 上下间距。 */
	public static final int TEXT_BLOCK_TO_PROGRESS =
		TITLE_SIZE + ARTIST_GAP + ARTIST_SIZE + TEXT_PROGRESS_GAP;

	private PlayerDetailLayout()
	{}

	/**
	 * 详情层滑入/滑出的垂直位移：{@code progress} 为 0 时整层停在面板下方，
	 * 为 1 时完全覆盖面板。渲染与命中测试必须用同一个位移量。
	 */
	public static int slideOffset(float progress, int height)
	{
		return Math.round((1 - progress) * height);
	}

	/**
	 * 封面边长：先由高度反推（面板底到封面顶之间要装下封面、文字块与底部三行），
	 * 再用宽度限一次（歌词栏至少 {@code LYRICS_MIN_WIDTH}），最后夹在
	 * {@code COVER_SIZE_MIN}~{@code COVER_SIZE_MAX}。
	 */
	public static int coverSize(int width, int height)
	{
		int available = height - COVER_TOP_PADDING - TITLE_GAP
			- TEXT_BLOCK_TO_PROGRESS - PROGRESS_BOTTOM_OFFSET;
		int widthLimit = Math.max(COVER_SIZE_MIN, width - LEFT_PADDING
			- COLUMN_GAP - RIGHT_PADDING - LYRICS_MIN_WIDTH);
		return clamp(available, COVER_SIZE_MIN,
			Math.min(COVER_SIZE_MAX, widthLimit));
	}

	public static int coverLeft(int left)
	{
		return left + LEFT_PADDING;
	}

	public static int coverTop(int top)
	{
		return top + COVER_TOP_PADDING;
	}

	/**
	 * 歌名顶部：紧贴封面下方；面板矮到封面已经缩到下限时改为贴住进度条，
	 * 宁可压在封面下沿也不要压住进度条。
	 */
	public static int titleY(int top, int width, int height)
	{
		int hugging = coverTop(top) + coverSize(width, height) + TITLE_GAP;
		return Math.min(hugging, textBlockLimit(top, height));
	}

	public static int artistY(int top, int width, int height)
	{
		return titleY(top, width, height) + TITLE_SIZE + ARTIST_GAP;
	}

	/** 文字块下沿（歌手底）必须停在这里。 */
	public static int textBlockLimit(int top, int height)
	{
		return progressY(top, height) - TEXT_BLOCK_TO_PROGRESS;
	}

	public static int lyricLeft(int left, int width, int height)
	{
		return coverLeft(left) + coverSize(width, height) + COLUMN_GAP;
	}

	public static int lyricRight(int right)
	{
		return right - RIGHT_PADDING;
	}

	public static int lyricTop(int top)
	{
		return top + LYRICS_TOP_PADDING;
	}

	public static int lyricBottom(int top, int height)
	{
		return top + height - LYRICS_BOTTOM_PADDING;
	}

	public static int progressLeft(int left)
	{
		return left + LEFT_PADDING;
	}

	public static int progressRight(int left, int width, int height)
	{
		return coverLeft(left) + coverSize(width, height);
	}

	public static int progressY(int top, int height)
	{
		return top + height - PROGRESS_BOTTOM_OFFSET;
	}

	/** 播放键中心：与封面水平居中对齐。 */
	public static int controlCenterX(int left, int width, int height)
	{
		return coverLeft(left) + coverSize(width, height) / 2;
	}

	public static int controlY(int top, int height)
	{
		return top + height - CONTROL_BOTTOM_OFFSET;
	}

	/**
	 * 音量图标中心：左边与封面左边对齐（{@code drawVolume} 从 {@code x-6} 画到
	 * {@code x+7}）。
	 */
	public static int volumeIconX(int left)
	{
		return coverLeft(left) + VOLUME_ICON_RADIUS;
	}

	public static int volumeLeft(int left)
	{
		return left + LEFT_PADDING + VOLUME_INSET;
	}

	public static int volumeRight(int left, int width, int height)
	{
		return progressRight(left, width, height);
	}

	public static int volumeY(int top, int height)
	{
		return top + height - VOLUME_BOTTOM_OFFSET;
	}

	private static int clamp(int value, int min, int max)
	{
		return value < min ? min : value > max ? max : value;
	}
}

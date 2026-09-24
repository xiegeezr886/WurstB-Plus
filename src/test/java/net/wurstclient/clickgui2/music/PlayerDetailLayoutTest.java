package net.wurstclient.clickgui2.music;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

final class PlayerDetailLayoutTest
{
	/**
	 * {@code MusicContext.bounds()} 能给出的真实范围：宽被夹在 300~740，高被夹在
	 * 220~500。整个范围逐像素扫一遍，保证任何分辨率下都不会出现文字压在进度条
	 * 或控件上。
	 */
	private static final int[] WIDTHS =
		{300, 320, 360, 400, 460, 560, 640, 740};
	private static final int MIN_HEIGHT = 220;
	private static final int MAX_HEIGHT = 500;
	private static final int MIN_WIDTH = 300;

	@Test
	void leftColumnNeverOverlapsItsOwnRows()
	{
		for(int width : WIDTHS)
			for(int height = MIN_HEIGHT; height <= MAX_HEIGHT; height++)
			{
				int top = 0;
				int coverTop = PlayerDetailLayout.coverTop(top);
				int coverSize = PlayerDetailLayout.coverSize(width, height);
				int titleY = PlayerDetailLayout.titleY(top, width, height);
				int artistY = PlayerDetailLayout.artistY(top, width, height);
				int progressY = PlayerDetailLayout.progressY(top, height);
				int controlY = PlayerDetailLayout.controlY(top, height);
				int volumeY = PlayerDetailLayout.volumeY(top, height);
				String at = " (" + width + "x" + height + ")";

				assertTrue(coverSize >= PlayerDetailLayout.COVER_SIZE_MIN
					&& coverSize <= PlayerDetailLayout.COVER_SIZE_MAX,
					"封面边长越界" + at);
				// 歌手底下留 TEXT_PROGRESS_GAP 才能碰到进度条
				assertTrue(artistY + PlayerDetailLayout.ARTIST_SIZE
					+ PlayerDetailLayout.TEXT_PROGRESS_GAP <= progressY,
					"歌手压到进度条" + at);
				// 进度条刻度文字与播放键（悬停时外扩 1）
				assertTrue(progressY + PlayerDetailLayout.PROGRESS_LABEL_HEIGHT
					<= controlY - PlayerDetailLayout.PLAY_BUTTON_RADIUS - 1,
					"进度条文字压到控件" + at);
				// 播放键与音量图标
				assertTrue(controlY + PlayerDetailLayout.PLAY_BUTTON_RADIUS + 1
					+ PlayerDetailLayout.VOLUME_ICON_RADIUS <= volumeY,
					"控件压到音量" + at);
				assertTrue(volumeY + PlayerDetailLayout.VOLUME_ICON_RADIUS
					<= top + height, "音量跑出面板" + at);
				// 歌名贴着封面，或（面板太矮时）被拉到进度条上方
				assertTrue(titleY <= coverTop + coverSize
					+ PlayerDetailLayout.TITLE_GAP, "歌名与封面脱节" + at);
				assertTrue(titleY == coverTop + coverSize
					+ PlayerDetailLayout.TITLE_GAP
					|| titleY == PlayerDetailLayout.textBlockLimit(top, height),
					"歌名既没贴封面也没贴进度条" + at);
				assertEquals(artistY, titleY + PlayerDetailLayout.TITLE_SIZE
					+ PlayerDetailLayout.ARTIST_GAP, "歌名与歌手脱节" + at);
			}
	}

	@Test
	void columnsAndBarsStayInsideThePanel()
	{
		for(int width : WIDTHS)
			for(int height = MIN_HEIGHT; height <= MAX_HEIGHT; height++)
			{
				int left = 0;
				int right = left + width;
				int top = 0;
				int coverLeft = PlayerDetailLayout.coverLeft(left);
				int coverSize = PlayerDetailLayout.coverSize(width, height);
				int lyricLeft = PlayerDetailLayout.lyricLeft(left, width,
					height);
				int lyricRight = PlayerDetailLayout.lyricRight(right);
				int progressLeft = PlayerDetailLayout.progressLeft(left);
				int progressRight = PlayerDetailLayout.progressRight(left,
					width, height);
				String at = " (" + width + "x" + height + ")";

				assertTrue(coverLeft >= left
					&& coverLeft + coverSize <= right, "封面出界" + at);
				// 拖动进度/音量时除的是这条宽度，不能为 0
				assertTrue(progressRight - progressLeft == coverSize
					&& coverSize > 0, "进度条宽度非法" + at);
				assertTrue(PlayerDetailLayout.volumeRight(left, width,
					height) > PlayerDetailLayout.volumeLeft(left),
					"音量条宽度非法" + at);
				assertTrue(PlayerDetailLayout.volumeIconX(left)
					+ PlayerDetailLayout.VOLUME_ICON_RADIUS
					<= PlayerDetailLayout.volumeLeft(left),
					"音量图标压到音量条" + at);
				// 左栏与歌词栏之间至少留出 COLUMN_GAP
				assertTrue(lyricLeft >= progressRight
					+ PlayerDetailLayout.COLUMN_GAP, "两栏重叠" + at);
				assertTrue(lyricRight - lyricLeft
					>= PlayerDetailLayout.LYRICS_MIN_WIDTH, "歌词栏太窄" + at);
			}
	}

	@Test
	void coverGrowsWithThePanelAndIsCappedByWidth()
	{
		for(int width : WIDTHS)
			for(int height = MIN_HEIGHT; height < MAX_HEIGHT; height++)
				assertTrue(PlayerDetailLayout.coverSize(width, height + 1)
					>= PlayerDetailLayout.coverSize(width, height),
					"封面边长随高度回落 (" + width + "x" + height + ")");

		// 窄面板不放 190 的大封面，否则歌词栏会被挤没
		assertTrue(PlayerDetailLayout.coverSize(MIN_WIDTH, MAX_HEIGHT) < 190);
		assertEquals(PlayerDetailLayout.LYRICS_MIN_WIDTH,
			PlayerDetailLayout.lyricRight(MIN_WIDTH)
				- PlayerDetailLayout.lyricLeft(0, MIN_WIDTH, MAX_HEIGHT));
		assertEquals(190, PlayerDetailLayout.coverSize(740, 500));
	}

	/**
	 * 740x500（面板的完整尺寸，GUI 高度 ≥ 524 时就是它）是主要使用场景，数字
	 * 钉死，避免以后调布局时悄悄改掉这张「Apple Music 详情页」的样子。
	 */
	@Test
	void fullSizePanelMatchesTheDesignedGeometry()
	{
		int left = 0;
		int top = 0;
		int width = 740;
		int height = 500;

		assertEquals(44, PlayerDetailLayout.coverTop(top));
		assertEquals(190, PlayerDetailLayout.coverSize(width, height));
		assertEquals(28, PlayerDetailLayout.coverLeft(left));
		assertEquals(248, PlayerDetailLayout.titleY(top, width, height));
		assertEquals(266, PlayerDetailLayout.artistY(top, width, height));
		assertEquals(370, PlayerDetailLayout.progressY(top, height));
		assertEquals(28, PlayerDetailLayout.progressLeft(left));
		assertEquals(218, PlayerDetailLayout.progressRight(left, width, height));
		assertEquals(123,
			PlayerDetailLayout.controlCenterX(left, width, height));
		assertEquals(416, PlayerDetailLayout.controlY(top, height));
		assertEquals(52, PlayerDetailLayout.volumeLeft(left));
		assertEquals(34, PlayerDetailLayout.volumeIconX(left));
		assertEquals(218,
			PlayerDetailLayout.volumeRight(left, width, height));
		assertEquals(464, PlayerDetailLayout.volumeY(top, height));
		assertEquals(250, PlayerDetailLayout.lyricLeft(left, width, height));
		assertEquals(728, PlayerDetailLayout.lyricRight(left + width));
		assertEquals(34, PlayerDetailLayout.lyricTop(top));
		assertEquals(434, PlayerDetailLayout.lyricBottom(top, height));
	}

	@Test
	void geometryOnlyDependsOnOffsets()
	{
		int width = 740;
		int height = 500;
		assertEquals(PlayerDetailLayout.progressY(0, height) + 57,
			PlayerDetailLayout.progressY(57, height));
		assertEquals(PlayerDetailLayout.coverTop(0) + 57,
			PlayerDetailLayout.coverTop(57));
		assertEquals(PlayerDetailLayout.coverLeft(0) + 91,
			PlayerDetailLayout.coverLeft(91));
		assertEquals(PlayerDetailLayout.lyricLeft(0, width, height) + 91,
			PlayerDetailLayout.lyricLeft(91, width, height));
	}

	/**
	 * 面板高 336 是 1080p 用自动 GUI 缩放（scale 3 → 640x360）时的实际高度，
	 * 重构前这里歌名会压在播放键上。
	 */
	@Test
	void autoScalePanelKeepsTheTextAboveTheControls()
	{
		int width = 740;
		int height = 336;
		int titleY = PlayerDetailLayout.titleY(0, width, height);
		int artistY = PlayerDetailLayout.artistY(0, width, height);
		int controlY = PlayerDetailLayout.controlY(0, height);

		assertEquals(173, titleY);
		assertEquals(191, artistY);
		assertEquals(252, controlY);
		assertTrue(artistY + PlayerDetailLayout.ARTIST_SIZE
			< controlY - PlayerDetailLayout.PLAY_BUTTON_RADIUS,
			"歌手压到播放键");
	}

	@Test
	void slideOffsetMovesTheWholeLayerTogether()
	{
		assertEquals(0, PlayerDetailLayout.slideOffset(1, 500));
		assertEquals(500, PlayerDetailLayout.slideOffset(0, 500));
		assertEquals(250, PlayerDetailLayout.slideOffset(0.5F, 500));
		assertEquals(336, PlayerDetailLayout.slideOffset(0, 336));

		// 渲染走的是 update() 之后的值、命中测试走的是 get()，位移必须一致且单调
		for(int step = 0; step < 20; step++)
		{
			float lower = step / 20F;
			float higher = (step + 1) / 20F;
			assertTrue(PlayerDetailLayout.slideOffset(higher, 500)
				<= PlayerDetailLayout.slideOffset(lower, 500),
				"位移量不单调 @" + higher);
		}
	}
}

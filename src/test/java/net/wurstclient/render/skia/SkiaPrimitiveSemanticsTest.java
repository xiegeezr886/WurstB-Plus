package net.wurstclient.render.skia;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.io.File;
import java.util.function.Consumer;

import org.jetbrains.skia.Bitmap;
import org.jetbrains.skia.Canvas;
import org.jetbrains.skia.ColorAlphaType;
import org.jetbrains.skia.ColorType;
import org.jetbrains.skia.Font;
import org.jetbrains.skia.FontMgr;
import org.jetbrains.skia.FontStyle;
import org.jetbrains.skia.ImageInfo;
import org.jetbrains.skia.Paint;
import org.jetbrains.skia.PaintMode;
import org.jetbrains.skia.RRect;
import org.jetbrains.skia.Rect;
import org.jetbrains.skia.Surface;
import org.jetbrains.skia.Typeface;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/**
 * 用真的 Skia 光栅画布验证 {@link EspSkia} 依赖的那几条绘制语义。
 *
 * <p>
 * 这不是重复测 Skia，而是把「本工程对 Skia 的假设」钉下来。最要紧的一条是
 * {@code drawString} 的 {@code y} 到底是<b>基线</b>还是顶边：参考的铭牌排版
 * 按基线算（背景是 {@code y - 2 - 4.5}、正文在 {@code y}），本工程照抄了那
 * 个偏移。如果 Skia 的 {@code y} 其实是顶边，整套铭牌会整体下移约一个字高，
 * 而这种错在编译期完全看不出来。
 *
 * <p>
 * Skiko 原生库加载不了时（例如换到非 Windows 机器、或本工程的资源没打包）
 * 整个类会被 {@code assumeTrue} 跳过，而不是失败。
 */
final class SkiaPrimitiveSemanticsTest
{
	private static final int SIZE = 64;
	private static final int RED = 0xFFFF0000;

	/** 无头环境里 FontMgr 字族列表为空的兜底字体文件。 */
	private static final String[] FONT_CANDIDATES = {
		"C:/Windows/Fonts/arial.ttf", "C:/Windows/Fonts/segoeui.ttf",
		"C:/Windows/Fonts/tahoma.ttf",
		"/System/Library/Fonts/Helvetica.ttc",
		"/usr/share/fonts/truetype/dejavu/DejaVuSans.ttf"};

	@BeforeAll
	static void loadNatives()
	{
		File dir = null;
		for(String candidate : new String[]{
			"src/main/resources/assets/wurst/skiko",
			"build/resources/main/assets/wurst/skiko"})
		{
			File probe = new File(candidate);
			if(new File(probe, "skiko-windows-x64.dll").isFile())
			{
				dir = probe;
				break;
			}
		}

		assumeTrue(dir != null, "bundled Skiko natives not found");

		System.setProperty("skiko.library.path", dir.getAbsolutePath());
		System.setProperty("skiko.data.path", dir.getAbsolutePath());

		try
		{
			Surface surface = Surface.Companion.makeRasterN32Premul(2, 2);
			try
			{
				surface.getCanvas().clear(0xFF000000);
			}finally
			{
				surface.close();
			}
		}catch(Throwable e)
		{
			assumeTrue(false, "Skiko natives unavailable: " + e);
		}
	}

	@Test
	void drawRectFillsExactlyTheRequestedPixels()
	{
		Bitmap bitmap = render(canvas -> canvas.drawRect(new Rect(4, 4, 12, 9),
			fill(RED)));

		// Skia 的 Rect right/bottom 是<b>开区间</b>：4..11 与 4..8
		assertEquals(RED, bitmap.getColor(4, 4), "top-left corner");
		assertEquals(RED, bitmap.getColor(11, 8), "bottom-right inside pixel");

		assertEquals(0, bitmap.getColor(3, 8), "just left of the rect");
		assertEquals(0, bitmap.getColor(12, 8), "just right of the rect");
		assertEquals(0, bitmap.getColor(4, 3), "just above the rect");
		assertEquals(0, bitmap.getColor(4, 9), "just below the rect");
	}

	/**
	 * 这一条是整套铭牌排版的立足点，见类注释。
	 */
	@Test
	void drawStringTreatsYAsTheBaselineNotTheTopEdge()
	{
		Font font = systemFont(20F);
		int baseline = 40;

		Bitmap bitmap = render(canvas -> canvas.drawString("H", 10, baseline,
			font, fill(0xFFFFFFFF)));

		assertTrue(inkRows(bitmap, 0, baseline - 1) > 0,
			"expected glyph ink above the baseline");
		assertEquals(0, inkRows(bitmap, baseline + 2, SIZE - 1),
			"'y' behaved like a top edge: ink found well below it");
	}

	@Test
	void drawStringStartsAtTheGivenLeftEdge()
	{
		Font font = systemFont(20F);
		int left = 20;

		Bitmap bitmap = render(canvas -> canvas.drawString("H", left, 40, font,
			fill(0xFFFFFFFF)));

		assertEquals(0, inkColumns(bitmap, 0, left - 2),
			"ink appeared left of the requested x");
		assertTrue(inkColumns(bitmap, left, left + 2) > 0,
			"expected ink right at the requested x");
	}

	@Test
	void measureTextWidthIsZeroForEmptyTextAndGrowsWithLength()
	{
		Font font = systemFont(16F);

		assertEquals(0F, font.measureTextWidth(""), 0.0001F);
		assertTrue(font.measureTextWidth("W") > 0F,
			"a real glyph must have a positive advance");
		assertTrue(font.measureTextWidth("WW") > font.measureTextWidth("W"),
			"two glyphs must be wider than one");
	}

	@Test
	void roundedRectClipsTheCornersButKeepsTheEdges()
	{
		Bitmap bitmap = render(canvas -> canvas.drawRRect(
			RRect.Companion.makeLTRB(10, 10, 54, 54, 12), fill(RED)));

		assertEquals(RED, bitmap.getColor(32, 32), "centre must be filled");
		assertEquals(0, bitmap.getColor(10, 10), "sharp corner must be cut");
		assertEquals(0, bitmap.getColor(53, 53), "far corner must be cut too");
		assertEquals(RED, bitmap.getColor(32, 11), "top edge midpoint filled");
		assertEquals(RED, bitmap.getColor(11, 32), "left edge midpoint filled");
	}

	@Test
	void roundRectRadiusIsClampedInsteadOfOverflowing()
	{
		// 半径远大于短边：Skia 必须把它收进一半，而不是画出畸形
		Bitmap bitmap = render(canvas -> canvas.drawRRect(
			RRect.Companion.makeLTRB(10, 10, 50, 30, 999), fill(RED)));

		assertEquals(RED, bitmap.getColor(30, 20), "centre must still be filled");
		assertEquals(0, bitmap.getColor(10, 10), "corner still cut");
	}

	@Test
	void strokeModeDrawsARingInsteadOfAFill()
	{
		Bitmap bitmap = render(canvas -> {
			Paint paint = fill(RED);
			paint.setMode(PaintMode.STROKE);
			paint.setStrokeWidth(3);
			canvas.drawRect(new Rect(10, 10, 54, 54), paint);
		});

		// 这也是 EspSkia.outlineRectCased() 的前提：STROKE 只画一圈
		assertEquals(RED, bitmap.getColor(10, 32), "left edge of the ring");
		assertEquals(RED, bitmap.getColor(32, 10), "top edge of the ring");
		assertEquals(0, bitmap.getColor(32, 32), "the ring must be hollow");
	}

	// ------------------------------------------------------------------

	private static Bitmap render(Consumer<Canvas> draw)
	{
		Surface surface = Surface.Companion.makeRasterN32Premul(SIZE, SIZE);
		try
		{
			Canvas canvas = surface.getCanvas();
			canvas.clear(0x00000000);
			draw.accept(canvas);

			Bitmap bitmap = new Bitmap();
			bitmap.allocPixels(new ImageInfo(SIZE, SIZE, ColorType.RGBA_8888,
				ColorAlphaType.UNPREMUL));

			boolean read = surface.readPixels(bitmap, 0, 0);
			assumeTrue(read, "could not read pixels back from the surface");
			return bitmap;
		}finally
		{
			surface.close();
		}
	}

	private static Paint fill(int color)
	{
		Paint paint = new Paint();
		paint.setAntiAlias(true);
		paint.setMode(PaintMode.FILL);
		paint.setColor(color);
		return paint;
	}

	private static Font systemFont(float size)
	{
		// 无头测试 JVM 里 FontMgr 的字族列表往往是空的（matchFamilyStyle(null,
		// ...) 返回 null），所以退回直接加载一个已知的系统字体文件。都找不到
		// 就跳过文字相关的用例，而不是让它们变红。
		FontMgr mgr = FontMgr.Companion.getDefault();
		Typeface typeface =
			mgr.matchFamilyStyle(null, FontStyle.Companion.getNORMAL());

		for(int i = 0; typeface == null && i < FONT_CANDIDATES.length; i++)
		{
			File file = new File(FONT_CANDIDATES[i]);
			if(file.isFile())
				typeface = mgr.makeFromFile(file.getAbsolutePath(), 0);
		}

		assumeTrue(typeface != null,
			"no usable typeface found, cannot test text rendering");

		Font font = new Font(typeface, size);
		assumeTrue(font.measureTextWidth("H") > 0F,
			"the resolved typeface has no glyph for 'H'");

		return font;
	}

	/** 统计 [fromY, toY] 这些行里有多少个不完全透明的像素。 */
	private static int inkRows(Bitmap bitmap, int fromY, int toY)
	{
		int count = 0;
		for(int y = Math.max(0, fromY); y <= Math.min(SIZE - 1, toY); y++)
			for(int x = 0; x < SIZE; x++)
				if((bitmap.getColor(x, y) >>> 24) != 0)
					count++;
		return count;
	}

	/** 统计 [fromX, toX] 这些列里有多少个不完全透明的像素。 */
	private static int inkColumns(Bitmap bitmap, int fromX, int toX)
	{
		int count = 0;
		for(int x = Math.max(0, fromX); x <= Math.min(SIZE - 1, toX); x++)
			for(int y = 0; y < SIZE; y++)
				if((bitmap.getColor(x, y) >>> 24) != 0)
					count++;
		return count;
	}
}

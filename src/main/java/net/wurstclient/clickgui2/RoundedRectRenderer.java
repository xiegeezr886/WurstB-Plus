package net.wurstclient.clickgui2;

import org.joml.Matrix4f;
import org.lwjgl.opengl.GL11;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.GameRenderer;

final class RoundedRectRenderer
{
	private static final int MAX_SEGMENTS = 16;
	private static final int MAX_POINTS = MAX_SEGMENTS * 4;
	/** 直角横向渐变的竖条上限（圆角那条路用不着分段）。 */
	private static final int MAX_GRADIENT_BANDS = 48;
	private static final float[][] POINT_X = new float[4][MAX_POINTS];
	private static final float[][] POINT_Y = new float[4][MAX_POINTS];

	private RoundedRectRenderer()
	{
	}

	public static void fill(GuiGraphics graphics, float x1, float y1, float x2,
		float y2, float radius, int color)
	{
		if(x2 <= x1 || y2 <= y1 || color >>> 24 == 0)
			return;

		float safeRadius = clampRadius(x1, y1, x2, y2, radius);
		if(safeRadius < 0.5F)
		{
			graphics.fill((int)x1, (int)y1, (int)x2, (int)y2, color);
			return;
		}

		int segments = segmentsFor(safeRadius);
		prepareContour(0, x1 + 0.5F, y1 + 0.5F, x2 - 0.5F,
			y2 - 0.5F, Math.max(0, safeRadius - 0.5F), segments);
		prepareContour(1, x1 - 0.5F, y1 - 0.5F, x2 + 0.5F,
			y2 + 0.5F, safeRadius + 0.5F, segments);

		RenderState state = begin(graphics);
		RenderSystem.setShader(GameRenderer::getPositionColorShader);
		BufferBuilder buffer = Tesselator.getInstance().getBuilder();
		buffer.begin(VertexFormat.Mode.TRIANGLES,
			DefaultVertexFormat.POSITION_COLOR);
		Matrix4f pose = graphics.pose().last().pose();
		addSolidFan(buffer, pose, 0, segments, color);
		addColorStrip(buffer, pose, 0, 1, segments, color,
			color & 0xFFFFFF);
		Tesselator.getInstance().end();
		state.restore();
	}

	public static void outline(GuiGraphics graphics, float x1, float y1,
		float x2, float y2, float radius, int color)
	{
		if(x2 <= x1 || y2 <= y1 || color >>> 24 == 0)
			return;

		float safeRadius = clampRadius(x1, y1, x2, y2, radius);
		int segments = segmentsFor(safeRadius);
		prepareContour(0, x1 - 0.5F, y1 - 0.5F, x2 + 0.5F,
			y2 + 0.5F, safeRadius + 0.5F, segments);
		prepareContour(1, x1 + 0.3F, y1 + 0.3F, x2 - 0.3F,
			y2 - 0.3F, Math.max(0, safeRadius - 0.3F), segments);
		prepareContour(2, x1 + 1.35F, y1 + 1.35F, x2 - 1.35F,
			y2 - 1.35F, Math.max(0, safeRadius - 1.35F), segments);

		RenderState state = begin(graphics);
		RenderSystem.setShader(GameRenderer::getPositionColorShader);
		BufferBuilder buffer = Tesselator.getInstance().getBuilder();
		buffer.begin(VertexFormat.Mode.TRIANGLES,
			DefaultVertexFormat.POSITION_COLOR);
		Matrix4f pose = graphics.pose().last().pose();
		int transparent = color & 0xFFFFFF;
		addColorStrip(buffer, pose, 0, 1, segments, transparent, color);
		addColorStrip(buffer, pose, 1, 2, segments, color, transparent);
		Tesselator.getInstance().end();
		state.restore();
	}

	public static void outlineGradient(GuiGraphics graphics, float x1,
		float y1, float x2, float y2, float radius,
		FlatRenderer.GradientColorFn colorFn)
	{
		if(x2 <= x1 || y2 <= y1)
			return;

		float safeRadius = clampRadius(x1, y1, x2, y2, radius);
		int segments = segmentsFor(safeRadius);
		prepareContour(0, x1 - 0.5F, y1 - 0.5F, x2 + 0.5F,
			y2 + 0.5F, safeRadius + 0.5F, segments);
		prepareContour(1, x1 + 0.3F, y1 + 0.3F, x2 - 0.3F,
			y2 - 0.3F, Math.max(0, safeRadius - 0.3F), segments);
		prepareContour(2, x1 + 1.35F, y1 + 1.35F, x2 - 1.35F,
			y2 - 1.35F, Math.max(0, safeRadius - 1.35F), segments);

		RenderState state = begin(graphics);
		RenderSystem.setShader(GameRenderer::getPositionColorShader);
		BufferBuilder buffer = Tesselator.getInstance().getBuilder();
		buffer.begin(VertexFormat.Mode.TRIANGLES,
			DefaultVertexFormat.POSITION_COLOR);
		Matrix4f pose = graphics.pose().last().pose();
		addGradientColorStrip(buffer, pose, 0, 1, segments, colorFn, false);
		addGradientColorStrip(buffer, pose, 1, 2, segments, colorFn, true);
		Tesselator.getInstance().end();
		state.restore();
	}

	/**
	 * 每角独立半径的填充。
	 *
	 * @param radii
	 *            按 CSS 的 {@code border-radius} 顺序：{@code {左上, 右上, 右下,
	 *            左下}}。传 {@code null} 表示四角都取 0。
	 */
	public static void fillCorners(GuiGraphics graphics, float x1, float y1,
		float x2, float y2, float[] radii, int color)
	{
		if(x2 <= x1 || y2 <= y1 || color >>> 24 == 0)
			return;

		if(maxRadius(x1, y1, x2, y2, radii) < 0.5F)
		{
			graphics.fill((int)x1, (int)y1, (int)x2, (int)y2, color);
			return;
		}

		int segments = segmentsFor(maxRadius(x1, y1, x2, y2, radii));
		prepareCornerContour(0, x1 + 0.5F, y1 + 0.5F, x2 - 0.5F, y2 - 0.5F,
			shrink(radii, 0.5F), segments);
		prepareCornerContour(1, x1 - 0.5F, y1 - 0.5F, x2 + 0.5F, y2 + 0.5F,
			grow(radii, 0.5F), segments);

		RenderState state = begin(graphics);
		RenderSystem.setShader(GameRenderer::getPositionColorShader);
		BufferBuilder buffer = Tesselator.getInstance().getBuilder();
		buffer.begin(VertexFormat.Mode.TRIANGLES,
			DefaultVertexFormat.POSITION_COLOR);
		Matrix4f pose = graphics.pose().last().pose();
		addSolidFan(buffer, pose, 0, segments, color);
		addColorStrip(buffer, pose, 0, 1, segments, color, color & 0xFFFFFF);
		Tesselator.getInstance().end();
		state.restore();
	}

	/**
	 * 每角独立半径、且按 y 从上到下渐变的填充。圆角由几何裁出，所以渐变在角上
	 * 同样成立——这是原版路线替代 Skia 圆角渐变填充的关键一块。
	 */
	public static void fillCornersVerticalGradient(GuiGraphics graphics,
		float x1, float y1, float x2, float y2, float[] radii, int topColor,
		int bottomColor)
	{
		if(x2 <= x1 || y2 <= y1)
			return;
		if((topColor >>> 24 == 0) && (bottomColor >>> 24 == 0))
			return;

		float maxRadius = maxRadius(x1, y1, x2, y2, radii);

		if(maxRadius < 0.5F)
		{
			graphics.fillGradient((int)x1, (int)y1, (int)x2, (int)y2,
				topColor, bottomColor);
			return;
		}

		int segments = segmentsFor(maxRadius);
		prepareCornerContour(0, x1 + 0.5F, y1 + 0.5F, x2 - 0.5F, y2 - 0.5F,
			shrink(radii, 0.5F), segments);
		prepareCornerContour(1, x1 - 0.5F, y1 - 0.5F, x2 + 0.5F, y2 + 0.5F,
			grow(radii, 0.5F), segments);

		RenderState state = begin(graphics);
		RenderSystem.setShader(GameRenderer::getPositionColorShader);
		BufferBuilder buffer = Tesselator.getInstance().getBuilder();
		buffer.begin(VertexFormat.Mode.TRIANGLES,
			DefaultVertexFormat.POSITION_COLOR);
		Matrix4f pose = graphics.pose().last().pose();
		float top = Math.min(y1, y2);
		float bottom = Math.max(y1, y2);
		addVerticalGradientStrip(buffer, pose, 1, segments, top, bottom,
			topColor, bottomColor);
		addVerticalGradientFan(buffer, pose, 0, segments, top, bottom, topColor,
			bottomColor);
		Tesselator.getInstance().end();
		state.restore();
	}

	/**
	 * 每角独立半径、且按 x 从左到右渐变的填充。几何与
	 * {@link #fillCornersVerticalGradient} 完全一样，只是顶点色沿 x 插值——
	 * 参考实现的播放进度条是 {@code linear-gradient(90deg, accent, #0d9488)}，
	 * 也就是同一根圆角条左蓝右青。原版只有纵向的 {@code fillGradient}，所以
	 * 这一块必须自己做顶点色。
	 */
	public static void fillCornersHorizontalGradient(GuiGraphics graphics,
		float x1, float y1, float x2, float y2, float[] radii, int leftColor,
		int rightColor)
	{
		if(x2 <= x1 || y2 <= y1)
			return;
		if((leftColor >>> 24 == 0) && (rightColor >>> 24 == 0))
			return;

		float maxRadius = maxRadius(x1, y1, x2, y2, radii);

		if(maxRadius < 0.5F)
		{
			fillHorizontalBands(graphics, x1, y1, x2, y2, leftColor,
				rightColor);
			return;
		}

		int segments = segmentsFor(maxRadius);
		prepareCornerContour(0, x1 + 0.5F, y1 + 0.5F, x2 - 0.5F, y2 - 0.5F,
			shrink(radii, 0.5F), segments);
		prepareCornerContour(1, x1 - 0.5F, y1 - 0.5F, x2 + 0.5F, y2 + 0.5F,
			grow(radii, 0.5F), segments);

		RenderState state = begin(graphics);
		RenderSystem.setShader(GameRenderer::getPositionColorShader);
		BufferBuilder buffer = Tesselator.getInstance().getBuilder();
		buffer.begin(VertexFormat.Mode.TRIANGLES,
			DefaultVertexFormat.POSITION_COLOR);
		Matrix4f pose = graphics.pose().last().pose();
		float left = Math.min(x1, x2);
		float right = Math.max(x1, x2);
		addHorizontalGradientStrip(buffer, pose, 1, segments, left, right,
			leftColor, rightColor);
		addHorizontalGradientFan(buffer, pose, 0, segments, left, right,
			leftColor, rightColor);
		Tesselator.getInstance().end();
		state.restore();
	}

	/**
	 * 没有圆角可裁时的横向渐变：切成若干竖条，每条取中点颜色铺一色。原版
	 * {@code fillGradient} 只沿 y 插值，直角矩形这条退化路上用不了。
	 */
	private static void fillHorizontalBands(GuiGraphics graphics, float x1,
		float y1, float x2, float y2, int leftColor, int rightColor)
	{
		int left = Math.round(Math.min(x1, x2));
		int right = Math.round(Math.max(x1, x2));
		int top = Math.round(Math.min(y1, y2));
		int bottom = Math.round(Math.max(y1, y2));
		int width = right - left;

		if(width <= 0 || bottom <= top)
			return;

		int bands = Math.min(width, MAX_GRADIENT_BANDS);

		for(int i = 0; i < bands; i++)
		{
			int bandLeft = left + width * i / bands;
			int bandRight = left + width * (i + 1) / bands;

			if(bandRight <= bandLeft)
				continue;

			graphics.fill(bandLeft, top, bandRight, bottom,
				lerpByX((bandLeft + bandRight) / 2F, left, right, leftColor,
					rightColor));
		}
	}

	/** 按 y 线性插值两个颜色。 */
	private static int lerpByY(float y, float top, float bottom, int topColor,
		int bottomColor)
	{
		return lerpColor(topColor, bottomColor,
			axisRatio(y, top, bottom));
	}

	/** 按 x 线性插值两个颜色。 */
	private static int lerpByX(float x, float left, float right, int leftColor,
		int rightColor)
	{
		return lerpColor(leftColor, rightColor, axisRatio(x, left, right));
	}

	/**
	 * {@code value} 落在 {@code from..to} 里的比例，夹在 {@code 0..1}。区间退化
	 * （或方向反了）时返回 0，与渐变填充原有行为一致。
	 *
	 * <p>
	 * 无 GL、无 Skia 类型，单独放出来是为了能单测：顶点色插值的比例算错，颜色
	 * 就会在条子中间跳变。</p>
	 */
	static float axisRatio(float value, float from, float to)
	{
		float span = to - from;

		if(span <= 0.0001F)
			return 0;

		return Math.max(0F, Math.min(1F, (value - from) / span));
	}

	/**
	 * 两个 ARGB 颜色按 {@code t}（已夹在 0..1）逐通道插值，四个通道各自取整。
	 *
	 * <p>
	 * 纯函数，可单测：像素里 0xFF2563EB 与 0xFF0D9488 的中点是
	 * 0xFF197CBA，四个通道各自取整。</p>
	 */
	static int lerpColor(int from, int to, float t)
	{
		float clamped = Math.max(0F, Math.min(1F, t));
		int a = Math.round(((from >>> 24)
			+ ((to >>> 24) - (from >>> 24)) * clamped));
		int r = Math.round((((from >> 16) & 0xFF)
			+ (((to >> 16) & 0xFF) - ((from >> 16) & 0xFF)) * clamped));
		int g = Math.round((((from >> 8) & 0xFF)
			+ (((to >> 8) & 0xFF) - ((from >> 8) & 0xFF)) * clamped));
		int b = Math.round(((from & 0xFF)
			+ ((to & 0xFF) - (from & 0xFF)) * clamped));
		return a << 24 | r << 16 | g << 8 | b;
	}

	private static void addVerticalGradientStrip(BufferBuilder buffer,
		Matrix4f pose, int contour, int segments, float top, float bottom,
		int topColor, int bottomColor)
	{
		int points = segments * 4;
		for(int i = 0; i < points; i++)
		{
			int next = (i + 1) % points;
			addColorVertex(buffer, pose, POINT_X[contour][i], POINT_Y[contour][i],
				lerpByY(POINT_Y[contour][i], top, bottom, topColor, bottomColor));
			addColorVertex(buffer, pose, POINT_X[contour][next],
				POINT_Y[contour][next],
				lerpByY(POINT_Y[contour][next], top, bottom, topColor,
					bottomColor));
		}
	}

	private static void addVerticalGradientFan(BufferBuilder buffer,
		Matrix4f pose, int contour, int segments, float top, float bottom,
		int topColor, int bottomColor)
	{
		int points = segments * 4;
		float centerX = 0;
		float centerY = 0;

		for(int i = 0; i < points; i++)
		{
			centerX += POINT_X[contour][i];
			centerY += POINT_Y[contour][i];
		}

		centerX /= points;
		centerY /= points;
		int centerColor =
			lerpByY(centerY, top, bottom, topColor, bottomColor);

		for(int i = 0; i < points; i++)
		{
			int next = (i + 1) % points;
			addColorVertex(buffer, pose, centerX, centerY, centerColor);
			addColorVertex(buffer, pose, POINT_X[contour][i], POINT_Y[contour][i],
				lerpByY(POINT_Y[contour][i], top, bottom, topColor, bottomColor));
			addColorVertex(buffer, pose, POINT_X[contour][next],
				POINT_Y[contour][next],
				lerpByY(POINT_Y[contour][next], top, bottom, topColor,
					bottomColor));
		}
	}

	private static void addHorizontalGradientStrip(BufferBuilder buffer,
		Matrix4f pose, int contour, int segments, float left, float right,
		int leftColor, int rightColor)
	{
		int points = segments * 4;
		for(int i = 0; i < points; i++)
		{
			int next = (i + 1) % points;
			addColorVertex(buffer, pose, POINT_X[contour][i],
				POINT_Y[contour][i],
				lerpByX(POINT_X[contour][i], left, right, leftColor,
					rightColor));
			addColorVertex(buffer, pose, POINT_X[contour][next],
				POINT_Y[contour][next],
				lerpByX(POINT_X[contour][next], left, right, leftColor,
					rightColor));
		}
	}

	private static void addHorizontalGradientFan(BufferBuilder buffer,
		Matrix4f pose, int contour, int segments, float left, float right,
		int leftColor, int rightColor)
	{
		int points = segments * 4;
		float centerX = 0;
		float centerY = 0;

		for(int i = 0; i < points; i++)
		{
			centerX += POINT_X[contour][i];
			centerY += POINT_Y[contour][i];
		}

		centerX /= points;
		centerY /= points;
		int centerColor =
			lerpByX(centerX, left, right, leftColor, rightColor);

		for(int i = 0; i < points; i++)
		{
			int next = (i + 1) % points;
			addColorVertex(buffer, pose, centerX, centerY, centerColor);
			addColorVertex(buffer, pose, POINT_X[contour][i],
				POINT_Y[contour][i],
				lerpByX(POINT_X[contour][i], left, right, leftColor,
					rightColor));
			addColorVertex(buffer, pose, POINT_X[contour][next],
				POINT_Y[contour][next],
				lerpByX(POINT_X[contour][next], left, right, leftColor,
					rightColor));
		}
	}

	/** 取四角里最大的那个半径，用来决定细分段数。 */
	private static float maxRadius(float x1, float y1, float x2, float y2,
		float[] radii)
	{
		if(radii == null)
			return 0;

		float[] clamped = clampRadii(x1, y1, x2, y2, radii);
		float max = 0;

		for(float radius : clamped)
			max = Math.max(max, radius);

		return max;
	}

	/**
	 * 把四角半径各自钳到「不超过所在边的一半」，否则相邻角的圆弧会互相穿插。
	 *
	 * @param radii
	 *            CSS 顺序：{@code {左上, 右上, 右下, 左下}}
	 */
	static float[] clampRadii(float x1, float y1, float x2, float y2,
		float[] radii)
	{
		float width = Math.abs(x2 - x1);
		float height = Math.abs(y2 - y1);
		float[] out = new float[4];

		if(radii == null)
			return out;

		for(int corner = 0; corner < 4; corner++)
		{
			float value = corner < radii.length ? radii[corner] : 0;
			out[corner] = Math.max(0, Math.min(value,
				Math.min(width / 2, height / 2)));
		}

		return out;
	}

	private static float[] grow(float[] radii, float amount)
	{
		float[] out = new float[4];

		if(radii != null)
			for(int i = 0; i < 4 && i < radii.length; i++)
				out[i] = radii[i] + amount;

		return out;
	}

	private static float[] shrink(float[] radii, float amount)
	{
		float[] out = new float[4];

		if(radii != null)
			for(int i = 0; i < 4 && i < radii.length; i++)
				out[i] = Math.max(0, radii[i] - amount);

		return out;
	}

	/**
	 * 按每角半径铺一圈轮廓点。角序与旧的单半径版本一致（右上、右下、左下、
	 * 左上），这样扇面与条带的三角化可以原样复用。
	 */
	private static void prepareCornerContour(int contour, float x1, float y1,
		float x2, float y2, float[] radii, int segments)
	{
		float safeX2 = Math.max(x1, x2);
		float safeY2 = Math.max(y1, y2);
		float[] r = clampRadii(x1, y1, safeX2, safeY2, radii);

		// CSS 顺序 {左上, 右上, 右下, 左下} → 本类的角序 {右上, 右下, 左下, 左上}
		float topRight = r[1];
		float bottomRight = r[2];
		float bottomLeft = r[3];
		float topLeft = r[0];

		float[] centersX =
			{safeX2 - topRight, safeX2 - bottomRight, x1 + bottomLeft,
				x1 + topLeft};
		float[] centersY =
			{y1 + topRight, safeY2 - bottomRight, safeY2 - bottomLeft,
				y1 + topLeft};
		float[] cornerRadii =
			{topRight, bottomRight, bottomLeft, topLeft};
		float[] starts = {-90, 0, 90, 180};
		int index = 0;

		for(int corner = 0; corner < 4; corner++)
			for(int step = 0; step < segments; step++)
			{
				double angle = Math.toRadians(
					starts[corner] + step * 90F / segments);
				POINT_X[contour][index] = centersX[corner]
					+ (float)Math.cos(angle) * cornerRadii[corner];
				POINT_Y[contour][index] = centersY[corner]
					+ (float)Math.sin(angle) * cornerRadii[corner];
				index++;
			}
	}

	private static RenderState begin(GuiGraphics graphics)
	{
		graphics.flush();
		RenderState state = new RenderState(GL11.glIsEnabled(GL11.GL_BLEND),
			GL11.glIsEnabled(GL11.GL_DEPTH_TEST),
			GL11.glIsEnabled(GL11.GL_CULL_FACE));
		RenderSystem.enableBlend();
		RenderSystem.defaultBlendFunc();
		RenderSystem.disableDepthTest();
		RenderSystem.disableCull();
		return state;
	}

	private static void addSolidFan(BufferBuilder buffer, Matrix4f pose,
		int contour, int segments, int color)
	{
		int points = segments * 4;
		float centerX = 0;
		float centerY = 0;
		for(int i = 0; i < points; i++)
		{
			centerX += POINT_X[contour][i];
			centerY += POINT_Y[contour][i];
		}
		centerX /= points;
		centerY /= points;

		for(int i = 0; i < points; i++)
		{
			int next = (i + 1) % points;
			addColorVertex(buffer, pose, centerX, centerY, color);
			addColorVertex(buffer, pose, POINT_X[contour][i],
				POINT_Y[contour][i], color);
			addColorVertex(buffer, pose, POINT_X[contour][next],
				POINT_Y[contour][next], color);
		}
	}

	private static void addColorStrip(BufferBuilder buffer, Matrix4f pose,
		int inner, int outer, int segments, int innerColor, int outerColor)
	{
		int points = segments * 4;
		for(int i = 0; i < points; i++)
		{
			int next = (i + 1) % points;
			addColorVertex(buffer, pose, POINT_X[inner][i], POINT_Y[inner][i],
				innerColor);
			addColorVertex(buffer, pose, POINT_X[outer][i], POINT_Y[outer][i],
				outerColor);
			addColorVertex(buffer, pose, POINT_X[outer][next],
				POINT_Y[outer][next], outerColor);
			addColorVertex(buffer, pose, POINT_X[inner][i], POINT_Y[inner][i],
				innerColor);
			addColorVertex(buffer, pose, POINT_X[outer][next],
				POINT_Y[outer][next], outerColor);
			addColorVertex(buffer, pose, POINT_X[inner][next],
				POINT_Y[inner][next], innerColor);
		}
	}

	private static void addGradientColorStrip(BufferBuilder buffer,
		Matrix4f pose, int inner, int outer, int segments,
		FlatRenderer.GradientColorFn colorFn, boolean innerIsColor)
	{
		int points = segments * 4;
		for(int i = 0; i < points; i++)
		{
			int next = (i + 1) % points;
			int ic = colorFor(colorFn, POINT_X[inner][i], innerIsColor);
			int oc = colorFor(colorFn, POINT_X[outer][i], !innerIsColor);
			int inc = colorFor(colorFn, POINT_X[inner][next], innerIsColor);
			int onc = colorFor(colorFn, POINT_X[outer][next], !innerIsColor);
			addColorVertex(buffer, pose, POINT_X[inner][i], POINT_Y[inner][i],
				ic);
			addColorVertex(buffer, pose, POINT_X[outer][i], POINT_Y[outer][i],
				oc);
			addColorVertex(buffer, pose, POINT_X[outer][next],
				POINT_Y[outer][next], onc);
			addColorVertex(buffer, pose, POINT_X[inner][i], POINT_Y[inner][i],
				ic);
			addColorVertex(buffer, pose, POINT_X[outer][next],
				POINT_Y[outer][next], onc);
			addColorVertex(buffer, pose, POINT_X[inner][next],
				POINT_Y[inner][next], inc);
		}
	}

	private static int colorFor(FlatRenderer.GradientColorFn colorFn, float x,
		boolean isColor)
	{
		int color = colorFn.colorAt(x);
		return isColor ? color : color & 0xFFFFFF;
	}

	private static void addColorVertex(BufferBuilder buffer, Matrix4f pose,
		float x, float y, int color)
	{
		buffer.vertex(pose, x, y, 0).color(color).endVertex();
	}

	private static void prepareContour(int contour, float x1, float y1,
		float x2, float y2, float radius, int segments)
	{
		float safeX2 = Math.max(x1, x2);
		float safeY2 = Math.max(y1, y2);
		float safeRadius = clampRadius(x1, y1, safeX2, safeY2, radius);
		float[] centersX = {safeX2 - safeRadius, safeX2 - safeRadius,
			x1 + safeRadius, x1 + safeRadius};
		float[] centersY = {y1 + safeRadius, safeY2 - safeRadius,
			safeY2 - safeRadius, y1 + safeRadius};
		float[] starts = {-90, 0, 90, 180};
		int index = 0;
		for(int corner = 0; corner < 4; corner++)
			for(int step = 0; step < segments; step++)
			{
				double angle = Math.toRadians(starts[corner]
					+ step * 90F / segments);
				POINT_X[contour][index] =
					centersX[corner] + (float)Math.cos(angle) * safeRadius;
				POINT_Y[contour][index] =
					centersY[corner] + (float)Math.sin(angle) * safeRadius;
				index++;
			}
	}

	private static int segmentsFor(float radius)
	{
		return Math.max(8,
			Math.min(MAX_SEGMENTS, (int)Math.ceil(radius * 2)));
	}

	private static float clampRadius(float x1, float y1, float x2, float y2,
		float radius)
	{
		return Math.max(0,
			Math.min(radius, Math.min((x2 - x1) / 2, (y2 - y1) / 2)));
	}

	private record RenderState(boolean blend, boolean depth, boolean cull)
	{
		private void restore()
		{
			if(blend)
				RenderSystem.enableBlend();
			else
				RenderSystem.disableBlend();
			if(depth)
				RenderSystem.enableDepthTest();
			else
				RenderSystem.disableDepthTest();
			if(cull)
				RenderSystem.enableCull();
			else
				RenderSystem.disableCull();
		}
	}
}

package net.wurstclient.gui.glass;

import org.joml.Matrix4f;
import org.joml.Vector4f;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.ShaderInstance;

/**
 * 原版控件的玻璃外观：抓一帧背景，再把控件画成 LumaGlass 的面板。
 *
 * <p>
 * 移植自 LumaGlass 0.2.9（MIT，Copyright © 2026 Sunny5349；许可正文见
 * {@code META-INF/licenses/lumaglass/}）。上游是三个混入（{@code ButtonMixin}、
 * {@code SliderMixin}、{@code CheckboxMixin}）加 {@code VanillaGlass} 的自动主题路径
 * （同一个屏幕渲染趟次里只抓一次背景、每趟开头重置）。本类把取帧那部分合到一处：
 * {@link #beginPass()} 由屏幕渲染事件调用，三个 {@code render*} 由各自的混入调用。</p>
 *
 * <p>
 * 材质取上游 {@code GlassConfig} 的默认值（模糊 4.8、折射 24、磨砂 1、底色 0、泛光
 * 关闭）；悬停时按上游 {@code VanillaGlass.style()} 的做法加 0.13 底色与 +0.7 高光，
 * 只是这里按悬停动画的进度插值，而不是上游的布尔量——本工程的按钮本来就有 20 帧的
 * 悬停动画，直接跳变会退步。</p>
 *
 * <p>
 * 任何一步出错（着色器没装上、uniform 名字对不上、驱动报错）都会把玻璃永久关掉并返回
 * {@code false}，调用方于是画回原来的样子（按钮/滑块是扁平样式，复选框是原版贴图）。
 * 这条回退路径很重要：这几个混入覆盖的是<b>全客户端所有原版控件</b>，一旦异常逃出去，
 * 每个界面都会跟着崩。</p>
 */
public final class GlassButtons
{
	/** 上游 {@code GlassConfig} 的默认值。 */
	private static final float BLUR_RADIUS = 4.8F;
	private static final float REFRACTION = 24F;
	private static final float FROST = 1F;
	private static final float BASE_TINT = 0F;
	private static final float FOCUS_TINT = 0.13F;
	private static final float BASE_HIGHLIGHT = 1F;
	private static final float FOCUS_HIGHLIGHT = 0.7F;
	/** 上游 {@code ButtonMixin} 的圆角：{@code min(10, height * 0.5)}。 */
	private static final float MAX_RADIUS = 10F;
	/** 上游对未激活按钮的透明度处理：{@code alpha * (active ? 1 : 0.5)}。 */
	private static final float DISABLED_OPACITY = 0.5F;
	/** 上游 {@code SliderMixin}：滑条圆角 10、把手宽 8 / 圆角 4 / 上下各留 1。 */
	private static final float SLIDER_RADIUS = 10F;
	private static final float SLIDER_HANDLE_WIDTH = 8F;
	private static final float SLIDER_HANDLE_RADIUS = 4F;
	/** 上游 {@code CheckboxMixin}：方块 20 宽、圆角 7。 */
	private static final float CHECKBOX_SIZE = 20F;
	private static final float CHECKBOX_RADIUS = 7F;
	/**
	 * pose 缩放的钳制范围。本工程的 {@code ScreenMixin} 在界面打开时给整个界面压一个
	 * 等比缩放（{@code VisualScreenMotion}），玻璃的折射位移按屏幕像素算，所以要把它
	 * 乘进 pixelScale；上游只按窗口 GUI scale 算，在缩放动画里会略有偏差。
	 */
	private static final float MIN_POSE_SCALE = 0.25F;
	private static final float MAX_POSE_SCALE = 4F;

	private static final GlassRenderer RENDERER = new GlassRenderer();

	/** 出过错就永久回退，不每帧重试。 */
	private static boolean failed;
	/** 本趟屏幕渲染是否已经抓过背景。 */
	private static boolean captured;

	private GlassButtons()
	{}

	/** 由模组总线在着色器注册时调用。 */
	public static void installShaders(ShaderInstance glass, ShaderInstance blur)
	{
		RENDERER.release();
		captured = false;
		GlassRenderer.setShaders(glass, blur);
	}

	/**
	 * 资源重载/着色器重注册时调用：旧纹理不能与新着色器混用，所以先整体关掉（控件回到
	 * 原样），等新实例装好再打开。这不是失败，所以不置 {@link #failed}。
	 */
	public static void onShadersReloaded()
	{
		RENDERER.release();
		captured = false;
		GlassRenderer.setShaders(null, null);
	}

	/**
	 * 新的屏幕渲染趟次开始：下一块玻璃会重新抓一次背景。由
	 * {@code ScreenEvent.Render.Pre} 调用。
	 */
	public static void beginPass()
	{
		captured = false;
	}

	/**
	 * 把一块按钮画成玻璃面板（上游 {@code ButtonMixin}）。
	 *
	 * @param focus
	 *            0–1 的悬停/按下进度
	 * @param active
	 *            控件是否可用（不可用时整块变淡）
	 * @param alpha
	 *            控件自身的透明度（{@code AbstractWidget.alpha}）
	 * @return 是否真的画了玻璃；{@code false} 表示调用方应回退到扁平样式
	 */
	public static boolean renderButton(GuiGraphics graphics, float x, float y,
		float width, float height, float focus, boolean active, float alpha)
	{
		return panel(graphics, x, y, width, height, radiusFor(height), focus,
			opacityFor(active, alpha));
	}

	/**
	 * 把滑块画成玻璃（上游 {@code SliderMixin}）：整条是一条玻璃，把手是一块小玻璃，
	 * 位置按 {@code value} 在条内滑动。
	 */
	public static boolean renderSlider(GuiGraphics graphics, float x, float y,
		float width, float height, double value, float focus, boolean active,
		float alpha)
	{
		float opacity = clamp01(alpha);
		if(!canDraw(width, height, opacity))
			return false;

		try
		{
			if(!drawPanel(graphics, x, y, width, height, SLIDER_RADIUS, focus,
				opacity))
				return false;
			// 把手按上游的写法恒为 focused：它自己就是那点高光
			drawPanel(graphics,
				x + handleOffset(width, value), y + 1F, SLIDER_HANDLE_WIDTH,
				height - 2F, SLIDER_HANDLE_RADIUS, 1F, opacity);
			return true;

		}catch(RuntimeException | LinkageError e)
		{
			return fail(e);
		}
	}

	/**
	 * 把任意一块矩形画成玻璃（HUD 卡片、通知卡、面板用）。控件那几条各有各的策略
	 * （按钮半透明、滑块画两块、复选框是个方块），这里只画一块。
	 *
	 * @param radius
	 *            圆角，按短边一半钳制（着色器里也钳一道）
	 * @param focus
	 *            0–1 的高亮进度；HUD 卡片没有悬停态，传 0
	 * @param opacity
	 *            整体不透明度
	 * @return 是否真的画了玻璃；{@code false} 表示调用方应回退到自己的扁平画法
	 */
	public static boolean renderPanel(GuiGraphics graphics, float x, float y,
		float width, float height, float radius, float focus, float opacity)
	{
		return panel(graphics, x, y, width, height, radius, focus, opacity);
	}

	/**
	 * 把复选框画成玻璃方块（上游 {@code CheckboxMixin}）。只画方块本身：勾与标签由调用
	 * 方按上游的画法补上。
	 */
	public static boolean renderCheckbox(GuiGraphics graphics, float x, float y,
		float height, boolean focused, float alpha)
	{
		return panel(graphics, x, y, CHECKBOX_SIZE, height, CHECKBOX_RADIUS,
			focused ? 1F : 0F, clamp01(alpha));
	}

	/**
	 * 把列表滚动条画成细玻璃条（上游 {@code VanillaGlass.scrollbar}）。轨道用低透明度的
	 * 暗玻璃，滑块用接近不透明的高光玻璃，所以这里不复用悬停材质。
	 */
	public static boolean renderScrollbar(GuiGraphics graphics, float x, float y,
		float width, float height, boolean track, boolean disabled)
	{
		float opacity = 1F;
		if(!canDraw(width, height, opacity))
			return false;

		try
		{
			if(!captureIfNeeded(graphics))
				return false;
			Vector4f mouse = mouseInLocalSpace(graphics);
			RENDERER.panel(graphics, x, y, width, height,
				scrollbarStyle(width, height, track, disabled), mouse.x,
				mouse.y, pixelScaleOf(graphics));
			return true;

		}catch(RuntimeException | LinkageError e)
		{
			return fail(e);
		}
	}

	private static boolean panel(GuiGraphics graphics, float x, float y,
		float width, float height, float radius, float focus, float opacity)
	{
		if(!canDraw(width, height, opacity))
			return false;

		try
		{
			return drawPanel(graphics, x, y, width, height, radius, focus,
				opacity);

		}catch(RuntimeException | LinkageError e)
		{
			return fail(e);
		}
	}

	private static boolean drawPanel(GuiGraphics graphics, float x, float y,
		float width, float height, float radius, float focus, float opacity)
	{
		if(!captureIfNeeded(graphics))
			return false;
		Vector4f mouse = mouseInLocalSpace(graphics);
		RENDERER.panel(graphics, x, y, width, height,
			styleFor(radius, focus, opacity), mouse.x, mouse.y,
			pixelScaleOf(graphics));
		return true;
	}

	private static boolean canDraw(float width, float height, float opacity)
	{
		return !failed && width > 0 && height > 0 && opacity > 0
			&& GlassRenderer.available();
	}

	private static boolean captureIfNeeded(GuiGraphics graphics)
	{
		if(captured)
			return true;
		if(!RENDERER.capture(graphics, BLUR_RADIUS, pixelScaleOf(graphics)))
			return false;
		captured = true;
		return true;
	}

	private static boolean fail(Throwable e)
	{
		failed = true;
		RENDERER.release();
		System.err.println("[Glass] 玻璃控件绘制失败，已永久回退到原样: " + e);
		return false;
	}

	static GlassStyle styleFor(float radius, float focus, float opacity)
	{
		float clampedFocus = clamp01(focus);
		return new GlassStyle(radius, REFRACTION, FROST,
			BASE_TINT + FOCUS_TINT * clampedFocus,
			BASE_HIGHLIGHT + FOCUS_HIGHLIGHT * clampedFocus, clamp01(opacity));
	}

	/** 上游 {@code VanillaGlass.scrollbar} 的材质，与本类其它控件的材质不同。 */
	static GlassStyle scrollbarStyle(float width, float height, boolean track,
		boolean disabled)
	{
		return new GlassStyle(Math.min(width, height) * 0.5F, 0.6F, 0.85F,
			track ? 0.08F : 0.38F, 0.15F,
			track ? 0.22F : disabled ? 0.4F : 0.95F);
	}

	/** 滑块把手左边缘：条内可滑动的范围是 {@code width - 8}。 */
	static float handleOffset(float width, double value)
	{
		float clamped = (float)Math.max(0, Math.min(1, value));
		return clamped * Math.max(0, width - SLIDER_HANDLE_WIDTH);
	}

	static float radiusFor(float height)
	{
		return Math.max(0, Math.min(MAX_RADIUS, height * 0.5F));
	}

	static float opacityFor(boolean active, float alpha)
	{
		float clamped = clamp01(alpha);
		return active ? clamped : clamped * DISABLED_OPACITY;
	}

	static float pixelScaleFor(double guiScale, float poseScale)
	{
		if(!Double.isFinite(guiScale) || guiScale <= 0)
			return 1;
		float scale = (float)guiScale;
		if(Float.isFinite(poseScale) && poseScale > 0)
			scale *= Math.max(MIN_POSE_SCALE, Math.min(MAX_POSE_SCALE, poseScale));
		return scale;
	}

	private static float clamp01(float value)
	{
		return Math.max(0, Math.min(1, value));
	}

	private static float pixelScaleOf(GuiGraphics graphics)
	{
		return pixelScaleFor(
			Minecraft.getInstance().getWindow().getGuiScale(),
			poseScaleOf(graphics));
	}

	/** pose 里的等比缩放；非等比缩放上游不支持，这里只取 X 轴。 */
	private static float poseScaleOf(GuiGraphics graphics)
	{
		Matrix4f pose = graphics.pose().last().pose();
		return (float)pose.m00();
	}

	/**
	 * 鼠标在 {@code gui.pose()} 局部坐标里的位置：窗口像素坐标除以 GUI scale，再用
	 * pose 的逆矩阵变换回去（与上游 {@code VanillaGlass.panel} 一致）。
	 */
	private static Vector4f mouseInLocalSpace(GuiGraphics graphics)
	{
		Minecraft minecraft = Minecraft.getInstance();
		double guiScale = Math.max(1, minecraft.getWindow().getGuiScale());
		Vector4f point = new Vector4f(
			(float)(minecraft.mouseHandler.xpos() / guiScale),
			(float)(minecraft.mouseHandler.ypos() / guiScale), 0, 1);

		Matrix4f inverse = new Matrix4f(graphics.pose().last().pose());
		if(!inverse.isFinite())
			return new Vector4f(-10000, -10000, 0, 1);
		inverse.invert().transform(point);
		return point;
	}
}

/*
 * Copyright (c) 2014-2025 Wurst-Imperium and contributors.
 *
 * This source code is subject to the terms of the GNU General Public
 * License, version 3. If a copy of the GPL was not distributed with this
 * file, You can obtain one at: https://www.gnu.org/licenses/gpl-3.0.txt
 */
package net.wurstclient.background;

/**
 * 场景画布到屏幕的换算（纯数学，可单测）。
 *
 * <p>
 * Wallpaper Engine 的场景用正交投影：{@code general.orthogonalprojection} 给出
 * 作者画布的宽高（Persica 是 3840x2160），{@code zoom} 是 1.0，对象坐标以画布
 * 左上角为原点、y 向下，{@code alignment: center} 时 {@code origin} 是图层
 * 中心。</p>
 *
 * <p>
 * 屏幕不是 16:9 时按 <b>cover</b> 缩放（取宽高比里大的那个），这样画布一定铺满
 * 屏幕，不会出现黑边——和壁纸的语义一致。</p>
 *
 * <p>
 * 视差：Wallpaper Engine 里 {@code parallaxDepth} 是「鼠标移动时这一层跟着走
 * 多少」，0 完全不动、1 与鼠标 1:1。这里把屏幕像素位移取成
 * {@code -depth * amount * influence * 鼠标相对屏幕中心的偏移}，深度 0.05～0.12
 * 的场景就是一点轻微的错动。</p>
 *
 * <p>
 * <b>阻尼跟随</b>：深度这么小的时候，鼠标每动一个像素图层只该动零点零几像素，
 * 如果直接取整定位就会变成「攒够一格才跳一次」的台阶感。所以两件事一起做：
 * 目标位移按帧时间做指数逼近（时间常数来自场景的 {@code cameraparallaxdelay}），
 * 画的时候再把小数部分交给模型矩阵（见 {@code WeSceneWallpaper}）。</p>
 */
public final class WeSceneLayout
{
	/**
	 * {@code cameraparallaxdelay} 的最大时间常数。
	 *
	 * <p>
	 * Wallpaper Engine 这个字段是 0..1 的无量纲值（Persica 是 0.5），公开资料里
	 * 没有给出它到秒的换算，所以这里按经验映射：0 表示完全不平滑，1 表示
	 * {@value #MAX_PARALLAX_DELAY_SECONDS} 秒的时间常数（Persica 因此是
	 * 0.125 秒）。取值刻意偏小——阻尼太大会变成迟滞，那又是另一种不流畅。
	 * </p>
	 */
	public static final float MAX_PARALLAX_DELAY_SECONDS = 0.25F;

	/** 一帧最长按这么算：窗口卡住或切出去再回来时不要一步跳过去。 */
	private static final float MAX_FRAME_SECONDS = 0.1F;

	/** 屏幕上的一个矩形，单位是逻辑像素，可能超出屏幕。 */
	public record Rect(float x, float y, float width, float height)
	{
		public boolean isEmpty()
		{
			return width <= 0 || height <= 0;
		}
	}

	private WeSceneLayout()
	{
	}

	/** 画布铺满屏幕所需的比例，两个方向取大的那个。 */
	public static float coverScale(int canvasWidth, int canvasHeight, float zoom,
		int screenWidth, int screenHeight)
	{
		if(canvasWidth <= 0 || canvasHeight <= 0 || screenWidth <= 0
			|| screenHeight <= 0)
			return 0;

		float fit = Math.max(screenWidth / (float)canvasWidth,
			screenHeight / (float)canvasHeight);

		return fit * (zoom > 0 ? zoom : 1);
	}

	/**
	 * 一个图层在屏幕上的位置。
	 *
	 * @param textureWidth
	 *            贴图自身的宽，只在图层没写 {@code size}（{@code autosize}）
	 *            时用
	 * @param offsetX
	 *            视差带来的额外位移，屏幕像素
	 */
	public static Rect rect(WeScene.Layer layer, float scale, int canvasWidth,
		int canvasHeight, int screenWidth, int screenHeight, float textureWidth,
		float textureHeight, float offsetX, float offsetY)
	{
		if(layer == null || scale <= 0)
			return new Rect(0, 0, 0, 0);

		float width = (layer.sizeX() > 0 ? layer.sizeX() : textureWidth)
			* layer.scaleX() * scale;
		float height = (layer.sizeY() > 0 ? layer.sizeY() : textureHeight)
			* layer.scaleY() * scale;

		float centreX = screenWidth / 2F
			+ (layer.originX() - canvasWidth / 2F) * scale + offsetX;
		float centreY = screenHeight / 2F
			+ (layer.originY() - canvasHeight / 2F) * scale + offsetY;

		return new Rect(centreX - width / 2F, centreY - height / 2F, width,
			height);
	}

	/** 一层因为视差产生的屏幕位移。 */
	public static float parallaxOffset(float depth, float amount,
		float influence, float mouseOffset)
	{
		return -depth * amount * influence * mouseOffset;
	}

	/**
	 * 把当前值朝目标值推进一帧，用于视差的阻尼跟随。
	 *
	 * <p>
	 * 指数逼近：{@code alpha = 1 - exp(-dt / tau)}，所以结果与帧率无关——把一帧
	 * 拆成两半跑两次，和整帧跑一次得到同一个值（单测里就是这么验的）。
	 * </p>
	 *
	 * @param delay
	 *            场景里的 {@code cameraparallaxdelay}；{@code <= 0} 表示不平滑，
	 *            立刻贴到目标值
	 * @param deltaSeconds
	 *            距上一帧的秒数
	 */
	public static float approach(float current, float target, float deltaSeconds,
		float delay)
	{
		if(delay <= 0 || deltaSeconds <= 0)
			return delay <= 0 ? target : current;

		float tau = Math.min(delay, 1) * MAX_PARALLAX_DELAY_SECONDS;
		float dt = Math.min(deltaSeconds, MAX_FRAME_SECONDS);
		float alpha = 1 - (float)Math.exp(-dt / tau);
		return current + (target - current) * alpha;
	}
}

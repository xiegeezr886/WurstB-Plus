package net.wurstclient.compose;

import net.wurstclient.clickgui2.FlatRenderer;
import net.wurstclient.gui.visual.VisualTheme;

/**
 * HackAI {@code NotificationView}「流动渐变描边」的 1:1 移植。
 *
 * <p>渐变公式与 HackAI 完全一致：水平 {@code LinearGradient(shift-w, shift+w,
 * CLAMP)}，其中 {@code shift = flowOffset * width}，{@code flowOffset} 在 5s
 * 内从 0 到 2 线性循环。绿 {@code #00FF88} 为起点、青 {@code #00BFFF} 为
 * 终点，超出范围钳到端点色。</p>
 */
public final class FlowingGradient
{
	public static final long PERIOD = 5000;
	private static final int START = 0xFF00FF88;
	private static final int END = 0xFF00BFFF;

	private FlowingGradient()
	{}

	/** 0..2 的循环相位，对应 HackAI 的 {@code ValueAnimator.ofFloat(0, 2)}。 */
	public static float flow()
	{
		return (System.currentTimeMillis() % PERIOD) / (float)PERIOD * 2;
	}

	/**
	 * 生成卡片 {@code [x1, x1+width]} 的渐变取色函数，{@code alpha} 控制
	 * 整体透明度。取色公式即上面的 LinearGradient + CLAMP。
	 */
	public static FlatRenderer.GradientColorFn flowing(float x1, float width,
		float alpha)
	{
		float flow = flow();
		float safeWidth = Math.max(1, width);
		return x -> {
			float rel = (x - x1) / safeWidth;
			float t = Math.max(0, Math.min(1, (rel + 1 - flow) / 2));
			return VisualTheme.withAlpha(VisualTheme.mix(START, END, t),
				Math.round(alpha));
		};
	}
}

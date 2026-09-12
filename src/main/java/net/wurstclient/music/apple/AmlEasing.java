package net.wurstclient.music.apple;

/**
 * applemusic-like-lyrics {@code lyric-player/dom/lyric-line.ts} 的缓动函数移植。
 *
 * <p>{@code bezIn = cubic-bezier(0.2, 0.4, 0.58, 1.0)}，
 * {@code bezOut = cubic-bezier(0.3, 0, 0.58, 1.0)}，
 * 强调动画用 {@link #empEasing}：前半段 bezIn、后半段 bezOut 的镜像。</p>
 */
public final class AmlEasing
{
	private static final double EMP_EASING_MID = 0.5;

	private AmlEasing()
	{}

	/** 强调动画缓动，对应 {@code makeEmpEasing(0.5)}。 */
	public static double empEasing(double x)
	{
		double v = clamp01(x);
		return v < EMP_EASING_MID ? bezIn(v / EMP_EASING_MID)
			: 1 - bezOut((v - EMP_EASING_MID) / (1 - EMP_EASING_MID));
	}

	public static double bezIn(double x)
	{
		return cubicBezier(0.2, 0.4, 0.58, 1.0, x);
	}

	public static double bezOut(double x)
	{
		return cubicBezier(0.3, 0.0, 0.58, 1.0, x);
	}

	/** CSS {@code ease-out} = cubic-bezier(0, 0, 0.58, 1)，行内单词上浮用。 */
	public static double easeOut(double x)
	{
		return cubicBezier(0.0, 0.0, 0.58, 1.0, x);
	}

	/** 起步急促并按指数收敛，间奏点入场用。 */
	public static double easeOutExpo(double x)
	{
		return x >= 1 ? 1 : 1 - Math.pow(2, -10 * x);
	}

	/** 带过冲回弹，间奏点结束收缩用。 */
	public static double easeInOutBack(double x)
	{
		double c1 = 1.70158;
		double c2 = c1 * 1.525;
		return x < 0.5 ? Math.pow(2 * x, 2) * ((c2 + 1) * 2 * x - c2) / 2
			: (Math.pow(2 * x - 2, 2) * ((c2 + 1) * (x * 2 - 2) + c2) + 2) / 2;
	}

	/**
	 * 标准 cubic-bezier 求解：先按 x 用牛顿迭代 + 二分求出参数 t，再取 y。
	 */
	public static double cubicBezier(double x1, double y1, double x2, double y2,
		double x)
	{
		if(x <= 0)
			return 0;
		if(x >= 1)
			return 1;
		double t = x;
		for(int i = 0; i < 8; i++)
		{
			double current = bezierAxis(t, x1, x2) - x;
			if(Math.abs(current) < 1e-7)
				return bezierAxis(t, y1, y2);
			double slope = bezierAxisDerivative(t, x1, x2);
			if(Math.abs(slope) < 1e-7)
				break;
			t -= current / slope;
		}
		double low = 0;
		double high = 1;
		t = x;
		for(int i = 0; i < 32; i++)
		{
			double current = bezierAxis(t, x1, x2);
			if(Math.abs(current - x) < 1e-7)
				break;
			if(current < x)
				low = t;
			else
				high = t;
			t = (low + high) / 2;
		}
		return bezierAxis(t, y1, y2);
	}

	private static double bezierAxis(double t, double p1, double p2)
	{
		double u = 1 - t;
		return 3 * u * u * t * p1 + 3 * u * t * t * p2 + t * t * t;
	}

	private static double bezierAxisDerivative(double t, double p1, double p2)
	{
		double u = 1 - t;
		return 3 * u * u * p1 + 6 * u * t * (p2 - p1) + 3 * t * t * (1 - p2);
	}

	private static double clamp01(double value)
	{
		return Math.max(0, Math.min(1, value));
	}
}

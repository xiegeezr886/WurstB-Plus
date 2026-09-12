package net.wurstclient.music.apple;

/**
 * CSS {@code transition} 的等价实现。
 *
 * <p>AMLL 用 CSS 过渡而非弹簧处理透明度、模糊与遮罩 alpha：</p>
 * <ul>
 * <li>{@code .lyricLineWrapper} → {@code opacity 0.4s ease, filter 0.4s ease}</li>
 * <li>{@code .lyricLine} → 遮罩 alpha，激活时 0.3s、退回时 0.45s，均 ease-out</li>
 * </ul>
 *
 * <p>目标值中途改变时从**当前值**重新起算，与 CSS 的行为一致。</p>
 */
public final class AmlTween
{
	private double current;
	private double from;
	private double target;
	private double elapsed;
	private double duration;
	private boolean animating;
	private final boolean easeOut;

	/** @param easeOut true 用 {@code ease-out}，false 用 {@code ease} */
	public AmlTween(double initial, boolean easeOut)
	{
		current = initial;
		from = initial;
		target = initial;
		this.easeOut = easeOut;
	}

	public void setTarget(double value, double seconds)
	{
		if(Math.abs(value - target) < 1e-9)
			return;
		target = value;
		if(seconds <= 0)
		{
			current = value;
			from = value;
			animating = false;
			return;
		}
		from = current;
		elapsed = 0;
		duration = seconds;
		animating = true;
	}

	public void update(double delta)
	{
		if(!animating)
			return;
		elapsed += delta;
		double t = elapsed / duration;
		if(t >= 1)
		{
			current = target;
			animating = false;
			return;
		}
		double eased = easeOut ? AmlEasing.easeOut(t)
			: AmlEasing.cubicBezier(0.25, 0.1, 0.25, 1, t);
		current = from + (target - from) * eased;
	}

	public double get()
	{
		return current;
	}

	public boolean isAnimating()
	{
		return animating;
	}

	/** 立即跳到目标值，用于重建视图等需要瞬时对齐的场合。 */
	public void snapTo(double value)
	{
		current = value;
		from = value;
		target = value;
		animating = false;
	}
}

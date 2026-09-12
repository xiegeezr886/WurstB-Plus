package net.wurstclient.music.apple;

/**
 * applemusic-like-lyrics {@code lyric-player/dom/interlude-dots.ts} 的移植。
 *
 * <p>间奏三点：入场 easeOutExpo 放大、正弦呼吸、逐点点亮、结束 easeInOutBack
 * 收缩回弹。</p>
 */
public final class InterludeDots
{
	private static final double TARGET_BREATHE_MS = 4500;

	private boolean enabled;
	private boolean playing = true;
	private long animStartMs;
	private long animEndMs;
	private float scale = 1;
	private float globalOpacity;
	private final float[] dotOpacity = {0.25F, 0.25F, 0.25F};

	public void setPlaying(boolean playing)
	{
		this.playing = playing;
	}

	public void clear()
	{
		enabled = false;
		animStartMs = 0;
		animEndMs = 0;
		scale = 0;
		globalOpacity = 0;
	}

	/**
	 * @param startMs 间奏真实起点（仅作参考）
	 * @param endMs 间奏结束
	 * @param nowMs 当前播放时间；forceReset 时作为动画锚点
	 */
	public void setInterlude(long startMs, long endMs, long nowMs,
		boolean forceReset)
	{
		if(endMs <= startMs)
		{
			clear();
			return;
		}
		boolean isNew = animEndMs != endMs;
		if(forceReset || isNew || !enabled)
		{
			animStartMs = nowMs;
			animEndMs = endMs;
		}
		enabled = true;
	}

	public void update(long nowMs)
	{
		if(!enabled)
			return;
		if(!playing)
			return;
		double interludeDuration = animEndMs - animStartMs;
		double currentDuration = nowMs - animStartMs;
		if(interludeDuration <= 0 || currentDuration > interludeDuration)
		{
			scale = 0;
			globalOpacity = 0;
			dotOpacity[0] = dotOpacity[1] = dotOpacity[2] = 0;
			return;
		}

		double breatheDuration = interludeDuration
			/ Math.ceil(interludeDuration / TARGET_BREATHE_MS);
		double nextScale = Math.sin(1.5 * Math.PI
			- (currentDuration / breatheDuration) * 2 * Math.PI) / 20 + 1;
		double opacity = 1;

		if(currentDuration < 2000)
			nextScale *= easeOutExpo(currentDuration / 2000);
		if(currentDuration < 500)
			opacity = 0;
		else if(currentDuration < 1000)
			opacity *= (currentDuration - 500) / 500;

		if(interludeDuration - currentDuration < 750)
			nextScale *= 1 - easeInOutBack(
				(750 - (interludeDuration - currentDuration)) / 750 / 2);
		if(interludeDuration - currentDuration < 375)
			opacity *= clamp01((interludeDuration - currentDuration) / 375);

		double dotsDuration = Math.max(0, interludeDuration - 750);
		scale = (float)(Math.max(0, nextScale) * 0.7);
		globalOpacity = (float)clamp01(opacity);

		dotOpacity[0] = dotsDuration > 0
			? (float)clamp(0.25, currentDuration * 3 / dotsDuration * 0.75, 1)
			: 0.25F;
		dotOpacity[1] = dotsDuration > 0
			? (float)clamp(0.25,
				(currentDuration - dotsDuration / 3) * 3 / dotsDuration * 0.75,
				1)
			: 0.25F;
		dotOpacity[2] = dotsDuration > 0
			? (float)clamp(0.25,
				(currentDuration - dotsDuration / 3 * 2) * 3 / dotsDuration
					* 0.75,
				1)
			: 0.25F;
	}

	public boolean isVisible()
	{
		return enabled && globalOpacity > 0.01F && scale > 0.01F;
	}

	public float scale()
	{
		return scale;
	}

	public float dotOpacity(int index)
	{
		return globalOpacity * dotOpacity[index];
	}

	static double easeInOutBack(double x)
	{
		double c1 = 1.70158;
		double c2 = c1 * 1.525;
		return x < 0.5
			? Math.pow(2 * x, 2) * ((c2 + 1) * 2 * x - c2) / 2
			: (Math.pow(2 * x - 2, 2) * ((c2 + 1) * (x * 2 - 2) + c2) + 2) / 2;
	}

	static double easeOutExpo(double x)
	{
		return x >= 1 ? 1 : 1 - Math.pow(2, -10 * x);
	}

	private static double clamp01(double value)
	{
		return Math.max(0, Math.min(1, value));
	}

	private static double clamp(double min, double value, double max)
	{
		return Math.max(min, Math.min(max, value));
	}
}

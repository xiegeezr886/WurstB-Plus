package net.wurstclient.compose;

/**
 * Compose {@code animateFloatAsState} 的纯 Java 等价物。
 *
 * <p>指数平滑逼近目标值，用于透明度、位移、缩放的动画状态。</p>
 */
public final class AnimFloat
{
	private final float speed;
	private float current;
	private float target;

	public AnimFloat(float initialValue, float speed)
	{
		current = initialValue;
		target = initialValue;
		this.speed = speed;
	}

	public void snap(float value)
	{
		current = value;
		target = value;
	}

	/** 设置目标值。 */
	public void set(float target)
	{
		this.target = target;
	}

	public float get()
	{
		return current;
	}

	/** 每帧推进动画。dt 为秒。返回是否仍在动画中。 */
	public boolean update(float dt)
	{
		if(Math.abs(current - target) < 0.002F)
		{
			current = target;
			return false;
		}
		current += (target - current) * (1 - (float)Math.exp(-speed * dt));
		if(Math.abs(current - target) < 0.002F)
			current = target;
		return true;
	}
}

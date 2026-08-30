package net.wurstclient.music.apple;

/**
 * applemusic-like-lyrics {@code utils/spring.ts} 的 1:1 Java 移植。
 *
 * <p>解析解弹簧：阻尼比 ≥ 1 时过阻尼指数收敛，&lt; 1 时欠阻尼余弦振荡；
 * 支持延迟队列参数与延迟目标位置；arrived 判定位置/速度/加速度三重
 * 收敛。</p>
 */
public final class Spring
{
	private double currentPosition;
	private double targetPosition;
	private double currentTime;
	private double stiffness = 100;
	private double damping = 10;
	private double mass = 1;
	private boolean soft;
	private Solver solver;
	private Solver velocity;
	private Solver acceleration;

	private double[] queueParams;
	private double queueParamsTime;
	private boolean hasQueueParams;

	private double queuePosition;
	private double queuePositionTime;
	private boolean hasQueuePosition;

	public Spring(double currentPosition)
	{
		targetPosition = currentPosition;
		this.currentPosition = targetPosition;
		solver = t -> targetPosition;
		velocity = t -> 0;
		acceleration = t -> 0;
	}

	private interface Solver
	{
		double solve(double t);
	}

	public boolean arrived()
	{
		return Math.abs(targetPosition - currentPosition) < 0.01
			&& Math.abs(velocity.solve(currentTime)) < 0.01
			&& Math.abs(acceleration.solve(currentTime)) < 0.01
			&& !hasQueueParams && !hasQueuePosition;
	}

	public void setPosition(double target)
	{
		targetPosition = target;
		currentPosition = target;
		solver = t -> targetPosition;
		velocity = t -> 0;
		acceleration = t -> 0;
	}

	public void update(double delta)
	{
		currentTime += delta;
		currentPosition = solver.solve(currentTime);
		if(hasQueueParams)
		{
			queueParamsTime -= delta;
			if(queueParamsTime <= 0)
			{
				hasQueueParams = false;
				updateParams(queueParams[0], queueParams[1], queueParams[2],
					queueParams[3] > 0);
			}
		}
		if(hasQueuePosition)
		{
			queuePositionTime -= delta;
			if(queuePositionTime <= 0)
			{
				hasQueuePosition = false;
				setTargetPosition(queuePosition, 0);
			}
		}
		if(arrived())
			setPosition(targetPosition);
	}

	public void updateParams(double stiffness, double damping, double mass,
		boolean soft)
	{
		this.stiffness = stiffness;
		this.damping = damping;
		this.mass = mass;
		this.soft = soft;
		resetSolver();
	}

	public void setTargetPosition(double target, double delay)
	{
		if(delay <= 0 && Math.abs(targetPosition - target) < 0.001)
		{
			hasQueuePosition = false;
			return;
		}
		if(delay > 0)
		{
			queuePosition = target;
			queuePositionTime = delay;
			hasQueuePosition = true;
		}else
		{
			hasQueuePosition = false;
			targetPosition = target;
			resetSolver();
		}
	}

	public double getCurrentPosition()
	{
		return currentPosition;
	}

	private void resetSolver()
	{
		double currentVelocity = velocity.solve(currentTime);
		currentTime = 0;
		solver = solveSpring(currentPosition, currentVelocity, targetPosition,
			stiffness, damping, mass, soft);
		velocity = derivative(solver);
		acceleration = derivative(velocity);
	}

	private static Solver solveSpring(double from, double velocity, double to,
		double stiffness, double damping, double mass, boolean soft)
	{
		double delta = to - from;
		if(soft || 1 <= damping / (2 * Math.sqrt(stiffness * mass)))
		{
			double angularFrequency = -Math.sqrt(stiffness / mass);
			double leftover = -angularFrequency * delta - velocity;
			return t -> to - (delta + t * leftover)
				* Math.exp(t * angularFrequency);
		}
		double dampingFrequency =
			Math.sqrt(4 * mass * stiffness - damping * damping);
		double leftover =
			(damping * delta - 2 * mass * velocity) / dampingFrequency;
		double dfm = 0.5 * dampingFrequency / mass;
		double dm = -0.5 * damping / mass;
		return t -> to - (Math.cos(t * dfm) * delta
			+ Math.sin(t * dfm) * leftover) * Math.exp(t * dm);
	}

	/** 解析解的一阶导（速度）。 */
	private static Solver derivative(Solver solver)
	{
		double h = 1e-4;
		return t -> (solver.solve(t + h) - solver.solve(t - h)) / (2 * h);
	}
}

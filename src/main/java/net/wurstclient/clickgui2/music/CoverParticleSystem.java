package net.wurstclient.clickgui2.music;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import net.minecraft.client.gui.GuiGraphics;

/**
 * MineRadio {@code 00-pointer-cover-particles} 的 2D 移植。
 *
 * <p>鼠标在唱片封面区域按住/划过时，从指针位置迸发彩色粒子：随机初速、
 * 重力下落、寿命淡出（后 40% 平滑消失）、随寿命缩小。配色以封面强调色为主
 * （60%）、白色 30%、随机亮色 10%，与 MineRadio 的 cover-colored 粒子一致。</p>
 */
public final class CoverParticleSystem
{
	private static final int MAX_PARTICLES = 96;
	private static final float GRAVITY = 150F;

	private final List<Particle> particles = new ArrayList<>();
	private final Random random = new Random();
	private long lastUpdateNanos = System.nanoTime();

	/** 从指定位置迸发 count 个粒子。accent 为封面强调色。 */
	public void spawnBurst(double x, double y, int accentColor, int count)
	{
		for(int index = 0; index < count
			&& particles.size() < MAX_PARTICLES; index++)
		{
			double angle = random.nextDouble() * Math.PI * 2;
			double speed = 26 + random.nextDouble() * 70;
			float life = 0.45F + random.nextFloat() * 0.5F;
			int size = 1 + random.nextInt(3);
			particles.add(new Particle((float)x, (float)y,
				(float)(Math.cos(angle) * speed),
				(float)(Math.sin(angle) * speed - 24), life, size,
				particleColor(accentColor)));
		}
	}

	public void clear()
	{
		particles.clear();
	}

	public void update()
	{
		long now = System.nanoTime();
		float dt = Math.min(0.05F,
			(now - lastUpdateNanos) / 1_000_000_000F);
		lastUpdateNanos = now;
		for(Particle particle : particles)
		{
			particle.x += particle.vx * dt;
			particle.y += particle.vy * dt;
			particle.vy += GRAVITY * dt;
			particle.age += dt;
		}
		particles.removeIf(particle -> particle.age >= particle.life);
	}

	public void render(GuiGraphics graphics)
	{
		for(Particle particle : particles)
		{
			float lifeN = particle.age / particle.life;
			float fade = 1 - smoothstep(0.6F, 1F, lifeN);
			int alpha = Math.round(255 * fade);
			if(alpha <= 0)
				continue;
			int color = particle.color & 0x00FFFFFF | alpha << 24;
			int size = Math.max(1, Math.round(particle.size
				* (1 - lifeN * 0.5F)));
			graphics.fill(Math.round(particle.x - size),
				Math.round(particle.y - size), Math.round(particle.x + size),
				Math.round(particle.y + size), color);
		}
	}

	private int particleColor(int accent)
	{
		float roll = random.nextFloat();
		if(roll < 0.6F)
			return accent;
		if(roll < 0.9F)
			return 0xFFFFFFFF;
		int red = 170 + random.nextInt(86);
		int green = 170 + random.nextInt(86);
		int blue = 170 + random.nextInt(86);
		return 0xFF000000 | red << 16 | green << 8 | blue;
	}

	private static float smoothstep(float edge0, float edge1, float value)
	{
		float x = Math.max(0, Math.min(1, (value - edge0) / (edge1 - edge0)));
		return x * x * (3 - 2 * x);
	}

	private static final class Particle
	{
		float x;
		float y;
		float vx;
		float vy;
		final float life;
		float age;
		final float size;
		final int color;

		Particle(float x, float y, float vx, float vy, float life,
			float size, int color)
		{
			this.x = x;
			this.y = y;
			this.vx = vx;
			this.vy = vy;
			this.life = life;
			this.size = size;
			this.color = color;
		}
	}
}

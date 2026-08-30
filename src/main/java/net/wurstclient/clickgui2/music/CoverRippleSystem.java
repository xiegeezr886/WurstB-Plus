package net.wurstclient.clickgui2.music;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.client.gui.GuiGraphics;
import net.wurstclient.clickgui2.FlatRenderer;

/**
 * MineRadio {@code 15-ripples-cover-depth} 的 2D 移植。
 *
 * <p>按住唱片封面时，从按住点泛起同心圆环波纹：半径匀速扩散、透明度随
 * 扩散衰减，模拟水面涟漪。与 {@link CoverParticleSystem} 搭配构成完整的
 * MineRadio 封面交互。</p>
 */
public final class CoverRippleSystem
{
	private static final float RIPPLE_SPEED = 90F;
	private static final float MAX_RADIUS = 40F;

	private final List<Ripple> ripples = new ArrayList<>();
	private long lastUpdateNanos = System.nanoTime();

	/** 从指定位置产生一个波纹。 */
	public void spawnRipple(double x, double y, int color)
	{
		ripples.add(new Ripple((float)x, (float)y, 4F, color));
	}

	public void clear()
	{
		ripples.clear();
	}

	public void update()
	{
		long now = System.nanoTime();
		float dt = Math.min(0.05F,
			(now - lastUpdateNanos) / 1_000_000_000F);
		lastUpdateNanos = now;
		for(Ripple ripple : ripples)
			ripple.radius += RIPPLE_SPEED * dt;
		ripples.removeIf(ripple -> ripple.radius >= MAX_RADIUS);
	}

	public void render(GuiGraphics graphics)
	{
		for(Ripple ripple : ripples)
		{
			float lifeN = ripple.radius / MAX_RADIUS;
			float alpha = (1 - lifeN) * 0.65F;
			if(alpha <= 0.01F)
				continue;
			int r = Math.round(ripple.radius);
			int color = ripple.color & 0x00FFFFFF
				| Math.round(alpha * 255) << 24;
			FlatRenderer.drawRoundedOutline(graphics,
				Math.round(ripple.x) - r, Math.round(ripple.y) - r,
				Math.round(ripple.x) + r, Math.round(ripple.y) + r, r, color);
		}
	}

	private static final class Ripple
	{
		final float x;
		final float y;
		float radius;
		final int color;

		Ripple(float x, float y, float radius, int color)
		{
			this.x = x;
			this.y = y;
			this.radius = radius;
			this.color = color;
		}
	}
}

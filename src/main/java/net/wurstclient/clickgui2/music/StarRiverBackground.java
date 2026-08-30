package net.wurstclient.clickgui2.music;

import java.util.Random;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.util.Mth;

/**
 * MineRadio {@code 03-lyrics-star-river} 的 2D 移植。
 *
 * <p>歌词区背景的星河流：一簇缓慢漂移、明暗闪烁的星点。播放时亮度随
 * 模拟的音频能量脉动（treble/beat 简化），暂停时整体转暗。星星在区域内
 * 漂移并回绕，营造 MineRadio 歌词舞台的沉浸背景。</p>
 */
public final class StarRiverBackground
{
	private static final int STAR_COUNT = 42;

	private final Star[] stars = new Star[STAR_COUNT];
	private final Random random = new Random();
	private long lastUpdateNanos = System.nanoTime();

	public StarRiverBackground()
	{
		for(int index = 0; index < STAR_COUNT; index++)
		{
			stars[index] = new Star(random.nextFloat() * 2 - 1,
				random.nextFloat() * 2 - 1,
				4 + random.nextFloat() * 10,
				(float)(Math.random() * Math.PI * 2),
				0.4F + random.nextFloat() * 0.8F);
		}
	}

	public void update(float playingEnergy)
	{
		long now = System.nanoTime();
		float dt = Math.min(0.05F,
			(now - lastUpdateNanos) / 1_000_000_000F);
		lastUpdateNanos = now;
		float speed = 0.06F + playingEnergy * 0.05F;
		for(Star star : stars)
		{
			star.x += (float)Math.cos(star.angle) * speed * dt * 4;
			star.y += (float)Math.sin(star.angle) * speed * dt * 4;
			if(star.x < -1.1F)
				star.x = 1.1F;
			if(star.x > 1.1F)
				star.x = -1.1F;
			if(star.y < -1.1F)
				star.y = 1.1F;
			if(star.y > 1.1F)
				star.y = -1.1F;
			star.phase += dt * star.twinkleSpeed;
		}
	}

	/**
	 * 在区域内绘制星河流。区域为绝对屏幕坐标。
	 *
	 * @param playingEnergy 0..1，播放时的音频能量（影响亮度与速度）
	 */
	public void render(GuiGraphics graphics, int left, int top, int right,
		int bottom, float playingEnergy)
	{
		if(playingEnergy <= 0.01F)
			return;
		float width = right - left;
		float height = bottom - top;
		long now = System.currentTimeMillis();
		float beat = 0.5F + 0.5F
			* (float)Math.sin(now / 420D) * playingEnergy;
		for(Star star : stars)
		{
			int x = Math.round(left + (star.x + 1) * 0.5F * width);
			int y = Math.round(top + (star.y + 1) * 0.5F * height);
			float twinkle = 0.6F + 0.4F
				* (float)Math.sin(star.phase + now / 900D);
			float alpha = playingEnergy * (0.25F + 0.55F * twinkle)
				* (0.8F + 0.2F * beat);
			int size = Math.max(1, Math.round(star.size * (0.7F
				+ 0.5F * twinkle + 0.3F * beat)));
			int color = 0x00FFFFFF | Math.round(Mth.clamp(alpha, 0, 1)
				* 255) << 24;
			graphics.fill(x, y, x + size, y + size, color);
		}
	}

	private static final class Star
	{
		float x;
		float y;
		final float size;
		final float angle;
		float phase;
		final float twinkleSpeed;

		Star(float x, float y, float size, float angle, float twinkleSpeed)
		{
			this.x = x;
			this.y = y;
			this.size = size;
			this.angle = angle;
			this.phase = 0;
			this.twinkleSpeed = twinkleSpeed;
		}
	}
}

package net.wurstclient.compose;

import java.util.List;

/**
 * SuperSoftClient {@code Hud.getModuleColor} 的等价物：模块列表 5 种
 * 颜色模式（Rainbow / Static / Fade / Gradient / Wave）的逐项颜色计算。
 */
public final class ModuleColors
{
	public enum Mode
	{
		RAINBOW, STATIC, FADE, GRADIENT, WAVE
	}

	private Mode mode = Mode.STATIC;
	private int staticColor = 0xFFFFFFFF;
	private int gradientStart = 0xFF007CFF;
	private int gradientEnd = 0xFF8CC8FF;
	private int waveColor = 0xFF007CFF;
	private float rainbowSpeed = 3;
	private float rainbowSaturation = 0.6F;
	private float rainbowBrightness = 1;
	private float rainbowOffset = 0.05F;
	private float waveSpeed = 2;

	public ModuleColors mode(Mode mode)
	{
		this.mode = mode;
		return this;
	}

	public ModuleColors staticColor(int color)
	{
		staticColor = color;
		return this;
	}

	public ModuleColors gradient(int start, int end)
	{
		gradientStart = start;
		gradientEnd = end;
		return this;
	}

	public ModuleColors wave(int color, float speed)
	{
		waveColor = color;
		waveSpeed = speed;
		return this;
	}

	public ModuleColors rainbow(float speed, float saturation, float brightness,
		float offset)
	{
		rainbowSpeed = speed;
		rainbowSaturation = saturation;
		rainbowBrightness = brightness;
		rainbowOffset = offset;
		return this;
	}

	/** 计算第 index 项（共 total 项）的颜色。timeMs 为当前毫秒。 */
	public int colorFor(int index, int total, long timeMs)
	{
		return switch(mode)
		{
			case RAINBOW -> {
				float hue = ((timeMs / 1000F * rainbowSpeed / 3F)
					+ index * rainbowOffset) % 1F;
				yield hsvToRgb(hue, rainbowSaturation, rainbowBrightness);
			}
			case STATIC -> staticColor;
			case FADE -> {
				float alpha = (float)(Math.sin(timeMs / 1000D + index * 0.3)
					* 0.3 + 0.7);
				alpha = Math.max(0.4F, Math.min(1, alpha));
				yield 0xFFFFFFFF & 0x00FFFFFF
					| Math.round(alpha * 255) << 24;
			}
			case GRADIENT -> {
				float fraction = total > 1 ? index / (float)(total - 1) : 0;
				yield lerpColor(gradientStart, gradientEnd, fraction);
			}
			case WAVE -> {
				float wave = (float)(Math.sin(timeMs / (500D / waveSpeed)
					+ index * 0.5) * 0.3 + 0.7);
				yield scaleRgb(waveColor, wave);
			}
		};
	}

	private static int hsvToRgb(float hue, float saturation, float brightness)
	{
		int sector = (int)Math.floor(hue * 6) % 6;
		float f = hue * 6 - (float)Math.floor(hue * 6);
		float p = brightness * (1 - saturation);
		float q = brightness * (1 - f * saturation);
		float t = brightness * (1 - (1 - f) * saturation);
		float red;
		float green;
		float blue;
		switch(sector)
		{
			case 0 -> {red = brightness; green = t; blue = p;}
			case 1 -> {red = q; green = brightness; blue = p;}
			case 2 -> {red = p; green = brightness; blue = t;}
			case 3 -> {red = p; green = q; blue = brightness;}
			case 4 -> {red = t; green = p; blue = brightness;}
			default -> {red = brightness; green = p; blue = q;}
		}
		return 0xFF000000 | Math.round(red * 255) << 16
			| Math.round(green * 255) << 8 | Math.round(blue * 255);
	}

	private static int lerpColor(int start, int end, float fraction)
	{
		int ar = start >> 16 & 0xFF, ag = start >> 8 & 0xFF;
		int ab = start & 0xFF;
		int br = end >> 16 & 0xFF, bg = end >> 8 & 0xFF;
		int bb = end & 0xFF;
		return 0xFF000000 | (int)(ar + (br - ar) * fraction) << 16
			| (int)(ag + (bg - ag) * fraction) << 8
			| (int)(ab + (bb - ab) * fraction);
	}

	private static int scaleRgb(int color, float scale)
	{
		float clamped = Math.max(0, Math.min(1, scale));
		int red = Math.round((color >> 16 & 0xFF) * clamped);
		int green = Math.round((color >> 8 & 0xFF) * clamped);
		int blue = Math.round((color & 0xFF) * clamped);
		return 0xFF000000 | red << 16 | green << 8 | blue;
	}

	/** 供外部一次性构建颜色列表。 */
	public void fillColors(List<Integer> out, long timeMs)
	{
		int total = out.size();
		for(int index = 0; index < total; index++)
			out.set(index, colorFor(index, total, timeMs));
	}
}

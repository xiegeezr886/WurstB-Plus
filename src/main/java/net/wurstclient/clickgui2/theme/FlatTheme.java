package net.wurstclient.clickgui2.theme;

public final class FlatTheme
{
	private final float[] background = new float[3];
	private final float[] accent = new float[3];
	private int textColor;
	private float opacity;
	private float tooltipOpacity;

	public void update(float[] background, float[] accent, int textColor,
		float opacity, float tooltipOpacity)
	{
		System.arraycopy(background, 0, this.background, 0, 3);
		System.arraycopy(accent, 0, this.accent, 0, 3);
		this.textColor = textColor;
		this.opacity = clamp01(opacity);
		this.tooltipOpacity = clamp01(tooltipOpacity);
	}

	public int backdropTop()
	{
		return 0x18000000;
	}

	public int backdropBottom()
	{
		return 0x18000000;
	}

	public int windowFill(boolean focused)
	{
		return mix(focused ? 0.035F : 0, 1);
	}

	public int windowBody()
	{
		return background(1);
	}

	public int titleFill(boolean focused)
	{
		return mix(focused ? 0.055F : 0.02F, 1);
	}

	public int popupFill()
	{
		return mix(0.025F, 1);
	}

	public int tooltipFill()
	{
		return mix(0.02F, Math.max(0.92F, tooltipOpacity));
	}

	public int controlFill(float hover, boolean active)
	{
		if(active)
			return accent(1);

		float hoverAmount = clamp01(hover);
		return mix(hoverAmount * 0.035F, 1);
	}

	public int railFill()
	{
		return mix(0.03F, 1);
	}

	public int controlBorder(float hover, boolean active)
	{
		float alpha = active ? 1 : 0.18F + clamp01(hover) * 0.24F;
		return accent(alpha);
	}

	public int progressFill(float hover)
	{
		return accent(0.82F + clamp01(hover) * 0.18F);
	}

	public int border(boolean focused)
	{
		return focused ? accent(0.72F) : 0xFF303030;
	}

	public int highlight(float alpha)
	{
		return color(0.5F, 0.58F, 0.68F, alpha);
	}

	public int shadow(float alpha)
	{
		return color(0, 0, 0, alpha * 0.7F);
	}

	public int background(float alpha)
	{
		return color(background, alpha);
	}

	public int accent(float alpha)
	{
		return color(accent, alpha);
	}

	public int mix(float accentWeight, float alpha)
	{
		float weight = clamp01(accentWeight);
		float inverse = 1 - weight;
		return color(background[0] * inverse + accent[0] * weight,
			background[1] * inverse + accent[1] * weight,
			background[2] * inverse + accent[2] * weight, alpha);
	}

	public int text(float alpha)
	{
		int a = Math.round(clamp01(alpha) * 255);
		return a << 24 | textColor & 0xFFFFFF;
	}

	public float[] background()
	{
		return background;
	}

	public float[] accent()
	{
		return accent;
	}

	public int text()
	{
		return textColor;
	}

	public float opacity()
	{
		return opacity;
	}

	public float tooltipOpacity()
	{
		return tooltipOpacity;
	}

	private static int color(float[] rgb, float alpha)
	{
		return color(rgb[0], rgb[1], rgb[2], alpha);
	}

	private static int color(float red, float green, float blue, float alpha)
	{
		int a = Math.round(clamp01(alpha) * 255);
		int r = Math.round(clamp01(red) * 255);
		int g = Math.round(clamp01(green) * 255);
		int b = Math.round(clamp01(blue) * 255);
		return a << 24 | r << 16 | g << 8 | b;
	}

	private static float clamp01(float value)
	{
		return Math.max(0, Math.min(1, value));
	}
}

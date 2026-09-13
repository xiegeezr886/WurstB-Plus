/*
 * Copyright (c) 2014-2025 Wurst-Imperium and contributors.
 *
 * This source code is subject to the terms of the GNU General Public
 * License, version 3. If a copy of the GPL was not distributed with this
 * file, You can obtain one at: https://www.gnu.org/licenses/gpl-3.0.txt
 */
package net.wurstclient.twilight;

/**
 * Derives the accent colour of the Twilight Echo interface from an album cover.
 *
 * <p>
 * The reference implementation does not use a fixed accent: the play button and
 * the progress bar of the screenshots are purple on one page and green on
 * another, while the static theme token is the blue {@code #2563eb}. This class
 * reproduces that behaviour: it picks the dominant vivid hue of the cover and
 * normalises it so that the result is always usable as an accent on the light
 * and the dark theme.
 *
 * <p>
 * Deliberately free of Minecraft types, so it can be unit tested with plain
 * pixel arrays.
 */
public final class TwilightAccent
{
	/** {@code --te-navigation-active-text}, the fallback accent. */
	public static final int DEFAULT_ACCENT = 0xFF2563EB;
	
	/** Tint alpha of {@code --te-navigation-active}. */
	public static final float SOFT_ALPHA = 0.08F;
	
	private static final float MIN_COVER_SATURATION = 0.15F;
	private static final float MIN_COVER_LIGHTNESS = 0.08F;
	private static final int HUE_BUCKETS = 24;
	private static final int MIN_ALPHA = 128;
	
	private TwilightAccent()
	{
		
	}
	
	/**
	 * The colours derived from one cover.
	 */
	public static final class Palette
	{
		/** The accent used for the play button, the progress bar and links. */
		public final int accent;
		
		/** A darker accent, for accent coloured text on a light background. */
		public final int accentStrong;
		
		/** The accent at {@link #SOFT_ALPHA}, e.g. the "now playing" chip. */
		public final int accentSoft;
		
		/** Start of the hero card gradient, a very light accent tint. */
		public final int heroFrom;
		
		/** End of the hero card gradient. */
		public final int heroTo;
		
		/** Icon and label colour on top of {@link #accent}. */
		public final int onAccent;
		
		public final float hue;
		public final float saturation;
		public final float lightness;
		
		/** Whether the accent was taken from a cover or fell back. */
		public final boolean fromCover;
		
		private Palette(int accent, int accentStrong, int accentSoft,
			int heroFrom, int heroTo, int onAccent, float hue, float saturation,
			float lightness, boolean fromCover)
		{
			this.accent = accent;
			this.accentStrong = accentStrong;
			this.accentSoft = accentSoft;
			this.heroFrom = heroFrom;
			this.heroTo = heroTo;
			this.onAccent = onAccent;
			this.hue = hue;
			this.saturation = saturation;
			this.lightness = lightness;
			this.fromCover = fromCover;
		}
		
		public String describe()
		{
			return "accent " + hex(accent) + " (hue " + Math.round(hue)
				+ ", sat " + Math.round(saturation * 100) + "%, light "
				+ Math.round(lightness * 100) + "%, "
				+ (fromCover ? "from cover" : "fallback") + ")";
		}
	}
	
	/**
	 * @return the palette of the given ARGB pixels, or the fallback palette if
	 *         the image has no usable colour.
	 */
	public static Palette fromPixels(int[] argb, int count)
	{
		return fromPixels(argb, count, false);
	}
	
	/**
	 * @param dark
	 *            whether the palette is derived for the dark theme, which wants
	 *            a lighter accent.
	 */
	public static Palette fromPixels(int[] argb, int count, boolean dark)
	{
		if(argb == null || count <= 0)
			return fallback(dark);
		
		int limit = Math.min(count, argb.length);
		double[] weights = new double[HUE_BUCKETS];
		double[] cosSum = new double[HUE_BUCKETS];
		double[] sinSum = new double[HUE_BUCKETS];
		double[] satSum = new double[HUE_BUCKETS];
		double[] lightSum = new double[HUE_BUCKETS];
		double total = 0;
		
		for(int i = 0; i < limit; i++)
		{
			int pixel = argb[i];
			
			if((pixel >>> 24) < MIN_ALPHA)
				continue;
			
			float[] hsl = toHsl(pixel);
			float hue = hsl[0];
			float saturation = hsl[1];
			float lightness = hsl[2];
			
			if(saturation < MIN_COVER_SATURATION
				|| lightness < MIN_COVER_LIGHTNESS)
				continue;
			
			int bucket = (int)(hue / (360F / HUE_BUCKETS)) % HUE_BUCKETS;
			double weight = saturation;
			double radians = Math.toRadians(hue);
			
			weights[bucket] += weight;
			cosSum[bucket] += Math.cos(radians) * weight;
			sinSum[bucket] += Math.sin(radians) * weight;
			satSum[bucket] += saturation * weight;
			lightSum[bucket] += lightness * weight;
			total += weight;
		}
		
		if(total <= 0)
			return fallback(dark);
		
		int best = 0;
		
		for(int i = 1; i < HUE_BUCKETS; i++)
			if(weights[i] > weights[best])
				best = i;
		
		double weight = weights[best];
		float hue = (float)(Math.toDegrees(Math.atan2(sinSum[best],
			cosSum[best])));
		
		if(hue < 0)
			hue += 360F;
		
		float saturation = (float)(satSum[best] / weight);
		float lightness = (float)(lightSum[best] / weight);
		
		return derive(hue, saturation, lightness, dark, true);
	}
	
	/**
	 * A palette derived from an accent that is already known. The project's own
	 * cover sampler ({@code NeteaseImageCache}) returns one per cover, so the
	 * interface can follow the playing track without sampling the image again.
	 *
	 * @param accentRgb
	 *            the sampled accent, e.g. {@code 0xFF007CFF}.
	 */
	public static Palette fromAccent(int accentRgb, boolean dark)
	{
		float[] hsl = toHsl(accentRgb);
		return derive(hsl[0], hsl[1], hsl[2], dark, true);
	}
	
	/**
	 * A palette for covers that cannot be read (no cover, all grey artwork,
	 * loading failed), so that the interface always has an accent.
	 */
	public static Palette fallback()
	{
		return fallback(false);
	}
	
	public static Palette fallback(boolean dark)
	{
		float[] hsl = toHsl(DEFAULT_ACCENT);
		return derive(hsl[0], hsl[1], hsl[2], dark, false);
	}
	
	/**
	 * Normalises the cover colour and derives every value the interface needs.
	 */
	public static Palette derive(float hue, float saturation, float lightness,
		boolean dark, boolean fromCover)
	{
		float minSaturation = dark ? 0.5F : 0.45F;
		float maxSaturation = 0.85F;
		float minLightness = dark ? 0.55F : 0.42F;
		float maxLightness = dark ? 0.72F : 0.58F;
		
		float s = clamp(saturation, minSaturation, maxSaturation);
		float l = clamp(lightness, minLightness, maxLightness);
		
		int accent = toRgb(hue, s, l);
		int accentStrong = toRgb(hue, s, Math.max(0.2F, l - 0.14F));
		int accentSoft = withAlpha(accent, SOFT_ALPHA);
		int heroFrom = mix(accent, dark ? 0xFF101014 : 0xFFFFFFFF, 0.88F);
		int heroTo = mix(accent, dark ? 0xFF101014 : 0xFFFFFFFF, 0.94F);
		
		// whichever of white and the reference's dark navy reads better
		int onAccent = contrastRatio(accent, 0xFFFFFFFF) >= contrastRatio(accent,
			0xFF111827) ? 0xFFFFFFFF : 0xFF111827;
		
		return new Palette(accent, accentStrong, accentSoft, heroFrom, heroTo,
			onAccent, hue, s, l, fromCover);
	}
	
	/** Linear blend, {@code t = 0} keeps {@code from}. */
	public static int mix(int from, int to, float t)
	{
		float f = clamp(t, 0F, 1F);
		int a = Math.round(alpha(from) * (1 - f) + alpha(to) * f);
		int r = Math.round(red(from) * (1 - f) + red(to) * f);
		int g = Math.round(green(from) * (1 - f) + green(to) * f);
		int b = Math.round(blue(from) * (1 - f) + blue(to) * f);
		return a << 24 | r << 16 | g << 8 | b;
	}
	
	public static int withAlpha(int rgb, float alpha)
	{
		int a = Math.round(clamp(alpha, 0F, 1F) * 255F);
		return a << 24 | rgb & 0xFFFFFF;
	}
	
	public static int red(int argb)
	{
		return argb >> 16 & 0xFF;
	}
	
	public static int green(int argb)
	{
		return argb >> 8 & 0xFF;
	}
	
	public static int blue(int argb)
	{
		return argb & 0xFF;
	}
	
	public static int alpha(int argb)
	{
		return argb >>> 24;
	}
	
	/** WCAG relative luminance, used to pick a readable colour on the accent. */
	public static double relativeLuminance(int argb)
	{
		double r = channel(red(argb));
		double g = channel(green(argb));
		double b = channel(blue(argb));
		return 0.2126D * r + 0.7152D * g + 0.0722D * b;
	}
	
	/** WCAG contrast ratio between two opaque colours. */
	public static double contrastRatio(int first, int second)
	{
		double a = relativeLuminance(first);
		double b = relativeLuminance(second);
		double lighter = Math.max(a, b);
		double darker = Math.min(a, b);
		return (lighter + 0.05D) / (darker + 0.05D);
	}
	
	public static String hex(int argb)
	{
		return String.format("#%06X", argb & 0xFFFFFF);
	}
	
	/**
	 * @return {@code {hue, saturation, lightness}} with hue in degrees.
	 */
	public static float[] toHsl(int argb)
	{
		float r = red(argb) / 255F;
		float g = green(argb) / 255F;
		float b = blue(argb) / 255F;
		float max = Math.max(r, Math.max(g, b));
		float min = Math.min(r, Math.min(g, b));
		float lightness = (max + min) / 2F;
		float hue;
		float saturation;
		
		if(max == min)
		{
			hue = 0;
			saturation = 0;
		}else
		{
			float delta = max - min;
			saturation = lightness > 0.5F ? delta / (2F - max - min)
				: delta / (max + min);
			
			if(max == r)
				hue = (g - b) / delta + (g < b ? 6F : 0F);
			else if(max == g)
				hue = (b - r) / delta + 2F;
			else
				hue = (r - g) / delta + 4F;
			
			hue *= 60F;
		}
		
		return new float[]{hue, saturation, lightness};
	}
	
	public static int toRgb(float hue, float saturation, float lightness)
	{
		float h = ((hue % 360F) + 360F) % 360F;
		float s = clamp(saturation, 0F, 1F);
		float l = clamp(lightness, 0F, 1F);
		
		if(s == 0)
		{
			int grey = Math.round(l * 255F);
			return 0xFF000000 | grey << 16 | grey << 8 | grey;
		}
		
		float q = l < 0.5F ? l * (1F + s) : l + s - l * s;
		float p = 2F * l - q;
		float r = hueToRgb(p, q, h / 360F + 1F / 3F);
		float g = hueToRgb(p, q, h / 360F);
		float b = hueToRgb(p, q, h / 360F - 1F / 3F);
		
		return 0xFF000000 | Math.round(r * 255F) << 16
			| Math.round(g * 255F) << 8 | Math.round(b * 255F);
	}
	
	private static float hueToRgb(float p, float q, float t)
	{
		float value = t;
		
		if(value < 0)
			value += 1F;
		if(value > 1)
			value -= 1F;
		if(value < 1F / 6F)
			return p + (q - p) * 6F * value;
		if(value < 1F / 2F)
			return q;
		if(value < 2F / 3F)
			return p + (q - p) * (2F / 3F - value) * 6F;
		return p;
	}
	
	private static double channel(int value)
	{
		double c = value / 255D;
		return c <= 0.03928D ? c / 12.92D
			: Math.pow((c + 0.055D) / 1.055D, 2.4D);
	}
	
	private static float clamp(float value, float min, float max)
	{
		return value < min ? min : value > max ? max : value;
	}
}

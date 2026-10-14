package net.wurstclient.gui.glass;

/**
 * 玻璃材质，长度单位是绘制的本地 GUI 像素。
 *
 * <p>
 * 移植自 LumaGlass 0.2.9 的 {@code dev.lumaglass.api.client.GlassStyle}
 * （MIT，Copyright © 2026 Sunny5349；许可正文见
 * {@code META-INF/licenses/lumaglass/}）。字段名与取值范围跟上游逐项一致，校验语义
 * 也一样，这样以后按上游 diff 更新时不会因为"本地改过名"而对不上。</p>
 *
 * <p>
 * 上游第七个分量是每块面板的泛光颜色（{@code glowColor}）。本工程没有泛光调色盘，
 * 泛光强度恒为 0（见 {@link GlassRenderer}），所以这里不带该分量——带上它只会得到
 * 一个永远为默认值的字段。</p>
 */
record GlassStyle(float radius, float refraction, float frost, float tint,
	float highlight, float opacity)
{
	GlassStyle
	{
		range("radius", radius, 0, 256);
		range("refraction", refraction, 0, 64);
		range("frost", frost, 0, 1);
		range("tint", tint, 0, 1);
		range("highlight", highlight, 0, 4);
		range("opacity", opacity, 0, 1);
	}

	private static void range(String name, float value, float min, float max)
	{
		if(!Float.isFinite(value) || value < min || value > max)
			throw new IllegalArgumentException(name
				+ " must be finite and within [" + min + ", " + max + "]");
	}
}

package net.wurstclient.gui.glass;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

/**
 * 控件那一层的纯算式。绘制本身要 GL，测不到；圆角、材质漂移、把手位置、坐标换算这些
 * 可以先在这里钉住。
 */
class GlassButtonsTest
{
	@Test
	void radiusIsCappedAtTenAndNeverExceedsHalfTheHeight()
	{
		// 上游 ButtonMixin：min(10, height * 0.5)
		assertEquals(10F, GlassButtons.radiusFor(48F));
		assertEquals(10F, GlassButtons.radiusFor(20F));
		assertEquals(9F, GlassButtons.radiusFor(18F));
		assertEquals(4F, GlassButtons.radiusFor(8F));
		assertEquals(0F, GlassButtons.radiusFor(0F));
		assertEquals(0F, GlassButtons.radiusFor(-5F));
	}

	@Test
	void unfocusedStyleMatchesTheUpstreamDefaults()
	{
		// LumaGlass GlassConfig 默认：refraction 24、frost 1、tint 0、highlight 1
		GlassStyle style = GlassButtons.styleFor(10F, 0F, 1F);
		assertEquals(10F, style.radius());
		assertEquals(24F, style.refraction());
		assertEquals(1F, style.frost());
		assertEquals(0F, style.tint());
		assertEquals(1F, style.highlight());
		assertEquals(1F, style.opacity());
	}

	@Test
	void focusedStyleAddsTintAndHighlight()
	{
		// 上游 VanillaGlass.style()：focused 时 tint +0.13、highlight 1.7
		GlassStyle style = GlassButtons.styleFor(10F, 1F, 1F);
		assertEquals(0.13F, style.tint(), 1E-6F);
		assertEquals(1.7F, style.highlight(), 1E-6F);
	}

	@Test
	void focusIsClampedToZeroOne()
	{
		assertEquals(0F, GlassButtons.styleFor(10F, -3F, 1F).tint());
		assertEquals(1.7F, GlassButtons.styleFor(10F, 3F, 1F).highlight(),
			1E-6F);
	}

	@Test
	void styleIsInterpolatedNotSnapped()
	{
		// 本工程控件有 20 帧悬停动画，材质要跟着进度走，不能像上游那样布尔跳变
		float half = GlassButtons.styleFor(10F, 0.5F, 1F).tint();
		assertEquals(0.065F, half, 1E-6F);
	}

	@Test
	void opacityHalvesForInactiveButtons()
	{
		assertEquals(1F, GlassButtons.opacityFor(true, 1F));
		assertEquals(0.5F, GlassButtons.opacityFor(false, 1F));
		assertEquals(0.25F, GlassButtons.opacityFor(false, 0.5F));
		assertEquals(0F, GlassButtons.opacityFor(true, 0F));
	}

	@Test
	void opacityAndStyleOpacityAreClamped()
	{
		assertEquals(1F, GlassButtons.opacityFor(true, 4F));
		assertEquals(0F, GlassButtons.opacityFor(true, -1F));
		assertEquals(1F, GlassButtons.styleFor(10F, 0F, 9F).opacity());
		assertEquals(0F, GlassButtons.styleFor(10F, 0F, -9F).opacity());
	}

	@Test
	void sliderHandleStaysInsideTheTrack()
	{
		// 上游 SliderMixin：x + value * (width - 8)
		assertEquals(0F, GlassButtons.handleOffset(100F, 0));
		assertEquals(46F, GlassButtons.handleOffset(100F, 0.5));
		assertEquals(92F, GlassButtons.handleOffset(100F, 1));
	}

	@Test
	void sliderHandleClampsOutOfRangeValues()
	{
		assertEquals(0F, GlassButtons.handleOffset(100F, -1));
		assertEquals(92F, GlassButtons.handleOffset(100F, 4));
		assertEquals(0F, GlassButtons.handleOffset(4F, 1));
	}

	@Test
	void scrollbarStyleFollowsUpstream()
	{
		// 轨道：低透明度暗玻璃；滑块：接近不透明的高光玻璃
		GlassStyle track = GlassButtons.scrollbarStyle(6F, 40F, true, false);
		assertEquals(3F, track.radius());
		assertEquals(0.08F, track.tint(), 1E-6F);
		assertEquals(0.22F, track.opacity(), 1E-6F);

		GlassStyle thumb = GlassButtons.scrollbarStyle(6F, 40F, false, false);
		assertEquals(0.38F, thumb.tint(), 1E-6F);
		assertEquals(0.95F, thumb.opacity(), 1E-6F);

		GlassStyle disabled = GlassButtons.scrollbarStyle(6F, 40F, false, true);
		assertEquals(0.4F, disabled.opacity(), 1E-6F);
	}

	@Test
	void pixelScaleMultipliesTheWindowGuiScale()
	{
		assertEquals(2F, GlassButtons.pixelScaleFor(2, 1F));
		assertEquals(1F, GlassButtons.pixelScaleFor(2, 0.5F));
		assertEquals(4F, GlassButtons.pixelScaleFor(2, 2F));
	}

	@Test
	void pixelScaleClampsThePoseScale()
	{
		// 界面打开动画的缩放被钳在 [0.25, 4]：折射位移按屏幕像素算，极端值会糊掉整块玻璃
		assertEquals(0.5F, GlassButtons.pixelScaleFor(2, 0.01F));
		assertEquals(8F, GlassButtons.pixelScaleFor(2, 100F));
	}

	@Test
	void pixelScaleFallsBackWhenTheInputsAreUnusable()
	{
		assertEquals(1F, GlassButtons.pixelScaleFor(0, 1F));
		assertEquals(1F, GlassButtons.pixelScaleFor(-3, 1F));
		assertEquals(1F, GlassButtons.pixelScaleFor(Double.NaN, 1F));
		assertEquals(3F, GlassButtons.pixelScaleFor(3, Float.NaN));
		assertEquals(3F, GlassButtons.pixelScaleFor(3, 0F));
		assertEquals(3F, GlassButtons.pixelScaleFor(3, -1F));
	}
}

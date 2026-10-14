package net.wurstclient.gui.glass;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

class GlassStyleTest
{
	@Test
	void acceptsTheValuesTheButtonPathUses()
	{
		GlassStyle style = new GlassStyle(10, 24, 1, 0.13F, 1.7F, 1);
		assertEquals(10, style.radius());
		assertEquals(24, style.refraction());
		assertEquals(1, style.frost());
		assertEquals(0.13F, style.tint());
		assertEquals(1.7F, style.highlight());
		assertEquals(1, style.opacity());
	}

	@Test
	void acceptsTheRangeBoundaries()
	{
		new GlassStyle(0, 0, 0, 0, 0, 0);
		new GlassStyle(256, 64, 1, 1, 4, 1);
	}

	@Test
	void rejectsOutOfRangeComponents()
	{
		// 每条都是上游 GlassStyle 的取值范围，逐一越界
		assertThrows(IllegalArgumentException.class,
			() -> new GlassStyle(-1, 24, 1, 0, 1, 1));
		assertThrows(IllegalArgumentException.class,
			() -> new GlassStyle(257, 24, 1, 0, 1, 1));
		assertThrows(IllegalArgumentException.class,
			() -> new GlassStyle(10, 65, 1, 0, 1, 1));
		assertThrows(IllegalArgumentException.class,
			() -> new GlassStyle(10, 24, 1.1F, 0, 1, 1));
		assertThrows(IllegalArgumentException.class,
			() -> new GlassStyle(10, 24, 1, -0.1F, 1, 1));
		assertThrows(IllegalArgumentException.class,
			() -> new GlassStyle(10, 24, 1, 0, 4.1F, 1));
		assertThrows(IllegalArgumentException.class,
			() -> new GlassStyle(10, 24, 1, 0, 1, 1.1F));
	}

	@Test
	void rejectsNonFiniteComponents()
	{
		assertThrows(IllegalArgumentException.class,
			() -> new GlassStyle(Float.NaN, 24, 1, 0, 1, 1));
		assertThrows(IllegalArgumentException.class,
			() -> new GlassStyle(10, Float.POSITIVE_INFINITY, 1, 0, 1, 1));
		assertThrows(IllegalArgumentException.class,
			() -> new GlassStyle(10, 24, 1, Float.NaN, 1, 1));
		assertThrows(IllegalArgumentException.class,
			() -> new GlassStyle(10, 24, 1, 0, Float.NEGATIVE_INFINITY, 1));
	}

	@Test
	void isAValueType()
	{
		assertEquals(new GlassStyle(10, 24, 1, 0.13F, 1.7F, 1),
			new GlassStyle(10, 24, 1, 0.13F, 1.7F, 1));
		assertEquals(new GlassStyle(10, 24, 1, 0.13F, 1.7F, 1).hashCode(),
			new GlassStyle(10, 24, 1, 0.13F, 1.7F, 1).hashCode());
	}
}

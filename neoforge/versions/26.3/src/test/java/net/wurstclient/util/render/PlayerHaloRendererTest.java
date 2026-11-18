package net.wurstclient.util.render;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

final class PlayerHaloRendererTest
{
	@Test
	void scalesHaloToPlayerWidthWithinCompactBounds()
	{
		assertEquals(0.34, PlayerHaloRenderer.radiusForWidth(0.2), 0.0001);
		assertEquals(0.432, PlayerHaloRenderer.radiusForWidth(0.6), 0.0001);
		assertEquals(0.48, PlayerHaloRenderer.radiusForWidth(1.0), 0.0001);
	}
}

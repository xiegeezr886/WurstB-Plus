package net.wurstclient.hud2;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class ClientMetricsManagerTest
{
	@Test
	void calculatesAndClampsTps()
	{
		assertEquals(20, ClientMetricsManager.calculateTps(1_000_000_000L));
		assertEquals(10, ClientMetricsManager.calculateTps(2_000_000_000L));
		assertEquals(20, ClientMetricsManager.calculateTps(500_000_000L));
		assertEquals(20, ClientMetricsManager.calculateTps(0));
	}
}

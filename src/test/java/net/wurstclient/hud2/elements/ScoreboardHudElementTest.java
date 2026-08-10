package net.wurstclient.hud2.elements;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class ScoreboardHudElementTest
{
	@Test
	void widthMatchesRisePaddingFormula()
	{
		assertEquals(44, ScoreboardHudElement.panelWidth(30));
		assertEquals(164, ScoreboardHudElement.panelWidth(150));
	}

	@Test
	void panelHeightHasStablePadding()
	{
		assertEquals(15, ScoreboardHudElement.panelHeightForEntries(0));
		assertEquals(24, ScoreboardHudElement.panelHeightForEntries(1));
		assertEquals(51, ScoreboardHudElement.panelHeightForEntries(4));
	}
}

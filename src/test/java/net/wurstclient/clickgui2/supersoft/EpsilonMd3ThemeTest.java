package net.wurstclient.clickgui2.supersoft;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

import net.wurstclient.gui.visual.VisualTheme;

final class EpsilonMd3ThemeTest
{
	@Test
	void mixAndAlphaMatchVisualThemeSemantics()
	{
		assertEquals(VisualTheme.mix(0xFF000000, 0xFFFFFFFF, 0.5F),
			EpsilonMd3Theme.mix(0xFF000000, 0xFFFFFFFF, 0.5F));
		assertEquals(VisualTheme.withAlpha(EpsilonMd3Theme.PRIMARY, -1),
			EpsilonMd3Theme.withAlpha(EpsilonMd3Theme.PRIMARY, -1));
		assertEquals(VisualTheme.withAlpha(EpsilonMd3Theme.PRIMARY, 0.5F),
			EpsilonMd3Theme.withAlpha(EpsilonMd3Theme.PRIMARY, 0.5F));
		assertEquals(0xFF007CFF, EpsilonMd3Theme.PRIMARY);
	}

	@Test
	void stateLayerScalesAlphaByProgress()
	{
		assertEquals(0x00007CFF,
			EpsilonMd3Theme.stateLayer(EpsilonMd3Theme.PRIMARY, 0, 80));
		assertEquals(0x28007CFF,
			EpsilonMd3Theme.stateLayer(EpsilonMd3Theme.PRIMARY, 0.5F, 80));
		assertEquals(0x50007CFF,
			EpsilonMd3Theme.stateLayer(EpsilonMd3Theme.PRIMARY, 2, 80));
		assertEquals(0x00007CFF,
			EpsilonMd3Theme.stateLayer(EpsilonMd3Theme.PRIMARY, 1, -4));
	}
}

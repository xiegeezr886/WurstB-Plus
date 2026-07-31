package net.wurstclient.hud2.elements;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

final class KeystrokesHudElementTest
{
	@Test
	void keepsOriginalCompactKeyGridDimensions()
	{
		KeystrokesHudElement element = new KeystrokesHudElement();
		assertEquals(67, element.getWidth());
		assertEquals(95, element.getHeight());
	}

	@Test
	void rendersBodyInsideHudEditor()
	{
		assertTrue(new KeystrokesHudElement().renderEditorPreview());
	}
}

package net.wurstclient.mixin;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;

final class HudRenderHookTest
{
	@Test
	void guiRenderEventUsesTheVanillaHudLayer() throws IOException
	{
		String mixin = Files.readString(Path.of("src", "main", "java", "net",
			"wurstclient", "mixin", "GuiMixin.java"));
		String initializer = Files.readString(Path.of("src", "main", "java",
			"net", "wurstclient", "WurstInitializer.java"));

		assertTrue(mixin.contains("method = \"renderTabList("));
		assertTrue(mixin.contains("new GUIRenderEvent("));
		assertTrue(mixin.contains("new GuiGraphicsExtractor(graphics)"));
		assertFalse(initializer.contains("AddGuiOverlayLayersEvent"));
		assertFalse(initializer.contains("new GUIRenderEvent"));
	}
}

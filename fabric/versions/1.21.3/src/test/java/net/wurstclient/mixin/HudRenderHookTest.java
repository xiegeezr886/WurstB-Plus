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
		// 1.21.5 has no GuiMixin/GuiGraphicsExtractor yet (those arrive in
		// 1.21.6), so the hook lives in the vanilla HUD mixin instead.
		String mixin = Files.readString(Path.of("src", "main", "java", "net",
			"wurstclient", "mixin", "IngameHudMixin.java"));
		String initializer = Files.readString(Path.of("src", "main", "java",
			"net", "wurstclient", "WurstForgeInitializer.java"));

		assertTrue(mixin.contains("method = \"renderTabList("));
		assertTrue(mixin.contains("new GUIRenderEvent("));
		assertFalse(initializer.contains("AddGuiOverlayLayersEvent"));
		assertFalse(initializer.contains("new GUIRenderEvent"));
	}
}

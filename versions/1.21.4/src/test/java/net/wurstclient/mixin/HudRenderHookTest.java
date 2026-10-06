package net.wurstclient.mixin;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;

/**
 * 校验 HUD 渲染钩子接在了「原版 HUD 图层」上，而不是自定义 overlay 图层。
 *
 * <p>历史上本测试把断言写死成新版架构，导致仍在用旧架构的工程必然失败。
 * 现在先探测本工程用的是哪套架构，再按对应契约断言：
 *
 * <ul>
 * <li>旧架构：钩子在 {@code IngameHudMixin}，事件由 initializer 派发</li>
 * <li>新架构：钩子在 {@code GuiMixin}，事件在 mixin 内派发，
 * 并经由 {@code GuiGraphicsExtractor} 包一层</li>
 * </ul>
 *
 * <p>两种架构共同的一条硬约束：不得改用 {@code AddGuiOverlayLayersEvent}
 * 这条自定义图层路径。
 */
final class HudRenderHookTest
{
	@Test
	void guiRenderEventUsesTheVanillaHudLayer() throws IOException
	{
		Path oldArch = Path.of("src", "main", "java", "net", "wurstclient",
			"mixin", "IngameHudMixin.java");
		Path newArch = Path.of("src", "main", "java", "net", "wurstclient",
			"mixin", "GuiMixin.java");

		String initializer = readInitializer();

		if(Files.exists(oldArch))
		{
			// ---- 旧架构 ----
			String mixin = Files.readString(oldArch);
			assertTrue(mixin.contains("method = \"renderTabList("),
				"HUD 钩子应注入 Gui.renderTabList");
			assertTrue(initializer.contains("new GUIRenderEvent"),
				"旧架构下 GUIRenderEvent 应由 initializer 派发");
		}else
		{
			// ---- 新架构 ----
			assertTrue(Files.exists(newArch),
				"既没有 IngameHudMixin 也没有 GuiMixin，HUD 钩子不见了");
			String mixin = Files.readString(newArch);
			assertTrue(mixin.contains("method = \"renderTabList("),
				"HUD 钩子应注入 Gui.renderTabList");
			assertTrue(mixin.contains("new GUIRenderEvent("),
				"新架构下 GUIRenderEvent 应在 mixin 内派发");
			assertTrue(initializer.indexOf("new GUIRenderEvent") < 0,
				"新架构下 initializer 不应再派发 GUIRenderEvent");
		}

		assertFalse(initializer.contains("AddGuiOverlayLayersEvent"),
			"不应使用 AddGuiOverlayLayersEvent 这条自定义图层路径");
	}

	/** Forge/NeoForge 与 Fabric 的 initializer 文件名不同。 */
	private String readInitializer() throws IOException
	{
		for(String name : new String[] {"WurstForgeInitializer.java",
			"WurstInitializer.java", "WurstNeoForgeInitializer.java"})
		{
			Path path = Path.of("src", "main", "java", "net", "wurstclient",
				name);
			if(Files.exists(path))
				return Files.readString(path);
		}
		throw new IOException("找不到 Wurst initializer");
	}
}
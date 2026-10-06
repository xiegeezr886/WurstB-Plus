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
 * <p>本工程（1.21.3 / 1.21.4 / 1.21.5）用的是**旧架构**：钩子在
 * {@code IngameHudMixin} 里注入 {@code Gui.renderTabList} 的 HEAD，
 * 再在 {@code WurstInitializer} 中派发 {@code GUIRenderEvent}。
 *
 * <p>1.21.6 起换成新架构——钩子移到 {@code GuiMixin}，事件也在 mixin 内部派发。
 * 如果本工程将来移植到新架构，请同步改写本测试（可参考 1.21.6+ 工程里的同名文件）。
 */
final class HudRenderHookTest
{
	@Test
	void guiRenderEventUsesTheVanillaHudLayer() throws IOException
	{
		Path hudMixin = Path.of("src", "main", "java", "net", "wurstclient",
			"mixin", "IngameHudMixin.java");
		assertTrue(Files.exists(hudMixin),
			"旧架构的 HUD 钩子应在 IngameHudMixin 中");

		String mixin = Files.readString(hudMixin);
		String initializer = Files.readString(
			Path.of("src", "main", "java", "net", "wurstclient",
				"WurstInitializer.java"));

		// 钩子必须挂在原版 HUD 图层上
		assertTrue(mixin.contains("method = \"renderTabList("),
			"HUD 钩子应注入 Gui.renderTabList");
		// 事件在 WurstInitializer 中派发（旧架构）
		assertTrue(initializer.contains("new GUIRenderEvent"),
			"GUIRenderEvent 应由 WurstInitializer 派发");
		// 不得改用自定义 overlay 图层
		assertFalse(initializer.contains("AddGuiOverlayLayersEvent"),
			"不应使用 AddGuiOverlayLayersEvent 这条自定义图层路径");
	}
}
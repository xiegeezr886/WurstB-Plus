package net.wurstclient.infinitegui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonObject;

import net.minecraft.client.gui.GuiGraphics;
import org.junit.jupiter.api.Test;

/**
 * 窗口基类：配置往返、type 名（= 无限GUI 的 {@code Item::name}，配置里靠它对上）、
 * 以及"尺寸 = 内容 + WurstB+ 的卡片内边距"这条几何约定。
 *
 * <p>
 * 这里用一个不碰 Minecraft 的测试窗口（{@link TestWindow}）：真实那三项的尺寸要读
 * {@code Minecraft.getInstance().font}，在单测里没有客户端实例。</p>
 */
class InfiniteWindowTest
{
	/** 内容固定 40×9，专供几何断言。 */
	private static final class TestWindow extends InfiniteWindow
	{
		private TestWindow()
		{
			super("测试项", "测试用");
		}

		@Override
		protected int contentWidth()
		{
			return 40;
		}

		@Override
		protected int contentHeight()
		{
			return 9;
		}

		@Override
		protected void drawContent(GuiGraphics graphics, int x, int y,
			float partialTicks)
		{}
	}

	@Test
	void geometryUsesTheWurstbCardPadding()
	{
		TestWindow window = new TestWindow();

		// WurstB+ 的文本卡片：左右各 2、上下各 1
		assertEquals(40 + InfiniteWindow.PADDING_X * 2, window.getWidth());
		assertEquals(9 + InfiniteWindow.PADDING_Y * 2, window.getHeight());
	}

	@Test
	void defaultGeometryIsTheWurstbCardRadius()
	{
		assertEquals(4, InfiniteWindow.CARD_RADIUS);
		assertEquals(2, InfiniteWindow.PADDING_X);
		assertEquals(1, InfiniteWindow.PADDING_Y);
	}

	@Test
	void configRoundTrips()
	{
		TestWindow source = new TestWindow();
		source.setEnabled(true);
		source.moveTo(37, 91);

		JsonObject json = new JsonObject();
		source.save(json);

		assertEquals("测试项", json.get("type").getAsString());
		assertTrue(json.get("isEnabled").getAsBoolean());

		TestWindow target = new TestWindow();
		target.setEnabled(false);
		target.moveTo(0, 0);
		target.load(json);

		assertTrue(target.isEnabled());
		assertEquals(37, target.getX());
		assertEquals(91, target.getY());
	}

	@Test
	void hitTestFollowsTheWindowRect()
	{
		TestWindow window = new TestWindow();
		window.moveTo(10, 20);

		assertTrue(window.contains(10, 20));
		assertTrue(window.contains(10 + window.getWidth() - 1,
			20 + window.getHeight() - 1));
		assertFalse(window.contains(9, 20));
		assertFalse(window.contains(10, 20 + window.getHeight()));
	}

	@Test
	void thePortedItemsKeepTheUpstreamTypeNames()
	{
		// 配置里的 type 就是无限GUI 的 Item::name —— 改名会让老配置对不上
		assertEquals("时间显示", new TimeWindow().getType());
		assertEquals("FPS显示", new FpsWindow().getType());
		assertEquals("CPS显示", new CpsWindow().getType());
	}

	@Test
	void fpsAndCpsKeepTheUpstreamAffixes()
	{
		assertEquals("[", new FpsWindow().getPrefix());
		assertEquals("FPS]", new FpsWindow().getSuffix());
		assertEquals("[CPS: ", new CpsWindow().getPrefix());
		assertEquals("]", new CpsWindow().getSuffix());
	}
}

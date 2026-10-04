package net.wurstclient.twilight;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import net.wurstclient.twilight.TwilightShellLayout.Frame;
import net.wurstclient.twilight.TwilightShellLayout.Rect;

/**
 * 校验 duo 卡里那叠封面的姿态与偏移，数值取自 {@code StreamingHome.vue} 的
 * {@code .duo-stack-cover-0/1/2}。
 *
 * <p>
 * 早先三张封面都按 60px 不转不缩地画，叠出来是一叠同样大的方块；参考里第 1 张
 * 转 5 度缩到 0.94、第 2 张转 -6 度缩到 0.88，三张大小是递进的。
 */
final class TwilightHomeLayoutDuoTest
{
	@Test
	void theStackOffsetsMatchTheReference()
	{
		Frame frame = TwilightShellLayout.layout(1500, 880);
		Rect card = TwilightHomeLayout.layout(frame).duoCardLeft;
		Rect[] covers = TwilightHomeLayout.duoCovers(frame, card);

		assertEquals(3, covers.length);

		// 三张的左上角相对位移：{0,6} / {22,0} / {42,10}
		int baseX = covers[0].x();
		int baseY = covers[0].y() - frame.px(6);
		assertEquals(0, covers[0].x() - baseX);
		assertEquals(22, covers[1].x() - baseX);
		assertEquals(42, covers[2].x() - baseX);
		assertEquals(0, covers[1].y() - baseY);
		assertEquals(10, covers[2].y() - baseY);
	}

	@Test
	void theStackSizesAreGraduated()
	{
		float[] first = TwilightHomeLayout.duoCoverTransform(0);
		float[] second = TwilightHomeLayout.duoCoverTransform(1);
		float[] third = TwilightHomeLayout.duoCoverTransform(2);

		assertEquals(0F, first[0], 1e-4F);
		assertEquals(1F, first[1], 1e-4F);

		assertEquals(5F, second[0], 1e-4F);
		assertEquals(0.94F, second[1], 1e-4F);

		assertEquals(-6F, third[0], 1e-4F);
		assertEquals(0.88F, third[1], 1e-4F);

		assertTrue(second[1] > third[1], "第二张应比第三张大");
	}

	/** 悬停时按参考：第 0 张转 -4 度，第 1 张转 8 度并抬起 3px。 */
	@Test
	void hoverTiltsTheStack()
	{
		float[] first = TwilightHomeLayout.duoCoverHoverTransform(0);
		float[] second = TwilightHomeLayout.duoCoverHoverTransform(1);

		assertEquals(-4F, first[0], 1e-4F);
		assertEquals(-2F, first[2], 1e-4F);

		assertEquals(8F, second[0], 1e-4F);
		assertEquals(-3F, second[2], 1e-4F);
	}

	/** 未知下标要退回不转不缩，别让调用方读到越界。 */
	@Test
	void unknownIndicesFallBackToTheIdentity()
	{
		for(int index : new int[]{-1, 3, 99})
		{
			float[] pose = TwilightHomeLayout.duoCoverTransform(index);
			assertEquals(0F, pose[0], 1e-4F);
			assertEquals(1F, pose[1], 1e-4F);
		}
	}
}

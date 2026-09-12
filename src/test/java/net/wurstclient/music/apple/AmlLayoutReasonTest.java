package net.wurstclient.music.apple;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/**
 * 校验 {@link AmlLayoutReason} 与 AMLL {@code LayoutReasonStrategyMap} 一致。
 */
final class AmlLayoutReasonTest
{
	@Test
	void onlyContinuousScrollSnapsPosY()
	{
		assertTrue(AmlLayoutReason.CONTINUOUS_SCROLL.snapPosY());
		// 载入歌词要保留"从下方飞入"的弹簧动画
		assertFalse(AmlLayoutReason.REBUILD_VIEW.snapPosY());
		assertFalse(AmlLayoutReason.SEEK.snapPosY());
		assertFalse(AmlLayoutReason.PLAYBACK_TICK.snapPosY());
		assertFalse(AmlLayoutReason.DISCRETE_SCROLL.snapPosY());
	}

	@Test
	void staggerOnlyEnabledDuringPlaybackAndAfterInteraction()
	{
		assertFalse(AmlLayoutReason.PLAYBACK_TICK.disableStagger());
		assertFalse(AmlLayoutReason.INTERACTION_END.disableStagger());
		assertTrue(AmlLayoutReason.SEEK.disableStagger());
		assertTrue(AmlLayoutReason.REBUILD_VIEW.disableStagger());
		assertTrue(AmlLayoutReason.RESIZE.disableStagger());
		assertTrue(AmlLayoutReason.CONFIG_CHANGE.disableStagger());
		assertTrue(AmlLayoutReason.CONTINUOUS_SCROLL.disableStagger());
		assertTrue(AmlLayoutReason.DISCRETE_SCROLL.disableStagger());
		assertTrue(AmlLayoutReason.INTERACTION_START.disableStagger());
	}

	@Test
	void interludeResetsOnSeekRebuildAndInteraction()
	{
		for(AmlLayoutReason reason : new AmlLayoutReason[]{
			AmlLayoutReason.SEEK, AmlLayoutReason.REBUILD_VIEW,
			AmlLayoutReason.INTERACTION_START,
			AmlLayoutReason.CONTINUOUS_SCROLL,
			AmlLayoutReason.DISCRETE_SCROLL})
			assertTrue(reason.resetInterlude(), reason + " 应重置间奏点");

		for(AmlLayoutReason reason : new AmlLayoutReason[]{
			AmlLayoutReason.PLAYBACK_TICK, AmlLayoutReason.INTERACTION_END,
			AmlLayoutReason.RESIZE, AmlLayoutReason.CONFIG_CHANGE})
			assertFalse(reason.resetInterlude(), reason + " 不应重置间奏点");
	}
}

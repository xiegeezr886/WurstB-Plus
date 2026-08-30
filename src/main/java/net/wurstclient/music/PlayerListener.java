package net.wurstclient.music;

import java.util.List;

/**
 * 播放器事件监听器。
 *
 * <p>所有方法均为默认空实现，消费者只覆写自己关心的事件。事件在发生变更的
 * 线程上同步触发（executor 线程、StreamPlayer 线程或主线程），处理器必须快速
 * 且非阻塞，不要直接在处理器内触碰 GL/Minecraft 渲染状态。</p>
 */
public interface PlayerListener
{
	default void onSongChanged(NeteaseSong song)
	{}

	default void onPlaybackStateChanged(
		NeteaseMusicPlayer.PlaybackState state)
	{}

	default void onLyricsLoaded(List<LyricLine> lyrics)
	{}

	default void onPositionChanged(long positionMs, long durationMs)
	{}
}

package net.wurstclient.music;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * 线程安全的播放器监听器注册表。
 *
 * <p>基于 {@link CopyOnWriteArrayList}，fire 时对每个监听器单独 try/catch，
 * 单个监听器抛异常不会阻断其余监听器。可脱离 Minecraft/播放器独立测试。</p>
 */
final class PlayerListenerRegistry
{
	private final List<PlayerListener> listeners =
		new CopyOnWriteArrayList<>();

	void add(PlayerListener listener)
	{
		if(listener != null && !listeners.contains(listener))
			listeners.add(listener);
	}

	void remove(PlayerListener listener)
	{
		listeners.remove(listener);
	}

	int listenerCount()
	{
		return listeners.size();
	}

	void fireSongChanged(NeteaseSong song)
	{
		for(PlayerListener listener : listeners)
		{
			try
			{
				listener.onSongChanged(song);
			}catch(RuntimeException ignored)
			{}
		}
	}

	void firePlaybackStateChanged(
		NeteaseMusicPlayer.PlaybackState state)
	{
		for(PlayerListener listener : listeners)
		{
			try
			{
				listener.onPlaybackStateChanged(state);
			}catch(RuntimeException ignored)
			{}
		}
	}

	void fireLyricsLoaded(List<LyricLine> lyrics)
	{
		for(PlayerListener listener : listeners)
		{
			try
			{
				listener.onLyricsLoaded(lyrics);
			}catch(RuntimeException ignored)
			{}
		}
	}

	void firePositionChanged(long positionMs, long durationMs)
	{
		for(PlayerListener listener : listeners)
		{
			try
			{
				listener.onPositionChanged(positionMs, durationMs);
			}catch(RuntimeException ignored)
			{}
		}
	}
}

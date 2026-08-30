package net.wurstclient.music;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.Test;

final class PlayerListenerRegistryTest
{
	private final PlayerListenerRegistry registry =
		new PlayerListenerRegistry();

	@Test
	void firesSongChangedWithArgument()
	{
		NeteaseSong song = new NeteaseSong(42, "name", "artist", "album",
			"http://cover", 180_000);
		AtomicReference<NeteaseSong> received = new AtomicReference<>();
		registry.add(new PlayerListener()
		{
			@Override
			public void onSongChanged(NeteaseSong changed)
			{
				received.set(changed);
			}
		});
		registry.fireSongChanged(song);
		assertEquals(song, received.get());
	}

	@Test
	void firesPlaybackStateChangedWithArgument()
	{
		AtomicReference<NeteaseMusicPlayer.PlaybackState> received =
			new AtomicReference<>();
		registry.add(new PlayerListener()
		{
			@Override
			public void onPlaybackStateChanged(
				NeteaseMusicPlayer.PlaybackState state)
			{
				received.set(state);
			}
		});
		registry.firePlaybackStateChanged(
			NeteaseMusicPlayer.PlaybackState.PLAYING);
		assertEquals(NeteaseMusicPlayer.PlaybackState.PLAYING,
			received.get());
	}

	@Test
	void firesLyricsLoadedWithArgument()
	{
		List<LyricLine> lyrics = List.of(new LyricLine(1000, "line"));
		AtomicReference<List<LyricLine>> received = new AtomicReference<>();
		registry.add(new PlayerListener()
		{
			@Override
			public void onLyricsLoaded(List<LyricLine> loaded)
			{
				received.set(loaded);
			}
		});
		registry.fireLyricsLoaded(lyrics);
		assertEquals(lyrics, received.get());
	}

	@Test
	void firesPositionChangedWithArguments()
	{
		AtomicLong position = new AtomicLong();
		AtomicLong duration = new AtomicLong();
		registry.add(new PlayerListener()
		{
			@Override
			public void onPositionChanged(long positionMs, long durationMs)
			{
				position.set(positionMs);
				duration.set(durationMs);
			}
		});
		registry.firePositionChanged(12_345, 200_000);
		assertEquals(12_345, position.get());
		assertEquals(200_000, duration.get());
	}

	@Test
	void removeStopsDelivery()
	{
		AtomicInteger received = new AtomicInteger();
		PlayerListener listener = new PlayerListener()
		{
			@Override
			public void onSongChanged(NeteaseSong song)
			{
				received.incrementAndGet();
			}
		};
		registry.add(listener);
		registry.fireSongChanged(null);
		registry.remove(listener);
		registry.fireSongChanged(null);
		assertEquals(1, received.get());
		assertEquals(0, registry.listenerCount());
	}

	@Test
	void removingAbsentListenerIsNoOp()
	{
		registry.add(new PlayerListener()
		{});
		registry.remove(new PlayerListener()
		{});
		assertEquals(1, registry.listenerCount());
	}

	@Test
	void listenerCountReflectsAddRemove()
	{
		PlayerListener a = new PlayerListener()
		{};
		PlayerListener b = new PlayerListener()
		{};
		registry.add(a);
		registry.add(b);
		assertEquals(2, registry.listenerCount());
		registry.add(a);
		assertEquals(2, registry.listenerCount());
		registry.remove(a);
		assertEquals(1, registry.listenerCount());
	}

	@Test
	void throwingListenerDoesNotBlockOthers()
	{
		AtomicInteger healthy = new AtomicInteger();
		registry.add(new PlayerListener()
		{
			@Override
			public void onSongChanged(NeteaseSong song)
			{
				throw new IllegalStateException("boom");
			}
		});
		registry.add(new PlayerListener()
		{
			@Override
			public void onSongChanged(NeteaseSong song)
			{
				healthy.incrementAndGet();
			}
		});
		registry.fireSongChanged(null);
		assertEquals(1, healthy.get());
	}

	@Test
	void concurrentAddRemoveFireIsSafe() throws InterruptedException
	{
		AtomicInteger received = new AtomicInteger();
		AtomicBoolean anyError = new AtomicBoolean();
		AtomicBoolean allDone = new AtomicBoolean();
		CountDownLatch allDoneSignal = new CountDownLatch(1);
		CountDownLatch firstFire = new CountDownLatch(1);
		CountDownLatch start = new CountDownLatch(1);
		PlayerListener permanent = new PlayerListener()
		{
			@Override
			public void onSongChanged(NeteaseSong song)
			{
				received.incrementAndGet();
				firstFire.countDown();
			}
		};
		registry.add(permanent);
		Thread fire = new Thread(() -> {
			try
			{
				start.await();
				while(!allDone.get())
				{
					try
					{
						registry.fireSongChanged(null);
					}catch(Throwable t)
					{
						anyError.set(true);
					}
				}
			}catch(InterruptedException ignored)
			{}
			allDoneSignal.countDown();
		});
		Thread mutate = new Thread(() -> {
			try
			{
				start.await();
				for(int i = 0; i < 500; i++)
				{
					PlayerListener l = new PlayerListener()
					{};
					registry.add(l);
					registry.remove(l);
				}
			}catch(InterruptedException ignored)
			{}
			allDone.set(true);
		});
		fire.start();
		mutate.start();
		start.countDown();
		assertTrue(allDoneSignal.await(5, TimeUnit.SECONDS),
			"threads should finish");
		assertFalse(anyError.get());
		assertTrue(firstFire.await(5, TimeUnit.SECONDS),
			"fire thread should have delivered at least once");
		registry.remove(permanent);
		assertTrue(received.get() > 0);
	}
}

package net.wurstclient.music.apple;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * applemusic-like-lyrics {@code timeline.ts} 的 1:1 Java 移植。
 *
 * <p>播放行 = 时间落在 [start, end) 的行；高亮行 = 播放行 + 多行高亮保留的
 * 已播完行（新行开始播放或全部播完时冲刷）；scrollToIndex = 高亮组最小索引；
 * 行间隔 ≥ 4000ms 判定为间奏（无高亮时聚焦间奏点）；seek 走二分清空重算；
 * 无高亮且越过最后一行 endTime 判定歌曲结束。</p>
 */
public final class AppleTimeline
{
	/** 间奏判定阈值（毫秒）。 */
	public static final long INTERLUDE_THRESHOLD_MS = 4_000;

	private long[] starts;
	private long[] ends;
	private final List<int[]> interludes = new ArrayList<>();
	private boolean isManualSeeking;
	private int playbackCursor;
	private int interludeCursor;
	private boolean isFocusOnInterlude;

	private final Set<Integer> playingGroups = new HashSet<>();
	private final Set<Integer> highlightedGroups = new HashSet<>();
	private final List<Integer> addedPlaying = new ArrayList<>();
	private final List<Integer> removedPlaying = new ArrayList<>();
	private final List<Integer> addedHighlighted = new ArrayList<>();
	private final List<Integer> removedHighlighted = new ArrayList<>();
	private final List<Integer> expiredHighlighted = new ArrayList<>();

	// 快照
	private long currentTime;
	private boolean isSeeking;
	private int scrollToIndex;
	private int latestHighlightedIndex = -1;
	private boolean isTimelineEmpty = true;
	private boolean isEndOfSong;
	private int activeInterludeIndex = -1;
	private boolean snapshotFocusOnInterlude;

	// diff
	private boolean hasChanged;
	private boolean isInterludeChanged;
	private boolean isScrollToChanged;
	private boolean isTimeJumped;

	public void setTimeBounds(long[] startTimes, long[] endTimes)
	{
		starts = startTimes;
		ends = endTimes;
		calculateInterludes();
		reset();
	}

	private void calculateInterludes()
	{
		interludes.clear();
		for(int i = -1; i < starts.length - 1; i++)
		{
			long prevEnd = i == -1 ? 0 : ends[i];
			long gapEnd = Math.max(prevEnd, starts[i + 1]);
			if(gapEnd - prevEnd >= INTERLUDE_THRESHOLD_MS)
				interludes.add(new int[] {i, (int)prevEnd, (int)gapEnd});
		}
	}

	/** 当前是否处于间奏（-1 表示否）。 */
	public int activeInterlude()
	{
		return activeInterludeIndex;
	}

	/** 间奏锚点行索引。 */
	public int interludeAnchorLine()
	{
		return interludes.get(activeInterludeIndex)[0];
	}

	public long interludeStart()
	{
		return interludes.get(activeInterludeIndex)[1];
	}

	public long interludeEnd()
	{
		return interludes.get(activeInterludeIndex)[2];
	}

	public long getCurrentTime()
	{
		return currentTime;
	}

	public boolean isSeeking()
	{
		return isSeeking;
	}

	public boolean isFocusOnInterlude()
	{
		return snapshotFocusOnInterlude;
	}

	public boolean isEndOfSong()
	{
		return isEndOfSong;
	}

	public boolean isTimelineEmpty()
	{
		return isTimelineEmpty;
	}

	public int scrollToIndex()
	{
		return scrollToIndex;
	}

	public int latestHighlightedIndex()
	{
		return latestHighlightedIndex;
	}

	public boolean isHighlighted(int index)
	{
		return highlightedGroups.contains(index);
	}

	public boolean isPlaying(int index)
	{
		return playingGroups.contains(index);
	}

	public boolean hasChanged()
	{
		return hasChanged;
	}

	public boolean isInterludeChanged()
	{
		return isInterludeChanged;
	}

	public boolean isScrollToChanged()
	{
		return isScrollToChanged;
	}

	public boolean isTimeJumped()
	{
		return isTimeJumped;
	}

	public List<Integer> addedHighlighted()
	{
		return addedHighlighted;
	}

	public List<Integer> removedHighlighted()
	{
		return removedHighlighted;
	}

	public void setSeekingState(boolean seeking)
	{
		isManualSeeking = seeking;
		isSeeking = seeking;
	}

	public void sync(long time, boolean forceSeek)
	{
		addedPlaying.clear();
		removedPlaying.clear();
		addedHighlighted.clear();
		removedHighlighted.clear();
		expiredHighlighted.clear();

		int prevInterlude = activeInterludeIndex;
		boolean prevFocus = snapshotFocusOnInterlude;
		int prevScrollTo = scrollToIndex;

		boolean isTimeRegression = time < currentTime;
		boolean isJump = forceSeek || isTimeRegression;

		isSeeking = isManualSeeking || isJump;
		if(isSeeking)
			performSeek(time);
		else
			performPlayback(time);

		updateInterludeState(time, isJump);

		isInterludeChanged = prevInterlude != activeInterludeIndex;
		boolean isFocusChanged =
			prevFocus != snapshotFocusOnInterlude;
		isScrollToChanged = prevScrollTo != scrollToIndex;

		hasChanged = isJump || !addedPlaying.isEmpty()
			|| !removedPlaying.isEmpty() || !addedHighlighted.isEmpty()
			|| !removedHighlighted.isEmpty() || isInterludeChanged
			|| isFocusChanged || isScrollToChanged;

		currentTime = time;
		isTimelineEmpty = highlightedGroups.isEmpty();

		latestHighlightedIndex = -1;
		for(int id : highlightedGroups)
			if(id > latestHighlightedIndex)
				latestHighlightedIndex = id;

		isEndOfSong = false;
		if(highlightedGroups.isEmpty() && starts.length > 0
			&& time >= ends[starts.length - 1])
			isEndOfSong = true;

		isTimeJumped = isJump;
	}

	private void performPlayback(long time)
	{
		// 清理不再播放的行
		for(int id : new ArrayList<>(playingGroups))
			if(time < starts[id] || ends[id] <= time)
			{
				playingGroups.remove(id);
				removedPlaying.add(id);
			}

		// 顺序查找并激活新的播放行
		int cursor = Math.max(0, playbackCursor);
		int len = starts.length;
		while(cursor < len)
		{
			if(starts[cursor] > time)
				break;
			if(starts[cursor] <= time && ends[cursor] > time
				&& !playingGroups.contains(cursor))
			{
				playingGroups.add(cursor);
				addedPlaying.add(cursor);
			}
			cursor++;
		}

		if(!playingGroups.isEmpty())
		{
			int minPlaying = Integer.MAX_VALUE;
			for(int id : playingGroups)
				if(id < minPlaying)
					minPlaying = id;
			playbackCursor = minPlaying;
		}else
			playbackCursor = cursor;

		// 已播完但仍高亮的行
		expiredHighlighted.clear();
		for(int id : highlightedGroups)
			if(!playingGroups.contains(id))
				expiredHighlighted.add(id);

		// 新行开始播放 → 加入高亮
		for(int id : addedPlaying)
		{
			highlightedGroups.add(id);
			addedHighlighted.add(id);
		}

		// 冲刷条件：有新行播放，或高亮全部播完
		boolean shouldTransitionToNext = !addedPlaying.isEmpty();
		boolean isCurrentGroupAllFinished = !expiredHighlighted.isEmpty()
			&& expiredHighlighted.size() == highlightedGroups.size();
		boolean shouldFlush =
			shouldTransitionToNext || isCurrentGroupAllFinished;

		if(shouldFlush)
			for(int id : expiredHighlighted)
			{
				highlightedGroups.remove(id);
				removedHighlighted.add(id);
			}

		// 高亮组有变化时更新滚动目标 = 最小高亮
		if((!addedPlaying.isEmpty() || shouldFlush)
			&& !highlightedGroups.isEmpty())
		{
			int min = Integer.MAX_VALUE;
			for(int id : highlightedGroups)
				if(id < min)
					min = id;
			scrollToIndex = min;
		}
	}

	private void performSeek(long time)
	{
		for(int id : new ArrayList<>(playingGroups))
			removedPlaying.add(id);
		for(int id : new ArrayList<>(highlightedGroups))
			removedHighlighted.add(id);
		playingGroups.clear();
		highlightedGroups.clear();

		// 二分查找第一个 startTime >= time 的行
		int left = 0;
		int right = starts.length - 1;
		int firstGreaterOrEqual = starts.length;
		while(left <= right)
		{
			int mid = left + right >>> 1;
			if(starts[mid] >= time)
			{
				firstGreaterOrEqual = mid;
				right = mid - 1;
			}else
				left = mid + 1;
		}

		int minPlaying = Integer.MAX_VALUE;
		int startIndex = Math.min(firstGreaterOrEqual, starts.length - 1);
		for(int i = startIndex; i >= 0; i--)
			if(starts[i] <= time && ends[i] > time)
			{
				playingGroups.add(i);
				highlightedGroups.add(i);
				addedPlaying.add(i);
				addedHighlighted.add(i);
				if(i < minPlaying)
					minPlaying = i;
			}

		if(!highlightedGroups.isEmpty())
		{
			scrollToIndex = minPlaying;
			playbackCursor = minPlaying;
		}else
		{
			// 跳到两行间隔：聚焦下一行
			scrollToIndex = firstGreaterOrEqual;
			playbackCursor = firstGreaterOrEqual;
		}
	}

	private void updateInterludeState(long time, boolean isSeekNow)
	{
		int active = -1;
		if(!interludes.isEmpty())
		{
			if(isSeekNow)
			{
				// 二分
				int left = 0;
				int right = interludes.size() - 1;
				int cursor = interludes.size();
				while(left <= right)
				{
					int mid = left + right >>> 1;
					if(interludes.get(mid)[2] > time)
					{
						cursor = mid;
						right = mid - 1;
					}else
						left = mid + 1;
				}
				interludeCursor = cursor;
				if(cursor < interludes.size())
				{
					int[] inter = interludes.get(cursor);
					if(time >= inter[1] && time < inter[2])
						active = cursor;
				}
			}else
				while(interludeCursor < interludes.size())
				{
					int[] inter = interludes.get(interludeCursor);
					if(time >= inter[1] && time < inter[2])
					{
						active = interludeCursor;
						break;
					}else if(time >= inter[2])
						interludeCursor++;
					else
						break;
				}
		}

		activeInterludeIndex = active;

		if(active != -1 && highlightedGroups.isEmpty())
			isFocusOnInterlude = true;
		else if(!highlightedGroups.isEmpty() || active == -1)
			isFocusOnInterlude = false;
		snapshotFocusOnInterlude = isFocusOnInterlude;
	}

	private void reset()
	{
		playbackCursor = 0;
		interludeCursor = 0;
		isFocusOnInterlude = false;
		playingGroups.clear();
		highlightedGroups.clear();
		currentTime = 0;
		isSeeking = false;
		scrollToIndex = 0;
		latestHighlightedIndex = -1;
		isTimelineEmpty = true;
		isEndOfSong = false;
		activeInterludeIndex = -1;
		snapshotFocusOnInterlude = false;
	}
}

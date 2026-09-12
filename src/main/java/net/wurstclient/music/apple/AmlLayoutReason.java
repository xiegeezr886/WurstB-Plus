package net.wurstclient.music.apple;

/**
 * applemusic-like-lyrics {@code lyric-player/base/consts.ts} 中
 * {@code LayoutReason} 与 {@code LayoutReasonStrategyMap} 的移植。
 *
 * <p>注意 {@code snapPosY} 只在连续拖动时为真：载入歌词（
 * {@link #REBUILD_VIEW}）与跳转进度都刻意保留弹簧，让歌词行从容器下方远处
 * 飞入，这是 AMLL 观感的一部分。</p>
 */
public enum AmlLayoutReason
{
	/** 正常播放时间推进。 */
	PLAYBACK_TICK(false, false, false),
	/** 容器或窗口尺寸调整。 */
	RESIZE(true, false, false),
	/** 用户交互挂起开始（触摸 / 滚轮触发）。 */
	INTERACTION_START(true, true, false),
	/** 连续高频滚动（手指拖动或松手后的惯性滑动）。 */
	CONTINUOUS_SCROLL(true, true, true),
	/** 离散单步滚动（鼠标滚轮单次滚动）。 */
	DISCRETE_SCROLL(true, true, false),
	/** 用户交互结束并恢复自动对齐。 */
	INTERACTION_END(false, false, false),
	/** 跳转播放进度。 */
	SEEK(true, true, false),
	/** 重新构建歌词视图。 */
	REBUILD_VIEW(true, true, false),
	/** 视图结构或样式配置改变。 */
	CONFIG_CHANGE(true, false, false);

	private final boolean disableStagger;
	private final boolean resetInterlude;
	private final boolean snapPosY;

	AmlLayoutReason(boolean disableStagger, boolean resetInterlude,
		boolean snapPosY)
	{
		this.disableStagger = disableStagger;
		this.resetInterlude = resetInterlude;
		this.snapPosY = snapPosY;
	}

	/** 是否禁用阶梯交错动画。 */
	public boolean disableStagger()
	{
		return disableStagger;
	}

	/** 是否重置间奏圆点动画。 */
	public boolean resetInterlude()
	{
		return resetInterlude;
	}

	/** 是否瞬移 Y 轴位置而不经过弹簧动画。 */
	public boolean snapPosY()
	{
		return snapPosY;
	}
}

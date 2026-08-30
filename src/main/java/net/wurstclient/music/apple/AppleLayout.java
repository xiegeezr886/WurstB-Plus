package net.wurstclient.music.apple;

/**
 * applemusic-like-lyrics {@code layout.ts} 的 1:1 Java 移植。
 *
 * <p>前缀和缓存行高度；焦点度量 focalTopY + 对齐锚点偏移（center = 行高/2）；
 * 视口起点 = 容器高 × alignPosition − anchorOffset − scrollOffset −
 * focalTopY；行 y = 视口起点 + 前缀和（间奏之后的行额外加间奏高度）；
 * 滚动边界 min/max 按物理滚动区间计算。</p>
 */
public final class AppleLayout
{
	public static final float ALIGN_POSITION = 0.35F;
	public static final float OVERSCAN_PX = 300F;

	private double[] heights = new double[0];
	private double[] prefixSums = new double[0];
	private boolean[] isMeasured = new boolean[0];
	private boolean prefixDirty = true;
	private double totalLyricHeight;

	// 帧结果
	private double viewportStartY;
	private double[] lineYs = new double[0];
	private boolean[] inViewport = new boolean[0];
	private boolean hasInterlude;
	private double interludeY;
	private double bottomLineY;
	private boolean isBottomLineInViewport;

	// 帧边界
	private double minOffset;
	private double maxOffset;

	private double focalTopY;
	private double anchorOffset;

	public void initHeights(int count, double defaultHeight)
	{
		heights = new double[count];
		isMeasured = new boolean[count];
		for(int i = 0; i < count; i++)
			heights[i] = defaultHeight;
		prefixSums = new double[count + 1];
		lineYs = new double[count];
		inViewport = new boolean[count];
		prefixDirty = true;
		viewportStartY = 0;
		hasInterlude = false;
	}

	public void setLineHeight(int index, double height)
	{
		if(index < 0 || index >= heights.length)
			return;
		if(heights[index] != height || !isMeasured[index])
		{
			heights[index] = height;
			isMeasured[index] = true;
			prefixDirty = true;
		}
	}

	private void ensurePrefixSums()
	{
		if(!prefixDirty)
			return;
		double sum = 0;
		prefixSums[0] = 0;
		for(int i = 0; i < heights.length; i++)
		{
			sum += heights[i];
			prefixSums[i + 1] = sum;
		}
		totalLyricHeight = sum;
		prefixDirty = false;
	}

	/**
	 * 帧首：解析焦点度量并计算物理滚动边界。
	 *
	 * @param containerHeight 容器高度
	 * @param focalLine 焦点行索引（-1 = 无焦点）
	 * @param interludeAnchor 间奏锚点行（-1 = 无）
	 * @param interludeHeight 间奏点总高度
	 * @param bottomLineHeight 底栏高度
	 */
	public void beginFrame(double containerHeight, int focalLine,
		int interludeAnchor, double interludeHeight, double bottomLineHeight)
	{
		ensurePrefixSums();
		resolveMetrics(focalLine, interludeAnchor, interludeHeight,
			bottomLineHeight);

		if(!metricsValid)
		{
			minOffset = 0;
			maxOffset = 0;
			return;
		}

		minOffset = Math.min(0, -focalTopY);
		double basePosWithoutScroll = -focalTopY + containerHeight
			* ALIGN_POSITION - anchorOffset;
		double totalContentHeight = totalLyricHeight;
		if(interludeAnchor >= 0)
			totalContentHeight += interludeHeight;
		double rawMax = basePosWithoutScroll + totalContentHeight
			- containerHeight / 2;
		maxOffset = Math.max(0, rawMax);
	}

	private boolean metricsValid;

	private void resolveMetrics(int focalLine, int interludeAnchor,
		double interludeHeight, double bottomLineHeight)
	{
		int count = heights.length;
		if(count == 0 || focalLine < 0 || focalLine >= count)
		{
			metricsValid = false;
			focalTopY = 0;
			anchorOffset = 0;
			return;
		}

		focalTopY = prefixSums[focalLine];
		if(interludeAnchor >= 0 && focalLine > interludeAnchor)
			focalTopY += interludeHeight;
		double targetHeight = prefixSums[focalLine + 1]
			- prefixSums[focalLine];
		anchorOffset = targetHeight / 2; // Center 锚点
		metricsValid = true;
	}

	/**
	 * 帧尾：按钳制后的滚动偏移提交行指令。
	 */
	public void commit(double containerHeight, double scrollOffset,
		int interludeAnchor, double interludeHeight, double bottomLineHeight)
	{
		ensurePrefixSums();
		if(!metricsValid)
			return;

		viewportStartY = containerHeight * ALIGN_POSITION - anchorOffset
			- scrollOffset - focalTopY;

		double motionBuffer = containerHeight * 0.4;
		double topBound = -OVERSCAN_PX - motionBuffer;
		double bottomBound = containerHeight + OVERSCAN_PX + motionBuffer;

		for(int i = 0; i < heights.length; i++)
		{
			double lineY = viewportStartY + prefixSums[i];
			if(interludeAnchor >= 0 && i > interludeAnchor)
				lineY += interludeHeight;
			lineYs[i] = lineY;
			double lineH = prefixSums[i + 1] - prefixSums[i];
			inViewport[i] = lineY <= bottomBound
				&& lineY + lineH >= topBound;
		}

		if(interludeAnchor >= 0)
		{
			hasInterlude = true;
			interludeY = viewportStartY + prefixSums[interludeAnchor + 1];
		}else
		{
			hasInterlude = false;
			interludeY = 0;
		}

		double bottomY = viewportStartY + totalLyricHeight;
		if(interludeAnchor >= 0)
			bottomY += interludeHeight;
		bottomLineY = bottomY;
		double motionBuffer2 = containerHeight * 0.4;
		isBottomLineInViewport = bottomY <= containerHeight + OVERSCAN_PX
			+ motionBuffer2 && bottomY + bottomLineHeight
				>= -OVERSCAN_PX - motionBuffer2;
	}

	public double lineY(int index)
	{
		return lineYs[index];
	}

	public boolean isInViewport(int index)
	{
		return inViewport[index];
	}

	public double minOffset()
	{
		return minOffset;
	}

	public double maxOffset()
	{
		return maxOffset;
	}

	public boolean hasInterlude()
	{
		return hasInterlude;
	}

	public double interludeY()
	{
		return interludeY;
	}

	public double bottomLineY()
	{
		return bottomLineY;
	}

	public boolean isBottomLineInViewport()
	{
		return isBottomLineInViewport;
	}
}

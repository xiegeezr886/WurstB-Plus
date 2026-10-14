package net.wurstclient.infinitegui;

import java.util.ArrayDeque;

import org.lwjgl.glfw.GLFW;

import com.google.gson.JsonObject;

import net.minecraft.client.gui.GuiGraphics;

/**
 * CPS 显示（上游 {@code CPSItem}）。
 *
 * <p>
 * 上游：前后缀默认 {@code "[CPS: "} / {@code "]"}，左右都显示时中间是 {@code " | "}；
 * 计数来自它自己的 {@code CPSDetector}（最近一秒内的点击）。这里用同一口径：两个
 * {@link ArrayDeque} 记时间戳、渲染时剪掉一秒之前的。点击由 {@link InfiniteHud} 从客户端的
 * 鼠标事件转进来。</p>
 */
public final class CpsWindow extends InfiniteWindow
{
	private static final long WINDOW_NANOS = 1_000_000_000L;

	private final ArrayDeque<Long> leftClicks = new ArrayDeque<>();
	private final ArrayDeque<Long> rightClicks = new ArrayDeque<>();

	private boolean showLeft = true;
	private boolean showRight = true;

	public CpsWindow()
	{
		super("CPS显示", "显示左右键CPS");
		setAffix("[CPS: ", "]");
	}

	public void onClick(int button)
	{
		long now = System.nanoTime();
		if(button == GLFW.GLFW_MOUSE_BUTTON_LEFT)
			leftClicks.addLast(now);
		else if(button == GLFW.GLFW_MOUSE_BUTTON_RIGHT)
			rightClicks.addLast(now);
	}

	@Override
	protected int contentWidth()
	{
		return InfiniteText.textWidth(getText());
	}

	@Override
	protected int contentHeight()
	{
		return InfiniteText.textHeight();
	}

	@Override
	protected void drawContent(GuiGraphics graphics, int x, int y,
		float partialTicks)
	{
		InfiniteText.drawShadowed(graphics,
			net.wurstclient.WurstClient.MC.font, getText(), x, y, TEXT_COLOR);
	}

	private String getText()
	{
		long cutoff = System.nanoTime() - WINDOW_NANOS;
		return InfiniteText.cps(getPrefix(), prune(leftClicks, cutoff),
			prune(rightClicks, cutoff), getSuffix(), showLeft, showRight);
	}

	private static int prune(ArrayDeque<Long> clicks, long cutoff)
	{
		while(!clicks.isEmpty() && clicks.peekFirst() < cutoff)
			clicks.removeFirst();
		return clicks.size();
	}

	@Override
	public void load(JsonObject json)
	{
		super.load(json);
		if(json.has("showLeft"))
			showLeft = json.get("showLeft").getAsBoolean();
		if(json.has("showRight"))
			showRight = json.get("showRight").getAsBoolean();
	}

	@Override
	public void save(JsonObject json)
	{
		super.save(json);
		json.addProperty("showLeft", showLeft);
		json.addProperty("showRight", showRight);
	}
}

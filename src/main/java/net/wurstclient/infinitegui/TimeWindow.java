package net.wurstclient.infinitegui;

import java.time.LocalDateTime;

import com.google.gson.JsonObject;

import net.minecraft.client.gui.GuiGraphics;

/**
 * 时间显示（上游 {@code TimeItem}）。
 *
 * <p>
 * 上游：{@code updateIntervalMs = 1000}，{@code showDate} 默认开，文案
 * {@code "%Y-%m-%d | %H:%M:%S"}。这里按同一套走——每 1000ms 重算一次字符串。</p>
 */
public final class TimeWindow extends InfiniteWindow
{
	private static final long UPDATE_INTERVAL_MS = 1000;

	private boolean showDate = true;
	private String text = "";
	private long lastUpdateNanos;

	public TimeWindow()
	{
		super("时间显示", "显示当前时间和日期");
	}

	@Override
	protected void tick(float partialTicks)
	{
		long now = System.nanoTime();
		if(lastUpdateNanos != 0
			&& (now - lastUpdateNanos) / 1_000_000L < UPDATE_INTERVAL_MS)
			return;

		lastUpdateNanos = now;
		text = InfiniteText.time(LocalDateTime.now(), showDate);
	}

	@Override
	protected int contentWidth()
	{
		return InfiniteText.textWidth(getPrefix() + text + getSuffix());
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
			net.wurstclient.WurstClient.MC.font,
			getPrefix() + text + getSuffix(), x, y, TEXT_COLOR);
	}

	@Override
	public void load(JsonObject json)
	{
		super.load(json);
		if(json.has("showDate"))
			showDate = json.get("showDate").getAsBoolean();
	}

	@Override
	public void save(JsonObject json)
	{
		super.save(json);
		json.addProperty("showDate", showDate);
	}
}

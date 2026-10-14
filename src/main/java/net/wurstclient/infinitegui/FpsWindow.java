package net.wurstclient.infinitegui;

import com.google.gson.JsonObject;

import net.minecraft.client.gui.GuiGraphics;

/**
 * FPS 显示（上游 {@code FpsItem}）。
 *
 * <p>
 * 上游：{@code updateIntervalMs = 1000}，前后缀默认 {@code "["} / {@code "FPS]"}，
 * {@code showGuiFPS} 打开时文案是 {@code "a | b"}。它自己数帧（{@code frameCount++} 在
 * 每帧的 {@code RenderBeforeGui}，{@code guiFrameCount++} 在内容真的重画时），所以"GUI 帧率"
 * 在它的缓存命中时会低于游戏帧率；本移植每帧都重画内容，两者相等，字段与格式照样保留。</p>
 */
public final class FpsWindow extends InfiniteWindow
{
	private static final long UPDATE_INTERVAL_MS = 1000;

	private int frames;
	private int fps;
	private int guiFrames;
	private int guiFps;
	private boolean showGuiFps;
	private long lastUpdateNanos;

	public FpsWindow()
	{
		super("FPS显示", "显示当前帧率");
		setAffix("[", "FPS]");
	}

	/** 上游是在 {@code RenderBeforeGui} 里数帧的，等价于"每帧一次"。 */
	@Override
	protected void tick(float partialTicks)
	{
		frames++;
		guiFrames++;

		long now = System.nanoTime();
		if(lastUpdateNanos == 0)
		{
			lastUpdateNanos = now;
			return;
		}

		long elapsedMs = (now - lastUpdateNanos) / 1_000_000L;
		if(elapsedMs < UPDATE_INTERVAL_MS)
			return;

		fps = Math.round(frames * 1000F / elapsedMs);
		guiFps = Math.round(guiFrames * 1000F / elapsedMs);
		frames = 0;
		guiFrames = 0;
		lastUpdateNanos = now;
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
		return InfiniteText.fps(getPrefix(), fps, getSuffix(), showGuiFps,
			guiFps);
	}

	@Override
	public void load(JsonObject json)
	{
		super.load(json);
		if(json.has("showGuiFps"))
			showGuiFps = json.get("showGuiFps").getAsBoolean();
	}

	@Override
	public void save(JsonObject json)
	{
		super.save(json);
		json.addProperty("showGuiFps", showGuiFps);
	}
}

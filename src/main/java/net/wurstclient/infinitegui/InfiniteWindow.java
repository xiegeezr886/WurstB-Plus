package net.wurstclient.infinitegui;

import com.google.gson.JsonObject;

import net.minecraft.client.gui.GuiGraphics;
import net.wurstclient.gui.glass.GlassPanels;

/**
 * 无限GUI 的「信息项窗口」基类：上游 {@code Item} + {@code WindowModule} 里与 HUD 有关的那部分。
 *
 * <p>
 * 结构照上游走：一个窗口有 type（= 上游 {@code Item::name}，也是配置里的键）、启用状态、
 * 位置、前后缀（上游 {@code AffixModule}）、以及"内容自己量尺寸、窗口按内容自适应"的约定
 * （上游 {@code WindowModule} 的窗口是自动尺寸的）。</p>
 *
 * <p>
 * <b>材质是本工程的决定</b>：上游窗口底是 80% 黑（{@code WindowStyleModule.bgColor} 默认
 * {@code (0,0,0,0.8)}），这里改走 liquid glass（{@link GlassPanels}），玻璃不可用时回退到上游
 * 那个 80% 黑——所以外观是玻璃，几何与行为仍是上游的。</p>
 */
public abstract class InfiniteWindow
{
	/** WurstB+ 文本卡片的几何：左右各 2、上下各 1（原 TextHudElement 就是 +4 / +2）。 */
	public static final int PADDING_X = 2;
	public static final int PADDING_Y = 1;
	/** WurstB+ 的 HUD 卡片圆角（玻璃化时用的那个）。 */
	public static final int CARD_RADIUS = 4;
	/** 上游 {@code WindowStyleModule.bgColor} 默认 (0,0,0,0.8)——玻璃不可用时的回退底色。 */
	public static final int FALLBACK_BG = 0xCC000000;

	protected static final int TEXT_COLOR = 0xFFFFFFFF;
	protected static final int TEXT_SHADOW = 0x99000000;

	private final String type;
	private final String description;
	private boolean enabled;
	private int x = 12;
	private int y = 12;
	private boolean transparentBackground;
	private String prefix = "";
	private String suffix = "";

	protected InfiniteWindow(String type, String description)
	{
		this.type = type;
		this.description = description;
	}

	public final String getType()
	{
		return type;
	}

	public final String getDescription()
	{
		return description;
	}

	public final boolean isEnabled()
	{
		return enabled;
	}

	public final void setEnabled(boolean enabled)
	{
		this.enabled = enabled;
	}

	public final int getX()
	{
		return x;
	}

	public final int getY()
	{
		return y;
	}

	public final void moveTo(int x, int y)
	{
		this.x = x;
		this.y = y;
	}

	protected final void setTransparentBackground(boolean transparent)
	{
		transparentBackground = transparent;
	}

	public final String getPrefix()
	{
		return prefix;
	}

	public final String getSuffix()
	{
		return suffix;
	}

	/** 上游的 AffixModule：默认前后缀由各信息项自己在 Reset 里给。 */
	protected final void setAffix(String prefix, String suffix)
	{
		this.prefix = prefix;
		this.suffix = suffix;
	}

	/** 窗口宽 = 内容宽 + 两侧内边距（上游窗口是自动尺寸的）。 */
	public final int getWidth()
	{
		return Math.max(1, contentWidth()) + PADDING_X * 2;
	}

	public final int getHeight()
	{
		return Math.max(1, contentHeight()) + PADDING_Y * 2;
	}

	public final boolean contains(double mouseX, double mouseY)
	{
		return mouseX >= x && mouseX < x + getWidth() && mouseY >= y
			&& mouseY < y + getHeight();
	}

	/** 每帧画一次。{@code mouseX/mouseY} 用来给玻璃算鼠标高光。 */
	public final void render(GuiGraphics graphics, int mouseX, int mouseY,
		float partialTicks)
	{
		if(!enabled)
			return;

		tick(partialTicks);

		if(!transparentBackground)
			GlassPanels.draw(graphics, x, y, x + getWidth(), y + getHeight(),
				CARD_RADIUS, FALLBACK_BG);

		drawContent(graphics, x + PADDING_X, y + PADDING_Y, partialTicks);
	}

	/** 每秒/每帧的更新钩子；默认什么都不做。 */
	protected void tick(float partialTicks)
	{}

	protected abstract int contentWidth();

	protected abstract int contentHeight();

	/** 在窗口内边距之内的局部坐标里画内容。 */
	protected abstract void drawContent(GuiGraphics graphics, int x, int y,
		float partialTicks);

	/** 配置键沿用上游：{@code type} / {@code isEnabled}，其余是本工程补的（位置与前后缀）。 */
	public void load(JsonObject json)
	{
		if(json.has("isEnabled"))
			enabled = json.get("isEnabled").getAsBoolean();
		if(json.has("x"))
			x = json.get("x").getAsInt();
		if(json.has("y"))
			y = json.get("y").getAsInt();
		if(json.has("prefix"))
			prefix = json.get("prefix").getAsString();
		if(json.has("suffix"))
			suffix = json.get("suffix").getAsString();
	}

	public void save(JsonObject json)
	{
		json.addProperty("type", type);
		json.addProperty("isEnabled", enabled);
		json.addProperty("x", x);
		json.addProperty("y", y);
		json.addProperty("prefix", prefix);
		json.addProperty("suffix", suffix);
	}
}

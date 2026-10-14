package net.wurstclient.infinitegui;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;

/**
 * 上游那几项的画法：带阴影的一行字（{@code ImGuiStd::TextColoredShadow}）+ 三项的文案格式。
 *
 * <p>
 * 文案格式逐字照上游的三个 {@code DrawContent}：
 * 时间 {@code "%Y-%m-%d | %H:%M:%S"}（关掉日期时 {@code "%H:%M:%S"}）、
 * FPS {@code prefix + 帧率 + suffix}（开了 GUI 帧率是 {@code "a | b"}）、
 * CPS {@code prefix + 左 + (" | ") + 右 + suffix}。这些是纯函数，单测盯着。</p>
 */
public final class InfiniteText
{
	private static final DateTimeFormatter WITH_DATE =
		DateTimeFormatter.ofPattern("yyyy-MM-dd | HH:mm:ss");
	private static final DateTimeFormatter TIME_ONLY =
		DateTimeFormatter.ofPattern("HH:mm:ss");

	private InfiniteText()
	{}

	/** 带 1px 阴影的一行字：先按偏移画一层暗色，再画本体（与上游同一个做法）。 */
	public static void drawShadowed(GuiGraphics graphics, Font font,
		String text, int x, int y, int color)
	{
		graphics.drawString(font, text, x + 1, y + 1, InfiniteWindow.TEXT_SHADOW,
			false);
		graphics.drawString(font, text, x, y, color, false);
	}

	public static int textWidth(String text)
	{
		return Minecraft.getInstance().font.width(text);
	}

	public static int textHeight()
	{
		return Minecraft.getInstance().font.lineHeight;
	}

	/** 上游 TimeItem::Update 的格式。 */
	public static String time(LocalDateTime now, boolean showDate)
	{
		return now.format(showDate ? WITH_DATE : TIME_ONLY);
	}

	/** 上游 FpsItem::DrawContent 的格式。 */
	public static String fps(String prefix, int fps, String suffix,
		boolean showGuiFps, int guiFps)
	{
		String value = showGuiFps ? fps + " | " + guiFps : Integer.toString(fps);
		return prefix + value + suffix;
	}

	/** 上游 CPSItem::DrawContent 的格式：两边都显示时中间是 " | "。 */
	public static String cps(String prefix, int left, int right, String suffix,
		boolean showLeft, boolean showRight)
	{
		String leftText = showLeft ? Integer.toString(left) : "";
		String rightText = showRight ? Integer.toString(right) : "";
		String middle = showLeft && showRight ? " | " : "";
		return prefix + leftText + middle + rightText + suffix;
	}
}

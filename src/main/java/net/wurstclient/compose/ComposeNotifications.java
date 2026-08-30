package net.wurstclient.compose;

import java.util.List;

import net.minecraft.client.gui.GuiGraphics;
import net.wurstclient.WurstClient;
import net.wurstclient.clickgui2.FlatRenderer;
import net.wurstclient.hud2.NotificationContent;

/**
 * HackAI {@code NotificationContainer}/{@code NotificationView} 渲染的 1:1
 * 等价物：卡片组件（面板底 + 双层流光渐变描边 + 标题/消息）与动画模型
 * （入场 300ms easeOut 横滑、存活 2500ms、退场 250ms easeIn 滑出+淡出、
 * 堆叠位置独立平滑动画）均对应原版。
 *
 * <p>数据与计时保留在 {@code HudNotificationRenderer}（阶段机、存活时长、
 * severity），本类只负责卡片组件的渲染与堆叠布局。</p>
 */
public final class ComposeNotifications
{
	private static final int MIN_WIDTH = 180;
	private static final int PADDING = 8;
	private static final int CARD_RADIUS = 4;

	private ComposeNotifications()
	{}

	/** 一条通知卡片的数据快照（1:1 HackAI：纯标题/消息 + 可选自定义内容）。 */
	public static final class Card
	{
		public final String title;
		public final String message;
		public final NotificationContent content;
		/** 位置动画进度 0..1：入场 0→1，存活 1，退场 1→0。 */
		public float anim = 1;
		/** alpha 乘数：入场/存活 = 1，退场 = anim。 */
		public float fade = 1;
		/** 堆叠平滑位置（相对锚点，独立于 anim 的补位动画）。 */
		public float yOffset;
		private long lastNanos;

		public Card(String title, String message)
		{
			this(title, message, null);
		}

		public Card(String title, String message,
			NotificationContent content)
		{
			this.title = title;
			this.message = message;
			this.content = content;
		}

		public float getWidth()
		{
			if(content != null)
				return MIN_WIDTH;
			int titleW = WurstClient.MC.font.width(title);
			int messageW = WurstClient.MC.font.width(message);
			return Math.max(MIN_WIDTH,
				Math.max(titleW, messageW) + PADDING * 2 + 3);
		}
	}

	/**
	 * 渲染通知卡片列表。锚点已按 HUD 配置换算为屏幕坐标；alignment 决定
	 * 卡片在锚点上的滑动方向。堆叠位置每帧向固定槽位平滑逼近（对应
	 * 原版 relayout 的 bottomMargin 动画）。
	 */
	public static void render(GuiGraphics graphics, List<Card> cards,
		float anchorX, float anchorY, boolean rightAligned,
		boolean bottomAligned, boolean centerAligned, long nowNanos)
	{
		float posY = 0;
		for(Card card : cards)
		{
			float entryW = card.getWidth();
			float entryH = PADDING * 2 + 22;
			float slide = (entryW + 4) * (1 - card.anim);

			float x1;
			if(rightAligned)
				x1 = slide - entryW;
			else if(centerAligned)
				x1 = -entryW / 2 + slide;
			else
				x1 = -slide;

			float targetY;
			if(bottomAligned)
				targetY = posY - entryH;
			else if(centerAligned)
				targetY = posY - entryH / 2;
			else
				targetY = posY;

			// 补位动画：卡片始终完整占位，移除后平滑上移/下移补位
			float dt = card.lastNanos == 0 ? 0.05F
				: Math.min(0.1F,
					(nowNanos - card.lastNanos) / 1_000_000_000F);
			card.lastNanos = nowNanos;
			card.yOffset += (targetY - card.yOffset)
				* (1 - (float)Math.exp(-16 * dt));

			drawCard(graphics, card, anchorX + x1, anchorY + card.yOffset,
				anchorX + x1 + entryW, anchorY + card.yOffset + entryH);

			if(bottomAligned)
				posY -= (entryH + 4);
			else
				posY += (entryH + 4);
		}
	}

	private static void drawCard(GuiGraphics graphics, Card card, float left,
		float top, float right, float bottom)
	{
		float fade = card.fade;
		int l = Math.round(left);
		int t = Math.round(top);
		int r = Math.round(right);
		int b = Math.round(bottom);
		// 卡片底 + 发光 + 流动渐变描边（1:1 HackAI NotificationView）
		FlatRenderer.fillRoundedRect(graphics, l, t, r, b, CARD_RADIUS,
			withAlpha(0x0A0A0A, Math.round(217 * fade)));
		FlatRenderer.drawGradientOutline(graphics, l - 2, t - 2, r + 2, b + 2,
			CARD_RADIUS + 2, FlowingGradient.flowing(l, r - l,
				30 * fade));
		FlatRenderer.drawGradientOutline(graphics, l, t, r, b, CARD_RADIUS,
			FlowingGradient.flowing(l, r - l, 255 * fade));

		// 自定义内容（替代标题/消息）
		if(card.content != null)
		{
			card.content.render(graphics, l, t, r - l, b - t);
			return;
		}

		// 标题（白）+ 消息（半透明白），无强调条/进度条
		int titleY = t + (b - t - WurstClient.MC.font.lineHeight * 2 + 2) / 2;
		graphics.drawString(WurstClient.MC.font, card.title, l + PADDING + 1,
			titleY + 1, withAlpha(0, Math.round(145 * fade)), false);
		graphics.drawString(WurstClient.MC.font, card.title, l + PADDING,
			titleY, withAlpha(0xFFFFFF, Math.round(255 * fade)), false);
		graphics.drawString(WurstClient.MC.font, card.message, l + PADDING + 1,
			titleY + WurstClient.MC.font.lineHeight + 1,
			withAlpha(0, Math.round(145 * fade)), false);
		graphics.drawString(WurstClient.MC.font, card.message, l + PADDING,
			titleY + WurstClient.MC.font.lineHeight,
			withAlpha(0xFFFFFF, Math.round(179 * fade)), false);
	}

	private static int withAlpha(int color, int alpha)
	{
		return Math.max(0, Math.min(255, alpha)) << 24 | color & 0xFFFFFF;
	}

	/** PathInterpolator(0.16, 1, 0.3, 1) —— 入场横滑（HackAI easeOut）。 */
	public static float easeOut(float t)
	{
		return cubicBezier(0.16F, 1F, 0.3F, 1F, t);
	}

	/** PathInterpolator(0.7, 0, 0.84, 0) —— 退场滑出（HackAI easeIn）。 */
	public static float easeIn(float t)
	{
		return cubicBezier(0.7F, 0F, 0.84F, 0F, t);
	}

	/** cubic-bezier 数值求值（牛顿法，8 次迭代足够收敛）。 */
	private static float cubicBezier(float x1, float y1, float x2, float y2,
		float x)
	{
		float t = x;
		for(int i = 0; i < 8; i++)
		{
			float cx = 3 * x1 * (1 - t) * (1 - t) * t + 3 * x2 * (1 - t)
				* t * t + t * t * t - x;
			float dx = 3 * x1 * (1 - t) * (1 - t)
				+ 6 * (x2 - x1) * (1 - t) * t + 3 * (1 - x2) * t * t;
			if(Math.abs(cx) < 1e-5F || dx == 0)
				break;
			float next = t - cx / dx;
			if(next < 0 || next > 1)
				break;
			t = next;
		}
		return 3 * y1 * (1 - t) * (1 - t) * t + 3 * y2 * (1 - t) * t * t
			+ t * t * t;
	}
}

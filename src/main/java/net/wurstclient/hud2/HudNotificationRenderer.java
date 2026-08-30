package net.wurstclient.hud2;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.function.Predicate;

import net.minecraft.client.gui.GuiGraphics;
import net.wurstclient.WurstClient;
import net.wurstclient.compose.ComposeNotifications;
import net.wurstclient.events.GUIRenderListener;

public final class HudNotificationRenderer implements GUIRenderListener
{
	private final ArrayList<NotificationEntry> entries = new ArrayList<>();
	private static final int MAX_NOTIFICATIONS = 5;

	/** HackAI NotificationContainer 的动画参数。 */
	private static final long ENTER_MS = 300;
	private static final long EXIT_MS = 250;

	HudNotificationRenderer() {}

	void addNotification(HudNotification notification)
	{
		synchronized(entries)
		{
			NotificationSeverity severity = notification.getSeverity();
			if(severity == NotificationSeverity.ENABLED
				|| severity == NotificationSeverity.DISABLED)
				for(Iterator<NotificationEntry> it = entries.iterator();
					it.hasNext();)
				{
					NotificationEntry e = it.next();
					if(e.notification.getMessage()
						.equals(notification.getMessage()))
						it.remove();
				}

			while(entries.size() >= MAX_NOTIFICATIONS)
				entries.remove(0);
			entries.add(new NotificationEntry(notification));
		}
	}

	void removeNotificationIf(Predicate<HudNotification> predicate)
	{
		synchronized(entries)
		{
			entries.removeIf(e -> predicate.test(e.notification));
		}
	}

	void renderPreview(GuiGraphics graphics, int x, int y)
	{
		ComposeNotifications.Card preview = new ComposeNotifications.Card(
			"Notifications", "HUD notification preview");
		ComposeNotifications.render(graphics, List.of(preview), x, y, false,
			false, false, System.nanoTime());
	}

	@Override
	public void onRenderGUI(GuiGraphics graphics, float partialTicks)
	{
		if(!WurstClient.INSTANCE.isEnabled())
			return;

		HudLayout layout = WurstClient.INSTANCE.getHudManager().getLayout();
		HudLayout.HudElementConfig config = layout.get("notifications");
		if(config == null || !config.isEnabled())
			return;

		int screenW = graphics.guiWidth();
		int screenH = graphics.guiHeight();
		float anchorX;
		if(config.getHorizontalAlignment()
			.equals(HudLayout.HudElementConfig.HORIZONTAL_RIGHT))
			anchorX = screenW - config.getHorizontalOffset();
		else if(config.getHorizontalAlignment()
			.equals(HudLayout.HudElementConfig.HORIZONTAL_CENTER))
			anchorX = screenW / 2F + config.getHorizontalOffset();
		else
			anchorX = config.getHorizontalOffset();

		float anchorY;
		if(config.getVerticalAlignment()
			.equals(HudLayout.HudElementConfig.VERTICAL_BOTTOM))
			anchorY = screenH - config.getVerticalOffset();
		else if(config.getVerticalAlignment()
			.equals(HudLayout.HudElementConfig.VERTICAL_CENTER))
			anchorY = screenH / 2F + config.getVerticalOffset();
		else
			anchorY = config.getVerticalOffset();

		long now = System.nanoTime();

		graphics.pose().pushPose();
		try
		{
			graphics.pose().translate(anchorX, anchorY, 0);
			graphics.pose().scale(config.getScale(), config.getScale(), 1);
			synchronized(entries)
			{
				// 构建 Compose 卡片快照（数据与计时保留在本类）
				ArrayList<ComposeNotifications.Card> cards =
					new ArrayList<>();
				for(Iterator<NotificationEntry> it = entries.iterator();
					it.hasNext();)
				{
					NotificationEntry entry = it.next();

					if(entry.notification.isExpired())
						entry.active = false;

					entry.update(now);

					if(!entry.active && entry.fade <= 0)
					{
						it.remove();
						continue;
					}

					HudNotification n = entry.notification;
					ComposeNotifications.Card card =
						new ComposeNotifications.Card(
							truncate(n.getTitle(), 26),
							truncate(n.getMessage(), 34), n.getContent());
					card.anim = entry.anim;
					card.fade = entry.fade;
					cards.add(card);
				}
				boolean rightAligned = config.getHorizontalAlignment().equals(
					HudLayout.HudElementConfig.HORIZONTAL_RIGHT);
				boolean centerAligned = config.getHorizontalAlignment().equals(
					HudLayout.HudElementConfig.HORIZONTAL_CENTER);
				boolean bottomAligned = config.getVerticalAlignment().equals(
					HudLayout.HudElementConfig.VERTICAL_BOTTOM);
				ComposeNotifications.render(graphics, cards, 0, 0,
					rightAligned, bottomAligned, centerAligned, now);
			}
		}finally
		{
			graphics.pose().popPose();
		}
	}

	private static String truncate(String text, int maxLen)
	{
		if(text.length() <= maxLen)
			return text;
		return text.substring(0, maxLen - 1) + "…";
	}

	private static final class NotificationEntry
	{
		final HudNotification notification;
		boolean active = true;
		/** 位置动画 0..1：入场 0→1，存活 1，退场 1→0。 */
		float anim;
		/** alpha 乘数：入场/存活 1，退场随动画淡出。 */
		float fade = 1;
		private final long enterStartNanos = System.nanoTime();
		private boolean entered;
		private long exitAtNanos;
		private boolean exiting;
		private long exitStartNanos;

		NotificationEntry(HudNotification notification)
		{
			this.notification = notification;
		}

		/**
		 * HackAI NotificationContainer 阶段机：入场 300ms easeOut 横滑
		 * （期间不淡入），入场完成后启动存活计时，到点退场 250ms easeIn
		 * 滑出 + 淡出。
		 */
		void update(long now)
		{
			if(!entered)
			{
				long elapsed = now - enterStartNanos;
				if(elapsed >= ENTER_MS * 1_000_000L)
				{
					entered = true;
					exitAtNanos = notification.isPersistent()
						? Long.MAX_VALUE
						: now + notification.getDurationMillis() * 1_000_000L;
					anim = 1;
				}else
					anim = ComposeNotifications.easeOut(
						elapsed / (float)(ENTER_MS * 1_000_000L));
				return;
			}
			if(!exiting && now >= exitAtNanos)
			{
				exiting = true;
				exitStartNanos = now;
			}
			if(exiting)
			{
				float t = (now - exitStartNanos)
					/ (float)(EXIT_MS * 1_000_000L);
				if(t >= 1)
				{
					active = false;
					anim = 0;
					fade = 0;
				}else
				{
					float p = 1 - ComposeNotifications.easeIn(t);
					anim = p;
					fade = p;
				}
			}
		}
	}
}

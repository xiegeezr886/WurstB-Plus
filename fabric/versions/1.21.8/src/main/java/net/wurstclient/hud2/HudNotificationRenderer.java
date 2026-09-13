package net.wurstclient.hud2;

import java.util.ArrayList;
import java.util.Iterator;

import net.minecraft.client.gui.Font;
import net.wurstclient.util.render.GuiGraphicsExtractor;
import net.wurstclient.WurstClient;
import net.wurstclient.clickgui2.FlatRenderer;
import net.wurstclient.events.GUIRenderListener;

public final class HudNotificationRenderer implements GUIRenderListener
{
	private final ArrayList<NotificationEntry> entries = new ArrayList<>();
	private static final int MIN_WIDTH = 180;
	private static final int PADDING = 8;
	private static final int CARD_RADIUS = 4;
	private static final int CARD_GAP = 4;
	static final long DURATION_NANOS = 3_000_000_000L;
	private static final int MAX_NOTIFICATIONS = 8;

	HudNotificationRenderer() {}

	void addNotification(String title, String message,
		NotificationSeverity severity)
	{
		synchronized(entries)
		{
			if(severity == NotificationSeverity.ENABLED
				|| severity == NotificationSeverity.DISABLED)
				for(Iterator<NotificationEntry> it = entries.iterator();
					it.hasNext();)
				{
					NotificationEntry e = it.next();
					if(e.message.equals(message))
						it.remove();
				}

			while(entries.size() >= MAX_NOTIFICATIONS)
				entries.remove(0);
			entries.add(new NotificationEntry(title, message, severity));
		}
	}

	void renderPreview(GuiGraphicsExtractor graphics, int x, int y)
	{
		NotificationEntry preview = new NotificationEntry("Notifications",
			"HUD notification preview", NotificationSeverity.INFO);
		drawCard(graphics, preview, x, y, x + MIN_WIDTH,
			y + PADDING * 2 + 22, 1, 0.62F);
	}

	@Override
	public void onRenderGUI(GuiGraphicsExtractor graphics, float partialTicks)
	{
		if(!WurstClient.INSTANCE.isEnabled())
			return;

		HudLayout layout = WurstClient.INSTANCE.getHudManager().getLayout();
		HudLayout.HudElementConfig config = layout.get("notifications");
		if(config == null || !config.isEnabled())
			return;

		int screenW = graphics.guiWidth();
		int screenH = graphics.guiHeight();

		float posY;
		if(config.getVerticalAlignment()
			.equals(HudLayout.HudElementConfig.VERTICAL_BOTTOM))
			posY = screenH - config.getVerticalOffset();
		else
			posY = config.getVerticalOffset();

		long now = System.nanoTime();

		synchronized(entries)
		{
			for(Iterator<NotificationEntry> it = entries.iterator();
				it.hasNext();)
			{
				NotificationEntry entry = it.next();
				float visibility = entry.update(now);
				float lifetimeProgress = entry.getLifetimeProgress(now);

				if(lifetimeProgress >= 1 && entry.active)
					entry.active = false;

				if(!entry.active && visibility <= 0)
				{
					it.remove();
					continue;
				}

				float entryW = entry.getWidth();
				float entryH = PADDING * 2 + 22;
				float slide = (entryW + CARD_GAP) * (1 - visibility);

				float x1, x2;
				if(config.getHorizontalAlignment()
					.equals(HudLayout.HudElementConfig.HORIZONTAL_RIGHT))
				{
					x2 = screenW - config.getHorizontalOffset() + slide;
					x1 = x2 - entryW;
				}else
				{
					x1 = config.getHorizontalOffset() - slide;
					x2 = x1 + entryW;
				}

				float cardY;
				if(config.getVerticalAlignment()
					.equals(HudLayout.HudElementConfig.VERTICAL_BOTTOM))
					cardY = posY - entryH * visibility;
				else
					cardY = posY;

				int top = Math.round(cardY);
				int bottom = top + Math.round(entryH);
				int left = Math.round(x1);
				int right = Math.round(x2);

				drawCard(graphics, entry, left, top, right, bottom,
					visibility, lifetimeProgress);

				if(config.getVerticalAlignment()
					.equals(HudLayout.HudElementConfig.VERTICAL_BOTTOM))
					posY -= (entryH + CARD_GAP) * visibility;
				else
					posY += (entryH + CARD_GAP) * visibility;
			}
		}
	}

	private void drawCard(GuiGraphicsExtractor graphics, NotificationEntry entry,
		int left, int top, int right, int bottom, float visibility,
		float lifetimeProgress)
	{
		FlatRenderer.fillRoundedRect(graphics, left, top, right, bottom,
			CARD_RADIUS, withAlpha(0x050505, Math.round(173 * visibility)));

		FlatRenderer.drawRoundedOutline(graphics, left, top, right, bottom,
			CARD_RADIUS, withAlpha(0xFFFFFF, Math.round(16 * visibility)));

		int accColor = getAccentColor(entry.severity);
		FlatRenderer.fillRoundedRect(graphics, left + 1, top + 2, left + 3,
			bottom - 2, 1,
			withAlpha(accColor, Math.round(240 * visibility)));

		Font font = WurstClient.MC.font;
		String title = truncate(entry.title, 26);
		String message = truncate(entry.message, 34);

		int textX = left + PADDING;
		int titleY = top + (bottom - top - font.lineHeight * 2 + 2) / 2;

		graphics.text(font, title, textX + 1, titleY + 1,
			withAlpha(0, Math.round(145 * visibility)), false);
		graphics.text(font, title, textX, titleY,
			withAlpha(0xFFF2F4F7, Math.round(255 * visibility)), false);

		graphics.text(font, message, textX + 1, titleY + font.lineHeight + 1,
			withAlpha(0, Math.round(145 * visibility)), false);
		graphics.text(font, message, textX, titleY + font.lineHeight,
			withAlpha(0xFF727B88, Math.round(255 * visibility)), false);

		int barLeft = left + PADDING;
		int barRight = right - PADDING;
		int barTop = bottom - 4;
		FlatRenderer.fillRoundedRect(graphics, barLeft, barTop, barRight,
			barTop + 2, 1,
			withAlpha(0x2A3036, Math.round(170 * visibility)));
		int progressRight = barLeft
			+ Math.round((barRight - barLeft) * lifetimeProgress);
		if(progressRight > barLeft)
			FlatRenderer.fillRoundedRect(graphics, barLeft, barTop,
				progressRight, barTop + 2, 1,
				withAlpha(accColor, Math.round(230 * visibility)));
	}

	static float lifetimeProgress(long spawnNanos, long nowNanos)
	{
		return Math.max(0, Math.min(1,
			(float)(nowNanos - spawnNanos) / DURATION_NANOS));
	}

	private static int getAccentColor(NotificationSeverity severity)
	{
		if(severity == NotificationSeverity.DISABLED
			|| severity == NotificationSeverity.ERROR)
			return 0xFFFFFFFF;
		WurstClient.INSTANCE.getGui().updateColors();
		return WurstClient.INSTANCE.getGui().getTheme().accent(1);
	}

	private static String truncate(String text, int maxLen)
	{
		if(text.length() <= maxLen)
			return text;
		return text.substring(0, maxLen - 1) + "\u2026";
	}

	private static int withAlpha(int color, int alpha)
	{
		return Math.max(0, Math.min(255, alpha)) << 24 | color & 0xFFFFFF;
	}

	private static final class NotificationEntry
	{
		final String title;
		final String message;
		final NotificationSeverity severity;
		boolean active = true;
		final long spawnNanos = System.nanoTime();
		private float progress;
		private long lastUpdateNanos;

		NotificationEntry(String title, String message,
			NotificationSeverity severity)
		{
			this.title = title;
			this.message = message;
			this.severity = severity;
		}

		float getWidth()
		{
			Font font = WurstClient.MC.font;
			float w = font.width(truncate(title, 26));
			float mw = font.width(truncate(message, 34));
			float contentW = Math.max(w, mw) + PADDING * 2 + 3;
			return Math.max(MIN_WIDTH, contentW);
		}

		float update(long now)
		{
			if(lastUpdateNanos == 0)
			{
				lastUpdateNanos = now;
				return progress;
			}

			float frameTime = Math.min(0.05F,
				(now - lastUpdateNanos) / 1_000_000_000F);
			lastUpdateNanos = now;
			float target = active ? 1 : 0;
			progress += (target - progress)
				* (1 - (float)Math.exp(-16 * frameTime));
			if(Math.abs(target - progress) < 0.002F)
				progress = target;
			return progress;
		}

		float getLifetimeProgress(long now)
		{
			return lifetimeProgress(spawnNanos, now);
		}
	}
}

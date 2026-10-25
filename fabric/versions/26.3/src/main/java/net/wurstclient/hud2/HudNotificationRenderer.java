package net.wurstclient.hud2;

import java.util.ArrayList;
import java.util.Iterator;

import net.minecraft.client.gui.Font;
import net.wurstclient.WurstClient;
import net.wurstclient.clickgui2.FlatRenderer;
import net.minecraft.client.gui.GuiGraphicsExtractor;

/**
 * Notification cards drawn by {@link NotificationHudElement}. The renderer
 * keeps Soar v7's bottom-stacked card style but no longer owns a screen
 * position, so the notification HUD is draggable and scalable like every
 * other HUD mod.
 */
public final class HudNotificationRenderer
{
	private final ArrayList<NotificationEntry> entries = new ArrayList<>();
	private static final int MIN_WIDTH = 180;
	private static final int PADDING = 8;
	private static final int CARD_HEIGHT = 38;
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
				for(Iterator<NotificationEntry> it = entries.iterator(); it
					.hasNext();)
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

	public void renderAt(GuiGraphicsExtractor graphics, int x, int y)
	{
		long now = System.nanoTime();
		float offsetY = 0;
		synchronized(entries)
		{
			for(Iterator<NotificationEntry> it = entries.iterator(); it
				.hasNext();)
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

				int width = Math.round(entry.getWidth());
				int top = Math.round(y + offsetY);
				int alpha = Math.round(255 * visibility);
				drawCard(graphics, entry, x, top, x + width,
					top + CARD_HEIGHT, visibility, lifetimeProgress, alpha);
				offsetY += (CARD_HEIGHT + CARD_GAP) * visibility;
			}
		}
	}

	public void renderPreview(GuiGraphicsExtractor graphics, int x, int y)
	{
		NotificationEntry preview = new NotificationEntry("Notifications",
			"HUD notification preview", NotificationSeverity.INFO);
		drawCard(graphics, preview, x, y, x + MIN_WIDTH, y + CARD_HEIGHT,
			1, 0.62F, 255);
	}

	public int getVisibleHeight()
	{
		synchronized(entries)
		{
			return Math.max(CARD_HEIGHT,
				entries.size() * (CARD_HEIGHT + CARD_GAP) - CARD_GAP);
		}
	}

	public int getVisibleWidth()
	{
		synchronized(entries)
		{
			return entries.stream().mapToInt(e -> Math.round(e.getWidth()))
				.max().orElse(MIN_WIDTH);
		}
	}

	private void drawCard(GuiGraphicsExtractor graphics,
		NotificationEntry entry, int left, int top, int right, int bottom,
		float visibility, float lifetimeProgress, int alpha)
	{
		FlatRenderer.fillRoundedRect(graphics, left, top + 1, right, bottom + 2,
			6, withAlpha(0x000000, Math.round(20 * visibility)));
		FlatRenderer.fillRoundedRect(graphics, left, top, right, bottom, 6,
			withAlpha(0x000000, Math.round(100 * visibility)));

		int accColor = getAccentColor(entry.severity);
		FlatRenderer.fillRoundedRect(graphics, left + 1, top + 2, left + 3,
			bottom - 2, 1, withAlpha(accColor, Math.round(240 * visibility)));

		Font font = WurstClient.MC.font;
		String title = truncate(entry.title, 26);
		String message = truncate(entry.message, 34);

		int textX = left + PADDING;
		int titleY = top + (bottom - top - font.lineHeight * 2 + 2) / 2;

		graphics.text(font, title, textX + 1, titleY + 1,
			withAlpha(0, Math.round(145 * visibility)), false);
		graphics.text(font, title, textX, titleY,
			withAlpha(0xFFFFFF, alpha), false);

		graphics.text(font, message, textX + 1, titleY + font.lineHeight + 1,
			withAlpha(0, Math.round(145 * visibility)), false);
		graphics.text(font, message, textX, titleY + font.lineHeight,
			withAlpha(0xCCFFFFFF, alpha), false);

		int barLeft = left + PADDING;
		int barRight = right - PADDING;
		int barTop = bottom - 4;
		FlatRenderer.fillRoundedRect(graphics, barLeft, barTop, barRight,
			barTop + 2, 1, withAlpha(0x2A3036, Math.round(170 * visibility)));
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
		return HUDMod.accentColor(255);
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

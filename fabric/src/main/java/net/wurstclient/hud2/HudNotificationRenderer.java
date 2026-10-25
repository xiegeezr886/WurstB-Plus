/*
 * Copyright (c) 2026 WurstB+ Plus contributors
 *
 * This source code is subject to the terms of the GNU Affero General Public
 * License, version 3. If a copy of the AGPL was not distributed with this
 * file, You can obtain one at: https://www.gnu.org/licenses/agpl-3.0.txt
 */
package net.wurstclient.hud2;

import java.awt.Color;
import java.util.ArrayList;
import java.util.Iterator;

import net.wurstclient.hud2.nanovg.HudFonts;
import net.wurstclient.hud2.nanovg.NanoVGContext;

/**
 * NanoVG notification cards.
 *
 * <p>Style based on Soar Client v7.1.2's notification renderer; the cards are
 * owned by the {@code Notifications} HUD module so they can be dragged and
 * scaled with the rest of the HUD.
 */
public final class HudNotificationRenderer
{
	private static final int CARD_WIDTH = 190;
	private static final int CARD_HEIGHT = 34;
	private static final int GAP = 4;
	private static final int MAX_NOTIFICATIONS = 8;

	private final ArrayList<Entry> entries = new ArrayList<>();

	public HudNotificationRenderer() {}

	public void addNotification(String title, String message,
		NotificationSeverity severity)
	{
		synchronized(entries)
		{
			if(severity == NotificationSeverity.ENABLED
				|| severity == NotificationSeverity.DISABLED)
				for(Iterator<Entry> it = entries.iterator(); it.hasNext();)
				{
					Entry entry = it.next();
					if(entry.message.equals(message))
						it.remove();
				}

			while(entries.size() >= MAX_NOTIFICATIONS)
				entries.remove(0);
			entries.add(new Entry(title, message, severity));
		}
	}

	public int getCount()
	{
		synchronized(entries)
		{
			return entries.size();
		}
	}

	public float getHeight(float scale)
	{
		synchronized(entries)
		{
			return entries.size() * (CARD_HEIGHT + GAP) * scale;
		}
	}

	/**
	 * Draws the notification stack and returns the height it used.
	 */
	public float render(NanoVGContext nvg, float x, float y, float scale,
		long durationMs)
	{
		long now = System.currentTimeMillis();
		float offset = 0;

		synchronized(entries)
		{
			for(Iterator<Entry> it = entries.iterator(); it.hasNext();)
			{
				Entry entry = it.next();
				long age = now - entry.createdAt;
				if(age > durationMs + 400)
				{
					it.remove();
					continue;
				}

				float fadeIn = Math.min(1, age / 150F);
				float fadeOut = Math.min(1,
					Math.max(0, durationMs + 300 - age) / 300F);
				float visibility = Math.max(0, Math.min(fadeIn, fadeOut));
				float slide = (1 - fadeIn) * 24 * scale;

				float cardX = x + slide;
				float cardY = y + offset;
				float width = CARD_WIDTH * scale;
				float height = CARD_HEIGHT * scale;
				int alpha = Math.round(255 * visibility);

				nvg.drawRoundedRect(cardX, cardY, width, height, 5 * scale,
					toColor(withAlpha(0xE6141414, alpha)));
				nvg.drawRoundedRect(cardX, cardY, 3 * scale, height,
					2 * scale, toColor(withAlpha(
						severityColor(entry.severity), alpha)));
				nvg.drawText(entry.title, cardX + 10 * scale,
					cardY + 5 * scale, 9 * scale, HudFonts.DEMIBOLD,
					toColor(withAlpha(0xFFFFFFFF, alpha)));
				nvg.drawText(entry.message, cardX + 10 * scale,
					cardY + 17 * scale, 8 * scale, HudFonts.REGULAR,
					toColor(withAlpha(0xFFB8B8B8, alpha)));

				offset += (CARD_HEIGHT + GAP) * scale * visibility;
			}
		}

		return offset;
	}

	private static int severityColor(NotificationSeverity severity)
	{
		try
		{
			return 0xFF000000 | Color.decode(severity.getColorHex()).getRGB()
				& 0xFFFFFF;
		}catch(NumberFormatException e)
		{
			return 0xFF4677FF;
		}
	}

	private static int withAlpha(int argb, int alpha)
	{
		return (argb & 0xFFFFFF) | Math.max(0, Math.min(255, alpha)) << 24;
	}

	private static Color toColor(int argb)
	{
		return new Color(argb, true);
	}

	private static final class Entry
	{
		private final String title;
		private final String message;
		private final NotificationSeverity severity;
		private final long createdAt = System.currentTimeMillis();

		private Entry(String title, String message,
			NotificationSeverity severity)
		{
			this.title = title;
			this.message = message;
			this.severity = severity;
		}
	}
}

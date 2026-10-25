/*
 * Copyright (c) 2026 WurstB+ Plus contributors
 *
 * This source code is subject to the terms of the GNU Affero General Public
 * License, version 3. If a copy of the AGPL was not distributed with this
 * file, You can obtain one at: https://www.gnu.org/licenses/agpl-3.0.txt
 */
package net.wurstclient.hud2;

import java.awt.Color;
import java.util.ArrayDeque;
import java.util.Iterator;

import net.wurstclient.hud2.nanovg.HudFonts;
import net.wurstclient.hud2.nanovg.HudIcon;
import net.wurstclient.hud2.nanovg.NanoVGContext;

/**
 * Soar-style notification card with a black frosted-glass body.
 *
 * <p>Only one card is shown at a time, matching Soar's notification handler.
 * The card slides in from the right with a back-ease, stays for the configured
 * duration, then slides back out while fading.
 */
public final class HudNotificationRenderer
{
	private static final float CARD_WIDTH = 230;
	private static final float CARD_HEIGHT = 36;
	private static final long ENTER_MS = 300;
	private static final long EXIT_MS = 300;
	private static final int MAX_NOTIFICATIONS = 8;

	private final ArrayDeque<Entry> queue = new ArrayDeque<>();
	private Entry current;
	private long currentStart;

	public HudNotificationRenderer() {}

	public void addNotification(String title, String message,
		NotificationSeverity severity)
	{
		synchronized(queue)
		{
			if(severity == NotificationSeverity.ENABLED
				|| severity == NotificationSeverity.DISABLED)
			{
				if(current != null && current.message.equals(message))
					return;
				for(Iterator<Entry> it = queue.iterator(); it.hasNext();)
				{
					Entry entry = it.next();
					if(entry.message.equals(message))
						it.remove();
				}
			}

			while(queue.size() >= MAX_NOTIFICATIONS)
				queue.removeFirst();
			queue.addLast(new Entry(title, message, severity));
		}
	}

	public int getCount()
	{
		synchronized(queue)
		{
			return queue.size() + (current == null ? 0 : 1);
		}
	}

	/**
	 * Static preview used by the HUD editor when no notification is active.
	 */
	public float renderPreview(NanoVGContext nvg, float x, float y,
		float scale)
	{
		float cardWidth = CARD_WIDTH * scale;
		float cardHeight = CARD_HEIGHT * scale;
		int accent = severityColor(NotificationSeverity.INFO);

		nvg.drawShadow(x + 2 * scale, y + 3 * scale, cardWidth, cardHeight,
			6 * scale);
		nvg.drawGradientRoundedRect(x, y, cardWidth, cardHeight, 6 * scale,
			new Color(0xBC000000, true), new Color(0xA0000000, true));
		nvg.drawOutlineRoundedRect(x, y, cardWidth, cardHeight, 6 * scale,
			1 * scale, new Color(0x24FFFFFF, true));
		nvg.drawRoundedRect(x + 4 * scale, y + 8 * scale, 2 * scale,
			cardHeight - 16 * scale, 1 * scale, new Color(accent));
		nvg.drawText(HudIcon.INFO, x + 11 * scale, y + 8 * scale,
			17 * scale, HudFonts.ICON, new Color(accent));
		nvg.drawText("Notifications", x + 34 * scale, y + 6 * scale,
			9.5F * scale, HudFonts.MEDIUM, Color.WHITE);
		nvg.drawText("HUD notification preview", x + 34 * scale,
			y + 20 * scale, 8 * scale, HudFonts.REGULAR,
			new Color(0xFFD0D0D0));
		return cardHeight;
	}

	public float getHeight(float scale)
	{
		return current == null ? 0 : CARD_HEIGHT * scale;
	}

	/**
	 * Draws the current notification and returns the height it used.
	 */
	public float render(NanoVGContext nvg, float x, float y, float scale,
		long durationMs)
	{
		long now = System.currentTimeMillis();

		synchronized(queue)
		{
			if(current == null)
			{
				current = queue.pollFirst();
				currentStart = now;
			}

			// Advance past expired cards.
			while(current != null && now - currentStart > durationMs + EXIT_MS)
			{
				current = queue.pollFirst();
				currentStart = now;
			}

			if(current == null)
				return 0;

			long age = now - currentStart;
			float enter = clamp(age / (float)ENTER_MS);
			float exit = clamp((age - durationMs) / (float)EXIT_MS);
			float enterEase = easeOutBack(enter);
			float exitEase = easeInBack(exit);
			float visibility = Math.max(0,
				Math.min(1, enterEase * (1 - exitEase)));
			if(visibility <= 0.001F)
				return 0;

			float cardWidth = CARD_WIDTH * scale;
			float cardHeight = CARD_HEIGHT * scale;
			float slide = (1 - enterEase) * 160 * scale
				+ exitEase * 160 * scale;
			float cardX = x + slide;
			float cardY = y;
			int alpha = Math.round(255 * visibility);
			int accent = severityColor(current.severity);

			nvg.drawShadow(cardX + 2 * scale, cardY + 3 * scale, cardWidth,
				cardHeight, 6 * scale);
			nvg.drawGradientRoundedRect(cardX, cardY, cardWidth, cardHeight,
				6 * scale, toColor(withAlpha(0xBC000000, alpha)),
				toColor(withAlpha(0xA0000000, alpha)));
			nvg.drawOutlineRoundedRect(cardX, cardY, cardWidth, cardHeight,
				6 * scale, 1 * scale, toColor(withAlpha(0x24FFFFFF, alpha)));

			// Thin severity accent on the left edge.
			nvg.drawRoundedRect(cardX + 4 * scale, cardY + 8 * scale,
				2 * scale, cardHeight - 16 * scale, 1 * scale,
				toColor(withAlpha(accent, Math.round(210 * visibility))));

			// Severity glyph, like Soar's notification icon.
			nvg.drawText(iconFor(current.severity), cardX + 11 * scale,
				cardY + 8 * scale, 17 * scale, HudFonts.ICON,
				toColor(withAlpha(accent, alpha)));

			float textX = cardX + 34 * scale;
			float maxTextWidth = cardWidth - 46 * scale;
			String title = nvg.getLimitText(current.title, 9.5F * scale,
				HudFonts.MEDIUM, maxTextWidth);
			String message = nvg.getLimitText(current.message, 8 * scale,
				HudFonts.REGULAR, maxTextWidth);
			nvg.drawText(title, textX, cardY + 6 * scale, 9.5F * scale,
				HudFonts.MEDIUM,
				toColor(withAlpha(0xFFFFFFFF, alpha)));
			nvg.drawText(message, textX, cardY + 20 * scale, 8 * scale,
				HudFonts.REGULAR,
				toColor(withAlpha(0xFFD0D0D0, alpha)));

			return cardHeight;
		}
	}

	private static float clamp(float value)
	{
		return Math.max(0, Math.min(1, value));
	}

	private static float easeOutBack(float value)
	{
		float c1 = 1.70158F;
		float c3 = c1 + 1;
		float shifted = value - 1;
		return 1 + c3 * shifted * shifted * shifted
			+ c1 * shifted * shifted;
	}

	private static float easeInBack(float value)
	{
		float c1 = 1.70158F;
		float c3 = c1 + 1;
		return c3 * value * value * value - c1 * value * value;
	}

	private static String iconFor(NotificationSeverity severity)
	{
		return switch(severity)
		{
			case SUCCESS, ENABLED -> HudIcon.CHECK_CIRCLE;
			case ERROR, DISABLED -> HudIcon.X_CIRCLE;
			case INFO -> HudIcon.INFO;
		};
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

		private Entry(String title, String message,
			NotificationSeverity severity)
		{
			this.title = title;
			this.message = message;
			this.severity = severity;
		}
	}
}

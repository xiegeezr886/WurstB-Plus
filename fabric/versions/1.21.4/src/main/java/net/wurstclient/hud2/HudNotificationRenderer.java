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
 * <p>Multiple cards are shown at once. The newest card sits on the module
 * anchor and older cards are pushed upwards, so a burst of notifications
 * never blocks the queue. Each card slides in from the right with a back-ease,
 * stays for the configured duration, then slides back out while fading.
 */
public final class HudNotificationRenderer
{
	private static final float CARD_WIDTH = 230;
	private static final float CARD_HEIGHT = 36;
	private static final float CARD_GAP = 4;
	private static final long ENTER_MS = 220;
	private static final long EXIT_MS = 240;
	private static final int MAX_VISIBLE = 5;

	private final ArrayDeque<Entry> active = new ArrayDeque<>();
	private long lastFrameNanos;

	public HudNotificationRenderer() {}

	public void addNotification(String title, String message,
		NotificationSeverity severity)
	{
		synchronized(active)
		{
			if(severity == NotificationSeverity.ENABLED
				|| severity == NotificationSeverity.DISABLED)
				for(Iterator<Entry> it = active.iterator(); it.hasNext();)
				{
					Entry entry = it.next();
					// Keep "Enabled" and "Disabled" for the same feature as
					// separate cards; only drop duplicates of the same state.
					if(entry.severity == severity
						&& entry.message.equals(message))
						it.remove();
				}

			while(active.size() >= MAX_VISIBLE)
				active.removeFirst();
			active.addLast(new Entry(title, message, severity,
				System.currentTimeMillis()));
		}
	}

	public int getCount()
	{
		synchronized(active)
		{
			return active.size();
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
		int iconArgb = severityColor(NotificationSeverity.INFO);

		nvg.drawShadow(x + 2 * scale, y + 3 * scale, cardWidth, cardHeight,
			6 * scale);
		nvg.drawGradientRoundedRect(x, y, cardWidth, cardHeight, 6 * scale,
			new Color(0xBC000000, true), new Color(0xA0000000, true));
		nvg.drawOutlineRoundedRect(x, y, cardWidth, cardHeight, 6 * scale,
			1 * scale, new Color(0x24FFFFFF, true));
		nvg.drawText(HudIcon.INFO, x + 11 * scale, y + 8 * scale,
			17 * scale, HudFonts.ICON, new Color(iconArgb, true));
		nvg.drawText("Notifications", x + 34 * scale, y + 6 * scale,
			9.5F * scale, HudFonts.MEDIUM, Color.WHITE);
		nvg.drawText("HUD notification preview", x + 34 * scale,
			y + 20 * scale, 8 * scale, HudFonts.REGULAR,
			new Color(0xFFD0D0D0));
		return cardHeight;
	}

	/**
	 * Draws the active notification stack and returns the height of one card.
	 */
	public float render(NanoVGContext nvg, float x, float y, float scale,
		long durationMs)
	{
		long now = System.currentTimeMillis();
		float cardWidth = CARD_WIDTH * scale;
		float cardHeight = CARD_HEIGHT * scale;
		float gap = CARD_GAP * scale;
		float frameDelta = frameDelta(now);

		synchronized(active)
		{
			// Drop cards whose exit animation has finished.
			for(Iterator<Entry> it = active.iterator(); it.hasNext();)
			{
				Entry entry = it.next();
				if(now - entry.start > durationMs + EXIT_MS)
					it.remove();
			}

			if(active.isEmpty())
				return 0;

			// Newest card at the anchor, older cards pushed upwards.
			int slot = 0;
			for(Iterator<Entry> it = active.descendingIterator();
				it.hasNext();)
			{
				Entry entry = it.next();
				float targetY = y - slot * (cardHeight + gap);
				if(Float.isNaN(entry.animY))
					entry.animY = targetY;
				else
					entry.animY = approach(entry.animY, targetY, frameDelta);

				drawCard(nvg, entry, x, entry.animY, cardWidth, cardHeight,
					scale, now, durationMs);
				slot++;
			}
		}

		return cardHeight;
	}

	public float getHeight(float scale)
	{
		synchronized(active)
		{
			if(active.isEmpty())
				return 0;
			return active.size() * (CARD_HEIGHT + CARD_GAP) * scale - CARD_GAP
				* scale;
		}
	}

	private void drawCard(NanoVGContext nvg, Entry entry, float x, float y,
		float cardWidth, float cardHeight, float scale, long now,
		long durationMs)
	{
		long age = now - entry.start;
		float enter = clamp(age / (float)ENTER_MS);
		float exit = clamp((age - durationMs) / (float)EXIT_MS);
		float enterEase = easeOutBack(enter);
		float exitEase = easeInBack(exit);
		float visibility = Math.max(0,
			Math.min(1, enterEase * (1 - exitEase)));
		if(visibility <= 0.001F)
			return;

		float slide = (1 - enterEase) * 160 * scale + exitEase * 160 * scale;
		float cardX = x + slide;
		float cardY = y;
		int alpha = Math.round(255 * visibility);
		int iconArgb = withAlpha(severityColor(entry.severity), alpha);

		nvg.drawShadow(cardX + 2 * scale, cardY + 3 * scale, cardWidth,
			cardHeight, 6 * scale);
		nvg.drawGradientRoundedRect(cardX, cardY, cardWidth, cardHeight,
			6 * scale, toColor(withAlpha(0xBC000000, alpha)),
			toColor(withAlpha(0xA0000000, alpha)));
		nvg.drawOutlineRoundedRect(cardX, cardY, cardWidth, cardHeight,
			6 * scale, 1 * scale, toColor(withAlpha(0x24FFFFFF, alpha)));

		// Severity glyph, like Soar's notification icon.
		nvg.drawText(iconFor(entry.severity), cardX + 11 * scale,
			cardY + 8 * scale, 17 * scale, HudFonts.ICON,
			toColor(iconArgb));

		float textX = cardX + 34 * scale;
		float maxTextWidth = cardWidth - 46 * scale;
		String title = nvg.getLimitText(entry.title, 9.5F * scale,
			HudFonts.MEDIUM, maxTextWidth);
		String message = nvg.getLimitText(entry.message, 8 * scale,
			HudFonts.REGULAR, maxTextWidth);
		nvg.drawText(title, textX, cardY + 6 * scale, 9.5F * scale,
			HudFonts.MEDIUM, toColor(withAlpha(0xFFFFFFFF, alpha)));
		nvg.drawText(message, textX, cardY + 20 * scale, 8 * scale,
			HudFonts.REGULAR, toColor(withAlpha(0xFFD0D0D0, alpha)));
	}

	private float frameDelta(long now)
	{
		long previous = lastFrameNanos;
		lastFrameNanos = now * 1_000_000L;
		if(previous == 0)
			return 0;
		return Math.min(0.1F,
			(now * 1_000_000L - previous) / 1_000_000_000F);
	}

	private static float approach(float current, float target, float delta)
	{
		if(delta <= 0)
			return current;
		return current + (target - current)
			* (1 - (float)Math.exp(-22 * delta));
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

	/**
	 * Multiplies the colour's own alpha by the fade factor instead of
	 * replacing it. Replacing it turned the 14% white border into an opaque
	 * white ring and the frosted body into solid black.
	 */
	private static int withAlpha(int argb, int alpha)
	{
		int factor = Math.max(0, Math.min(255, alpha));
		int base = argb >>> 24;
		int result = base * factor / 255;
		return (argb & 0xFFFFFF) | result << 24;
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
		private final long start;
		private float animY = Float.NaN;

		private Entry(String title, String message,
			NotificationSeverity severity, long start)
		{
			this.title = title;
			this.message = message;
			this.severity = severity;
			this.start = start;
		}
	}
}

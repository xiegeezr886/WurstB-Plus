package net.wurstclient.hud2;

import net.minecraft.client.gui.GuiGraphics;

/**
 * Optional custom renderer for a {@link HudNotification}. Mirrors the
 * {@code composeContent} lambda in the Compose {@code Notify} model: when
 * set, the notification draws this content instead of the standard
 * title/message card body.
 */
@FunctionalInterface
public interface NotificationContent
{
	void render(GuiGraphics graphics, int x, int y, int width, int height);
}

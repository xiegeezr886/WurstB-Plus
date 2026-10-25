package net.wurstclient.hud2.elements;

import net.wurstclient.WurstClient;
import net.wurstclient.hud2.HUDMod;
import net.wurstclient.hud2.HudNotificationRenderer;

public final class NotificationHudElement extends HUDMod
{
	public NotificationHudElement()
	{
		super("notifications", "Notifications");
		setDefaultPosition(760, 440);
	}

	@Override
	protected void draw()
	{
		HudNotificationRenderer renderer =
			WurstClient.INSTANCE.getHudManager().getNotificationRenderer();
		if(isEditing())
			renderer.renderPreview(graphics, getX(), getY());
		else
			renderer.renderAt(graphics, getX(), getY());
		setSize(renderer.getVisibleWidth(), renderer.getVisibleHeight());
	}
}

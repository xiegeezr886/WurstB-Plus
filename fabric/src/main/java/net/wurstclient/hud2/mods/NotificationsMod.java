/*
 * Copyright (c) 2026 WurstB+ Plus contributors
 *
 * This source code is subject to the terms of the GNU Affero General Public
 * License, version 3. If a copy of the AGPL was not distributed with this
 * file, You can obtain one at: https://www.gnu.org/licenses/agpl-3.0.txt
 */
package net.wurstclient.hud2.mods;

import net.wurstclient.Category;
import net.wurstclient.hud2.HUDMod;
import net.wurstclient.hud2.HudManager;

/**
 * Draggable anchor for the HUD notification stack.
 */
public final class NotificationsMod extends HUDMod
{
	private final HudManager manager;

	public NotificationsMod(HudManager manager)
	{
		super("Notifications", "Notifications",
			"HUD notification cards.");
		this.manager = manager;
		setTranslationKey("notifications");
		setSize(230, 36);
		boolean hasWindow = WURST.MC != null
			&& WURST.MC.getWindow() != null;
		int screenWidth = hasWindow
			? WURST.MC.getWindow().getGuiScaledWidth() : 800;
		int screenHeight = hasWindow
			? WURST.MC.getWindow().getGuiScaledHeight() : 600;
		setDefaultPosition(Math.max(8, screenWidth - 250),
			Math.max(8, screenHeight - 80));
		setDefaultEnabled(true);
	}

	@Override
	protected void draw()
	{
		float scale = getScale();
		if(isEditing() && manager.getNotificationRenderer().getCount() == 0)
		{
			float preview = manager.getNotificationRenderer()
				.renderPreview(nvg, getX(), getY(), scale);
			setSize(230, Math.max(36,
				Math.round(preview / Math.max(scale, 0.01F))));
			return;
		}

		float height = manager.getNotificationRenderer().render(nvg, getX(),
			getY(), scale, manager.getSettings().getNotificationDurationMs());
		setSize(230, Math.max(36, Math.round(height / Math.max(scale, 0.01F))));
	}
}

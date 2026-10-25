/*
 * Copyright (c) 2026 WurstB+ Plus contributors
 *
 * This source code is subject to the terms of the GNU Affero General Public
 * License, version 3. If a copy of the AGPL was not distributed with this
 * file, You can obtain one at: https://www.gnu.org/licenses/agpl-3.0.txt
 */
// Ported from Soar Client v7.1.2 (LGPL-3.0).
package net.wurstclient.hud2.mods;

import java.awt.Color;

import net.wurstclient.hud2.HUDMod;
import net.wurstclient.hud2.compat.HudCompat;
import net.wurstclient.hud2.nanovg.HudFonts;
import net.wurstclient.hud2.nanovg.HudIcon;

public final class TargetInfoMod extends HUDMod
{
	private static final float RING_RADIUS = 9F;

	public TargetInfoMod()
	{
		super("TargetInfo", "Target Info",
			"Shows information about the entity being looked at.");
		setTranslationKey("target_info");
		setDefaultPosition(180, 54);
	}

	@Override
	protected void draw()
	{
		HudCompat.TargetInfo target = HudCompat.target();
		if(target == null && isEditing())
			target = HudCompat.localPlayerTarget();

		// No target: draw nothing at all.
		if(target == null)
		{
			setSize(0, 0);
			return;
		}

		boolean hasHead = target.skin() != null;
		float textX = hasHead ? 45.5F : 8F;
		float nameWidth = getTextWidth(target.name(), 10.2F,
			HudFonts.MEDIUM);
		float width = Math.max(140, textX + Math.max(nameWidth, 46F) + 8F);
		float height = 46;
		drawBackground(width, height);

		if(hasHead)
			drawPlayerHead(target.skin(), 5, 5, 36, 36, 6);

		drawText(target.name(), textX, 8F, 10.2F, HudFonts.MEDIUM);

		// Health and armour as circular gauges, like Soar's target HUD.
		float health = (float)Math.max(0,
			Math.min(1, target.health() / Math.max(target.maxHealth(), 0.001)));
		float armor = Math.max(0, Math.min(1, target.armor() / 20F));
		drawGauge(textX, 30.5F, HudIcon.HEART_FILL, health);
		drawGauge(textX + 24F, 30.5F, HudIcon.SHIELD_FILL, armor);

		setSize(Math.round(width), Math.round(height));
	}

	private void drawGauge(float baseX, float centerY, String icon,
		float ratio)
	{
		float centerX = baseX + RING_RADIUS + 2;

		drawArc(centerX, centerY, RING_RADIUS, -90, 270, 1.6F,
			getFontColor(120));
		if(ratio > 0.001F)
			drawArc(centerX, centerY, RING_RADIUS, -90,
				-90 + 360 * ratio, 1.6F, new Color(accentColor(255), true));

		// The glyph is centred inside the ring, like Soar's target HUD.
		drawText(icon, centerX - 4.5F, centerY - 4.5F, 9, HudFonts.ICON);
	}
}

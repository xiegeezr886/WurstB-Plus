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

import org.joml.Vector3f;

import net.minecraft.client.Camera;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import net.wurstclient.hud2.HUDMod;
import net.wurstclient.hud2.compat.HudCompat;
import net.wurstclient.hud2.nanovg.HudFonts;
import net.wurstclient.hud2.nanovg.HudIcon;
import net.wurstclient.util.EntityUtils;

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
		// The editor keeps a normal 2D preview so the module can be seen and
		// selected, while in-game the card is anchored next to the target.
		if(isEditing())
		{
			HudCompat.TargetInfo preview = HudCompat.localPlayerTarget();
			if(preview == null)
			{
				setSize(0, 0);
				return;
			}
			drawCard(preview, computeWidth(preview));
			return;
		}

		Entity target = resolveTarget();
		if(!(target instanceof LivingEntity living) || !living.isAlive())
		{
			setSize(0, 0);
			return;
		}

		HudCompat.TargetInfo info = HudCompat.infoFor(living);
		float[] screen = project(living);
		if(screen == null)
		{
			setSize(0, 0);
			return;
		}

		float width = computeWidth(info);
		int cardWidth = Math.round(width * getScale());
		int cardHeight = Math.round(46 * getScale());
		int cardX = Math.round(screen[0]) + 14;
		int cardY = Math.round(screen[1]) - cardHeight / 2;

		int screenWidth = MC.getWindow().getGuiScaledWidth();
		int screenHeight = MC.getWindow().getGuiScaledHeight();
		cardX = Math.max(0, Math.min(cardX, screenWidth - cardWidth));
		cardY = Math.max(0, Math.min(cardY, screenHeight - cardHeight));

		int oldX = getX();
		int oldY = getY();
		setX(cardX);
		setY(cardY);
		try
		{
			drawCard(info, width);
		}finally
		{
			setX(oldX);
			setY(oldY);
		}
	}

	private Entity resolveTarget()
	{
		LivingEntity auraTarget = null;
		if(WURST.getHax().killauraHack.isEnabled())
		{
			Entity entity = WURST.getHax().killauraHack.getCurrentTarget();
			if(entity instanceof LivingEntity living && living.isAlive())
				auraTarget = living;
		}

		return auraTarget != null ? auraTarget : HudCompat.targetEntity();
	}

	private float computeWidth(HudCompat.TargetInfo target)
	{
		boolean hasHead = target.skin() != null;
		float textX = hasHead ? 45.5F : 8F;
		float nameWidth = getTextWidth(target.name(), 10.2F,
			HudFonts.MEDIUM);
		return Math.max(140, textX + Math.max(nameWidth, 46F) + 8F);
	}

	private void drawCard(HudCompat.TargetInfo target, float width)
	{
		boolean hasHead = target.skin() != null;
		float textX = hasHead ? 45.5F : 8F;
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

	/**
	 * Projects the entity's centre to GUI coordinates using the camera basis.
	 */
	private float[] project(Entity entity)
	{
		Vec3 point = EntityUtils.getLerpedPos(entity, partialTicks)
			.add(0, entity.getBbHeight() * 0.5D, 0);
		Camera camera = MC.gameRenderer.getMainCamera();
		Vec3 relative = point.subtract(camera.getPosition());

		Vector3f look = camera.getLookVector();
		double forward = relative.x * look.x() + relative.y * look.y()
			+ relative.z * look.z();
		if(forward <= 0.05D)
			return null;

		Vector3f upVector = camera.getUpVector();
		double up = relative.x * upVector.x() + relative.y * upVector.y()
			+ relative.z * upVector.z();
		Vector3f leftVector = camera.getLeftVector();
		double right = -(relative.x * leftVector.x()
			+ relative.y * leftVector.y() + relative.z * leftVector.z());
		double tan = Math.tan(
			Math.toRadians(MC.options.fov().get()) / 2.0D);
		double aspect = (double)MC.getWindow().getWidth()
			/ MC.getWindow().getHeight();

		double ndcX = right / (forward * tan * aspect);
		double ndcY = up / (forward * tan);
		if(ndcX < -1.2D || ndcX > 1.2D || ndcY < -1.2D || ndcY > 1.2D)
			return null;

		float x = (float)((ndcX + 1.0D) / 2.0D
			* MC.getWindow().getGuiScaledWidth());
		float y = (float)((1.0D - ndcY) / 2.0D
			* MC.getWindow().getGuiScaledHeight());
		return new float[]{x, y};
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

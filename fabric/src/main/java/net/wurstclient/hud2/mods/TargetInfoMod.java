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
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import org.joml.Vector3f;

import net.minecraft.client.Camera;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.wurstclient.hacks.KillauraHack;
import net.wurstclient.hud2.HUDMod;
import net.wurstclient.hud2.compat.HudCompat;
import net.wurstclient.hud2.nanovg.HudFonts;
import net.wurstclient.hud2.nanovg.HudIcon;
import net.wurstclient.settings.SliderSetting;
import net.wurstclient.settings.SliderSetting.ValueDisplay;
import net.wurstclient.util.EntityUtils;

public final class TargetInfoMod extends HUDMod
{
	private static final float RING_RADIUS = 9F;

	private final SliderSetting range = new SliderSetting("Range",
		"How far to search for nearby entities.", 12, 1, 32, 1,
		ValueDisplay.INTEGER);
	private final SliderSetting maxTargets = new SliderSetting("Max targets",
		"How many entity cards to show.", 5, 1, 10, 1,
		ValueDisplay.INTEGER);

	public TargetInfoMod()
	{
		super("TargetInfo", "Target Info",
			"Shows nearby entities and the pursuit target.");
		setTranslationKey("target_info");
		setDefaultPosition(180, 54);
		addSetting(range);
		addSetting(maxTargets);
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

		List<LivingEntity> targets = collectTargets();
		if(targets.isEmpty())
		{
			setSize(0, 0);
			return;
		}

		for(LivingEntity entity : targets)
			drawEntityCard(entity);
		setSize(140, 46);
	}

	/**
	 * Killaura's current target first, then every other living entity within
	 * the configured player range. Both are capped by the target limit.
	 */
	private List<LivingEntity> collectTargets()
	{
		if(MC.player == null || MC.level == null)
			return List.of();

		List<LivingEntity> result = new ArrayList<>();
		double searchRange = range.getValue();

		KillauraHack aura = WURST.getHax().killauraHack;
		if(aura != null && aura.isEnabled())
		{
			searchRange = Math.max(searchRange, aura.getRange());
			Entity auraTarget = aura.getCurrentTarget();
			if(auraTarget instanceof LivingEntity living && living.isAlive()
				&& living != MC.player)
				result.add(living);
		}

		AABB area = MC.player.getBoundingBox().inflate(searchRange);
		for(LivingEntity entity : MC.level.getEntitiesOfClass(
			LivingEntity.class, area, e -> e != MC.player && e.isAlive()
				&& !e.isSpectator()))
			if(!result.contains(entity))
				result.add(entity);

		result.sort(Comparator
			.comparingDouble(e -> MC.player.distanceToSqr(e)));
		int limit = maxTargets.getValueI();
		return result.size() > limit
			? new ArrayList<>(result.subList(0, limit)) : result;
	}

	private void drawEntityCard(LivingEntity entity)
	{
		HudCompat.TargetInfo info = HudCompat.infoFor(entity);
		float[] screen = project(entity);
		if(screen == null)
			return;

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

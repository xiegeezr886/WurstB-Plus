/*
 * Copyright (c) 2026 WurstB+ Plus contributors
 *
 * This source code is subject to the terms of the GNU Affero General Public
 * License, version 3. If a copy of the AGPL was not distributed with this
 * file, You can obtain one at: https://www.gnu.org/licenses/agpl-3.0.txt
 */
// Ported from Soar Client v7.1.2 (LGPL-3.0).
package net.wurstclient.hacks;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import net.wurstclient.Category;
import net.wurstclient.SearchTags;
import net.wurstclient.events.GUIRenderListener;
import net.wurstclient.hack.Hack;
import net.wurstclient.settings.CheckboxSetting;
import net.wurstclient.settings.SliderSetting;
import net.wurstclient.settings.SliderSetting.ValueDisplay;
import net.minecraft.client.gui.GuiGraphicsExtractor;

/**
 * HUD radar showing entities behind the player.
 *
 * <p>Soar rendered a second camera into a texture. This port shows a compact
 * rear-view radar, which keeps the awareness benefit without a per-version
 * framebuffer/camera hook.
 */
@SearchTags({"rearview", "rear view", "rear-view", "radar", "behind"})
public final class RearviewHack extends Hack implements GUIRenderListener
{
	private final SliderSetting range = new SliderSetting("Range",
		"Maximum distance shown in the rear-view radar.", 48, 8, 128, 4,
		ValueDisplay.INTEGER.withSuffix(" blocks"));

	private final SliderSetting width = new SliderSetting("Width",
		"Width of the rear-view radar.", 180, 80, 400, 10,
		ValueDisplay.INTEGER);

	private final SliderSetting height = new SliderSetting("Height",
		"Height of the rear-view radar.", 100, 40, 220, 10,
		ValueDisplay.INTEGER);

	private final SliderSetting posX = new SliderSetting("Position X",
		"X position of the radar.", 20, 0, 2500, 1, ValueDisplay.INTEGER);

	private final SliderSetting posY = new SliderSetting("Position Y",
		"Y position of the radar.", 100, 0, 2500, 1, ValueDisplay.INTEGER);

	private final CheckboxSetting showPlayers = new CheckboxSetting(
		"Show players", "Shows players on the radar.", true);

	private final CheckboxSetting showMobs = new CheckboxSetting("Show mobs",
		"Shows living mobs on the radar.", true);

	private final CheckboxSetting showOther = new CheckboxSetting("Show other",
		"Shows other entities on the radar.", false);

	public RearviewHack()
	{
		super("Rearview");
		setCategory(Category.RENDER);
		addSetting(range);
		addSetting(width);
		addSetting(height);
		addSetting(posX);
		addSetting(posY);
		addSetting(showPlayers);
		addSetting(showMobs);
		addSetting(showOther);
	}

	@Override
	protected void onEnable()
	{
		EVENTS.add(GUIRenderListener.class, this);
	}

	@Override
	protected void onDisable()
	{
		EVENTS.remove(GUIRenderListener.class, this);
	}

	@Override
	public void onRenderGUI(GuiGraphicsExtractor context, float partialTicks)
	{
		if(MC.player == null || MC.level == null || uiHidden())
			return;

		int left = (int)posX.getValue();
		int top = (int)posY.getValue();
		int w = width.getValueI();
		int h = height.getValueI();
		int centerX = left + w / 2;
		int bottom = top + h - 6;
		double r = range.getValue();

		context.fill(left, top, left + w, top + h, 0xB0000000);
		context.fill(left, top, left + w, top + 1, 0xFF606060);
		context.fill(left, top + h - 1, left + w, top + h, 0xFF606060);
		context.fill(left, top, left + 1, top + h, 0xFF606060);
		context.fill(left + w - 1, top, left + w, top + h, 0xFF606060);
		text(context, "Rearview", left + 5, top + 5, 0xFFFFFFFF, true);

		Vec3 look = MC.player.getLookAngle();
		Vec3 right = look.cross(new Vec3(0, 1, 0));
		if(right.lengthSqr() < 1.0E-4)
			right = new Vec3(1, 0, 0);
		else
			right = right.normalize();

		for(Entity entity : MC.level.entitiesForRendering())
		{
			if(entity == MC.player || entity.isRemoved() || !entity.isAlive())
				continue;

			boolean player = entity instanceof Player;
			boolean mob = entity instanceof LivingEntity && !player;
			if(player && !showPlayers.isChecked()
				|| mob && !showMobs.isChecked()
				|| !player && !mob && !showOther.isChecked())
				continue;

			Vec3 rel = entity.position().subtract(MC.player.position());
			if(rel.lengthSqr() > r * r)
				continue;

			double forward = look.dot(rel);
			if(forward >= 0)
				continue;

			double lateral = right.dot(rel);
			double depth = -forward;
			int px = centerX + (int)(lateral / r * (w / 2.0 - 5));
			int py = bottom - (int)(depth / r * (h - 20));
			if(px < left + 3 || px > left + w - 3 || py < top + 14
				|| py > top + h - 3)
				continue;

			int color = player ? 0xFFFF5555
				: mob ? 0xFFFFAA00 : 0xFFAAAAAA;
			context.fill(px - 2, py - 2, px + 2, py + 2, color);
		}

		context.fill(centerX, bottom + 1, centerX + 1, bottom + 5, 0x80FFFFFF);
	}

	private int argb(java.awt.Color color, int alpha)
	{
		int a = Math.max(0, Math.min(255, alpha));
		return a << 24 | color.getRGB() & 0xFFFFFF;
	}

	private boolean uiHidden()
	{
		try
		{
			Object gui = MC.getClass().getField("gui").get(MC);
			if(gui.getClass().getMethod("screen").invoke(gui) != null)
				return true;
		}catch(Exception ignored)
		{}

		try
		{
			return Boolean.TRUE.equals(MC.options.getClass()
				.getField("hideGui").get(MC.options));
		}catch(Exception ignored)
		{
			return false;
		}
	}

	private void text(GuiGraphicsExtractor context, String text, int x,
		int y, int color, boolean shadow)
	{
		context.text(MC.font, text, x, y, color, shadow);
	}

	private void centeredText(GuiGraphicsExtractor context, String text,
		int x, int y, int color)
	{
		context.centeredText(MC.font, text, x, y, color);
	}

}

/*
 * Copyright (c) 2026 WurstB+ Plus contributors
 *
 * This source code is subject to the terms of the GNU Affero General Public
 * License, version 3. If a copy of the AGPL was not distributed with this
 * file, You can obtain one at: https://www.gnu.org/licenses/agpl-3.0.txt
 */
// Ported from Soar Client v7.1.2 (LGPL-3.0).
package net.wurstclient.hacks;

import java.awt.Color;
import java.util.Locale;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.wurstclient.Category;
import net.wurstclient.SearchTags;
import net.wurstclient.events.GUIRenderListener;
import net.wurstclient.events.PlayerAttacksEntityListener;
import net.wurstclient.events.RightClickListener;
import net.wurstclient.hack.Hack;
import net.wurstclient.settings.CheckboxSetting;
import net.wurstclient.settings.ColorSetting;
import net.wurstclient.settings.SliderSetting;
import net.wurstclient.settings.SliderSetting.ValueDisplay;
import net.minecraft.client.gui.GuiGraphics;

/**
 * HUD display of the last attack or interaction reach distance.
 *
 * <p>Simplification: Soar measured the distance from the crosshair hit vector
 * of every damage event. This port measures from the player's eyes to the
 * current hit result when an attack or right-click happens, and clears the
 * value after five seconds like the upstream display.
 */
@SearchTags({"reach display", "reach", "distance", "hit distance",
	"attack range"})
public final class ReachDisplayHack extends Hack
	implements PlayerAttacksEntityListener, RightClickListener,
	GUIRenderListener
{
	private static final long DISPLAY_TIMEOUT_MS = 5000L;

	private final SliderSetting scale = new SliderSetting("Scale",
		"Scale of the reach text.", 1.5, 0.5, 5, 0.1, ValueDisplay.DECIMAL);

	private final SliderSetting posX = new SliderSetting("Position X",
		"X position of the reach text.", 500, 0, 2500, 1,
		ValueDisplay.INTEGER);

	private final SliderSetting posY = new SliderSetting("Position Y",
		"Y position of the reach text.", 290, 0, 2500, 1,
		ValueDisplay.INTEGER);

	private final CheckboxSetting background = new CheckboxSetting(
		"Show background", "Draws a colored box behind the reach text.", true);

	private final ColorSetting bgColor = new ColorSetting("Background color",
		"Color of the background box.", new Color(0, 0, 0, 150));

	private final ColorSetting textColor = new ColorSetting("Text color",
		"Color of the reach text.", new Color(255, 255, 255));

	private double distance;
	private long hitTime;

	public ReachDisplayHack()
	{
		super("ReachDisplay");
		setCategory(Category.RENDER);
		addSetting(scale);
		addSetting(posX);
		addSetting(posY);
		addSetting(background);
		addSetting(bgColor);
		addSetting(textColor);
		bgColor.visibleWhen(background::isChecked);
	}

	@Override
	protected void onEnable()
	{
		distance = 0;
		hitTime = 0;
		EVENTS.add(PlayerAttacksEntityListener.class, this);
		EVENTS.add(RightClickListener.class, this);
		EVENTS.add(GUIRenderListener.class, this);
	}

	@Override
	protected void onDisable()
	{
		EVENTS.remove(PlayerAttacksEntityListener.class, this);
		EVENTS.remove(RightClickListener.class, this);
		EVENTS.remove(GUIRenderListener.class, this);
		distance = 0;
		hitTime = 0;
	}

	@Override
	public void onPlayerAttacksEntity(Entity target)
	{
		if(MC.player == null)
			return;

		Vec3 hitVec = target.getBoundingBox().getCenter();
		if(MC.hitResult instanceof EntityHitResult entityHit
			&& entityHit.getEntity() == target)
			hitVec = MC.hitResult.getLocation();

		recordDistance(hitVec);
	}

	@Override
	public void onRightClick(RightClickEvent event)
	{
		if(MC.player == null || MC.hitResult == null
			|| MC.hitResult.getType() == HitResult.Type.MISS)
			return;

		recordDistance(MC.hitResult.getLocation());
	}

	@Override
	public void onRenderGUI(GuiGraphics context, float partialTicks)
	{
		if(MC.player == null)
			return;

		String text = getText();
		double textScale = scale.getValue();
		double width = MC.font.width(text) * textScale;
		double height = MC.font.lineHeight * textScale;
		double x = posX.getValue();
		double y = posY.getValue();

		if(background.isChecked())
			context.fill((int)(x - 4), (int)(y - 4), (int)(x + width + 4),
				(int)(y + height + 4), bgColor.getColor().getRGB());

		var pose = context.pose();
		pose.pushPose();
		pose.translate((float)x, (float)y, 0);
		pose.scale((float)textScale, (float)textScale, 1);
		context.drawString(MC.font, text, 0, 0, textColor.getColor().getRGB(), true);
		pose.popPose();
	}

	private void recordDistance(Vec3 hitVec)
	{
		distance = MC.player.getEyePosition(1.0F).distanceTo(hitVec);
		hitTime = System.currentTimeMillis();
	}

	private String getText()
	{
		if(hitTime == 0
			|| System.currentTimeMillis() - hitTime > DISPLAY_TIMEOUT_MS)
			return "Hasn't attacked";

		return String.format(Locale.ROOT, "%.2f blocks", distance);
	}
}

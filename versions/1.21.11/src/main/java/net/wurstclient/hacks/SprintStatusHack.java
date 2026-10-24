/*
 * Copyright (c) 2026 WurstB+ Plus contributors
 *
 * This source code is subject to the terms of the GNU Affero General Public
 * License, version 3. If a copy of the AGPL was not distributed with this
 * file, You can obtain one at: https://www.gnu.org/licenses/agpl-3.0.txt
 */
// Ported from InvincibleMachineGun (GPL-3.0).
package net.wurstclient.hacks;

import java.awt.Color;

import net.wurstclient.Category;
import net.wurstclient.SearchTags;
import net.wurstclient.events.GUIRenderListener;
import net.wurstclient.hack.Hack;
import net.wurstclient.settings.CheckboxSetting;
import net.wurstclient.settings.ColorSetting;
import net.wurstclient.settings.SliderSetting;
import net.wurstclient.settings.SliderSetting.ValueDisplay;
import net.wurstclient.util.render.GuiGraphicsExtractor;

/**
 * HUD indicator that shows whether the player is currently sprinting.
 */
@SearchTags({"sprint status", "sprinting", "sprint indicator"})
public final class SprintStatusHack extends Hack implements GUIRenderListener
{
	private final SliderSetting scale = new SliderSetting("Scale",
		"Scale of the status text.", 1.5, 0.5, 5, 0.1, ValueDisplay.DECIMAL);

	private final SliderSetting posX = new SliderSetting("Position X",
		"X position of the status text.", 500, 0, 2500, 1,
		ValueDisplay.INTEGER);

	private final SliderSetting posY = new SliderSetting("Position Y",
		"Y position of the status text.", 230, 0, 2500, 1,
		ValueDisplay.INTEGER);

	private final CheckboxSetting showIcon = new CheckboxSetting("Show icon",
		"Prefixes the status with a sprint arrow.", true);

	private final CheckboxSetting useChinese = new CheckboxSetting("Chinese",
		"Shows the status in Chinese.", false);

	private final ColorSetting sprintColor = new ColorSetting("Sprint color",
		"Color used while sprinting.", new Color(0, 255, 0));

	private final ColorSetting idleColor = new ColorSetting("Idle color",
		"Color used while not sprinting.", new Color(255, 0, 0));

	private final CheckboxSetting background = new CheckboxSetting(
		"Show background", "Draws a colored box behind the status text.", true);

	private final ColorSetting bgColor = new ColorSetting("Background color",
		"Color of the background box.", new Color(0, 0, 0, 150));

	public SprintStatusHack()
	{
		super("SprintStatus");
		setCategory(Category.RENDER);
		addSetting(scale);
		addSetting(posX);
		addSetting(posY);
		addSetting(showIcon);
		addSetting(useChinese);
		addSetting(sprintColor);
		addSetting(idleColor);
		addSetting(background);
		addSetting(bgColor);
		bgColor.visibleWhen(background::isChecked);
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
		if(MC.player == null)
			return;

		boolean sprinting = MC.player.isSprinting();
		String icon = showIcon.isChecked() ? sprinting ? ">> " : "> " : "";
		String status;

		if(useChinese.isChecked())
			status = sprinting ? "\u75BE\u8DD1: \u5F00\u542F"
				: "\u75BE\u8DD1: \u5173\u95ED";
		else
			status = sprinting ? "Sprint: ON" : "Sprint: OFF";

		String text = icon + status;
		double textScale = scale.getValue();
		double width = MC.font.width(text) * textScale;
		double height = MC.font.lineHeight * textScale;
		double x = posX.getValue();
		double y = posY.getValue();

		if(background.isChecked())
			context.fill((int)(x - 4), (int)(y - 4), (int)(x + width + 4),
				(int)(y + height + 4), bgColor.getColor().getRGB());

		var pose = context.pose();
		pose.pushMatrix();
		pose.translate((float)x, (float)y);
		pose.scale((float)textScale, (float)textScale);
		context.text(MC.font, text, 0, 0,
			(sprinting ? sprintColor : idleColor).getColor().getRGB(), true);
		pose.popMatrix();
	}
}

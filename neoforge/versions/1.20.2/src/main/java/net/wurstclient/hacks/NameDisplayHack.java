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

import net.wurstclient.Category;
import net.wurstclient.SearchTags;
import net.wurstclient.events.GUIRenderListener;
import net.wurstclient.hack.Hack;
import net.wurstclient.settings.CheckboxSetting;
import net.wurstclient.settings.ColorSetting;
import net.wurstclient.settings.SliderSetting;
import net.wurstclient.settings.SliderSetting.ValueDisplay;
import net.minecraft.client.gui.GuiGraphics;

/**
 * HUD overlay showing the local player's account/skin name.
 *
 * <p>Simplification: Soar's Name/Ign combo is reduced to a checkbox that
 * switches the prefix between "Name:" and "Ign:".
 */
@SearchTags({"name display", "name", "username", "skin name", "ign",
	"account name"})
public final class NameDisplayHack extends Hack implements GUIRenderListener
{
	private static final int PADDING = 4;

	private final SliderSetting scale = new SliderSetting("Scale",
		"Scale of the name display text.", 1.5, 0.5, 5, 0.1,
		ValueDisplay.DECIMAL);

	private final SliderSetting posX = new SliderSetting("Position X",
		"X position of the name display.", 100, 0, 2500, 1,
		ValueDisplay.INTEGER);

	private final SliderSetting posY = new SliderSetting("Position Y",
		"Y position of the name display.", 250, 0, 2500, 1,
		ValueDisplay.INTEGER);

	private final CheckboxSetting showPrefix = new CheckboxSetting(
		"Show prefix", "Shows a prefix in front of the name.", true);

	private final CheckboxSetting ignPrefix = new CheckboxSetting(
		"Use IGN prefix", "Shows 'Ign:' instead of 'Name:'.", false);

	private final CheckboxSetting background = new CheckboxSetting(
		"Show background", "Draws a colored box behind the text.", true);

	private final ColorSetting bgColor = new ColorSetting("Background color",
		"Color of the background box.", new Color(0, 0, 0, 150));

	private final ColorSetting textColor = new ColorSetting("Text color",
		"Color of the name display text.", Color.WHITE);

	public NameDisplayHack()
	{
		super("NameDisplay");
		setCategory(Category.RENDER);
		addSetting(scale);
		addSetting(posX);
		addSetting(posY);
		addSetting(showPrefix);
		addSetting(ignPrefix);
		addSetting(background);
		addSetting(bgColor);
		addSetting(textColor);
		ignPrefix.visibleWhen(showPrefix::isChecked);
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
	public void onRenderGUI(GuiGraphics context, float partialTicks)
	{
		if(MC.player == null)
			return;

		String name = MC.getUser().getName();
		String prefix = ignPrefix.isChecked() ? "Ign: " : "Name: ";
		String text = showPrefix.isChecked() ? prefix + name : name;

		int width = MC.font.width(text);

		var pose = context.pose();
		pose.pushPose();
		pose.translate((float)posX.getValue(), (float)posY.getValue(), 0);
		pose.scale(scale.getValueF(), scale.getValueF(), 1);

		if(background.isChecked())
			context.fill(-PADDING, -PADDING, width + PADDING,
				MC.font.lineHeight + PADDING, bgColor.getColor().getRGB());

		context.drawString(MC.font, text, 0, 0, textColor.getColorI(), true);

		pose.popPose();
	}
}

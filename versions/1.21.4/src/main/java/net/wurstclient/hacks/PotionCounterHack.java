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

import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
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
 * HUD overlay counting active potion effects and potion items.
 *
 * <p>Simplification: Soar counted only healing potions in the inventory and
 * rendered an icon. This port counts all potion, splash potion and lingering
 * potion items (in stack quantities) and shows the number of active effects
 * alongside them. No icon is drawn.
 */
@SearchTags({"potion counter", "potions", "pots", "effects", "heal potions"})
public final class PotionCounterHack extends Hack implements GUIRenderListener
{
	private static final int PADDING = 4;
	private static final int LINE_GAP = 2;

	private final SliderSetting scale = new SliderSetting("Scale",
		"Scale of the potion counter.", 1.5, 0.5, 5, 0.1,
		ValueDisplay.DECIMAL);

	private final SliderSetting posX = new SliderSetting("Position X",
		"X position of the potion counter.", 500, 0, 2500, 1,
		ValueDisplay.INTEGER);

	private final SliderSetting posY = new SliderSetting("Position Y",
		"Y position of the potion counter.", 100, 0, 2500, 1,
		ValueDisplay.INTEGER);

	private final CheckboxSetting background = new CheckboxSetting(
		"Show background", "Draws a colored box behind the counter.", true);

	private final ColorSetting bgColor = new ColorSetting("Background color",
		"Color of the background box.", new Color(0, 0, 0, 150));

	private final ColorSetting textColor = new ColorSetting("Text color",
		"Color of the potion counter text.", Color.WHITE);

	public PotionCounterHack()
	{
		super("PotionCounter");
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

		String[] lines = {"Active effects: " + MC.player.getActiveEffects().size(),
			"Potion items: " + countPotionItems()};

		int width = 0;
		for(String line : lines)
			width = Math.max(width, MC.font.width(line));

		int lineHeight = MC.font.lineHeight + LINE_GAP;
		int height = lines.length * lineHeight;

		var pose = context.pose();
		pose.pushPose();
		pose.translate((float)posX.getValue(), (float)posY.getValue(), 0);
		pose.scale(scale.getValueF(), scale.getValueF(), 1);

		if(background.isChecked())
			context.fill(-PADDING, -PADDING, width + PADDING,
				height + PADDING, bgColor.getColor().getRGB());

		int color = textColor.getColorI();
		for(int i = 0; i < lines.length; i++)
			context.drawString(MC.font, lines[i], 0, i * lineHeight, color, true);

		pose.popPose();
	}

	private int countPotionItems()
	{
		var inventory = MC.player.getInventory();
		int count = 0;

		for(int i = 0; i < inventory.getContainerSize(); i++)
		{
			ItemStack stack = inventory.getItem(i);
			if(stack.is(Items.POTION) || stack.is(Items.SPLASH_POTION)
				|| stack.is(Items.LINGERING_POTION))
				count += stack.getCount();
		}

		return count;
	}
}

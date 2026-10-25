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
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import net.minecraft.core.component.DataComponents;
import net.minecraft.world.food.FoodData;
import net.minecraft.world.food.FoodProperties;
import net.minecraft.world.item.ItemStack;
import net.wurstclient.Category;
import net.wurstclient.SearchTags;
import net.wurstclient.events.GUIRenderListener;
import net.wurstclient.hack.Hack;
import net.wurstclient.settings.CheckboxSetting;
import net.wurstclient.settings.ColorSetting;
import net.wurstclient.settings.SliderSetting;
import net.wurstclient.settings.SliderSetting.ValueDisplay;
import net.minecraft.client.gui.GuiGraphicsExtractor;

/**
 * HUD showing the player's hunger and saturation, plus a preview of the food
 * values of the held item.
 *
 * <p>Simplification: the 1.21.11 {@link FoodData} API no longer exposes the
 * exhaustion level, so that value is omitted. Soar's vanilla hunger bar overlay
 * would also need a Gui mixin; this port draws a standalone HUD instead.
 */
@SearchTags({"apple skin", "appleskin", "hunger", "saturation", "food values",
	"food preview"})
public final class AppleSkinHack extends Hack implements GUIRenderListener
{
	private final SliderSetting scale = new SliderSetting("Scale",
		"Scale of the hunger text.", 1.5, 0.5, 5, 0.1, ValueDisplay.DECIMAL);

	private final SliderSetting posX = new SliderSetting("Position X",
		"X position of the hunger text.", 500, 0, 2500, 1,
		ValueDisplay.INTEGER);

	private final SliderSetting posY = new SliderSetting("Position Y",
		"Y position of the hunger text.", 180, 0, 2500, 1,
		ValueDisplay.INTEGER);

	private final CheckboxSetting showFoodPreview = new CheckboxSetting(
		"Show food preview",
		"Shows the hunger and saturation restored by the held food.", true);

	private final CheckboxSetting background = new CheckboxSetting(
		"Show background", "Draws a colored box behind the text.", true);

	private final ColorSetting bgColor = new ColorSetting("Background color",
		"Color of the background box.", new Color(0, 0, 0, 150));

	private final ColorSetting textColor = new ColorSetting("Text color",
		"Color of the hunger text.", new Color(255, 255, 255));

	public AppleSkinHack()
	{
		super("AppleSkin");
		setCategory(Category.RENDER);
		addSetting(scale);
		addSetting(posX);
		addSetting(posY);
		addSetting(showFoodPreview);
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
	public void onRenderGUI(GuiGraphicsExtractor context, float partialTicks)
	{
		if(MC.player == null)
			return;

		FoodData food = MC.player.getFoodData();
		List<String> lines = new ArrayList<>();
		lines.add("Hunger: " + food.getFoodLevel() + " / 20");
		lines.add(String.format(Locale.ROOT, "Saturation: %.1f",
			food.getSaturationLevel()));

		if(showFoodPreview.isChecked())
		{
			ItemStack held = MC.player.getMainHandItem();
			FoodProperties preview = held.get(DataComponents.FOOD);

			if(preview != null)
			{
				float saturationGain =
					preview.nutrition() * preview.saturation() * 2.0F;
				lines.add(String.format(Locale.ROOT,
					"Food: +%d hunger, +%.1f saturation", preview.nutrition(),
					saturationGain));
			}
		}

		double textScale = scale.getValue();
		double width = 0;
		for(String line : lines)
			width = Math.max(width, MC.font.width(line));
		width *= textScale;
		double height = lines.size() * MC.font.lineHeight * textScale;
		double x = posX.getValue();
		double y = posY.getValue();

		if(background.isChecked())
			context.fill((int)(x - 4), (int)(y - 4), (int)(x + width + 4),
				(int)(y + height + 4), bgColor.getColor().getRGB());

		var pose = context.pose();
		pose.pushMatrix();
		pose.translate((float)x, (float)y);
		pose.scale((float)textScale, (float)textScale);

		int color = textColor.getColor().getRGB();
		for(int i = 0; i < lines.size(); i++)
			context.text(MC.font, lines.get(i), 0, i * MC.font.lineHeight,
				color, true);

		pose.popMatrix();
	}
}

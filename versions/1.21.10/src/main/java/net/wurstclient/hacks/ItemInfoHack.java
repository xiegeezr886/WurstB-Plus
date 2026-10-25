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

import net.minecraft.world.item.ItemStack;
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
 * HUD overlay showing the name, count and remaining durability of the item in
 * the main hand.
 *
 * <p>Simplification: Soar rendered enchantment abbreviations and potion
 * effects above the hotbar. This port shows the basic item information at a
 * configurable position instead and skips those effect strings.
 */
@SearchTags({"item info", "held item", "durability", "item count",
	"item hud", "hand item"})
public final class ItemInfoHack extends Hack implements GUIRenderListener
{
	private static final int PADDING = 4;
	private static final int LINE_GAP = 2;
	private static final int MAX_NAME_WIDTH = 180;

	private final SliderSetting scale = new SliderSetting("Scale",
		"Scale of the item info text.", 1.5, 0.5, 5, 0.1,
		ValueDisplay.DECIMAL);

	private final SliderSetting posX = new SliderSetting("Position X",
		"X position of the item info.", 100, 0, 2500, 1, ValueDisplay.INTEGER);

	private final SliderSetting posY = new SliderSetting("Position Y",
		"Y position of the item info.", 500, 0, 2500, 1, ValueDisplay.INTEGER);

	private final CheckboxSetting showCount = new CheckboxSetting("Show count",
		"Shows the stack size when it is greater than one.", true);

	private final CheckboxSetting showDurability = new CheckboxSetting(
		"Show durability", "Shows remaining durability for damageable items.",
		true);

	private final CheckboxSetting background = new CheckboxSetting(
		"Show background", "Draws a colored box behind the text.", true);

	private final ColorSetting bgColor = new ColorSetting("Background color",
		"Color of the background box.", new Color(0, 0, 0, 150));

	private final ColorSetting textColor = new ColorSetting("Text color",
		"Color of the item info text.", Color.WHITE);

	public ItemInfoHack()
	{
		super("ItemInfo");
		setCategory(Category.RENDER);
		addSetting(scale);
		addSetting(posX);
		addSetting(posY);
		addSetting(showCount);
		addSetting(showDurability);
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

		ItemStack stack = MC.player.getMainHandItem();
		List<String> lines = new ArrayList<>();

		if(stack.isEmpty())
			lines.add("Empty hand");
		else
		{
			lines.add(trimToWidth(stack.getHoverName().getString()));

			if(showCount.isChecked() && stack.getCount() > 1)
				lines.add("Count: " + stack.getCount());

			if(showDurability.isChecked() && stack.isDamageableItem())
				lines.add(getDurabilityLine(stack));
		}

		int width = 0;
		for(String line : lines)
			width = Math.max(width, MC.font.width(line));

		int lineHeight = MC.font.lineHeight + LINE_GAP;
		int height = lines.size() * lineHeight - LINE_GAP;

		var pose = context.pose();
		pose.pushMatrix();
		pose.translate((float)posX.getValue(), (float)posY.getValue());
		pose.scale(scale.getValueF(), scale.getValueF());

		if(background.isChecked())
			context.fill(-PADDING, -PADDING, width + PADDING,
				height + PADDING, bgColor.getColor().getRGB());

		int color = textColor.getColorI();
		for(int i = 0; i < lines.size(); i++)
			context.text(MC.font, lines.get(i), 0, i * lineHeight, color,
				true);

		pose.popMatrix();
	}

	private String getDurabilityLine(ItemStack stack)
	{
		int max = stack.getMaxDamage();
		if(max <= 0)
			return "Durability: -";

		int remaining = max - stack.getDamageValue();
		int percent = Math.round(remaining * 100F / max);
		return "Durability: " + remaining + " / " + max + " (" + percent + "%)";
	}

	private String trimToWidth(String text)
	{
		if(MC.font.width(text) <= MAX_NAME_WIDTH)
			return text;

		return MC.font.plainSubstrByWidth(text, MAX_NAME_WIDTH - 6) + "...";
	}
}

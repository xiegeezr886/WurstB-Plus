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

import net.minecraft.world.entity.EquipmentSlot;
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
 * HUD showing the equipped armor pieces with their remaining durability as a
 * percentage.
 *
 * <p>Simplification: empty slots are omitted instead of rendering placeholders,
 * and only the horizontal layout is provided.
 */
@SearchTags({"armor status", "armor", "durability", "armor hud"})
public final class ArmorStatusHack extends Hack implements GUIRenderListener
{
	private static final EquipmentSlot[] ARMOR_SLOTS = {EquipmentSlot.HEAD,
		EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET};
	private static final int ICON_SIZE = 16;
	private static final int SLOT_STEP = 24;
	private static final int TEXT_GAP = 2;

	private final SliderSetting scale = new SliderSetting("Scale",
		"Scale of the armor status.", 1.5, 0.5, 5, 0.1, ValueDisplay.DECIMAL);

	private final SliderSetting posX = new SliderSetting("Position X",
		"X position of the armor status.", 500, 0, 2500, 1,
		ValueDisplay.INTEGER);

	private final SliderSetting posY = new SliderSetting("Position Y",
		"Y position of the armor status.", 200, 0, 2500, 1,
		ValueDisplay.INTEGER);

	private final CheckboxSetting showPercent = new CheckboxSetting(
		"Show durability", "Shows each piece's remaining durability in percent.",
		true);

	private final CheckboxSetting background = new CheckboxSetting(
		"Show background", "Draws a colored box behind the armor status.", true);

	private final ColorSetting bgColor = new ColorSetting("Background color",
		"Color of the background box.", new Color(0, 0, 0, 150));

	private final ColorSetting textColor = new ColorSetting("Text color",
		"Color of the durability text.", new Color(255, 255, 255));

	public ArmorStatusHack()
	{
		super("ArmorStatus");
		setCategory(Category.RENDER);
		addSetting(scale);
		addSetting(posX);
		addSetting(posY);
		addSetting(showPercent);
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

		List<ItemStack> armor = new ArrayList<>();
		for(EquipmentSlot slot : ARMOR_SLOTS)
		{
			ItemStack stack = MC.player.getItemBySlot(slot);
			if(!stack.isEmpty())
				armor.add(stack);
		}

		if(armor.isEmpty())
			return;

		double textScale = scale.getValue();
		double width = armor.size() * SLOT_STEP - (SLOT_STEP - ICON_SIZE);
		double height = ICON_SIZE + TEXT_GAP
			+ (showPercent.isChecked() ? MC.font.lineHeight : 0);
		double x = posX.getValue();
		double y = posY.getValue();

		if(background.isChecked())
			context.fill((int)(x - 4), (int)(y - 4),
				(int)(x + width * textScale + 4),
				(int)(y + height * textScale + 4), bgColor.getColor().getRGB());

		var pose = context.pose();
		pose.pushMatrix();
		pose.translate((float)x, (float)y);
		pose.scale((float)textScale, (float)textScale);

		int color = textColor.getColor().getRGB();
		for(int i = 0; i < armor.size(); i++)
		{
			ItemStack stack = armor.get(i);
			int itemX = i * SLOT_STEP;

			context.item(stack, itemX, 0);
			context.itemDecorations(MC.font, stack, itemX, 0);

			if(showPercent.isChecked())
			{
				String label = durabilityPercent(stack);
				int textX = itemX + (ICON_SIZE - MC.font.width(label)) / 2;
				context.text(MC.font, label, textX,
					ICON_SIZE + TEXT_GAP, color, true);
			}
		}

		pose.popMatrix();
	}

	private String durabilityPercent(ItemStack stack)
	{
		if(!stack.isDamageableItem())
			return "-";

		int max = stack.getMaxDamage();
		if(max <= 0)
			return "-";

		int remaining = max - stack.getDamageValue();
		return Math.round(remaining * 100.0F / max) + "%";
	}
}

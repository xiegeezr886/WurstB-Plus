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
 * HUD overlay showing the 27 main inventory slots as a 9x3 grid.
 *
 * <p>Simplification: Soar drew the panel with NanoVG. This port uses the flat
 * GUI renderer and omits empty slot backgrounds, so only occupied slots are
 * visible. The hotbar, armor and offhand slots are not shown, matching the
 * upstream module.
 */
@SearchTags({"inventory display", "inventory", "items", "inventory hud",
	"item grid"})
public final class InventoryDisplayHack extends Hack
	implements GUIRenderListener
{
	private static final int COLUMNS = 9;
	private static final int ROWS = 3;
	private static final int FIRST_SLOT = 9;
	private static final int SLOT_SIZE = 16;
	private static final int SLOT_STEP = 18;
	private static final int PADDING = 4;
	private static final int TITLE_GAP = 3;

	private final SliderSetting scale = new SliderSetting("Scale",
		"Scale of the inventory display.", 1.5, 0.5, 5, 0.1,
		ValueDisplay.DECIMAL);

	private final SliderSetting posX = new SliderSetting("Position X",
		"X position of the inventory display.", 100, 0, 2500, 1,
		ValueDisplay.INTEGER);

	private final SliderSetting posY = new SliderSetting("Position Y",
		"Y position of the inventory display.", 400, 0, 2500, 1,
		ValueDisplay.INTEGER);

	private final CheckboxSetting showTitle = new CheckboxSetting("Show title",
		"Shows the 'Inventory' title above the grid.", true);

	private final CheckboxSetting background = new CheckboxSetting(
		"Show background", "Draws a colored box behind the grid.", true);

	private final ColorSetting bgColor = new ColorSetting("Background color",
		"Color of the background box.", new Color(0, 0, 0, 150));

	private final ColorSetting textColor = new ColorSetting("Text color",
		"Color of the title text.", Color.WHITE);

	public InventoryDisplayHack()
	{
		super("InventoryDisplay");
		setCategory(Category.RENDER);
		addSetting(scale);
		addSetting(posX);
		addSetting(posY);
		addSetting(showTitle);
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

		var inventory = MC.player.getInventory();

		var pose = context.pose();
		pose.pushPose();
		pose.translate((float)posX.getValue(), (float)posY.getValue(), 0);
		pose.scale(scale.getValueF(), scale.getValueF(), 1);

		int gridWidth = (COLUMNS - 1) * SLOT_STEP + SLOT_SIZE;
		int gridHeight = (ROWS - 1) * SLOT_STEP + SLOT_SIZE;
		int titleHeight = showTitle.isChecked()
			? MC.font.lineHeight + TITLE_GAP : 0;
		int width = Math.max(gridWidth,
			showTitle.isChecked() ? MC.font.width("Inventory") : 0);
		int height = titleHeight + gridHeight;

		if(background.isChecked())
			context.fill(-PADDING, -PADDING, width + PADDING,
				height + PADDING, bgColor.getColor().getRGB());

		if(showTitle.isChecked())
			context.drawString(MC.font, "Inventory", 0, 0, textColor.getColorI(),
				true);

		for(int row = 0; row < ROWS; row++)
			for(int column = 0; column < COLUMNS; column++)
			{
				ItemStack stack = inventory
					.getItem(FIRST_SLOT + row * COLUMNS + column);
				if(stack.isEmpty())
					continue;

				int itemX = column * SLOT_STEP;
				int itemY = titleHeight + row * SLOT_STEP;
				context.renderItem(stack, itemX, itemY);
				context.renderItemDecorations(MC.font, stack, itemX, itemY);
			}

		pose.popPose();
	}
}

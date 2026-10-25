/*
 * Copyright (c) 2026 WurstB+ Plus contributors
 *
 * This source code is subject to the terms of the GNU Affero General Public
 * License, version 3. If a copy of the AGPL was not distributed with this
 * file, You can obtain one at: https://www.gnu.org/licenses/agpl-3.0.txt
 */
// Ported from Soar Client v7.1.2 (LGPL-3.0).
package net.wurstclient.hacks;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemStack;
import net.wurstclient.Category;
import net.wurstclient.SearchTags;
import net.wurstclient.clickgui2.FlatRenderer;
import net.wurstclient.events.GUIRenderListener;
import net.wurstclient.hack.Hack;
import net.wurstclient.settings.CheckboxSetting;
import net.wurstclient.settings.EnumSetting;
import net.wurstclient.util.RenderUtils;

/**
 * Modernized hotbar drawn over the vanilla hotbar, with a styled background,
 * animated selection highlight, item names and health/armor bars.
 *
 * <p>Simplification: Soar drew its rounded panels and gradients with NanoVG.
 * This port uses flat fills and rounded rectangles from the existing flat
 * renderer. Shadows and the separate experience-bar hooks are skipped.
 */
@SearchTags({"modern hotbar", "hotbar", "hud", "inventory", "item names"})
public final class ModernHotbarHack extends Hack implements GUIRenderListener
{
	private enum Design
	{
		NORMAL("Normal"),
		SOAR("Soar"),
		CHILL("Chill");

		private final String name;

		Design(String name)
		{
			this.name = name;
		}

		@Override
		public String toString()
		{
			return name;
		}
	}

	private final EnumSetting<Design> design = new EnumSetting<>("Design",
		"Visual style of the hotbar.", Design.values(), Design.NORMAL);

	private final CheckboxSetting smooth = new CheckboxSetting("Smooth",
		"Animates the selection highlight.", true);

	private final CheckboxSetting showItemName = new CheckboxSetting("Item name",
		"Shows the name of the selected item above the hotbar.", true);

	private final CheckboxSetting showHealth = new CheckboxSetting("Health bar",
		"Shows your health above the hotbar.", true);

	private final CheckboxSetting showArmor = new CheckboxSetting("Armor bar",
		"Shows your armor value above the hotbar.", true);

	private float highlightX = Float.NaN;
	private long lastFrameNanos;

	public ModernHotbarHack()
	{
		super("ModernHotbar");
		setCategory(Category.RENDER);
		addSetting(design);
		addSetting(smooth);
		addSetting(showItemName);
		addSetting(showHealth);
		addSetting(showArmor);
	}

	@Override
	protected void onEnable()
	{
		highlightX = Float.NaN;
		lastFrameNanos = 0;
		EVENTS.add(GUIRenderListener.class, this);
	}

	@Override
	protected void onDisable()
	{
		EVENTS.remove(GUIRenderListener.class, this);
		highlightX = Float.NaN;
		lastFrameNanos = 0;
	}

	@Override
	public void onRenderGUI(GuiGraphics context, float partialTicks)
	{
		if(MC.player == null || MC.options.hideGui)
			return;

		int guiWidth = MC.getWindow().getGuiScaledWidth();
		int guiHeight = MC.getWindow().getGuiScaledHeight();
		int center = guiWidth / 2;
		Design style = design.getSelected();

		int panelX1;
		int panelX2;
		int panelY1;
		int panelY2;

		if(style == Design.CHILL)
		{
			panelX1 = 0;
			panelX2 = guiWidth;
			panelY1 = guiHeight - 22;
			panelY2 = guiHeight;
		}else
		{
			panelX1 = center - 92;
			panelX2 = center + 92;
			panelY1 = guiHeight - 26;
			panelY2 = guiHeight - 2;
		}

		drawPanel(context, style, panelX1, panelY1, panelX2, panelY2);

		int selected = MC.player.getInventory().getSelectedSlot();
		float targetX = center - 91 + selected * 20;
		float highlight = updateHighlight(targetX);
		drawHighlight(context, style, Math.round(highlight), panelY1);

		int itemY = panelY1 + (panelY2 - panelY1 - 16) / 2;
		for(int i = 0; i < 9; i++)
		{
			ItemStack stack = MC.player.getInventory().getItem(i);
			if(stack.isEmpty())
				continue;

			int itemX = center - 90 + i * 20 + 2;
			context.renderItem(stack, itemX, itemY);
			context.renderItemDecorations(MC.font, stack, itemX, itemY);
		}

		drawBars(context, panelX1, panelX2, panelY1);
		drawItemName(context, center, panelY1);
	}

	private void drawPanel(GuiGraphics context, Design style, int x1,
		int y1, int x2, int y2)
	{
		switch(style)
		{
			case SOAR:
			{
				int top =
					RenderUtils.toIntColor(WURST.getGui().getAcColor(), 0.8F);
				int bottom =
					RenderUtils.toIntColor(WURST.getGui().getAcColor(), 0.45F);
				context.fillGradient(x1, y1, x2, y2, top, bottom);
				FlatRenderer.drawRoundedOutline(context, x1, y1, x2, y2, 6,
					0x60FFFFFF);
				break;
			}

			case CHILL:
			context.fill(x1, y1, x2, y2, 0xB41A1A1A);
			break;

			case NORMAL:
			default:
			FlatRenderer.fillRoundedRect(context, x1, y1, x2, y2, 6,
				0xA0000000);
			FlatRenderer.drawRoundedOutline(context, x1, y1, x2, y2, 6,
				0x30FFFFFF);
			break;
		}
	}

	private void drawHighlight(GuiGraphics context, Design style,
		int x, int y)
	{
		switch(style)
		{
			case SOAR:
			FlatRenderer.fillRoundedRect(context, x, y, x + 22, y + 22, 4,
				0x8CFFFFFF);
			break;

			case CHILL:
			FlatRenderer.fillRoundedRect(context, x, y, x + 22, y + 22, 4,
				0xB3E6E6E6);
			break;

			case NORMAL:
			default:
			FlatRenderer.fillRoundedRect(context, x, y, x + 22, y + 22, 4,
				0x70000000);
			FlatRenderer.drawRoundedOutline(context, x, y, x + 22, y + 22, 4,
				RenderUtils.toIntColor(WURST.getGui().getAcColor(), 0.85F));
			break;
		}
	}

	private float updateHighlight(float targetX)
	{
		if(!smooth.isChecked())
		{
			highlightX = targetX;
			return highlightX;
		}

		long now = System.nanoTime();
		if(Float.isNaN(highlightX) || lastFrameNanos == 0)
			highlightX = targetX;
		else
		{
			float delta = Math.min(0.1F, (now - lastFrameNanos) / 1_000_000_000F);
			float factor = 1 - (float)Math.exp(-18 * delta);
			highlightX += (targetX - highlightX) * factor;
		}

		lastFrameNanos = now;
		return highlightX;
	}

	private void drawBars(GuiGraphics context, int panelX1,
		int panelX2, int panelY1)
	{
		if(!showHealth.isChecked() && !showArmor.isChecked())
			return;

		int center = MC.getWindow().getGuiScaledWidth() / 2;
		int y = panelY1 - 7;

		if(showHealth.isChecked())
		{
			float max = Math.max(1, MC.player.getMaxHealth());
			float ratio =
				Mth.clamp(MC.player.getHealth() + MC.player.getAbsorptionAmount(),
					0, max) / max;
			drawBar(context, panelX1 + 2, center - 4, y, ratio, 0xFFFF5555);
		}

		if(showArmor.isChecked())
		{
			float ratio = Mth.clamp(MC.player.getArmorValue() / 20F, 0, 1);
			drawBar(context, center + 4, panelX2 - 2, y, ratio, 0xFF55AAFF);
		}
	}

	private void drawBar(GuiGraphics context, int x1, int x2, int y,
		float ratio, int fillColor)
	{
		if(x2 <= x1)
			return;

		context.fill(x1, y, x2, y + 3, 0xA0000000);
		int filled = x1 + Math.round((x2 - x1) * Mth.clamp(ratio, 0, 1));
		if(filled > x1)
			context.fill(x1, y, filled, y + 3, fillColor);
	}

	private void drawItemName(GuiGraphics context, int center,
		int panelY1)
	{
		if(!showItemName.isChecked())
			return;

		ItemStack stack = MC.player.getInventory().getSelectedItem();
		if(stack.isEmpty())
			return;

		String name = stack.getHoverName().getString();
		int width = MC.font.width(name);
		int y = panelY1 - 19;

		context.fill(center - width / 2 - 3, y - 2, center + width / 2 + 3,
			y + MC.font.lineHeight + 1, 0x90000000);
		context.drawCenteredString(MC.font, name, center, y, 0xFFFFFFFF);
	}
}

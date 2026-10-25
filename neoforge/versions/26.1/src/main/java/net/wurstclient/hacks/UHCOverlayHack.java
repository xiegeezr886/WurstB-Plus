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

import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
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
import net.minecraft.client.gui.GuiGraphicsExtractor;

/**
 * UHC-style health overlay showing current health, absorption and any active
 * healing source.
 *
 * <p>Simplification: the original Soar module only scaled the gold and skull
 * icons drawn by other UHC code, which would require mixins. This port instead
 * draws the health/healing information described for the Wurst version.
 */
@SearchTags({"uhc overlay", "uhc", "health overlay", "healing", "absorption"})
public final class UHCOverlayHack extends Hack implements GUIRenderListener
{
	private static final int BAR_WIDTH = 100;
	private static final int BAR_HEIGHT = 4;
	private static final int BAR_GAP = 2;

	private final SliderSetting scale = new SliderSetting("Scale",
		"Scale of the health overlay.", 1.5, 0.5, 5, 0.1,
		ValueDisplay.DECIMAL);

	private final SliderSetting posX = new SliderSetting("Position X",
		"X position of the health overlay.", 500, 0, 2500, 1,
		ValueDisplay.INTEGER);

	private final SliderSetting posY = new SliderSetting("Position Y",
		"Y position of the health overlay.", 240, 0, 2500, 1,
		ValueDisplay.INTEGER);

	private final CheckboxSetting showAbsorption = new CheckboxSetting(
		"Show absorption", "Shows the player's absorption hearts.", true);

	private final CheckboxSetting background = new CheckboxSetting(
		"Show background", "Draws a colored box behind the overlay.", true);

	private final ColorSetting bgColor = new ColorSetting("Background color",
		"Color of the background box.", new Color(0, 0, 0, 150));

	private final ColorSetting textColor = new ColorSetting("Text color",
		"Color of the health text.", new Color(255, 255, 255));

	private final ColorSetting healthColor = new ColorSetting("Health color",
		"Color of the health bar and of low-health text.",
		new Color(0, 255, 0));

	public UHCOverlayHack()
	{
		super("UHCOverlay");
		setCategory(Category.RENDER);
		addSetting(scale);
		addSetting(posX);
		addSetting(posY);
		addSetting(showAbsorption);
		addSetting(background);
		addSetting(bgColor);
		addSetting(textColor);
		addSetting(healthColor);
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

		float health = MC.player.getHealth();
		float maxHealth = Math.max(1.0F, MC.player.getMaxHealth());
		float absorption = MC.player.getAbsorptionAmount();

		List<String> lines = new ArrayList<>();
		lines.add(String.format(Locale.ROOT, "Health: %.1f / %.1f", health,
			maxHealth));

		if(showAbsorption.isChecked() && absorption > 0)
			lines.add(String.format(Locale.ROOT, "Absorption: %.1f",
				absorption));

		MobEffectInstance regeneration = findRegeneration();
		if(regeneration != null)
		{
			String name =
				regeneration.getEffect().value().getDisplayName().getString();
			if(regeneration.getAmplifier() > 0)
				name += " " + (regeneration.getAmplifier() + 1);

			String time = regeneration.isInfiniteDuration() ? "infinite"
				: formatDuration(regeneration.getDuration());
			float perSecond = 0.4F * (regeneration.getAmplifier() + 1);

			lines.add(String.format(Locale.ROOT,
				"Healing: +%.1f HP/s (%s, %s)", perSecond, name, time));
		}

		ItemStack held = MC.player.getMainHandItem();
		if(held.is(Items.GOLDEN_APPLE))
			lines.add("Held: Golden Apple");
		else if(held.is(Items.ENCHANTED_GOLDEN_APPLE))
			lines.add("Held: Enchanted Golden Apple");

		int maxTextWidth = BAR_WIDTH;
		for(String line : lines)
			maxTextWidth = Math.max(maxTextWidth, MC.font.width(line));

		double textScale = scale.getValue();
		double width = maxTextWidth * textScale;
		int localHeight = MC.font.lineHeight + BAR_GAP + BAR_HEIGHT + BAR_GAP
			+ (lines.size() - 1) * MC.font.lineHeight;
		double height = localHeight * textScale;
		double x = posX.getValue();
		double y = posY.getValue();

		if(background.isChecked())
			context.fill((int)(x - 4), (int)(y - 4), (int)(x + width + 4),
				(int)(y + height + 4), bgColor.getColor().getRGB());

		var pose = context.pose();
		pose.pushMatrix();
		pose.translate((float)x, (float)y);
		pose.scale((float)textScale, (float)textScale);

		boolean lowHealth = health / maxHealth < 0.5F;
		int healthRgb = healthColor.getColor().getRGB();
		int textRgb = textColor.getColor().getRGB();
		int healthTextRgb = lowHealth ? healthRgb : textRgb;

		int lineY = 0;
		context.text(MC.font, lines.get(0), 0, lineY, healthTextRgb, true);

		int barY = lineY + MC.font.lineHeight + BAR_GAP;
		context.fill(0, barY, BAR_WIDTH, barY + BAR_HEIGHT, 0x80000000);
		int fillWidth =
			Math.round(BAR_WIDTH * Math.max(0, Math.min(1, health / maxHealth)));
		if(fillWidth > 0)
			context.fill(0, barY, fillWidth, barY + BAR_HEIGHT, healthRgb);

		lineY = barY + BAR_HEIGHT + BAR_GAP;
		for(int i = 1; i < lines.size(); i++)
		{
			context.text(MC.font, lines.get(i), 0, lineY, textRgb, true);
			lineY += MC.font.lineHeight;
		}

		pose.popMatrix();
	}

	private MobEffectInstance findRegeneration()
	{
		for(MobEffectInstance effect : MC.player.getActiveEffects())
			if(effect.getEffect().is(MobEffects.REGENERATION))
				return effect;

		return null;
	}

	private String formatDuration(int ticks)
	{
		int seconds = Math.max(0, ticks / 20);
		return String.format(Locale.ROOT, "%d:%02d", seconds / 60,
			seconds % 60);
	}
}

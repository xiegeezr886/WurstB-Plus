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
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

import net.minecraft.world.effect.MobEffectInstance;
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
 * HUD listing the player's active potion effects with their remaining time.
 *
 * <p>Simplification: Soar drew the vanilla status-effect icons; this port uses
 * a text list in the flat HUD style.
 */
@SearchTags({"potion status", "potions", "effects", "potion timer",
	"effect timer"})
public final class PotionStatusHack extends Hack implements GUIRenderListener
{
	private final SliderSetting scale = new SliderSetting("Scale",
		"Scale of the potion list.", 1.5, 0.5, 5, 0.1, ValueDisplay.DECIMAL);

	private final SliderSetting posX = new SliderSetting("Position X",
		"X position of the potion list.", 500, 0, 2500, 1,
		ValueDisplay.INTEGER);

	private final SliderSetting posY = new SliderSetting("Position Y",
		"Y position of the potion list.", 220, 0, 2500, 1,
		ValueDisplay.INTEGER);

	private final CheckboxSetting compact = new CheckboxSetting("Compact",
		"Shows each effect's name and time on a single line.", true);

	private final CheckboxSetting background = new CheckboxSetting(
		"Show background", "Draws a colored box behind the potion list.", true);

	private final ColorSetting bgColor = new ColorSetting("Background color",
		"Color of the background box.", new Color(0, 0, 0, 150));

	private final ColorSetting textColor = new ColorSetting("Text color",
		"Color of the potion text.", new Color(255, 255, 255));

	public PotionStatusHack()
	{
		super("PotionStatus");
		setCategory(Category.RENDER);
		addSetting(scale);
		addSetting(posX);
		addSetting(posY);
		addSetting(compact);
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

		List<MobEffectInstance> effects =
			new ArrayList<>(MC.player.getActiveEffects());

		if(effects.isEmpty())
			return;

		effects.sort(Comparator.comparing(effect -> effect.getEffect().value()
			.getDisplayName().getString()));

		List<String> lines = new ArrayList<>();
		for(MobEffectInstance effect : effects)
		{
			String name = effect.getEffect().value().getDisplayName().getString();
			if(effect.getAmplifier() > 0)
				name += " " + (effect.getAmplifier() + 1);

			String time = effect.isInfiniteDuration() ? "infinite"
				: formatDuration(effect.getDuration());

			if(compact.isChecked())
				lines.add(name + "  " + time);
			else
			{
				lines.add(name);
				lines.add("  " + time);
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

	private String formatDuration(int ticks)
	{
		int seconds = Math.max(0, ticks / 20);
		return String.format(Locale.ROOT, "%d:%02d", seconds / 60,
			seconds % 60);
	}
}

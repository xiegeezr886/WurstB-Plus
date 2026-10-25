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
import java.util.Map;
import java.util.UUID;

import net.minecraft.world.BossEvent;
import net.wurstclient.Category;
import net.wurstclient.SearchTags;
import net.wurstclient.events.GUIRenderListener;
import net.wurstclient.hack.Hack;
import net.wurstclient.mixinterface.IBossHealthOverlay;
import net.wurstclient.settings.CheckboxSetting;
import net.wurstclient.settings.ColorSetting;
import net.wurstclient.settings.SliderSetting;
import net.wurstclient.settings.SliderSetting.ValueDisplay;
import net.minecraft.client.gui.GuiGraphics;

/**
 * HUD rendering the vanilla boss bar(s) at a configurable screen position.
 *
 * <p>The boss events are read through the project's existing
 * {@link IBossHealthOverlay} accessor; no new mixin is added by this port.
 */
@SearchTags({"boss health", "boss bar", "bossbar", "boss hud"})
public final class BossHealthHack extends Hack implements GUIRenderListener
{
	private static final int BAR_HEIGHT = 10;
	private static final int NAME_GAP = 1;
	private static final int ROW_GAP = 4;

	private final SliderSetting scale = new SliderSetting("Scale",
		"Scale of the boss bar.", 1.5, 0.5, 5, 0.1, ValueDisplay.DECIMAL);

	private final SliderSetting posX = new SliderSetting("Position X",
		"X position of the boss bar.", 500, 0, 2500, 1,
		ValueDisplay.INTEGER);

	private final SliderSetting posY = new SliderSetting("Position Y",
		"Y position of the boss bar.", 120, 0, 2500, 1,
		ValueDisplay.INTEGER);

	private final SliderSetting barWidth = new SliderSetting("Width",
		"Width of the boss bar.", 182, 80, 400, 1, ValueDisplay.INTEGER);

	private final CheckboxSetting background = new CheckboxSetting(
		"Show background", "Draws a colored box behind the boss bar.", true);

	private final ColorSetting bgColor = new ColorSetting("Background color",
		"Color of the background box.", new Color(0, 0, 0, 150));

	private final ColorSetting barColor = new ColorSetting("Bar color",
		"Color of the boss health bar.", new Color(255, 0, 0));

	private final ColorSetting textColor = new ColorSetting("Text color",
		"Color of the boss name.", new Color(255, 255, 255));

	public BossHealthHack()
	{
		super("BossHealth");
		setCategory(Category.RENDER);
		addSetting(scale);
		addSetting(posX);
		addSetting(posY);
		addSetting(barWidth);
		addSetting(background);
		addSetting(bgColor);
		addSetting(barColor);
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
		Map<UUID, ? extends BossEvent> events = ((IBossHealthOverlay)MC.gui
			.getBossOverlay()).wurst_getEvents();

		if(events.isEmpty())
			return;

		List<BossEvent> bosses = new ArrayList<>(events.values());
		bosses.sort(Comparator.comparing(
			boss -> boss.getName().getString()));

		int width = (int)barWidth.getValue();
		int rowHeight = MC.font.lineHeight + NAME_GAP + BAR_HEIGHT;
		int totalHeight =
			bosses.size() * rowHeight + (bosses.size() - 1) * ROW_GAP;
		double textScale = scale.getValue();
		double x = posX.getValue();
		double y = posY.getValue();

		if(background.isChecked())
			context.fill((int)(x - 4), (int)(y - 4),
				(int)(x + width * textScale + 4),
				(int)(y + totalHeight * textScale + 4),
				bgColor.getColor().getRGB());

		var pose = context.pose();
		pose.pushPose();
		pose.translate((float)x, (float)y, 0);
		pose.scale((float)textScale, (float)textScale, 1);

		int nameColor = textColor.getColor().getRGB();
		int fillColor = barColor.getColor().getRGB();
		int rowY = 0;

		for(BossEvent boss : bosses)
		{
			String name = boss.getName().getString();
			int nameX = (width - MC.font.width(name)) / 2;
			context.drawString(MC.font, name, nameX, rowY, nameColor, true);

			int barY = rowY + MC.font.lineHeight + NAME_GAP;
			context.fill(0, barY, width, barY + BAR_HEIGHT, 0x80000000);

			float progress = Math.max(0, Math.min(1, boss.getProgress()));
			int fillWidth = Math.round(width * progress);
			if(fillWidth > 0)
				context.fill(0, barY, fillWidth, barY + BAR_HEIGHT,
					fillColor);

			rowY += rowHeight + ROW_GAP;
		}

		pose.popPose();
	}
}

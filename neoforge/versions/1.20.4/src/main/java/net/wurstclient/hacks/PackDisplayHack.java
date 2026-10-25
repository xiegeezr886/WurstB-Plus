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
import java.util.Collection;
import java.util.List;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.server.packs.repository.Pack;
import net.wurstclient.Category;
import net.wurstclient.SearchTags;
import net.wurstclient.events.GUIRenderListener;
import net.wurstclient.hack.Hack;
import net.wurstclient.settings.CheckboxSetting;
import net.wurstclient.settings.ColorSetting;
import net.wurstclient.settings.SliderSetting;
import net.wurstclient.settings.SliderSetting.ValueDisplay;

/**
 * HUD overlay showing the name(s) of the currently active resource packs.
 *
 * <p>Simplification: Soar drew the pack's icon through NanoVG and only showed
 * the top-most pack. This port lists every active pack as flat text and skips
 * the icon, whose texture is not exposed by the local API.
 */
@SearchTags({"pack display", "resource pack", "texture pack", "pack name",
	"resource packs"})
public final class PackDisplayHack extends Hack implements GUIRenderListener
{
	private final SliderSetting scale = new SliderSetting("Scale",
		"Scale of the pack display.", 1, 0.5, 4, 0.1, ValueDisplay.DECIMAL);

	private final SliderSetting posX = new SliderSetting("Position X",
		"X position of the pack display.", 10, 0, 2500, 1,
		ValueDisplay.INTEGER);

	private final SliderSetting posY = new SliderSetting("Position Y",
		"Y position of the pack display.", 10, 0, 2500, 1,
		ValueDisplay.INTEGER);

	private final CheckboxSetting background = new CheckboxSetting(
		"Show background", "Draws a colored box behind the pack names.", true);

	private final ColorSetting bgColor = new ColorSetting("Background color",
		"Color of the background box.", new Color(0, 0, 0, 150));

	private final ColorSetting textColor = new ColorSetting("Text color",
		"Color of the pack names.", Color.WHITE);

	public PackDisplayHack()
	{
		super("PackDisplay");
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

		List<String> names = getActivePackNames();
		double textScale = scale.getValue();
		double x = posX.getValue();
		double y = posY.getValue();
		int lineHeight = MC.font.lineHeight + 1;
		int textWidth = 0;
		for(String name : names)
			textWidth = Math.max(textWidth, MC.font.width(name));

		double width = textWidth * textScale;
		double height = names.size() * lineHeight * textScale;

		if(background.isChecked())
			context.fill((int)(x - 3), (int)(y - 2), (int)(x + width + 3),
				(int)(y + height + 1), bgColor.getColor().getRGB());

		var pose = context.pose();
		pose.pushPose();
		pose.translate((float)x, (float)y, 0);
		pose.scale((float)textScale, (float)textScale, 1);
		int color = textColor.getColorI();
		for(int i = 0; i < names.size(); i++)
			context.drawString(MC.font, names.get(i), 0, i * lineHeight, color,
				true);
		pose.popPose();
	}

	private List<String> getActivePackNames()
	{
		List<String> names = new ArrayList<>();
		Collection<Pack> packs =
			MC.getResourcePackRepository().getSelectedPacks();

		for(Pack pack : packs)
		{
			if(isBuiltIn(pack.getId()))
				continue;

			String name = pack.getDescription().getString().trim();
			if(name.isEmpty())
				name = pack.getTitle().getString().trim();

			if(!name.isEmpty() && !names.contains(name))
				names.add(name);
		}

		if(names.isEmpty())
			names.add("Vanilla");

		return names;
	}

	private boolean isBuiltIn(String id)
	{
		return id.equals("vanilla") || id.equals("mod_resources")
			|| id.equals("forge");
	}
}

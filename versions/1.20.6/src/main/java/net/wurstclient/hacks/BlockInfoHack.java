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

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
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
 * HUD display of the block currently under the crosshair, including its name,
 * coordinates and hardness.
 *
 * <p>Simplification: Soar rendered a 3D block preview with NanoVG and a
 * slide-in animation. This port shows a text panel and hides it immediately
 * when no block is targeted.
 */
@SearchTags({"block info", "block", "block name", "hardness", "coordinates",
	"looked at block"})
public final class BlockInfoHack extends Hack implements GUIRenderListener
{
	private final SliderSetting scale = new SliderSetting("Scale",
		"Scale of the block info text.", 1.5, 0.5, 5, 0.1,
		ValueDisplay.DECIMAL);

	private final SliderSetting posX = new SliderSetting("Position X",
		"X position of the block info panel.", 500, 0, 2500, 1,
		ValueDisplay.INTEGER);

	private final SliderSetting posY = new SliderSetting("Position Y",
		"Y position of the block info panel.", 350, 0, 2500, 1,
		ValueDisplay.INTEGER);

	private final CheckboxSetting showCoords = new CheckboxSetting(
		"Show coordinates", "Shows the block's X/Y/Z coordinates.", true);

	private final CheckboxSetting showHardness = new CheckboxSetting(
		"Show hardness", "Shows how hard the block is to break.", true);

	private final CheckboxSetting background = new CheckboxSetting(
		"Show background", "Draws a colored box behind the block info.",
		true);

	private final ColorSetting bgColor = new ColorSetting("Background color",
		"Color of the background box.", new Color(0, 0, 0, 150));

	private final ColorSetting textColor = new ColorSetting("Text color",
		"Color of the block info text.", new Color(255, 255, 255));

	public BlockInfoHack()
	{
		super("BlockInfo");
		setCategory(Category.RENDER);
		addSetting(scale);
		addSetting(posX);
		addSetting(posY);
		addSetting(showCoords);
		addSetting(showHardness);
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
		if(MC.player == null || MC.level == null)
			return;

		List<String> lines = getLines();
		if(lines == null || lines.isEmpty())
			return;

		double textScale = scale.getValue();
		double lineHeight = MC.font.lineHeight + 2;
		double width = 0;
		for(String line : lines)
			width = Math.max(width, MC.font.width(line));

		double height = lines.size() * lineHeight - 2;
		double x = posX.getValue();
		double y = posY.getValue();

		if(background.isChecked())
			context.fill((int)(x - 4), (int)(y - 4),
				(int)(x + width * textScale + 4),
				(int)(y + height * textScale + 4),
				bgColor.getColor().getRGB());

		var pose = context.pose();
		pose.pushPose();
		pose.translate((float)x, (float)y, 0.0F);
		pose.scale((float)textScale, (float)textScale, 1.0F);

		int color = textColor.getColor().getRGB();
		for(int i = 0; i < lines.size(); i++)
			context.drawString(MC.font, lines.get(i), 0, (int)(i * lineHeight),
				color, true);

		pose.popPose();
	}

	private List<String> getLines()
	{
		if(!(MC.hitResult instanceof BlockHitResult blockHit)
			|| blockHit.getType() == HitResult.Type.MISS)
			return null;

		BlockPos pos = blockHit.getBlockPos();
		BlockState state = MC.level.getBlockState(pos);
		if(state.isAir())
			return null;

		List<String> lines = new ArrayList<>();
		lines.add(state.getBlock().getName().getString());

		if(showCoords.isChecked())
			lines.add("XYZ: " + pos.getX() + ", " + pos.getY() + ", "
				+ pos.getZ());

		if(showHardness.isChecked())
		{
			float hardness = state.getDestroySpeed(MC.level, pos);
			lines.add(hardness < 0 ? "Hardness: unbreakable"
				: String.format(Locale.ROOT, "Hardness: %.1f", hardness));
		}

		return lines;
	}
}

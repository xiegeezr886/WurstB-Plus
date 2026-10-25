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

import com.mojang.blaze3d.vertex.PoseStack;

import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.wurstclient.Category;
import net.wurstclient.SearchTags;
import net.wurstclient.events.RenderListener;
import net.wurstclient.hack.Hack;
import net.wurstclient.settings.CheckboxSetting;
import net.wurstclient.settings.ColorSetting;
import net.wurstclient.settings.SliderSetting;
import net.wurstclient.settings.SliderSetting.ValueDisplay;
import net.wurstclient.util.BlockUtils;
import net.wurstclient.util.RenderUtils;

/**
 * Renders a colored overlay and outline around the block the player is looking
 * at.
 *
 * <p>Simplification: Soar's animated bounding-box slide, adjustable outline
 * width and accent-color mode are not ported. The overlay uses the block's
 * collision shape, which matches the vanilla selection box for full blocks.
 */
@SearchTags({"block overlay", "block highlight", "block outline", "overlay",
	"selection box", "block esp"})
public final class BlockOverlayHack extends Hack implements RenderListener
{
	private final CheckboxSetting fill = new CheckboxSetting("Fill",
		"Draws a translucent box over the selected block.", true);

	private final CheckboxSetting outline = new CheckboxSetting("Outline",
		"Draws an outline around the selected block.", true);

	private final SliderSetting fillOpacity = new SliderSetting(
		"Fill opacity", "Opacity of the fill overlay.", 0.15, 0, 1, 0.01,
		ValueDisplay.PERCENTAGE);

	private final SliderSetting outlineOpacity = new SliderSetting(
		"Outline opacity", "Opacity of the outline.", 0.15, 0, 1, 0.01,
		ValueDisplay.PERCENTAGE);

	private final ColorSetting fillColor = new ColorSetting("Fill color",
		"Color of the fill overlay.", Color.RED);

	private final ColorSetting outlineColor = new ColorSetting("Outline color",
		"Color of the outline.", Color.RED);

	private final CheckboxSetting throughWalls = new CheckboxSetting(
		"Through walls", "Renders the overlay through solid blocks.", false);

	public BlockOverlayHack()
	{
		super("BlockOverlay");
		setCategory(Category.RENDER);
		addSetting(fill);
		addSetting(outline);
		addSetting(fillOpacity);
		addSetting(outlineOpacity);
		addSetting(fillColor);
		addSetting(outlineColor);
		addSetting(throughWalls);
		fillOpacity.visibleWhen(fill::isChecked);
		fillColor.visibleWhen(fill::isChecked);
		outlineOpacity.visibleWhen(outline::isChecked);
		outlineColor.visibleWhen(outline::isChecked);
	}

	@Override
	protected void onEnable()
	{
		EVENTS.add(RenderListener.class, this);
	}

	@Override
	protected void onDisable()
	{
		EVENTS.remove(RenderListener.class, this);
	}

	@Override
	public void onRender(PoseStack poseStack, float partialTicks)
	{
		if(MC.level == null || MC.player == null)
			return;

		if(!(MC.hitResult instanceof BlockHitResult hitResult))
			return;

		BlockPos pos = hitResult.getBlockPos();
		if(!BlockUtils.canBeClicked(pos))
			return;

		AABB box = BlockUtils.getBoundingBox(pos).inflate(0.002);
		boolean depthTest = !throughWalls.isChecked();

		if(fill.isChecked())
			RenderUtils.drawSolidBox(poseStack, box,
				fillColor.getColorI(fillOpacity.getValueF()), depthTest);

		if(outline.isChecked())
			RenderUtils.drawOutlinedBox(poseStack, box,
				outlineColor.getColorI(outlineOpacity.getValueF()),
				depthTest);
	}
}

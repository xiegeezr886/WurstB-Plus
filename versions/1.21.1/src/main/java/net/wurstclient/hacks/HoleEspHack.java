/*
 * Copyright (c) 2025 Penguin
 *
 * This source code is subject to the terms of the GNU General Public
 * License, version 3. If a copy of the GPL was not distributed with this
 * file, You can obtain one at: https://www.gnu.org/licenses/gpl-3.0.txt
 */
package net.wurstclient.hacks;

import com.mojang.blaze3d.vertex.PoseStack;
import java.awt.Color;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.wurstclient.Category;
import net.wurstclient.SearchTags;
import net.wurstclient.events.RenderListener;
import net.wurstclient.events.UpdateListener;
import net.wurstclient.hack.Hack;
import net.wurstclient.settings.CheckboxSetting;
import net.wurstclient.settings.ColorSetting;
import net.wurstclient.settings.SliderSetting;
import net.wurstclient.settings.SliderSetting.ValueDisplay;
import net.wurstclient.util.BlockUtils;
import net.wurstclient.util.RenderUtils;
import net.wurstclient.util.RotationUtils;

@SearchTags({"hole esp", "HoleESP", "safe hole", "SafeHole"})
public final class HoleEspHack extends Hack
	implements UpdateListener, RenderListener
{
	private final SliderSetting range = new SliderSetting("Range",
		"How far to search for holes.", 8, 1, 16, 1,
		ValueDisplay.INTEGER);

	private final CheckboxSetting bedrockOnly = new CheckboxSetting(
		"Bedrock only", "Only shows bedrock holes.", false);

	private final CheckboxSetting obsidian = new CheckboxSetting(
		"Obsidian", "Shows obsidian holes.", true);

	private final ColorSetting bedrockColor = new ColorSetting(
		"Bedrock color", "Color for bedrock holes.", new Color(0x00FF00));

	private final ColorSetting obsidianColor = new ColorSetting(
		"Obsidian color", "Color for obsidian holes.", new Color(0xFF0000));

	private final ColorSetting mixedColor = new ColorSetting(
		"Mixed color", "Color for mixed bedrock+obsidian holes.",
		new Color(0xFFFF00));

	private final ArrayList<Hole> holes = new ArrayList<>();
	private int searchCooldown;

	public HoleEspHack()
	{
		super("HoleESP");
		setCategory(Category.RENDER);
		addSetting(range);
		addSetting(bedrockOnly);
		addSetting(obsidian);
		addSetting(bedrockColor);
		addSetting(obsidianColor);
		addSetting(mixedColor);
	}

	@Override
	protected void onEnable()
	{
		holes.clear();
		EVENTS.add(UpdateListener.class, this);
		EVENTS.add(RenderListener.class, this);
	}

	@Override
	protected void onDisable()
	{
		EVENTS.remove(UpdateListener.class, this);
		EVENTS.remove(RenderListener.class, this);
		holes.clear();
	}

	@Override
	public void onUpdate()
	{
		searchCooldown++;
		if(searchCooldown < 20)
			return;
		searchCooldown = 0;

		holes.clear();
		int r = range.getValueI();
		BlockPos playerPos = BlockPos.containing(MC.player.position());

		for(int x = -r; x <= r; x++)
			for(int z = -r; z <= r; z++)
				for(int y = -4; y <= 4; y++)
				{
					BlockPos pos = playerPos.offset(x, y, z);

					if(!isReplaceable(pos)
						|| !isReplaceable(pos.above())
						|| !isReplaceable(pos.above(2)))
						continue;

					if(!isSolid(pos.below()))
						continue;

					boolean obsidianHole = true;
					boolean bedrockHole = true;

					for(int dx = -1; dx <= 1; dx++)
						for(int dz = -1; dz <= 1; dz++)
						{
							if(dx == 0 && dz == 0)
								continue;

							BlockPos check = pos.offset(dx, 0, dz);
							boolean obCheck = isObsidianLike(check)
								|| isReplaceable(check);
							boolean bedCheck = isBedrock(check)
								|| isReplaceable(check);

							if(!obCheck)
								obsidianHole = false;
							if(!bedCheck)
								bedrockHole = false;
						}

					if(obsidianHole || bedrockHole)
						holes.add(new Hole(pos, bedrockHole, obsidianHole));
				}
	}

	@Override
	public void onRender(PoseStack matrixStack, float partialTicks)
	{
		for(Hole hole : holes)
		{
			if(bedrockOnly.isChecked() && !hole.bedrockHole)
				continue;
			if(!obsidian.isChecked() && !hole.bedrockHole && hole.obsidianHole)
				continue;

			int color;
			if(hole.bedrockHole && hole.obsidianHole)
				color = mixedColor.getColorI();
			else if(hole.bedrockHole)
				color = bedrockColor.getColorI();
			else
				color = obsidianColor.getColorI();

			AABB box = new AABB(Vec3.atLowerCornerOf(hole.pos),
				Vec3.atLowerCornerOf(hole.pos.offset(1, 1, 1)));
			float[] rgb = {((color >> 16) & 0xFF) / 255F,
				((color >> 8) & 0xFF) / 255F, (color & 0xFF) / 255F};
			int quadColor = RenderUtils.toIntColor(rgb, 0.25F);
			int lineColor = RenderUtils.toIntColor(rgb, 0.6F);

			RenderUtils.drawSolidBox(matrixStack, box, quadColor, false);
			RenderUtils.drawOutlinedBox(matrixStack, box, lineColor, false);
		}
	}

	private boolean isReplaceable(BlockPos pos)
	{
		return BlockUtils.getState(pos).canBeReplaced();
	}

	private boolean isSolid(BlockPos pos)
	{
		return !BlockUtils.getState(pos).canBeReplaced();
	}

	private boolean isBedrock(BlockPos pos)
	{
		return BlockUtils.getBlock(pos) == Blocks.BEDROCK;
	}

	private boolean isObsidianLike(BlockPos pos)
	{
		return BlockUtils.getBlock(pos) == Blocks.OBSIDIAN
			|| BlockUtils.getBlock(pos) == Blocks.CRYING_OBSIDIAN
			|| BlockUtils.getBlock(pos) == Blocks.BEDROCK;
	}

	private record Hole(BlockPos pos, boolean bedrockHole,
		boolean obsidianHole)
	{}

}

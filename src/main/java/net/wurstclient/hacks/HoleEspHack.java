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
					boolean wallHole = true;

					for(int dx = -1; dx <= 1; dx++)
						for(int dz = -1; dz <= 1; dz++)
						{
							if(dx == 0 && dz == 0)
								continue;

							BlockPos check = pos.offset(dx, 0, dz);
							boolean replaceable = isReplaceable(check);

							// 旧实现把基岩也算成"黑曜石类"，于是只要 bedrockHole 为真
							// obsidianHole 必然也为真：纯基岩洞会被当成"混合洞"涂成
							// 混合色，而"Bedrock color"这条设置永远用不到。
							// 这里把三种判定拆开：全基岩 / 全黑曜石 / 两者混合。
							bedrockHole &= replaceable || isBedrock(check);
							obsidianHole &= replaceable || isObsidian(check);
							wallHole &= replaceable || isObsidianLike(check);
						}

					// 一圈全部是基岩/黑曜石/可替换方块才算洞；是纯基岩还是纯黑曜石
					// 由上面两个标记决定，都不是就是混合洞（渲染时用混合色）
					if(wallHole)
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
			// "Obsidian" 关掉时只保留纯基岩洞（混合洞含黑曜石，一并隐藏）
			if(!obsidian.isChecked() && !hole.bedrockHole)
				continue;

			int color;
			if(hole.bedrockHole)
				color = bedrockColor.getColorI();
			else if(hole.obsidianHole)
				color = obsidianColor.getColorI();
			else
				color = mixedColor.getColorI();

			AABB box = new AABB(hole.pos, hole.pos.offset(1, 1, 1));
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

	private boolean isObsidian(BlockPos pos)
	{
		return BlockUtils.getBlock(pos) == Blocks.OBSIDIAN
			|| BlockUtils.getBlock(pos) == Blocks.CRYING_OBSIDIAN;
	}

	private boolean isObsidianLike(BlockPos pos)
	{
		return isObsidian(pos) || isBedrock(pos);
	}

	private record Hole(BlockPos pos, boolean bedrockHole,
		boolean obsidianHole)
	{}

}

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
import java.util.stream.StreamSupport;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
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
import net.wurstclient.util.FakePlayerEntity;
import net.wurstclient.util.RenderUtils;

@SearchTags({"city esp", "CityESP", "auto city esp"})
public final class CityEspHack extends Hack
	implements UpdateListener, RenderListener
{
	private final SliderSetting range = new SliderSetting("Range",
		"How far to search for cityable blocks around targets.", 8, 1, 16, 1,
		ValueDisplay.INTEGER);

	private final CheckboxSetting playersOnly = new CheckboxSetting(
		"Players only", "Only shows cityable blocks around players.", true);

	private final CheckboxSetting ignoreFriends = new CheckboxSetting(
		"Ignore friends", "Doesn't highlight blocks around your friends.",
		true);

	private final ColorSetting color = new ColorSetting("Color",
		"Color for cityable blocks.", new Color(0xFF00FF));

	private final ArrayList<CityBlock> cityBlocks = new ArrayList<>();
	private int searchCooldown;

	public CityEspHack()
	{
		super("CityESP");
		setCategory(Category.RENDER);
		addSetting(range);
		addSetting(playersOnly);
		addSetting(ignoreFriends);
		addSetting(color);
	}

	@Override
	protected void onEnable()
	{
		cityBlocks.clear();
		EVENTS.add(UpdateListener.class, this);
		EVENTS.add(RenderListener.class, this);
	}

	@Override
	protected void onDisable()
	{
		EVENTS.remove(UpdateListener.class, this);
		EVENTS.remove(RenderListener.class, this);
		cityBlocks.clear();
	}

	@Override
	public void onUpdate()
	{
		searchCooldown++;
		if(searchCooldown < 10)
			return;
		searchCooldown = 0;

		cityBlocks.clear();
		double rangeSq = Math.pow(range.getValue(), 2);

		StreamSupport
			.stream(MC.level.entitiesForRendering().spliterator(), false)
			.filter(e -> !e.isRemoved()).filter(e -> e instanceof LivingEntity
				&& ((LivingEntity)e).getHealth() > 0)
			.filter(e -> e != MC.player)
			.filter(e -> !(e instanceof FakePlayerEntity))
			.filter(e -> !ignoreFriends.isChecked()
				|| !WURST.getFriends().contains(e.getScoreboardName()))
			.filter(e -> playersOnly.isChecked() ? e instanceof Player : true)
			.filter(e -> MC.player.distanceToSqr(e) <= rangeSq)
			.sorted(Comparator.comparingDouble(e -> MC.player.distanceToSqr(e)))
			.forEach(this::findCityBlocks);
	}

	private void findCityBlocks(Entity target)
	{
		BlockPos targetPos = target.blockPosition();
		BlockPos[] offsets = {targetPos.north(), targetPos.south(),
			targetPos.east(), targetPos.west()};

		for(BlockPos pos : offsets)
		{
			if(!isCityable(pos))
				continue;

			if(BlockUtils.isUnbreakable(pos))
				continue;

			cityBlocks.add(new CityBlock(pos, target));
		}
	}

	private boolean isCityable(BlockPos pos)
	{
		return BlockUtils.getBlock(pos) == Blocks.OBSIDIAN
			|| BlockUtils.getBlock(pos) == Blocks.CRYING_OBSIDIAN;
	}

	@Override
	public void onRender(PoseStack matrixStack, float partialTicks)
	{
		int colorI = color.getColorI();
		float[] rgb = {((colorI >> 16) & 0xFF) / 255F,
			((colorI >> 8) & 0xFF) / 255F, (colorI & 0xFF) / 255F};
		int quadColor = RenderUtils.toIntColor(rgb, 0.25F);
		int lineColor = RenderUtils.toIntColor(rgb, 0.6F);

		for(CityBlock cityBlock : cityBlocks)
		{
			AABB box = new AABB(cityBlock.pos, cityBlock.pos.offset(1, 1, 1));
			RenderUtils.drawSolidBox(matrixStack, box, quadColor, false);
			RenderUtils.drawOutlinedBox(matrixStack, box, lineColor, false);
		}
	}

	private record CityBlock(BlockPos pos, Entity target)
	{
	}
}

/*
 * Copyright (c) 2026 WurstB+ Plus contributors
 *
 * This source code is subject to the terms of the GNU Affero General Public
 * License, version 3. If a copy of the AGPL was not distributed with this
 * file, You can obtain one at: https://www.gnu.org/licenses/agpl-3.0.txt
 */
// Ported from LeavesHack (AGPL-3.0).
package net.wurstclient.hacks;

import java.util.HashSet;
import java.util.Set;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.wurstclient.Category;
import net.wurstclient.SearchTags;
import net.wurstclient.events.UpdateListener;
import net.wurstclient.hack.Hack;
import net.wurstclient.settings.CheckboxSetting;
import net.wurstclient.settings.ItemListSetting;
import net.wurstclient.settings.SliderSetting;
import net.wurstclient.settings.SliderSetting.ValueDisplay;
import net.wurstclient.util.BlockPlacer;
import net.wurstclient.util.BlockPlacer.BlockPlacingParams;
import net.wurstclient.util.BlockUtils;
import net.wurstclient.util.InventoryUtils;

/**
 * Fills the blocks around the player with glass (or any configured block),
 * similar to obsidian/crystal placement helpers.
 */
@SearchTags({"glass filler", "fill glass", "block filler", "feet filler",
	"glass"})
public final class GlassFillerHack extends Hack implements UpdateListener
{
	private final ItemListSetting blocks = new ItemListSetting("Blocks",
		"Blocks to place. The first matching item in your inventory is used.",
		"minecraft:glass");

	private final SliderSetting radius = new SliderSetting("Radius",
		"How many blocks around the player to fill.", 4, 1, 8, 1,
		ValueDisplay.INTEGER);

	private final SliderSetting delay = new SliderSetting("Delay",
		"Time between placements.", 50, 0, 500, 10,
		ValueDisplay.INTEGER.withSuffix(" ms"));

	private final CheckboxSetting onlyBelow = new CheckboxSetting(
		"Only below feet",
		"Only fill blocks at or below the player's feet. Otherwise also fills"
			+ " the surrounding feet level and one block above.",
		false);

	private long nextPlace;

	public GlassFillerHack()
	{
		super("GlassFiller");
		setCategory(Category.BLOCKS);
		addSetting(blocks);
		addSetting(radius);
		addSetting(delay);
		addSetting(onlyBelow);
	}

	@Override
	protected void onEnable()
	{
		nextPlace = 0;
		EVENTS.add(UpdateListener.class, this);
	}

	@Override
	protected void onDisable()
	{
		EVENTS.remove(UpdateListener.class, this);
	}

	@Override
	public void onUpdate()
	{
		if(MC.player == null || MC.level == null || MC.gameMode == null)
			return;

		long now = System.currentTimeMillis();
		if(now < nextPlace)
			return;

		Set<String> names = new HashSet<>(blocks.getItemNames());
		if(names.isEmpty())
			return;

		BlockPlacingParams target = findPlacement();
		if(target == null)
		{
			nextPlace = now + 50;
			return;
		}

		if(!ensureBlockSelected(names))
			return;

		WURST.getRotationFaker().faceVectorPacket(target.hitVec());
		InteractionResult result = MC.gameMode.useItemOn(MC.player,
			InteractionHand.MAIN_HAND, target.toHitResult());

		if(result.consumesAction())
			MC.player.swing(InteractionHand.MAIN_HAND, net.minecraft.world.item.component.SwingAnimation.DEFAULT, false);

		nextPlace = now + (long)delay.getValue();
	}

	private BlockPlacingParams findPlacement()
	{
		BlockPos center = MC.player.blockPosition();
		int r = radius.getValueI();
		int minY = -1;
		int maxY = onlyBelow.isChecked() ? 0 : 1;
		BlockPlacingParams best = null;
		double bestDistance = Double.MAX_VALUE;
		double maxDistanceSq = 4.5 * 4.5;

		for(int x = -r; x <= r; x++)
			for(int z = -r; z <= r; z++)
				for(int y = minY; y <= maxY; y++)
				{
					BlockPos pos = center.offset(x, y, z);
					if(!BlockUtils.getState(pos).canBeReplaced())
						continue;

					if(MC.player.getBoundingBox().intersects(new AABB(pos)))
						continue;

					BlockPlacingParams params =
						BlockPlacer.getBlockPlacingParams(pos);
					if(params == null || params.distanceSq() > maxDistanceSq)
						continue;

					double distance =
						MC.player.distanceToSqr(Vec3.atCenterOf(pos));
					if(distance < bestDistance)
					{
						bestDistance = distance;
						best = params;
					}
				}

		return best;
	}

	private boolean ensureBlockSelected(Set<String> names)
	{
		if(matches(MC.player.getMainHandItem(), names))
			return true;

		for(int i = 0; i < 9; i++)
			if(matches(MC.player.getInventory().getItem(i), names))
			{
				MC.player.getInventory().setSelectedSlot(i);
				return true;
			}

		InventoryUtils.selectItem(stack -> matches(stack, names), 36);
		return matches(MC.player.getMainHandItem(), names);
	}

	private boolean matches(ItemStack stack, Set<String> names)
	{
		return !stack.isEmpty() && names
			.contains(BuiltInRegistries.ITEM.getKey(stack.getItem()).toString());
	}
}

/*
 * Copyright (c) 2014-2026 Wurst-Imperium and contributors.
 *
 * This source code is subject to the terms of the GNU General Public
 * License, version 3. If a copy of the GPL was not distributed with this
 * file, You can obtain one at: https://www.gnu.org/licenses/gpl-3.0.txt
 *
 * Ported from Meteor Client (GPL-3.0).
 */
package net.wurstclient.hacks;

import java.util.HashSet;
import java.util.Set;

import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.entity.AbstractFurnaceBlockEntity;
import net.minecraft.world.level.block.entity.BarrelBlockEntity;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.block.entity.DispenserBlockEntity;
import net.minecraft.world.level.block.entity.EnderChestBlockEntity;
import net.minecraft.world.level.block.entity.HopperBlockEntity;
import net.minecraft.world.level.block.entity.ShulkerBoxBlockEntity;
import net.minecraft.world.phys.Vec3;
import net.wurstclient.Category;
import net.wurstclient.SearchTags;
import net.wurstclient.events.UpdateListener;
import net.wurstclient.hack.Hack;
import net.wurstclient.settings.CheckboxSetting;
import net.wurstclient.settings.SliderSetting;
import net.wurstclient.settings.SliderSetting.ValueDisplay;
import net.wurstclient.util.ChatUtils;
import net.wurstclient.util.chunk.ChunkUtils;

/**
 * Simplified port of Meteor's StashFinder module.
 *
 * <p>
 * Scans the currently loaded chunks within a configurable radius for storage
 * block entities and reports new stashes in chat. Meteor's chunk list GUI,
 * JSON/CSV storage, waypoint integration and tracers are not implemented.
 */
@SearchTags({"stash finder", "stashfinder", "stash", "base finder", "storage"})
public final class StashFinderHack extends Hack implements UpdateListener
{
	private final SliderSetting radius = new SliderSetting("Radius",
		"Only scan chunks whose center is within this distance.", 128, 16, 512,
		1, ValueDisplay.INTEGER.withSuffix(" blocks"));

	private final SliderSetting minimumStorage = new SliderSetting(
		"Minimum storage",
		"Minimum number of storage blocks in a chunk to report it.", 4, 1, 64,
		1, ValueDisplay.INTEGER);

	private final SliderSetting interval = new SliderSetting("Scan interval",
		"Ticks between scans.", 40, 20, 400, 1,
		ValueDisplay.INTEGER.withSuffix(" ticks"));

	private final CheckboxSetting includeOthers = new CheckboxSetting(
		"Include other storage",
		"Also count ender chests, hoppers, furnaces, dispensers and droppers.",
		false);

	private final CheckboxSetting notifications = new CheckboxSetting(
		"Notifications", "Send a chat message when a new stash is found.",
		true);

	private final Set<Long> reported = new HashSet<>();

	private int timer;

	public StashFinderHack()
	{
		super("StashFinder");
		setCategory(Category.BLOCKS);
		addSetting(radius);
		addSetting(minimumStorage);
		addSetting(interval);
		addSetting(includeOthers);
		addSetting(notifications);
	}

	@Override
	protected void onEnable()
	{
		EVENTS.add(UpdateListener.class, this);
		reported.clear();
		timer = 0;
	}

	@Override
	protected void onDisable()
	{
		EVENTS.remove(UpdateListener.class, this);
	}

	@Override
	public void onUpdate()
	{
		if(MC.player == null || MC.level == null)
			return;

		if(++timer < interval.getValueI())
			return;

		timer = 0;
		scan();
	}

	private void scan()
	{
		Vec3 center = MC.player.position();
		double radiusSq = radius.getValueSq();

		ChunkUtils.getLoadedChunks().forEach(chunk ->
		{
			ChunkPos chunkPos = chunk.getPos();
			double dx = chunkPos.getMiddleBlockX() + 0.5 - center.x;
			double dz = chunkPos.getMiddleBlockZ() + 0.5 - center.z;

			if(dx * dx + dz * dz > radiusSq)
				return;

			int chests = 0;
			int barrels = 0;
			int shulkers = 0;
			int others = 0;

			for(BlockEntity blockEntity : chunk.getBlockEntities().values())
			{
				if(blockEntity instanceof ChestBlockEntity)
					chests++;
				else if(blockEntity instanceof BarrelBlockEntity)
					barrels++;
				else if(blockEntity instanceof ShulkerBoxBlockEntity)
					shulkers++;
				else if(includeOthers.isChecked()
					&& isOtherStorage(blockEntity))
					others++;
			}

			int total = chests + barrels + shulkers + others;
			if(total < minimumStorage.getValueI())
				return;

			if(!reported.add(chunkPos.toLong()))
				return;

			if(!notifications.isChecked())
				return;

			StringBuilder message =
				new StringBuilder("Found stash at ")
					.append(chunkPos.getMiddleBlockX()).append(", ")
					.append(chunkPos.getMiddleBlockZ()).append(" (");

			message.append(chests).append(" chests, ");
			message.append(barrels).append(" barrels, ");
			message.append(shulkers).append(" shulkers");

			if(others > 0)
				message.append(", ").append(others).append(" other");

			message.append(")");
			ChatUtils.message(message.toString());
		});
	}

	private boolean isOtherStorage(BlockEntity blockEntity)
	{
		return blockEntity instanceof EnderChestBlockEntity
			|| blockEntity instanceof HopperBlockEntity
			|| blockEntity instanceof AbstractFurnaceBlockEntity
			|| blockEntity instanceof DispenserBlockEntity;
	}

	@Override
	public String getRenderName()
	{
		return isEnabled() ? getName() + " [" + reported.size() + "]"
			: getName();
	}
}

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

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.phys.Vec3;
import net.wurstclient.Category;
import net.wurstclient.SearchTags;
import net.wurstclient.events.UpdateListener;
import net.wurstclient.hack.DontSaveState;
import net.wurstclient.hack.Hack;
import net.wurstclient.settings.BlockListSetting;
import net.wurstclient.settings.CheckboxSetting;
import net.wurstclient.settings.SliderSetting;
import net.wurstclient.settings.SliderSetting.ValueDisplay;
import net.wurstclient.util.BaritoneUtils;
import net.wurstclient.util.BlockBreaker;
import net.wurstclient.util.BlockBreaker.BlockBreakingParams;
import net.wurstclient.util.BlockUtils;
import net.wurstclient.util.ChatUtils;
import net.wurstclient.util.EnchantmentUtils;
import net.wurstclient.util.InventoryUtils;
import net.wurstclient.util.RotationUtils;

/**
 * Simplified port of Meteor's InfinityMiner module.
 *
 * <p>
 * Mines target blocks until the pickaxe needs repairing, then switches to
 * repair blocks to trigger Mending. The bundled Baritone is used for
 * pathfinding when available; otherwise (or when disabled) blocks within the
 * configured radius are mined directly. Inventory restocking, shulker handling
 * and ender chest searching are not implemented.
 */
@SearchTags({"infinity miner", "infinity mine", "auto repair", "mending miner",
	"infinite miner"})
@DontSaveState
public final class InfinityMinerHack extends Hack implements UpdateListener
{
	private final BlockListSetting targetBlocks = new BlockListSetting(
		"Target blocks", "The blocks that should be mined.",
		"minecraft:diamond_ore", "minecraft:deepslate_diamond_ore",
		"minecraft:emerald_ore", "minecraft:deepslate_emerald_ore");

	private final BlockListSetting repairBlocks = new BlockListSetting(
		"Repair blocks", "Blocks that are mined while the pickaxe needs repair.",
		"minecraft:coal_ore", "minecraft:deepslate_coal_ore",
		"minecraft:redstone_ore", "minecraft:deepslate_redstone_ore",
		"minecraft:nether_quartz_ore");

	private final SliderSetting repairThreshold = new SliderSetting(
		"Repair threshold",
		"Durability percentage at which the repair blocks are mined.", 20, 1,
		99, 1, ValueDisplay.INTEGER.withSuffix("%"));

	private final SliderSetting mineThreshold = new SliderSetting(
		"Mine threshold",
		"Durability percentage at which normal mining is resumed.", 70, 1, 99,
		1, ValueDisplay.INTEGER.withSuffix("%"));

	private final CheckboxSetting requireMending = new CheckboxSetting(
		"Require mending", "Only use pickaxes enchanted with Mending.", true);

	private final CheckboxSetting useBaritone = new CheckboxSetting(
		"Use Baritone", "Lets Baritone pathfind and mine.\n\n"
			+ "When disabled or unavailable, blocks within the radius are mined"
			+ " directly.",
		true);

	private final SliderSetting radius = new SliderSetting("Radius",
		"Radius for direct mining when Baritone is not used.", 4.5, 1, 6, 0.1,
		ValueDisplay.DECIMAL.withSuffix(" blocks"));

	private final CheckboxSetting walkHome = new CheckboxSetting("Walk home",
		"Walks home with Baritone when the inventory is full.", false);

	private final CheckboxSetting logOut = new CheckboxSetting("Log out",
		"Disconnects when the inventory is full.", false);

	private boolean repairing;
	private int idleTicks;
	private int fullTicks;
	private int scanTimer;
	private boolean warnedNoPickaxe;
	private boolean warnedNoBaritone;

	public InfinityMinerHack()
	{
		super("InfinityMiner");
		setCategory(Category.BLOCKS);
		addSetting(targetBlocks);
		addSetting(repairBlocks);
		addSetting(repairThreshold);
		addSetting(mineThreshold);
		addSetting(requireMending);
		addSetting(useBaritone);
		addSetting(radius);
		addSetting(walkHome);
		addSetting(logOut);
	}

	@Override
	protected boolean canEnable()
	{
		return MC.player != null;
	}

	@Override
	protected void onEnable()
	{
		EVENTS.add(UpdateListener.class, this);
		repairing = false;
		idleTicks = 0;
		fullTicks = 0;
		scanTimer = 0;
		warnedNoPickaxe = false;
		warnedNoBaritone = false;
	}

	@Override
	protected void onDisable()
	{
		EVENTS.remove(UpdateListener.class, this);
		BaritoneUtils.stop();
	}

	@Override
	public void onUpdate()
	{
		if(MC.player == null || MC.level == null)
			return;

		if(repairThreshold.getValue() >= mineThreshold.getValue())
		{
			ChatUtils.error(
				"Repair threshold must be lower than the mine threshold.");
			setEnabled(false);
			return;
		}

		if(isInventoryFull())
		{
			handleFullInventory();
			return;
		}

		fullTicks = 0;

		if(!ensurePickaxe())
			return;

		double durability = getDurabilityPercentage();

		if(repairing && durability >= mineThreshold.getValue())
		{
			repairing = false;
			idleTicks = 0;
			ChatUtils.message("Finished repairing, going back to mining.");
		}else if(!repairing && durability <= repairThreshold.getValue())
		{
			repairing = true;
			idleTicks = 0;
			ChatUtils.warning("Pickaxe needs repair, mining repair blocks.");
		}

		List<Block> blocks =
			getBlocks(repairing ? repairBlocks : targetBlocks);
		if(blocks.isEmpty())
			return;

		if(useBaritone.isChecked() && BaritoneUtils.IS_AVAILABLE)
		{
			baritoneMine(blocks);
			return;
		}

		if(useBaritone.isChecked() && !warnedNoBaritone)
		{
			ChatUtils.warning(
				"Baritone is unavailable, mining within radius.");
			warnedNoBaritone = true;
		}

		localMine(blocks);
	}

	private void baritoneMine(List<Block> blocks)
	{
		if(BaritoneUtils.isPathing())
		{
			idleTicks = 0;
			return;
		}

		idleTicks++;
		if(idleTicks > 40)
		{
			if(BaritoneUtils.startMining(blocks.toArray(new Block[0])))
				idleTicks = 0;
		}
	}

	private void localMine(List<Block> blocks)
	{
		if(scanTimer++ < 5)
			return;

		scanTimer = 0;

		Vec3 eyes = RotationUtils.getEyesPos();
		double rangeSq = radius.getValueSq();
		BlockPos center = BlockPos.containing(eyes);

		Optional<BlockBreakingParams> best =
			BlockUtils.getAllInBoxStream(center, radius.getValueCeil())
				.filter(pos -> pos.distToCenterSqr(eyes) <= rangeSq)
				.filter(pos -> blocks.contains(BlockUtils.getBlock(pos)))
				.filter(pos -> BlockUtils.canBeClicked(pos)
					&& !BlockUtils.isUnbreakable(pos))
				.map(BlockBreaker::getBlockBreakingParams)
				.filter(Objects::nonNull)
				.min(BlockBreaker.comparingParams());

		if(best.isPresent())
			BlockBreaker.breakOneBlock(best.get());
	}

	private void handleFullInventory()
	{
		if(logOut.isChecked())
		{
			BaritoneUtils.stop();
			MC.getConnection().getConnection().disconnect(Component
				.literal("[InfinityMiner] Inventory is full."));
			setEnabled(false);
			return;
		}

		if(walkHome.isChecked())
		{
			fullTicks++;
			if(fullTicks % 40 == 0)
				BaritoneUtils.walkHome();
			return;
		}

		ChatUtils.warning("Inventory full, stopping InfinityMiner.");
		setEnabled(false);
	}

	private boolean ensurePickaxe()
	{
		ItemStack held = MC.player.getMainHandItem();
		if(isUsablePickaxe(held))
			return true;

		int hotbarSlot = InventoryUtils.indexOf(this::isUsablePickaxe, 9);
		if(hotbarSlot >= 0)
		{
			MC.player.getInventory().selected = hotbarSlot;
			return false;
		}

		int slot = InventoryUtils.indexOf(this::isUsablePickaxe, 36);
		if(slot >= 0)
		{
			InventoryUtils.selectItem(slot);
			return false;
		}

		if(!warnedNoPickaxe)
		{
			ChatUtils.error("Could not find a usable pickaxe.");
			warnedNoPickaxe = true;
			setEnabled(false);
		}

		return false;
	}

	private boolean isUsablePickaxe(ItemStack stack)
	{
		if(stack.isEmpty() || !stack.is(ItemTags.PICKAXES))
			return false;

		if(!requireMending.isChecked())
			return true;

		return EnchantmentUtils.getLevel(Enchantments.MENDING,
			stack) > 0;
	}

	private double getDurabilityPercentage()
	{
		ItemStack stack = MC.player.getMainHandItem();
		if(stack.getMaxDamage() <= 0)
			return 100;

		return (stack.getMaxDamage() - stack.getDamageValue()) * 100.0
			/ stack.getMaxDamage();
	}

	private List<Block> getBlocks(BlockListSetting setting)
	{
		List<Block> blocks = new ArrayList<>();

		for(String name : setting.getBlockNames())
		{
			Block block = BlockUtils.getBlockFromNameOrID(name);
			if(block != null)
				blocks.add(block);
		}

		return blocks;
	}

	private boolean isInventoryFull()
	{
		if(MC.player == null)
			return false;

		for(int i = 0; i < 36; i++)
			if(MC.player.getInventory().getItem(i).isEmpty())
				return false;

		return true;
	}

	@Override
	public String getRenderName()
	{
		if(!isEnabled())
			return getName();

		return getName() + (repairing ? " [Repairing]" : " [Mining]");
	}
}

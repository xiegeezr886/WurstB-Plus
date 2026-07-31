package net.wurstclient.hacks;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.wurstclient.Category;
import net.wurstclient.SearchTags;
import net.wurstclient.events.UpdateListener;
import net.wurstclient.hack.DontSaveState;
import net.wurstclient.hack.Hack;
import net.wurstclient.settings.BlockListSetting;
import net.wurstclient.settings.CheckboxSetting;
import net.wurstclient.util.BaritoneUtils;
import net.wurstclient.util.BlockUtils;

@SearchTags({"baritone mine", "baritone miner", "auto mine", "auto miner",
	"infinity miner", "ore miner"})
@DontSaveState
public final class BaritoneMineHack extends Hack implements UpdateListener
{
	private static final String[] DEFAULT_ORES = {"minecraft:ancient_debris",
		"minecraft:diamond_ore", "minecraft:deepslate_diamond_ore",
		"minecraft:emerald_ore", "minecraft:deepslate_emerald_ore",
		"minecraft:gold_ore", "minecraft:deepslate_gold_ore",
		"minecraft:iron_ore", "minecraft:deepslate_iron_ore",
		"minecraft:copper_ore", "minecraft:deepslate_copper_ore",
		"minecraft:lapis_ore", "minecraft:deepslate_lapis_ore",
		"minecraft:redstone_ore", "minecraft:deepslate_redstone_ore",
		"minecraft:coal_ore", "minecraft:deepslate_coal_ore",
		"minecraft:nether_quartz_ore"};

	private final BlockListSetting ores =
		new BlockListSetting("Ores",
			"Ores and blocks for Baritone to automatically mine.",
			DEFAULT_ORES);

	private final CheckboxSetting walkHome = new CheckboxSetting("Walk home",
		"Walks home when your inventory is full.", false);

	private final CheckboxSetting logOut = new CheckboxSetting("Log out",
		"Logs out when your inventory is full.\n"
			+ "Overrides Walk home if enabled.",
		false);

	private int noPathTick;

	public BaritoneMineHack()
	{
		super("BaritoneMine");
		setCategory(Category.BLOCKS);
		addSetting(ores);
		addSetting(walkHome);
		addSetting(logOut);
	}

	@Override
	protected boolean canEnable()
	{
		if(!BaritoneUtils.IS_AVAILABLE)
			return false;
		return MC.player != null;
	}

	@Override
	protected void onEnable()
	{
		EVENTS.add(UpdateListener.class, this);
		noPathTick = 0;
		startMining();
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
		if(!BaritoneUtils.IS_AVAILABLE)
		{
			setEnabled(false);
			return;
		}

		if(isInventoryFull())
		{
			if(logOut.isChecked())
			{
				BaritoneUtils.stop();
				MC.player.connection.getConnection()
					.disconnect(
						net.minecraft.network.chat.Component.literal(
							"BaritoneMine: Inventory full"));
				setEnabled(false);
				return;
			}

			if(walkHome.isChecked())
			{
				BaritoneUtils.walkHome();
				return;
			}

			setEnabled(false);
			return;
		}

		if(!BaritoneUtils.isPathing())
		{
			noPathTick++;
			if(noPathTick > 40)
			{
				startMining();
				noPathTick = 0;
			}
		}else
			noPathTick = 0;
	}

	private void startMining()
	{
		List<Block> blocks = new ArrayList<>();
		for(String name : ores.getBlockNames())
		{
			Block block = BlockUtils.getBlockFromNameOrID(name);
			if(block != null)
				blocks.add(block);
		}

		if(!blocks.isEmpty())
			BaritoneUtils.startMining(blocks.toArray(new Block[0]));
	}

	private boolean isInventoryFull()
	{
		if(MC.player == null)
			return false;

		for(int i = 0; i < 36; i++)
		{
			ItemStack stack = MC.player.getInventory().getItem(i);
			if(stack.isEmpty())
				return false;
		}
		return true;
	}

	@Override
	public String getRenderName()
	{
		if(isEnabled() && BaritoneUtils.isPathing())
			return getName() + " [Mining]";
		return getName();
	}
}

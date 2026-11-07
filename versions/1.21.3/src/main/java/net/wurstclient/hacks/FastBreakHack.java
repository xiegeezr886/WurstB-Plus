/*
 * Copyright (c) 2014-2025 Wurst-Imperium and contributors.
 * Copyright (c) 2025 Penguin
 *
 * This source code is subject to the terms of the GNU General Public
 * License, version 3. If a copy of the GPL was not distributed with this
 * file, You can obtain one at: https://www.gnu.org/licenses/gpl-3.0.txt
 */
package net.wurstclient.hacks;

import java.util.Random;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.protocol.game.ServerboundPlayerActionPacket;
import net.minecraft.network.protocol.game.ServerboundPlayerActionPacket.Action;
import net.wurstclient.Category;
import net.wurstclient.SearchTags;
import net.wurstclient.events.BlockBreakingProgressListener;
import net.wurstclient.events.LeftClickListener;
import net.wurstclient.hack.Hack;
import net.wurstclient.settings.CheckboxSetting;
import net.wurstclient.settings.SliderSetting;
import net.wurstclient.settings.SliderSetting.ValueDisplay;
import net.wurstclient.util.BlockUtils;

@SearchTags({"FastMine", "SpeedMine", "SpeedyGonzales", "fast break",
	"fast mine", "speed mine", "speedy gonzales", "NoBreakDelay",
	"no break delay"})
public final class FastBreakHack extends Hack
	implements LeftClickListener, BlockBreakingProgressListener
{
	private final SliderSetting acceleration = new SliderSetting(
		"Acceleration",
		"Break speed multiplier. Higher = faster.\n"
			+ "1.0 = normal, 2.0 = 2x faster, 5.0 = 5x faster.",
		1.5, 0.5, 5.0, 0.5, ValueDisplay.DECIMAL);

	private final CheckboxSetting legitMode = new CheckboxSetting("Legit mode",
		"Only removes the delay between breaking blocks, without speeding up"
			+ " the breaking process itself.\n\n"
			+ "This is much slower, but great at bypassing anti-cheat plugins.",
		false);

	private final Random random = new Random();
	private BlockPos lastBlockPos;
	// Meteor BreakDelay settings use the existing FastBreak module.
	private final SliderSetting cooldown = new SliderSetting("Break cooldown",
		"破坏方块之间的等待 tick 数。", 0, 0, 20, 1, ValueDisplay.INTEGER);
	private final CheckboxSetting noInstaBreak = new CheckboxSetting("Prevent instant chain",
		"秒破方块时，按住左键不会继续误挖相邻方块。", false);
	private boolean firstBreak;

	public FastBreakHack()
	{
		super("FastBreak");
		setCategory(Category.BLOCKS);
		addSetting(acceleration);
		addSetting(legitMode);
		addSetting(cooldown);
		addSetting(noInstaBreak);
	}

	@Override
	public String getRenderName()
	{
		if(legitMode.isChecked())
			return getName() + "Legit";
		return getName() + " [x" + acceleration.getValueString() + "]";
	}

	@Override
	protected void onEnable()
	{
		firstBreak = false;
		EVENTS.add(LeftClickListener.class, this);
		EVENTS.add(BlockBreakingProgressListener.class, this);
	}

	@Override
	protected void onDisable()
	{
		EVENTS.remove(LeftClickListener.class, this);
		EVENTS.remove(BlockBreakingProgressListener.class, this);
		lastBlockPos = null;
		firstBreak = false;
	}

	@Override
	public void onLeftClick(LeftClickEvent event)
	{
		firstBreak = noInstaBreak.isChecked();
	}

	public int getBreakCooldown(int vanilla)
	{
		if(!isEnabled())
			return vanilla;
		if(firstBreak && noInstaBreak.isChecked())
		{
			firstBreak = false;
			return 5;
		}
		firstBreak = false;
		return cooldown.getValueI();
	}

	public boolean shouldPreventInstantBreak()
	{
		return isEnabled() && noInstaBreak.isChecked();
	}

	@Override
	public void onBlockBreakingProgress(BlockBreakingProgressEvent event)
	{
		if(legitMode.isChecked())
			return;

		if(((net.wurstclient.mixin.MultiPlayerGameModeAccessor)(Object)MC.gameMode).getDestroyProgress() >= 1)
			return;

		BlockPos blockPos = event.getBlockPos();

		if(BlockUtils.isUnbreakable(blockPos))
			return;

		float multiplier = acceleration.getValueF();
		int packetCount = Math.max(1, Math.round(multiplier));

		for(int i = 0; i < packetCount; i++)
		{
			Action action = ServerboundPlayerActionPacket.Action.STOP_DESTROY_BLOCK;
			Direction direction = event.getDirection();
			IMC.getInteractionManager().sendPlayerActionC2SPacket(action,
				blockPos, direction);
		}
	}
}

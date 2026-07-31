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
import net.wurstclient.events.UpdateListener;
import net.wurstclient.hack.Hack;
import net.wurstclient.settings.CheckboxSetting;
import net.wurstclient.settings.SliderSetting;
import net.wurstclient.settings.SliderSetting.ValueDisplay;
import net.wurstclient.util.BlockUtils;

@SearchTags({"FastMine", "SpeedMine", "SpeedyGonzales", "fast break",
	"fast mine", "speed mine", "speedy gonzales", "NoBreakDelay",
	"no break delay"})
public final class FastBreakHack extends Hack
	implements UpdateListener, BlockBreakingProgressListener
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

	public FastBreakHack()
	{
		super("FastBreak");
		setCategory(Category.BLOCKS);
		addSetting(acceleration);
		addSetting(legitMode);
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
		EVENTS.add(UpdateListener.class, this);
		EVENTS.add(BlockBreakingProgressListener.class, this);
	}

	@Override
	protected void onDisable()
	{
		EVENTS.remove(UpdateListener.class, this);
		EVENTS.remove(BlockBreakingProgressListener.class, this);
		lastBlockPos = null;
	}

	@Override
	public void onUpdate()
	{
		// MC.gameMode.destroyDelay = 0; // TODO: 26.1.2 - destroyDelay is private
	}

	@Override
	public void onBlockBreakingProgress(BlockBreakingProgressEvent event)
	{
		if(legitMode.isChecked())
			return;

		if(IMC.getInteractionManager().getDestroyProgress() >= 1)
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

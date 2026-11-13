/*
 * Copyright (c) 2025 Penguin
 *
 * This source code is subject to the terms of the GNU General Public
 * License, version 3. If a copy of the GPL was not distributed with this
 * file, You can obtain one at: https://www.gnu.org/licenses/gpl-3.0.txt
 */
package net.wurstclient.hacks;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.stream.StreamSupport;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.wurstclient.Category;
import net.wurstclient.SearchTags;
import net.wurstclient.events.UpdateListener;
import net.wurstclient.hack.Hack;
import net.wurstclient.hack.HackConflictGroup;
import net.wurstclient.settings.CheckboxSetting;
import net.wurstclient.settings.SliderSetting;
import net.wurstclient.settings.SliderSetting.ValueDisplay;
import net.wurstclient.settings.SwingHandSetting;
import net.wurstclient.settings.SwingHandSetting.SwingHand;
import net.wurstclient.util.BlockUtils;
import net.wurstclient.util.FakePlayerEntity;
import net.wurstclient.util.RotationUtils;

@SearchTags({"auto city", "city", "AutoCity", "mine player"})
public final class AutoCityHack extends Hack implements UpdateListener
{
	private final SliderSetting range = new SliderSetting("Range",
		"How far to search for targets.", 5, 1, 8, 0.5,
		ValueDisplay.DECIMAL);

	private final CheckboxSetting autoSwitch = new CheckboxSetting(
		"Auto switch", "Switches to the best mining tool.", true);

	private final CheckboxSetting ignoreOwnSurround = new CheckboxSetting(
		"Ignore own surround",
		"Doesn't mine blocks that are part of your own surround.", true);

	private final SwingHandSetting swingHand =
		new SwingHandSetting(this, SwingHand.CLIENT);

	private int breakTimer;

	public AutoCityHack()
	{
		super("AutoCity");
		setCategory(Category.COMBAT);
		addConflictGroup(HackConflictGroup.BLOCK_BREAKING_AUTOMATION);
		addSetting(range);
		addSetting(autoSwitch);
		addSetting(ignoreOwnSurround);
		addSetting(swingHand);
	}

	@Override
	protected void onEnable()
	{
		WURST.getHax().killauraHack.setEnabled(false);
		breakTimer = 0;
		EVENTS.add(UpdateListener.class, this);
	}

	@Override
	protected void onDisable()
	{
		EVENTS.remove(UpdateListener.class, this);
		MC.gameMode.stopDestroyBlock();
	}

	@Override
	public void onUpdate()
	{
		Entity target = findTarget();
		if(target == null)
			return;

		BlockPos targetPos = target.blockPosition();
		BlockPos playerPos = BlockPos.containing(MC.player.position());

		ArrayList<BlockPos> toMine = new ArrayList<>();
		BlockPos[] cityOffsets = {
			new BlockPos(1, 0, 0), new BlockPos(-1, 0, 0),
			new BlockPos(0, 0, 1), new BlockPos(0, 0, -1)
		};

		for(BlockPos offset : cityOffsets)
		{
			BlockPos pos = targetPos.offset(offset);

			boolean isObsidian = BlockUtils.getBlock(pos) == Blocks.OBSIDIAN
				|| BlockUtils.getBlock(pos) == Blocks.CRYING_OBSIDIAN;

			if(!isObsidian)
				continue;

			if(BlockUtils.isUnbreakable(pos))
				continue;

			if(ignoreOwnSurround.isChecked()
				&& pos.distManhattan(playerPos) <= 1)
				continue;

			toMine.add(pos);
		}

		if(toMine.isEmpty())
			return;

		BlockPos closest = toMine.stream()
			.min(Comparator.comparingDouble(
				p -> MC.player.distanceToSqr(Vec3.atCenterOf(p))))
			.orElse(null);

		if(closest == null)
			return;

		Vec3 hitVec = Vec3.atCenterOf(closest);
		RotationUtils.getNeededRotations(hitVec).sendPlayerLookPacket();

		if(autoSwitch.isChecked())
			WURST.getHax().autoToolHack.equipIfEnabled(closest);

		breakTimer++;
		if(breakTimer >= 4)
		{
			breakTimer = 0;
			MC.gameMode.startDestroyBlock(closest, Direction.UP);
			swingHand.swing(net.minecraft.world.InteractionHand.MAIN_HAND);
		}

		MC.gameMode.continueDestroyBlock(closest,
			Direction.UP);
	}

	private Entity findTarget()
	{
		double rangeSq = Math.pow(range.getValue(), 2);

		return StreamSupport
			.stream(MC.level.entitiesForRendering().spliterator(), false)
			.filter(e -> e instanceof LivingEntity
				&& ((LivingEntity)e).getHealth() > 0)
			.filter(e -> e != MC.player)
			.filter(e -> !(e instanceof FakePlayerEntity))
			.filter(e -> !WURST.getFriends().contains(e.getScoreboardName()))
			.filter(e -> MC.player.distanceToSqr(e) <= rangeSq)
			.min(Comparator.comparingDouble(
				e -> MC.player.distanceToSqr(e)))
			.orElse(null);
	}
}

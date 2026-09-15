/*
 * Copyright (c) 2025 Penguin
 *
 * This source code is subject to the terms of the GNU General Public
 * License, version 3. If a copy of the GPL was not distributed with this
 * file, You can obtain one at: https://www.gnu.org/licenses/gpl-3.0.txt
 */
package net.wurstclient.hacks;

import java.util.Comparator;
import java.util.stream.StreamSupport;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.block.Block;
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
import net.wurstclient.util.CityBlockPlanner;
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

		BlockPos[] candidates = new BlockPos[CityBlockPlanner.COUNT];
		boolean[] usable = new boolean[CityBlockPlanner.COUNT];
		boolean[] punishable = new boolean[CityBlockPlanner.COUNT];
		double[] distanceSq = new double[CityBlockPlanner.COUNT];

		for(int i = 0; i < CityBlockPlanner.COUNT; i++)
		{
			BlockPos pos = targetPos.offset(CityBlockPlanner.offsetX(i), 0,
				CityBlockPlanner.offsetZ(i));
			candidates[i] = pos;
			distanceSq[i] = MC.player.distanceToSqr(Vec3.atCenterOf(pos));

			boolean isObsidian = BlockUtils.getBlock(pos) == Blocks.OBSIDIAN
				|| BlockUtils.getBlock(pos) == Blocks.CRYING_OBSIDIAN;

			usable[i] = isObsidian && !BlockUtils.isUnbreakable(pos)
				&& !(ignoreOwnSurround.isChecked()
					&& pos.distManhattan(playerPos) <= 1);

			// 挖掉之后能换来水晶位的墙排到前面，见 CityBlockPlanner 的类注释
			punishable[i] = opensCrystalSpot(pos);
		}

		int[] order = CityBlockPlanner.plan(distanceSq, usable, punishable);
		if(order.length == 0)
			return;

		BlockPos closest = candidates[order[0]];

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

	/**
	 * 挖掉 pos 之后 pos 自己会变成空气，所以只要它下方是抗爆基底（黑曜石/基岩，
	 * 与 CrystalAuraHack 判定水晶基底的写法一致）、上方又留得出水晶的高度，
	 * pos 处就是一个能直接砸到目标的水晶位。
	 *
	 * <p>
	 * 这一条来自参考项目 OpenEpsilon 的 AutoCity.checkPos：那边还有第二个分支
	 * （再看 pos 的另一个水平邻块是不是基底），但那一支没有校验基底上方的空间，
	 * 是 1.12.2 的写法，这里不收——宁可少认一个"能换来水晶位"，也不要认错。
	 */
	private boolean opensCrystalSpot(BlockPos pos)
	{
		Block below = BlockUtils.getBlock(pos.below());
		if(below != Blocks.BEDROCK && below != Blocks.OBSIDIAN)
			return false;

		return BlockUtils.getState(pos.above()).canBeReplaced();
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

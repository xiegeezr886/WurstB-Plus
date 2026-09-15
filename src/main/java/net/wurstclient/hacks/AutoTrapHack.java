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
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.wurstclient.Category;
import net.wurstclient.SearchTags;
import net.wurstclient.events.UpdateListener;
import net.wurstclient.hack.Hack;
import net.wurstclient.settings.CheckboxSetting;
import net.wurstclient.settings.SliderSetting;
import net.wurstclient.settings.SliderSetting.ValueDisplay;
import net.wurstclient.settings.SwingHandSetting;
import net.wurstclient.settings.SwingHandSetting.SwingHand;
import net.wurstclient.util.AutoTrapPlanner;
import net.wurstclient.util.BlockPlacer;
import net.wurstclient.util.BlockUtils;
import net.wurstclient.util.FakePlayerEntity;

@SearchTags({"auto trap", "trap", "AutoTrap", "cage"})
public final class AutoTrapHack extends Hack implements UpdateListener
{
	private final SliderSetting range = new SliderSetting("Range",
		"How far to search for targets.", 5, 1, 8, 0.5,
		ValueDisplay.DECIMAL);

	private final CheckboxSetting onlyObsidian = new CheckboxSetting(
		"Only obsidian", "Only uses obsidian blocks.", true);

	private final CheckboxSetting disableAfter = new CheckboxSetting(
		"Disable after", "Disables after trapping a target.", true);

	private final SwingHandSetting swingHand =
		new SwingHandSetting(this, SwingHand.CLIENT);

	private int placementTimer;

	public AutoTrapHack()
	{
		super("AutoTrap");
		setCategory(Category.COMBAT);
		addSetting(range);
		addSetting(onlyObsidian);
		addSetting(disableAfter);
		addSetting(swingHand);
	}

	@Override
	protected void onEnable()
	{
		WURST.getHax().killauraHack.setEnabled(false);
		WURST.getHax().crystalAuraHack.setEnabled(false);
		placementTimer = 0;
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
		placementTimer++;
		if(placementTimer < 2)
			return;
		placementTimer = 0;

		Entity target = findTarget();
		if(target == null)
		{
			if(disableAfter.isChecked())
				setEnabled(false);
			return;
		}

		BlockPos targetPos = target.blockPosition();
		boolean[] usable = new boolean[AutoTrapPlanner.COUNT];
		double[] distanceSq = new double[AutoTrapPlanner.COUNT];

		for(int i = 0; i < AutoTrapPlanner.COUNT; i++)
		{
			BlockPos pos = targetPos.offset(AutoTrapPlanner.offsetX(i),
				AutoTrapPlanner.offsetY(i), AutoTrapPlanner.offsetZ(i));

			usable[i] = BlockUtils.getState(pos).canBeReplaced()
				&& !MC.player.getBoundingBox().intersects(new AABB(pos))
				&& !isOccupiedByEntity(pos);
			distanceSq[i] = MC.player.distanceToSqr(Vec3.atCenterOf(pos));
		}

		int[] order = AutoTrapPlanner.plan(usable, distanceSq,
			target.getYRot());

		if(order.length == 0)
		{
			if(disableAfter.isChecked())
				setEnabled(false);
			return;
		}

		BlockPos closest = targetPos.offset(
			AutoTrapPlanner.offsetX(order[0]),
			AutoTrapPlanner.offsetY(order[0]),
			AutoTrapPlanner.offsetZ(order[0]));

		placeBlock(closest);
	}

	private Entity findTarget()
	{
		double rangeSq = range.getValue() * range.getValue();

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

	private void placeBlock(BlockPos pos)
	{
		int slot = findBlockSlot();
		if(slot == -1)
			return;

		int oldSlot = MC.player.getInventory().selected;
		MC.player.getInventory().selected = slot;

		if(BlockPlacer.place(pos, false, false))
			swingHand.swing(InteractionHand.MAIN_HAND);

		MC.player.getInventory().selected = oldSlot;
	}

	/**
	 * 该位置是否已经被生物占住。
	 *
	 * <p>
	 * 这一条取自参考项目 OpenEpsilon 的 AutoTrap#placeBlockInRange：那边是
	 * 照抄 1.12.2 原版 {@code World#checkNoEntityCollision} 的判定，也就是
	 * 原版“这个格子到底能不能放方块”的检查。1.20.1 依然保留着这条判定（放置方块
	 * 时原版会走 {@code Level#isUnobstructed}），所以往被生物占住的格子里放根本
	 * 不会成功。而 {@code BlockPlacer.place()} 又只看有没有能贴的面、不看方块有
	 * 没有真的放上去，于是它照样返回成功，这个模块就会一直以为“已经放下了”，
	 * 继续重试同一个位置、白等 tick。
	 *
	 * <p>
	 * 目标是一格宽两格高，它自己站着的时候正好占住 dy=1 那一圈，所以这条判断
	 * 直接决定选出来的位置能不能放进去。同时它也让“把自己脚底那格放了”这种
	 * 情况不可能发生。
	 */
	private boolean isOccupiedByEntity(BlockPos pos)
	{
		return MC.level.getEntities((Entity)null, new AABB(pos)).stream()
			.filter(e -> !e.isSpectator())
			.anyMatch(e -> e instanceof LivingEntity && !e.isRemoved());
	}

	private int findBlockSlot()
	{
		for(int i = 0; i < 9; i++)
		{
			ItemStack stack = MC.player.getInventory().getItem(i);
			if(stack.isEmpty() || !(stack.getItem() instanceof BlockItem))
				continue;

			Block block = Block.byItem(stack.getItem());

			if(onlyObsidian.isChecked())
			{
				if(block != Blocks.OBSIDIAN && block != Blocks.CRYING_OBSIDIAN
					&& block != Blocks.BEDROCK)
					continue;
			}

			if(block == Blocks.TNT || block == Blocks.RESPAWN_ANCHOR
				|| block == Blocks.COBWEB)
				continue;

			return i;
		}
		return -1;
	}
}

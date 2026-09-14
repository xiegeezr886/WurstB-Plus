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
import java.util.List;
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
import net.wurstclient.settings.EnumSetting;
import net.wurstclient.settings.SliderSetting;
import net.wurstclient.settings.SliderSetting.ValueDisplay;
import net.wurstclient.settings.SwingHandSetting;
import net.wurstclient.settings.SwingHandSetting.SwingHand;
import net.wurstclient.util.BlockPlacer;
import net.wurstclient.util.BlockUtils;
import net.wurstclient.util.FakePlayerEntity;
import net.wurstclient.util.RotationQueue;
import net.wurstclient.util.RotationUtils;
import net.wurstclient.util.SurroundPlanner;

@SearchTags({"surround", "AutoSurround", "auto surround", "CityBlock"})
public final class SurroundHack extends Hack implements UpdateListener
{
	private final EnumSetting<SupportMode> support = new EnumSetting<>(
		"Support", "\u00a7lPlace\u00a7r - Normal placement.\n"
			+ "\u00a7lAirPlace\u00a7r - Place in air.\n"
			+ "\u00a7lSkip\u00a7r - Skip unsupported positions.",
		SupportMode.values(), SupportMode.PLACE);

	private final SliderSetting bpt = new SliderSetting("BPT",
		"Blocks per tick.", 2, 1, 8, 1, ValueDisplay.INTEGER);

	private final CheckboxSetting autocenter = new CheckboxSetting(
		"Autocenter", "Teleports to block center before placing.", true);

	private final CheckboxSetting keepOn = new CheckboxSetting("Keep on",
		"Keeps the module enabled after placing all blocks.", false);

	private final CheckboxSetting jumpDisable = new CheckboxSetting(
		"Jump disable", "Disables if you jump.", true);

	private final CheckboxSetting rotate = new CheckboxSetting("Rotate",
		"Silently rotates your movement packets when placing.", true);

	private final SwingHandSetting swingHand =
		new SwingHandSetting(this, SwingHand.CLIENT);

	private RotationQueue rotationQueue;

	public SurroundHack()
	{
		super("Surround");
		setCategory(Category.COMBAT);
		addSetting(support);
		addSetting(bpt);
		addSetting(autocenter);
		addSetting(keepOn);
		addSetting(jumpDisable);
		addSetting(rotate);
		addSetting(swingHand);
	}

	@Override
	protected void onEnable()
	{
		rotationQueue = new RotationQueue(
			RotationQueue.Priority.BLOCK_PLACEMENT);
		rotationQueue.start();
		EVENTS.add(UpdateListener.class, this);
	}

	@Override
	protected void onDisable()
	{
		EVENTS.remove(UpdateListener.class, this);
		rotationQueue.stop();
		rotationQueue = null;
	}

	@Override
	public void onUpdate()
	{
		if(jumpDisable.isChecked() && !MC.player.onGround()
			&& MC.player.getDeltaMovement().y > 0)
		{
			setEnabled(false);
			return;
		}

		if(autocenter.isChecked())
		{
			BlockPos cp = MC.player.blockPosition();
			MC.player.setPos(cp.getX() + 0.5, MC.player.getY(),
				cp.getZ() + 0.5);
		}

		int slot = findBlockSlot();
		if(slot == -1)
		{
			if(!keepOn.isChecked())
				setEnabled(false);
			return;
		}

		int prevSlot = MC.player.getInventory().selected;
		MC.player.getInventory().selected = slot;

		BlockPos playerPos = MC.player.blockPosition();
		AABB box = MC.player.getBoundingBox();
		BlockPos[] positions = new BlockPos[SurroundPlanner.COUNT];
		boolean[] usable = new boolean[SurroundPlanner.COUNT];

		for(int i = 0; i < SurroundPlanner.COUNT; i++)
		{
			BlockPos pos = playerPos.offset(SurroundPlanner.offsetX(i), 0,
				SurroundPlanner.offsetZ(i));
			positions[i] = pos;
			usable[i] = BlockUtils.getState(pos).canBeReplaced()
				&& !(box.intersects(new AABB(pos))
					|| box.intersects(new AABB(pos.above())))
				&& !isOccupiedByEntity(pos);
		}

		Entity threat = findThreat();
		int[] order = SurroundPlanner.plan(MC.player.getX(), MC.player.getZ(),
			threat == null ? Double.NaN : threat.getX(),
			threat == null ? Double.NaN : threat.getZ(), usable);

		List<BlockPos> toPlace = new ArrayList<>();
		for(int i : order)
			toPlace.add(positions[i]);

		if(toPlace.isEmpty())
		{
			if(!keepOn.isChecked())
				setEnabled(false);
			MC.player.getInventory().selected = prevSlot;
			return;
		}

		int cap = 0;
		boolean anyPlaced = false;
		for(BlockPos pos : toPlace)
		{
			if(cap >= bpt.getValueI())
				break;

			SupportMode mode = support.getSelected();
			boolean placed = false;

			if(mode == SupportMode.PLACE)
				placed = BlockPlacer.place(pos, false, false);
			else if(mode == SupportMode.AIRPLACE)
				placed = BlockPlacer.place(pos, true, false);
			else
				placed = BlockPlacer.place(pos, false, false);

			if(placed)
			{
				swingHand.swing(InteractionHand.MAIN_HAND);
				cap++;
				anyPlaced = true;
			}
		}

		if(!anyPlaced && !keepOn.isChecked())
			setEnabled(false);

		MC.player.getInventory().selected = prevSlot;
	}

	/**
	 * 最近的敌人，用来决定先补哪一侧；附近没有别人时返回 null。
	 *
	 * <p>
	 * 参考项目 OpenEpsilon 的 Surround 是按枚举声明顺序（正下方 → 北 → 东 →
	 * 南 → 西）固定放置的。这里保留“没有敌人时顺序不变”，只在有敌人时把面对
	 * 敌人的那一侧提到最前面——BPT 默认只有 2，四个方向本来就补不完一 tick，
	 * 先补最危险的一侧能少暴露一 tick。
	 */
	private Entity findThreat()
	{
		return StreamSupport
			.stream(MC.level.entitiesForRendering().spliterator(), false)
			.filter(e -> e instanceof LivingEntity
				&& ((LivingEntity)e).getHealth() > 0)
			.filter(e -> e != MC.player)
			.filter(e -> !(e instanceof FakePlayerEntity))
			.filter(e -> !WURST.getFriends().contains(e.getScoreboardName()))
			.min(Comparator.comparingDouble(
				e -> MC.player.distanceToSqr(e)))
			.orElse(null);
	}

	/**
	 * 该位置是否已经被实体占住。
	 *
	 * <p>
	 * 这一条取自参考项目 OpenEpsilon 的 Surround#checkColliding：那边是照抄
	 * 1.12.2 原版 World#checkNoEntityCollision 的判定，也就是原版“这个格子到底
	 * 能不能放方块”的检查。1.20.1 依然保留着这条判定（放置时
	 * BlockItem#canPlace 会调用 Level#isUnobstructed），这里的过滤条件就是
	 * 原版那条判定用的条件，所以往被实体占住的格子里放根本不会成功。而
	 * BlockPlacer.place() 又只看有没有能贴的面、不看方块有没有真的放上去，
	 * 于是它一直返回成功，让这个模块永远关不掉、每 tick 重复发一遍无效的
	 * 交互包（原版交互失败时还会再退化成一次右键空气）。
	 */
	private boolean isOccupiedByEntity(BlockPos pos)
	{
		return MC.level.getEntities((Entity)null, new AABB(pos)).stream()
			.anyMatch(e -> !e.isSpectator() && !e.isRemoved() && e.isPickable());
	}

	private int findBlockSlot()
	{
		Block[] allowed = {Blocks.OBSIDIAN, Blocks.CRYING_OBSIDIAN,
			Blocks.BEDROCK, Blocks.ENDER_CHEST, Blocks.RESPAWN_ANCHOR,
			Blocks.ANCIENT_DEBRIS, Blocks.NETHERITE_BLOCK};

		for(int i = 0; i < 9; i++)
		{
			ItemStack stack =
				MC.player.getInventory().getItem(i);
			if(stack.isEmpty() || !(stack.getItem() instanceof BlockItem))
				continue;

			Block block = Block.byItem(stack.getItem());
			for(Block allowedBlock : allowed)
			{
				if(block == allowedBlock)
					return i;
			}
		}
		return -1;
	}

	private enum SupportMode
	{
		PLACE("Place"),
		AIRPLACE("AirPlace"),
		SKIP("Skip");

		private final String name;

		SupportMode(String name)
		{
			this.name = name;
		}

		@Override
		public String toString()
		{
			return name;
		}
	}
}

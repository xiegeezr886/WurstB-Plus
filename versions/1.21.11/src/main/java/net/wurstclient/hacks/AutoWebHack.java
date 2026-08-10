/*
 * Copyright (c) 2025 Penguin
 *
 * This source code is subject to the terms of the GNU General Public
 * License, version 3. If a copy of the GPL was not distributed with this
 * file, You can obtain one at: https://www.gnu.org/licenses/gpl-3.0.txt
 */
package net.wurstclient.hacks;

import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.Items;
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
import net.wurstclient.util.BlockPlacer;
import net.wurstclient.util.BlockUtils;
import net.wurstclient.util.FakePlayerEntity;
import net.wurstclient.util.InventoryUtils;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.stream.StreamSupport;

@SearchTags({"auto web", "cobweb", "web aura", "AutoWeb"})
public final class AutoWebHack extends Hack implements UpdateListener
{
	private final SliderSetting range = new SliderSetting("Range",
		5, 1, 8, 0.5, ValueDisplay.DECIMAL);

	private final CheckboxSetting feetOnly = new CheckboxSetting("Feet only",
		"Only places webs at the target's feet.", true);

	private final CheckboxSetting airPlace = new CheckboxSetting("Air place",
		"Place webs even without a supporting block.", true);

	private final SwingHandSetting swingHand =
		new SwingHandSetting(this, SwingHand.CLIENT);

	private int placeTimer;

	public AutoWebHack()
	{
		super("AutoWeb");
		setCategory(Category.COMBAT);
		addSetting(range);
		addSetting(feetOnly);
		addSetting(airPlace);
		addSetting(swingHand);
	}

	@Override
	protected void onEnable()
	{
		WURST.getHax().killauraHack.setEnabled(false);
		placeTimer = 0;
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
		placeTimer++;
		if(placeTimer < 3)
			return;
		placeTimer = 0;

		Entity target = findTarget();
		if(target == null)
			return;

		if(InventoryUtils.indexOf(Items.COBWEB) == -1)
			return;

		BlockPos targetPos = target.blockPosition();
		ArrayList<BlockPos> toPlace = new ArrayList<>();

		if(feetOnly.isChecked())
		{
			BlockPos feet = targetPos;
			if(BlockUtils.getState(feet).canBeReplaced())
				toPlace.add(feet);
			if(BlockUtils.getState(feet.above()).canBeReplaced())
				toPlace.add(feet.above());
		}
		else
		{
			for(int x = -1; x <= 1; x++)
				for(int z = -1; z <= 1; z++)
					for(int y = 0; y <= 2; y++)
					{
						BlockPos pos = targetPos.offset(x, y, z);
						if(BlockUtils.getState(pos).canBeReplaced()
							&& !MC.player.getBoundingBox().intersects(new AABB(pos)))
							toPlace.add(pos);
					}
		}

		if(toPlace.isEmpty())
			return;

		BlockPos closest = toPlace.stream()
			.min(Comparator.comparingDouble(
				p -> MC.player.distanceToSqr(Vec3.atCenterOf(p))))
			.orElse(null);

		if(closest == null)
			return;

		placeWeb(closest);
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

	private void placeWeb(BlockPos pos)
	{
		int oldSlot = MC.player.getInventory().getSelectedSlot();
		InventoryUtils.selectItem(Items.COBWEB);
		if(!MC.player.isHolding(Items.COBWEB))
			return;

		if(BlockPlacer.place(pos, airPlace.isChecked(), false))
			swingHand.swing(InteractionHand.MAIN_HAND);

		MC.player.getInventory().setSelectedSlot(oldSlot);
	}
}

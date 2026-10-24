/*
 * Copyright (c) 2014-2025 Wurst-Imperium and contributors.
 *
 * This source code is subject to the terms of the GNU General Public
 * License, version 3. If a copy of the GPL was not distributed with this
 * file, You can obtain one at: https://www.gnu.org/licenses/gpl-3.0.txt
 *
 * Ported from Meteor Client (GPL-3.0).
 */
package net.wurstclient.hacks;

import java.util.stream.Stream;
import java.util.stream.StreamSupport;
import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Items;
import net.wurstclient.Category;
import net.wurstclient.SearchTags;
import net.wurstclient.events.UpdateListener;
import net.wurstclient.hack.Hack;
import net.wurstclient.settings.CheckboxSetting;
import net.wurstclient.settings.EnumSetting;
import net.wurstclient.settings.SliderSetting;
import net.wurstclient.settings.SliderSetting.ValueDisplay;
import net.wurstclient.util.BlockPlacer;
import net.wurstclient.util.EntityUtils;
import net.wurstclient.util.InventoryUtils;

@SearchTags({"self web", "cobweb", "web yourself", "SelfWeb"})
public final class SelfWebHack extends Hack implements UpdateListener
{
	private final EnumSetting<Mode> mode =
		new EnumSetting<>("Mode", "The mode to use for self web.", Mode.values(),
			Mode.NORMAL);

	private final SliderSetting range = new SliderSetting("Range",
		"How far away the player has to be from you to place webs."
			+ " Requires mode to be Smart.",
		3, 1, 7, 1, ValueDisplay.INTEGER);

	private final CheckboxSetting doubles = new CheckboxSetting(
		"Double place", "Places webs in your upper hitbox as well.", false);

	private final CheckboxSetting turnOff = new CheckboxSetting("Auto toggle",
		"Toggles off after placing the webs.", true);

	private final CheckboxSetting rotate = new CheckboxSetting("Rotate",
		"Forces you to rotate downwards when placing webs.", true);

	public SelfWebHack()
	{
		super("SelfWeb");
		setCategory(Category.COMBAT);
		addSetting(mode);
		addSetting(range);
		addSetting(doubles);
		addSetting(turnOff);
		addSetting(rotate);
	}

	@Override
	protected void onEnable()
	{
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
		if(mode.getSelected() == Mode.SMART && !hasPlayerNearby())
			return;

		int slot = InventoryUtils.indexOf(Items.COBWEB, 9);
		if(slot == -1)
		{
			if(turnOff.isChecked())
				setEnabled(false);
			return;
		}

		int prevSlot = MC.player.getInventory().selected;
		InventoryUtils.selectItem(slot);

		if(MC.player.isHolding(Items.COBWEB))
		{
			place(MC.player.blockPosition());

			if(doubles.isChecked())
				place(MC.player.blockPosition().above());
		}

		MC.player.getInventory().selected = prevSlot;

		if(turnOff.isChecked())
			setEnabled(false);
	}

	private boolean hasPlayerNearby()
	{
		double rangeSq = range.getValueSq();

		return getPlayers().anyMatch(
			p -> MC.player.distanceToSqr(p) <= rangeSq);
	}

	private Stream<Player> getPlayers()
	{
		return StreamSupport
			.stream(MC.level.entitiesForRendering().spliterator(), false)
			.filter(e -> e instanceof Player && e != MC.player)
			.filter(EntityUtils.IS_ATTACKABLE)
			.map(e -> (Player)e);
	}

	private void place(BlockPos pos)
	{
		BlockPlacer.BlockPlacingParams params =
			BlockPlacer.getBlockPlacingParams(pos);
		if(params == null)
			return;

		if(rotate.isChecked())
			WURST.getRotationFaker().faceVectorPacket(params.hitVec());

		IMC.getInteractionManager().rightClickBlock(params.neighbor(),
			params.side(), params.hitVec());
		MC.player.swing(InteractionHand.MAIN_HAND);
	}

	private enum Mode
	{
		NORMAL("Normal"),
		SMART("Smart");

		private final String name;

		private Mode(String name)
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

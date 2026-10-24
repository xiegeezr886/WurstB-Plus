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

import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.wurstclient.Category;
import net.wurstclient.SearchTags;
import net.wurstclient.events.UpdateListener;
import net.wurstclient.hack.Hack;
import net.wurstclient.settings.CheckboxSetting;
import net.wurstclient.settings.EnumSetting;
import net.wurstclient.settings.SliderSetting;
import net.wurstclient.settings.SliderSetting.ValueDisplay;
import net.wurstclient.settings.TextFieldSetting;
import net.wurstclient.util.ChatUtils;

/**
 * Ported from Meteor Client (GPL-3.0).
 *
 * <p>
 * Limitation: a name tag without a custom name cannot name anything. Rename
 * the name tag in an anvil to the name configured here before enabling this
 * hack.
 */
@SearchTags({"auto nametag", "AutoNameTag", "NameTagBot", "name tag bot"})
public final class AutoNametagHack extends Hack implements UpdateListener
{
	private final TextFieldSetting nameSetting = new TextFieldSetting("Name",
		"The name that the held name tag should apply. The name tag itself must"
			+ " be renamed to this name in an anvil first. Leave empty to use"
			+ " whatever custom name the held name tag already has.",
		"");

	private final SliderSetting range = new SliderSetting("Range",
		"How far away entities can be to be nametagged.", 5, 1, 6, 0.05,
		ValueDisplay.DECIMAL.withSuffix(" blocks"));

	private final EnumSetting<TargetMode> targetMode = new EnumSetting<>(
		"Target", "Which kinds of entities to nametag.", TargetMode.values(),
		TargetMode.MOBS);

	private final CheckboxSetting renameExisting = new CheckboxSetting(
		"Rename existing", "Also renames entities that already have a different"
			+ " custom name.",
		true);

	private final SliderSetting cooldown = new SliderSetting("Cooldown",
		"How long to wait between nametag interactions.", 20, 0, 100, 1,
		ValueDisplay.INTEGER.withSuffix(" ticks"));

	private int timer;

	public AutoNametagHack()
	{
		super("AutoNametag");
		setCategory(Category.OTHER);
		addSetting(nameSetting);
		addSetting(range);
		addSetting(targetMode);
		addSetting(renameExisting);
		addSetting(cooldown);
	}

	@Override
	protected void onEnable()
	{
		timer = 0;
		EVENTS.add(UpdateListener.class, this);
	}

	@Override
	protected void onDisable()
	{
		EVENTS.remove(UpdateListener.class, this);
		timer = 0;
	}

	@Override
	public void onUpdate()
	{
		if(MC.player == null || MC.level == null || MC.gameMode == null)
			return;

		if(timer > 0)
		{
			timer--;
			return;
		}

		// find a name tag in the hotbar or offhand
		int slot = findNameTagSlot();
		if(slot == -1)
		{
			ChatUtils.error("No name tag in the hotbar or offhand.");
			setEnabled(false);
			return;
		}

		InteractionHand hand;
		if(slot < 9)
		{
			MC.player.getInventory().setSelectedSlot(slot);
			hand = InteractionHand.MAIN_HAND;
		}else
			hand = InteractionHand.OFF_HAND;

		// a name tag only works if it has a custom name
		ItemStack tag = MC.player.getItemInHand(hand);
		Component tagNameComponent = tag.get(DataComponents.CUSTOM_NAME);
		String wantedName = nameSetting.getValue().trim();

		if(tagNameComponent == null)
		{
			ChatUtils.error("The held name tag has no custom name."
				+ " Rename it in an anvil first.");
			setEnabled(false);
			return;
		}

		String tagName = tagNameComponent.getString();
		if(!wantedName.isEmpty() && !tagName.equals(wantedName))
		{
			ChatUtils.error("The held name tag is called \"" + tagName
				+ "\", but the configured name is \"" + wantedName
				+ "\". Rename it in an anvil first.");
			setEnabled(false);
			return;
		}

		LivingEntity target = findTarget(tagName);
		if(target == null)
			return;

		WURST.getRotationFaker()
			.faceVectorPacket(target.getBoundingBox().getCenter());

		InteractionResult result =
			MC.gameMode.interact(MC.player, target, hand);
		if(result.consumesAction())
			MC.player.swing(hand);

		timer = cooldown.getValueI();
	}

	private int findNameTagSlot()
	{
		for(int i = 0; i < 9; i++)
			if(MC.player.getInventory().getItem(i).is(Items.NAME_TAG))
				return i;

		if(MC.player.getOffhandItem().is(Items.NAME_TAG))
			return 40;

		return -1;
	}

	private LivingEntity findTarget(String tagName)
	{
		double rangeSq = range.getValueSq();
		LivingEntity best = null;
		double bestDistanceSq = Double.MAX_VALUE;

		for(Entity entity : MC.level.entitiesForRendering())
		{
			if(!(entity instanceof LivingEntity living)
				|| entity instanceof Player)
				continue;
			if(entity == MC.player || entity.isRemoved()
				|| living.getHealth() <= 0)
				continue;
			if(!matchesTargetMode(living))
				continue;

			double distanceSq = MC.player.distanceToSqr(entity);
			if(distanceSq > rangeSq)
				continue;

			// don't rename entities that already have the right name
			if(living.hasCustomName())
			{
				Component currentName = living.getCustomName();
				if(currentName != null
					&& currentName.getString().equals(tagName))
					continue;

				if(!renameExisting.isChecked())
					continue;
			}

			if(distanceSq < bestDistanceSq)
			{
				bestDistanceSq = distanceSq;
				best = living;
			}
		}

		return best;
	}

	private boolean matchesTargetMode(LivingEntity entity)
	{
		return switch(targetMode.getSelected())
		{
			case ANIMALS -> entity instanceof Animal;
			case MOBS -> entity instanceof Mob;
			case VILLAGERS -> entity instanceof Villager;
			case ALL -> true;
		};
	}

	private enum TargetMode
	{
		ANIMALS("Animals"),
		MOBS("Mobs"),
		VILLAGERS("Villagers"),
		ALL("All living entities");

		private final String name;

		private TargetMode(String name)
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

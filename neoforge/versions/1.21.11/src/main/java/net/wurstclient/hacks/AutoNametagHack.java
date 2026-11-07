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

import java.util.HashMap;
import java.util.Map;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.client.multiplayer.ClientLevel;
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
import net.wurstclient.util.RegistryIdFilter;
import net.wurstclient.util.Rotation;
import net.wurstclient.util.RotationUtils;
import net.wurstclient.events.WorldChangeListener;

/**
 * Ported from Meteor Client (GPL-3.0).
 *
 * <p>
 * Limitation: a name tag without a custom name cannot name anything. Rename
 * the name tag in an anvil to the name configured here before enabling this
 * hack.
 */
@SearchTags({"auto nametag", "AutoNameTag", "NameTagBot", "name tag bot"})
public final class AutoNametagHack extends Hack implements UpdateListener, WorldChangeListener
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

	private final TextFieldSetting entities = new TextFieldSetting("Entity types",
		"Optional entity type IDs, separated by commas. Empty uses the Target group.", "");
	private final EnumSetting<Priority> priority = new EnumSetting<>("Priority",
		"How to choose between eligible entities.", Priority.values(), Priority.NEAREST);
	private final CheckboxSetting rotate = new CheckboxSetting("Rotate",
		"Sends a look packet before using the name tag.", true);
	private final CheckboxSetting perEntityCooldown = new CheckboxSetting("Per-entity cooldown",
		"Allows another entity to be named while the previous target is cooling down.", false);
	private final Map<Entity, Integer> entityCooldowns = new HashMap<>();
	private final RegistryIdFilter entityFilter = new RegistryIdFilter();
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
		addSetting(entities);
		addSetting(priority);
		addSetting(rotate);
		addSetting(perEntityCooldown);
	}

	@Override
	protected void onEnable()
	{
		timer = 0;
		EVENTS.add(UpdateListener.class, this);
		EVENTS.add(WorldChangeListener.class, this);
		entityCooldowns.clear();
	}

	@Override
	protected void onDisable()
	{
		EVENTS.remove(UpdateListener.class, this);
		EVENTS.remove(WorldChangeListener.class, this);
		entityCooldowns.clear();
		timer = 0;
	}

	@Override
	public void onWorldChange(ClientLevel world)
	{
		timer = 0;
		entityCooldowns.clear();
	}

	@Override
	public void onUpdate()
	{
		if(MC.player == null || MC.level == null || MC.gameMode == null)
			return;

		entityCooldowns.replaceAll((entity, ticks) -> ticks - 1);
		entityCooldowns.entrySet().removeIf(entry -> entry.getValue() <= 0 || entry.getKey().isRemoved());

		if(!perEntityCooldown.isChecked() && timer > 0)
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

		InteractionHand hand = slot < 9 ? InteractionHand.MAIN_HAND : InteractionHand.OFF_HAND;
		ItemStack tag = slot < 9 ? MC.player.getInventory().getItem(slot) : MC.player.getOffhandItem();
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

		int previousSlot = MC.player.getInventory().getSelectedSlot();
		try
		{
			if(slot < 9)
				MC.player.getInventory().setSelectedSlot(slot);
			if(rotate.isChecked() && MC.getConnection() != null)
			{
				Rotation rotation = RotationUtils.getNeededRotations(target.getBoundingBox().getCenter());
				rotation.sendPlayerLookPacket();
			}
			InteractionResult result = MC.gameMode.interact(MC.player, target, hand);
			if(result.consumesAction())
				MC.player.swing(hand);
			entityCooldowns.put(target, cooldown.getValueI());
			timer = cooldown.getValueI();
		}finally
		{
			MC.player.getInventory().setSelectedSlot(previousSlot);
		}
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
		double bestScore = Double.POSITIVE_INFINITY;

		for(Entity entity : MC.level.entitiesForRendering())
		{
			if(!(entity instanceof LivingEntity living)
				|| entity instanceof Player)
				continue;
			if(entity == MC.player || entity.isRemoved()
				|| living.getHealth() <= 0)
				continue;
			if(!entities.getValue().isBlank()
				? !entityFilter.contains(entities.getValue(), BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType()).toString())
				: !matchesTargetMode(living))
				continue;

			if(perEntityCooldown.isChecked() && entityCooldowns.containsKey(entity))
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

			double score = switch(priority.getSelected())
			{
				case NEAREST -> distanceSq;
				case FARTHEST -> -distanceSq;
				case LOWEST_HEALTH -> living.getHealth();
				case HIGHEST_HEALTH -> -living.getHealth();
				case CLOSEST_ANGLE -> RotationUtils.getAngleToLookVec(entity.getBoundingBox().getCenter());
			};
			if(score < bestScore)
			{
				bestScore = score;
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

	private enum Priority {NEAREST, FARTHEST, LOWEST_HEALTH, HIGHEST_HEALTH, CLOSEST_ANGLE}

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

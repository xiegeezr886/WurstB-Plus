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
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.EntityHitResult;
import net.wurstclient.Category;
import net.wurstclient.SearchTags;
import net.wurstclient.events.UpdateListener;
import net.wurstclient.events.WorldChangeListener;
import net.wurstclient.settings.TextFieldSetting;
import net.wurstclient.util.RegistryIdFilter;
import net.wurstclient.util.Rotation;
import net.wurstclient.util.RotationUtils;
import net.wurstclient.hack.Hack;
import net.wurstclient.settings.CheckboxSetting;
import net.wurstclient.settings.EnumSetting;
import net.wurstclient.settings.SliderSetting;
import net.wurstclient.settings.SliderSetting.ValueDisplay;

@SearchTags({"auto breed", "AutoBreeder", "auto breeder", "BreedAura",
	"breed aura"})
public final class AutoBreedHack extends Hack implements UpdateListener, WorldChangeListener
{
	private final SliderSetting range = new SliderSetting("Range",
		"How far away animals can be to be bred.", 4.5, 1, 10, 0.1,
		ValueDisplay.DECIMAL.withSuffix(" blocks"));

	private final CheckboxSetting cows = new CheckboxSetting("Cows",
		"Breed cows and mooshrooms.", true);

	private final CheckboxSetting sheep =
		new CheckboxSetting("Sheep", "Breed sheep.", true);

	private final CheckboxSetting pigs =
		new CheckboxSetting("Pigs", "Breed pigs.", true);

	private final CheckboxSetting chickens =
		new CheckboxSetting("Chickens", "Breed chickens.", true);

	private final CheckboxSetting horses = new CheckboxSetting("Horses",
		"Breed horses, donkeys, mules and llamas.", true);

	private final CheckboxSetting wolves = new CheckboxSetting("Wolves",
		"Breed wolves.", true);

	private final CheckboxSetting cats = new CheckboxSetting("Cats",
		"Breed cats and ocelots.", true);

	private final CheckboxSetting otherAnimals = new CheckboxSetting(
		"Other animals", "Breed all other animals, such as rabbits, foxes,"
			+ " pandas, turtles, bees, striders and hoglins.",
		true);

	private final CheckboxSetting onlyAdults = new CheckboxSetting(
		"Only adults", "Won't try to breed baby animals.", true);

	private final EnumSetting<HandMode> hand = new EnumSetting<>("Hand",
		"The hand used to feed the animals.", HandMode.values(),
		HandMode.MAIN_HAND);

	private final TextFieldSetting entities = new TextFieldSetting("Entity types",
		"Optional entity type IDs, separated by commas. Empty uses the animal groups.", "");
	private final EnumSetting<AgeFilter> ageFilter = new EnumSetting<>("Age filter",
		"Native uses Only adults. Baby feeds young animals to speed up growth.",
		AgeFilter.values(), AgeFilter.NATIVE);
	private final EnumSetting<FeedMode> feedMode = new EnumSetting<>("Feeding mode",
		"Native uses the animal's breeding cooldown. Once feeds each animal once."
			+ " Repeat also waits the configured interval for each animal.",
		FeedMode.values(), FeedMode.NATIVE);
	private final SliderSetting breedingInterval = new SliderSetting("Breeding interval",
		"Minimum ticks between feeding the same animal in Repeat mode.",
		6600, 1, 24000, 1, ValueDisplay.INTEGER.withSuffix(" ticks"));
	private final CheckboxSetting rotate = new CheckboxSetting("Rotate",
		"Sends a look packet before feeding an animal.", true);
	private final RegistryIdFilter entityFilter = new RegistryIdFilter();
	private final Map<Entity, Long> animalsFed = new HashMap<>();
	private long ticks;
	private int cooldown;

	public AutoBreedHack()
	{
		super("AutoBreed");
		setCategory(Category.OTHER);
		addSetting(range);
		addSetting(cows);
		addSetting(sheep);
		addSetting(pigs);
		addSetting(chickens);
		addSetting(horses);
		addSetting(wolves);
		addSetting(cats);
		addSetting(otherAnimals);
		addSetting(onlyAdults);
		addSetting(hand);
		addSetting(entities);
		addSetting(ageFilter);
		addSetting(feedMode);
		addSetting(breedingInterval);
		addSetting(rotate);
	}

	@Override
	protected void onEnable()
	{
		EVENTS.add(UpdateListener.class, this);
		EVENTS.add(WorldChangeListener.class, this);
		onWorldChange(MC.level);
	}

	@Override
	protected void onDisable()
	{
		EVENTS.remove(UpdateListener.class, this);
		EVENTS.remove(WorldChangeListener.class, this);
		onWorldChange(null);
	}

	@Override
	public void onWorldChange(ClientLevel world)
	{
		animalsFed.clear();
		ticks = 0;
		cooldown = 0;
	}

	@Override
	public void onUpdate()
	{
		if(MC.player == null || MC.level == null || MC.gameMode == null)
			return;

		ticks++;
		animalsFed.keySet().removeIf(Entity::isRemoved);
		if(cooldown > 0)
		{
			cooldown--;
			return;
		}

		InteractionHand breedingHand = hand.getSelected().hand;
		ItemStack heldStack = breedingHand == InteractionHand.MAIN_HAND
			? MC.player.getMainHandItem() : MC.player.getOffhandItem();
		if(heldStack.isEmpty())
			return;

		double rangeSq = range.getValueSq();
		Animal target = null;

		for(Entity entity : MC.level.entitiesForRendering())
		{
			if(!(entity instanceof Animal animal))
				continue;
			if(entity.isRemoved() || animal.getHealth() <= 0)
				continue;
			if(!isConfigured(animal))
				continue;
			if(!isCorrectAge(animal) || wasFed(animal))
				continue;
			if(!animal.isBaby() && (animal.isInLove() || !animal.canFallInLove()))
				continue;
			if(MC.player.distanceToSqr(animal) > rangeSq)
				continue;
			if(!animal.isFood(heldStack))
				continue;

			target = animal;
			break;
		}

		if(target == null)
			return;

		try
		{
			if(rotate.isChecked())
				RotationUtils.getNeededRotations(target.getBoundingBox().getCenter())
					.sendPlayerLookPacket();
			InteractionResult result =
				MC.gameMode.interact(MC.player, target,
				new EntityHitResult(target), breedingHand);
			if(result.consumesAction())
			{
				animalsFed.put(target, ticks);
				MC.player.swing(breedingHand);
			}
			// Retry rejected interactions slowly; successful source modes may
			// feed the next animal on the next tick.
			cooldown = feedMode.getSelected() == FeedMode.NATIVE
				|| !result.consumesAction() ? 10 : 0;
		}finally
		{
			if(rotate.isChecked() && MC.player != null && MC.getConnection() != null)
				new Rotation(MC.player.getYRot(), MC.player.getXRot())
					.sendPlayerLookPacket();
		}
	}

	private boolean wasFed(Animal animal)
	{
		Long last = animalsFed.get(animal);
		if(last == null || feedMode.getSelected() == FeedMode.NATIVE)
			return false;
		return feedMode.getSelected() == FeedMode.ONCE
			|| ticks - last < breedingInterval.getValueI();
	}

	private boolean isCorrectAge(Animal animal)
	{
		return switch(ageFilter.getSelected())
		{
			case NATIVE -> !onlyAdults.isChecked() || !animal.isBaby();
			case BABY -> animal.isBaby();
			case ADULT -> !animal.isBaby();
			case BOTH -> true;
		};
	}

	private boolean isConfigured(Animal animal)
	{
		EntityType<?> type = animal.getType();
		if(!entities.getValue().isBlank())
			return entityFilter.contains(entities.getValue(),
				BuiltInRegistries.ENTITY_TYPE.getKey(type).toString());

		if(type == EntityType.COW || type == EntityType.MOOSHROOM)
			return cows.isChecked();
		if(type == EntityType.SHEEP)
			return sheep.isChecked();
		if(type == EntityType.PIG)
			return pigs.isChecked();
		if(type == EntityType.CHICKEN)
			return chickens.isChecked();
		if(type == EntityType.HORSE || type == EntityType.DONKEY
			|| type == EntityType.MULE || type == EntityType.LLAMA
			|| type == EntityType.TRADER_LLAMA)
			return horses.isChecked();
		if(type == EntityType.WOLF)
			return wolves.isChecked();
		if(type == EntityType.CAT || type == EntityType.OCELOT)
			return cats.isChecked();

		return otherAnimals.isChecked();
	}

	private enum AgeFilter {NATIVE, BABY, ADULT, BOTH}
	private enum FeedMode {NATIVE, ONCE, REPEAT}

	private enum HandMode
	{
		MAIN_HAND("Main hand", InteractionHand.MAIN_HAND),
		OFF_HAND("Off hand", InteractionHand.OFF_HAND);

		private final String name;
		private final InteractionHand hand;

		HandMode(String name, InteractionHand hand)
		{
			this.name = name;
			this.hand = hand;
		}

		@Override
		public String toString()
		{
			return name;
		}
	}
}

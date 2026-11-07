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

import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.item.ItemStack;
import net.wurstclient.Category;
import net.wurstclient.SearchTags;
import net.wurstclient.events.UpdateListener;
import net.wurstclient.hack.Hack;
import net.wurstclient.settings.CheckboxSetting;
import net.wurstclient.settings.EnumSetting;
import net.wurstclient.settings.SliderSetting;
import net.wurstclient.settings.SliderSetting.ValueDisplay;

@SearchTags({"auto breed", "AutoBreeder", "auto breeder", "BreedAura",
	"breed aura"})
public final class AutoBreedHack extends Hack implements UpdateListener
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
	}

	@Override
	protected void onEnable()
	{
		EVENTS.add(UpdateListener.class, this);
		cooldown = 0;
	}

	@Override
	protected void onDisable()
	{
		EVENTS.remove(UpdateListener.class, this);
		cooldown = 0;
	}

	@Override
	public void onUpdate()
	{
		if(MC.player == null || MC.level == null || MC.gameMode == null)
			return;

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
			if(onlyAdults.isChecked() && animal.isBaby())
				continue;
			if(animal.isInLove() || !animal.canFallInLove())
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

		WURST.getRotationFaker()
			.faceVectorPacket(target.getBoundingBox().getCenter());

		InteractionResult result =
			MC.gameMode.interact(MC.player, target, breedingHand);
		if(result.consumesAction())
			MC.player.swing(breedingHand);

		// Give the server time to put the animal into love mode so that the
		// same animal isn't fed twice in a row.
		cooldown = 10;
	}

	private boolean isConfigured(Animal animal)
	{
		EntityType<?> type = animal.getType();

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

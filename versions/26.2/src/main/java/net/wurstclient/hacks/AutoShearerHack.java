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
import net.minecraft.world.entity.animal.sheep.Sheep;
import net.minecraft.world.item.DyeColor;
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

@SearchTags({"auto shearer", "AutoShear", "auto shear", "SheepBot",
	"sheep bot"})
public final class AutoShearerHack extends Hack implements UpdateListener
{
	private final SliderSetting range = new SliderSetting("Range",
		"How far away sheep can be to be sheared.", 5, 1, 6, 0.05,
		ValueDisplay.DECIMAL.withSuffix(" blocks"));

	private final EnumSetting<ColorFilter> color =
		new EnumSetting<>("Color", "Only shear sheep of this color.",
			ColorFilter.values(), ColorFilter.ANY);

	private final CheckboxSetting targetBabies = new CheckboxSetting(
		"Target babies", "Also tries to shear baby sheep.", false);

	private final CheckboxSetting antiBreak = new CheckboxSetting("Anti-break",
		"Stops using shears when they are about to break.", false);

	private final CheckboxSetting rotate = new CheckboxSetting("Rotate",
		"Faces the sheep before shearing it.", true);

	private final SliderSetting cooldown = new SliderSetting("Cooldown",
		"How long to wait between shear interactions.", 10, 0, 100, 1,
		ValueDisplay.INTEGER.withSuffix(" ticks"));

	private int timer;

	public AutoShearerHack()
	{
		super("AutoShearer");
		setCategory(Category.OTHER);
		addSetting(range);
		addSetting(color);
		addSetting(targetBabies);
		addSetting(antiBreak);
		addSetting(rotate);
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

		Sheep target = findSheep();
		if(target == null)
			return;

		int slot = findShearsSlot();
		if(slot == -1)
			return;

		InteractionHand hand;
		if(slot < 9)
		{
			MC.player.getInventory().setSelectedSlot(slot);
			hand = InteractionHand.MAIN_HAND;
		}else
			hand = InteractionHand.OFF_HAND;

		if(rotate.isChecked())
			WURST.getRotationFaker()
				.faceVectorPacket(target.getBoundingBox().getCenter());

		InteractionResult result =
			MC.gameMode.interact(MC.player, target, new net.minecraft.world.phys.EntityHitResult(target, target.getBoundingBox().getCenter()), hand);
		if(result.consumesAction())
			MC.player.swing(hand);

		timer = cooldown.getValueI();
	}

	private Sheep findSheep()
	{
		double rangeSq = range.getValueSq();
		DyeColor wantedColor = color.getSelected().color;
		Sheep best = null;
		double bestDistanceSq = Double.MAX_VALUE;

		for(Entity entity : MC.level.entitiesForRendering())
		{
			if(!(entity instanceof Sheep sheep) || entity.isRemoved()
				|| sheep.getHealth() <= 0 || sheep.isSheared())
				continue;
			if(!targetBabies.isChecked() && sheep.isBaby())
				continue;
			if(wantedColor != null && sheep.getColor() != wantedColor)
				continue;

			double distanceSq = MC.player.distanceToSqr(entity);
			if(distanceSq > rangeSq)
				continue;

			if(distanceSq < bestDistanceSq)
			{
				bestDistanceSq = distanceSq;
				best = sheep;
			}
		}

		return best;
	}

	private int findShearsSlot()
	{
		for(int i = 0; i < 9; i++)
			if(isUsableShears(MC.player.getInventory().getItem(i)))
				return i;

		if(isUsableShears(MC.player.getOffhandItem()))
			return 40;

		return -1;
	}

	private boolean isUsableShears(ItemStack stack)
	{
		if(!stack.is(Items.SHEARS))
			return false;

		return !antiBreak.isChecked() || !stack.isDamageableItem()
			|| stack.getDamageValue() < stack.getMaxDamage() - 1;
	}

	private enum ColorFilter
	{
		ANY("Any", null),
		WHITE("White", DyeColor.WHITE),
		ORANGE("Orange", DyeColor.ORANGE),
		MAGENTA("Magenta", DyeColor.MAGENTA),
		LIGHT_BLUE("Light blue", DyeColor.LIGHT_BLUE),
		YELLOW("Yellow", DyeColor.YELLOW),
		LIME("Lime", DyeColor.LIME),
		PINK("Pink", DyeColor.PINK),
		GRAY("Gray", DyeColor.GRAY),
		LIGHT_GRAY("Light gray", DyeColor.LIGHT_GRAY),
		CYAN("Cyan", DyeColor.CYAN),
		PURPLE("Purple", DyeColor.PURPLE),
		BLUE("Blue", DyeColor.BLUE),
		BROWN("Brown", DyeColor.BROWN),
		GREEN("Green", DyeColor.GREEN),
		RED("Red", DyeColor.RED),
		BLACK("Black", DyeColor.BLACK);

		private final String name;
		private final DyeColor color;

		private ColorFilter(String name, DyeColor color)
		{
			this.name = name;
			this.color = color;
		}

		@Override
		public String toString()
		{
			return name;
		}
	}
}

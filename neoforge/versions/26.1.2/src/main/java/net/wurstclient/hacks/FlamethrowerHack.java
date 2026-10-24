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

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.wurstclient.Category;
import net.wurstclient.SearchTags;
import net.wurstclient.events.UpdateListener;
import net.wurstclient.hack.Hack;
import net.wurstclient.settings.CheckboxSetting;
import net.wurstclient.settings.EnumSetting;
import net.wurstclient.settings.SliderSetting;
import net.wurstclient.settings.SliderSetting.ValueDisplay;
import net.wurstclient.util.BlockUtils;

/**
 * Ported from Meteor Client (GPL-3.0).
 *
 * <p>
 * Uses flint and steel or fire charges to set animals on fire, or to set the
 * ground under the player on fire. The block below the target position must
 * have a collision shape for the ignition to work.
 */
@SearchTags({"flame thrower", "Flamethrower", "fire", "ignite",
	"animal cooker"})
public final class FlamethrowerHack extends Hack implements UpdateListener
{
	private final SliderSetting range = new SliderSetting("Range",
		"How far away animals can be to be ignited.", 5, 1, 6, 0.05,
		ValueDisplay.DECIMAL.withSuffix(" blocks"));

	private final EnumSetting<Mode> mode = new EnumSetting<>("Mode",
		"\u00a7lTarget\u00a7r ignites the ground below the closest animal.\n\n"
			+ "\u00a7lUnder player\u00a7r ignites the ground below yourself.",
		Mode.values(), Mode.TARGET);

	private final CheckboxSetting targetBabies = new CheckboxSetting(
		"Target babies", "Also ignites baby animals.", false);

	private final CheckboxSetting antiBreak = new CheckboxSetting("Anti-break",
		"Stops using flint and steel when it is about to break.", false);

	private final CheckboxSetting rotate = new CheckboxSetting("Rotate",
		"Faces the target before igniting it.", true);

	private final SliderSetting interval = new SliderSetting("Interval",
		"How long to wait between ignition attempts.", 5, 1, 40, 1,
		ValueDisplay.INTEGER.withSuffix(" ticks"));

	private int timer;

	public FlamethrowerHack()
	{
		super("Flamethrower");
		setCategory(Category.OTHER);
		addSetting(range);
		addSetting(mode);
		addSetting(targetBabies);
		addSetting(antiBreak);
		addSetting(rotate);
		addSetting(interval);
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

		BlockPos firePos;
		if(mode.getSelected() == Mode.UNDER_PLAYER)
			firePos = MC.player.blockPosition();
		else
		{
			Animal target = findTarget();
			if(target == null)
				return;

			firePos = target.blockPosition();

			if(rotate.isChecked())
				WURST.getRotationFaker()
					.faceVectorPacket(target.getBoundingBox().getCenter());
		}

		if(!MC.level.getBlockState(firePos).canBeReplaced())
			return;

		BlockPos support = firePos.below();
		BlockState supportState = MC.level.getBlockState(support);
		if(!BlockUtils.canBeClicked(support)
			|| !supportState.getFluidState().isEmpty())
			return;

		Vec3 hitVec =
			new Vec3(support.getX() + 0.5, support.getY() + 1.0,
				support.getZ() + 0.5);
		if(MC.player.getEyePosition().distanceToSqr(hitVec) > 4.55 * 4.55)
			return;

		int slot = findIgniterSlot();
		if(slot == -1)
			return;

		InteractionHand hand;
		if(slot < 9)
		{
			MC.player.getInventory().setSelectedSlot(slot);
			hand = InteractionHand.MAIN_HAND;
		}else
			hand = InteractionHand.OFF_HAND;

		BlockHitResult hitResult =
			new BlockHitResult(hitVec, Direction.UP, support, false);
		InteractionResult result =
			MC.gameMode.useItemOn(MC.player, hand, hitResult);

		if(result.consumesAction())
			MC.player.swing(hand);

		// don't retry every tick if the ignition failed
		timer = interval.getValueI();
	}

	private Animal findTarget()
	{
		double rangeSq = range.getValueSq();
		Animal best = null;
		double bestDistanceSq = Double.MAX_VALUE;

		for(Entity entity : MC.level.entitiesForRendering())
		{
			if(!(entity instanceof Animal animal) || entity == MC.player
				|| entity.isRemoved() || animal.getHealth() <= 0)
				continue;
			if(!targetBabies.isChecked() && animal.isBaby())
				continue;
			if(animal.isOnFire() || animal.fireImmune()
				|| animal.isInWaterOrRain())
				continue;

			double distanceSq = MC.player.distanceToSqr(entity);
			if(distanceSq > rangeSq)
				continue;

			if(distanceSq < bestDistanceSq)
			{
				bestDistanceSq = distanceSq;
				best = animal;
			}
		}

		return best;
	}

	private int findIgniterSlot()
	{
		for(int i = 0; i < 9; i++)
			if(isUsableIgniter(MC.player.getInventory().getItem(i)))
				return i;

		if(isUsableIgniter(MC.player.getOffhandItem()))
			return 40;

		return -1;
	}

	private boolean isUsableIgniter(ItemStack stack)
	{
		if(!stack.is(Items.FLINT_AND_STEEL)
			&& !stack.is(Items.FIRE_CHARGE))
			return false;

		return !antiBreak.isChecked() || !stack.isDamageableItem()
			|| stack.getDamageValue() < stack.getMaxDamage() - 1;
	}

	private enum Mode
	{
		TARGET("Target"),
		UNDER_PLAYER("Under player");

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

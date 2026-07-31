/*
 * Copyright (c) 2014-2025 Wurst-Imperium and contributors.
 * Copyright (c) 2025 Penguin
 *
 * This source code is subject to the terms of the GNU General Public
 * License, version 3. If a copy of the GPL was not distributed with this
 * file, You can obtain one at: https://www.gnu.org/licenses/gpl-3.0.txt
 */
package net.wurstclient.hacks;

import java.util.HashSet;
import java.util.Set;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemUseAnimation;
// UseAnim removed in MC 26.1.2
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.wurstclient.Category;
import net.wurstclient.SearchTags;
import net.wurstclient.hack.Hack;
import net.wurstclient.settings.CheckboxSetting;
import net.wurstclient.util.AttributeValuePlanner;

@SearchTags({"no slowdown", "no slow down", "anti slowdown"})
public final class NoSlowdownHack extends Hack
{
	private final CheckboxSetting usingItems = new CheckboxSetting(
		"Using items", "Removes slowdown when eating, drinking, or using a bow.",
		true);

	private final CheckboxSetting blocking = new CheckboxSetting(
		"Blocking", "Removes slowdown when blocking with a shield.", true);

	private final CheckboxSetting soulSand = new CheckboxSetting(
		"Soul sand", "Removes slowdown when walking on soul sand.", true);

	private final CheckboxSetting honeyBlock = new CheckboxSetting(
		"Honey block", "Removes slowdown when walking on honey blocks.", true);

	private final CheckboxSetting slimeBlock = new CheckboxSetting(
		"Slime block", "Removes slowdown when walking on slime blocks.", false);

	private final CheckboxSetting cobweb = new CheckboxSetting(
		"Cobweb", "Removes slowdown when moving through cobwebs.", true);

	private final CheckboxSetting powderSnow = new CheckboxSetting(
		"Powder snow", "Removes movement sticking in powder snow.", true);

	private final CheckboxSetting sweetBerryBush = new CheckboxSetting(
		"Sweet berry bush", "Removes movement sticking in berry bushes.", true);

	private final CheckboxSetting usingItemSlowness = new CheckboxSetting(
		"Item slowness",
		"Removes the slowness from held item attributes (e.g. heavy weapons).",
		false);

	public NoSlowdownHack()
	{
		super("NoSlowdown");
		setCategory(Category.MOVEMENT);
		addSetting(usingItems);
		addSetting(blocking);
		addSetting(soulSand);
		addSetting(honeyBlock);
		addSetting(slimeBlock);
		addSetting(cobweb);
		addSetting(powderSnow);
		addSetting(sweetBerryBush);
		addSetting(usingItemSlowness);
	}

	public boolean shouldBypassUsingItem()
	{
		if(MC.player == null || !MC.player.isUsingItem())
			return false;

		ItemUseAnimation animation = MC.player.getUseItem().getUseAnimation();
		return animation == ItemUseAnimation.BLOCK ? blocking.isChecked()
			: usingItems.isChecked();
	}

	public boolean shouldBypassBlocking()
	{
		return blocking.isChecked();
	}

	public boolean shouldBypassSoulSand()
	{
		return soulSand.isChecked();
	}

	public boolean shouldBypassHoneyBlock()
	{
		return honeyBlock.isChecked();
	}

	public boolean shouldBypassSlimeBlock()
	{
		return slimeBlock.isChecked();
	}

	public boolean shouldBypassCobweb()
	{
		return cobweb.isChecked();
	}

	public boolean shouldBypassStuckBlock(BlockState state)
	{
		Block block = state.getBlock();
		return block == Blocks.COBWEB && cobweb.isChecked()
			|| block == Blocks.POWDER_SNOW && powderSnow.isChecked()
			|| block == Blocks.SWEET_BERRY_BUSH && sweetBerryBush.isChecked();
	}

	public boolean shouldBypassItemSlowness()
	{
		return isEnabled() && usingItemSlowness.isChecked();
	}

	public double getMovementSpeedWithoutItemSlowness(AttributeInstance instance)
	{
		if(!shouldBypassItemSlowness() || MC.player == null)
			return instance.getValue();

		ItemStack stack = MC.player.getMainHandItem();
		Set<Identifier> excluded = new HashSet<>();
		stack.forEachModifier(EquipmentSlot.MAINHAND, (attribute, modifier) -> {
			if(attribute.equals(Attributes.MOVEMENT_SPEED)
				&& modifier.amount() < 0)
				excluded.add(modifier.id());
		});

		return AttributeValuePlanner.calculateExcluding(instance, excluded);
	}

	// See BlockMixin.onGetVelocityMultiplier() and
	// ClientPlayerEntityMixin.wurstIsUsingItem()
}

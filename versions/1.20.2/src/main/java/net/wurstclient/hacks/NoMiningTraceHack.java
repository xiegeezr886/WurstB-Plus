/* Adapted from Meteor Client. Copyright (c) Meteor Development. SPDX-License-Identifier: GPL-3.0 */
package net.wurstclient.hacks;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.wurstclient.Category;
import net.wurstclient.SearchTags;
import net.wurstclient.hack.Hack;
import net.wurstclient.settings.CheckboxSetting;
import net.wurstclient.settings.TextFieldSetting;
import net.wurstclient.util.RegistryIdFilter;

@SearchTags({"no mining trace", "mine through entities", "穿实体挖掘"})
public final class NoMiningTraceHack extends Hack
{
	private final CheckboxSetting onlyPickaxe = new CheckboxSetting(
		"Only with pickaxe", "仅在任意手持镐时穿过实体选择方块。", true);
	private final TextFieldSetting excludedEntities = new TextFieldSetting(
		"Excluded entities", "保持正常交互的实体类型 ID，逗号分隔。", "");
	private final RegistryIdFilter filter = new RegistryIdFilter();

	public NoMiningTraceHack()
	{
		super("NoMiningTrace");
		setCategory(Category.BLOCKS);
		addSetting(onlyPickaxe);
		addSetting(excludedEntities);
	}

	public HitResult selectBlock(HitResult original, Entity camera,
		double blockRange, float partialTicks)
	{
		if(!isEnabled() || MC.player == null || camera == null
			|| !(original instanceof EntityHitResult hit))
			return original;
		if(onlyPickaxe.isChecked()
			&& !MC.player.getMainHandItem().is(ItemTags.PICKAXES)
			&& !MC.player.getOffhandItem().is(ItemTags.PICKAXES))
			return original;
		if(filter.contains(excludedEntities.getValue(), BuiltInRegistries
			.ENTITY_TYPE.getKey(hit.getEntity().getType()).toString()))
			return original;
		HitResult block = camera.pick(blockRange, partialTicks, false);
		return block.getType() == HitResult.Type.BLOCK ? block : original;
	}
}

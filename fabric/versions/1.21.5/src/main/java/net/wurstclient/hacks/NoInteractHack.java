/* Adapted from Meteor Client. Copyright (c) Meteor Development. SPDX-License-Identifier: GPL-3.0 */
package net.wurstclient.hacks;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.player.Player;
import net.wurstclient.Category;
import net.wurstclient.SearchTags;
import net.wurstclient.hack.Hack;
import net.wurstclient.settings.BlockListSetting;
import net.wurstclient.settings.EnumSetting;
import net.wurstclient.settings.TextFieldSetting;
import net.wurstclient.util.RegistryIdFilter;

@SearchTags({"no interact", "interaction filter", "交互过滤"})
public final class NoInteractHack extends Hack
{
	private final BlockListSetting blockMine = new BlockListSetting("Mining blocks", "挖掘过滤名单。");
	private final EnumSetting<ListMode> blockMineMode = mode("Mining list mode");
	private final BlockListSetting blockInteract = new BlockListSetting("Interaction blocks", "方块交互过滤名单。");
	private final EnumSetting<ListMode> blockInteractMode = mode("Block interaction mode");
	private final EnumSetting<HandMode> blockHand = hand("Block blocked hand");
	private final TextFieldSetting entityHit = new TextFieldSetting("Attack entities",
		"实体类型 ID，逗号分隔，例如 player,zombie。", "");
	private final EnumSetting<ListMode> entityHitMode = mode("Attack list mode");
	private final TextFieldSetting entityInteract = new TextFieldSetting("Interaction entities",
		"实体类型 ID，逗号分隔，例如 villager,horse。", "");
	private final EnumSetting<ListMode> entityInteractMode = mode("Entity interaction mode");
	private final EnumSetting<HandMode> entityHand = hand("Entity blocked hand");
	private final EnumSetting<InteractMode> friends = interactions("Friends");
	private final EnumSetting<InteractMode> babies = interactions("Babies");
	private final EnumSetting<InteractMode> named = interactions("Named entities");
	private final RegistryIdFilter hitFilter = new RegistryIdFilter();
	private final RegistryIdFilter interactFilter = new RegistryIdFilter();

	public NoInteractHack()
	{
		super("NoInteract");
		setCategory(Category.OTHER);
		addSetting(blockMine); addSetting(blockMineMode);
		addSetting(blockInteract); addSetting(blockInteractMode); addSetting(blockHand);
		addSetting(entityHit); addSetting(entityHitMode);
		addSetting(entityInteract); addSetting(entityInteractMode); addSetting(entityHand);
		addSetting(friends); addSetting(babies); addSetting(named);
	}

	private static EnumSetting<ListMode> mode(String name)
	{
		return new EnumSetting<>(name, "黑名单禁止列出的类型；白名单只允许列出的类型。",
			ListMode.values(), ListMode.BLACKLIST);
	}

	private static EnumSetting<HandMode> hand(String name)
	{
		return new EnumSetting<>(name, "禁止哪个手执行交互。", HandMode.values(), HandMode.NONE);
	}

	private static EnumSetting<InteractMode> interactions(String name)
	{
		return new EnumSetting<>(name, "禁止攻击、交互、两者或都允许。", InteractMode.values(), InteractMode.NONE);
	}

	public boolean shouldAttackBlock(BlockPos pos)
	{
		return !isEnabled() || MC.level == null || allowed(blockMine.contains(
			MC.level.getBlockState(pos).getBlock()), blockMineMode.getSelected());
	}

	public boolean shouldInteractBlock(BlockPos pos, InteractionHand hand)
	{
		return !isEnabled() || MC.level == null || !blockHand.getSelected().blocks(hand)
			&& allowed(blockInteract.contains(MC.level.getBlockState(pos).getBlock()), blockInteractMode.getSelected());
	}

	public boolean shouldAttackEntity(Entity entity)
	{
		return !isEnabled() || entity == null || !special(entity, true)
			&& allowed(hitFilter.contains(entityHit.getValue(),
				BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType()).toString()), entityHitMode.getSelected());
	}

	public boolean shouldInteractEntity(Entity entity, InteractionHand hand)
	{
		return !isEnabled() || entity == null || !entityHand.getSelected().blocks(hand)
			&& !special(entity, false) && allowed(interactFilter.contains(entityInteract.getValue(),
				BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType()).toString()), entityInteractMode.getSelected());
	}

	private boolean special(Entity entity, boolean hit)
	{
		return entity instanceof Player && WURST.getFriends().contains(entity.getScoreboardName())
			&& friends.getSelected().blocks(hit)
			|| entity instanceof Animal animal && animal.isBaby() && babies.getSelected().blocks(hit)
			|| entity.hasCustomName() && named.getSelected().blocks(hit);
	}

	private static boolean allowed(boolean listed, ListMode mode)
	{
		return mode == ListMode.WHITELIST ? listed : !listed;
	}

	private enum ListMode {WHITELIST, BLACKLIST}
	private enum HandMode
	{
		MAIN_HAND, OFF_HAND, BOTH, NONE;
		boolean blocks(InteractionHand hand)
		{
			return this == BOTH || this == MAIN_HAND && hand == InteractionHand.MAIN_HAND
				|| this == OFF_HAND && hand == InteractionHand.OFF_HAND;
		}
	}
	private enum InteractMode
	{
		HIT, INTERACT, BOTH, NONE;
		boolean blocks(boolean hit) {return this == BOTH || (hit ? this == HIT : this == INTERACT);}
	}
}

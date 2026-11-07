/*
 * Copyright (c) 2014-2025 Wurst-Imperium and contributors.
 * Copyright (c) 2015-2026 CCBlueX
 *
 * This source code is subject to the terms of the GNU General Public
 * License, version 3. If a copy of the GPL was not distributed with this
 * file, You can obtain one at: https://www.gnu.org/licenses/gpl-3.0.txt
 */
package net.wurstclient.mixin;

import java.util.ArrayList;
import java.util.List;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.wurstclient.WurstClient;
import net.wurstclient.hack.HackList;

/**
 * Appends Wurst's extra tooltip lines at the end of
 * {@code ItemStack.getTooltipLines}.
 *
 * <p>
 * Using the vanilla method instead of a loader-specific tooltip event keeps
 * the behaviour identical on Fabric, Forge and NeoForge.
 */
@Mixin(ItemStack.class)
public class ItemStackMixin
{
	@Inject(
		method = "getTooltipLines(Lnet/minecraft/world/item/Item$TooltipContext;Lnet/minecraft/world/entity/player/Player;Lnet/minecraft/world/item/TooltipFlag;)Ljava/util/List;",
		at = @At("RETURN"))
	private void onGetTooltipLines(Item.TooltipContext context, Player player,
		TooltipFlag flag, CallbackInfoReturnable<List<Component>> cir)
	{
		HackList hax = WurstClient.INSTANCE.getHax();
		if(hax == null || !hax.betterTooltipsHack.isEnabled())
			return;

		List<Component> lines = cir.getReturnValue();
		if(lines == null || lines.isEmpty())
			return;

		List<Component> result = new ArrayList<>(lines);
		hax.betterTooltipsHack.appendTooltip((ItemStack)(Object)this, result);

		if(result.size() != lines.size())
			cir.setReturnValue(result);
	}
}

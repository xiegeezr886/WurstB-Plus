/* Adapted from Meteor Client. Copyright (c) Meteor Development. SPDX-License-Identifier: GPL-3.0 */
package net.wurstclient.mixin;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.inventory.BeaconScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.core.Holder;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.BeaconMenu;
import net.wurstclient.WurstClient;
import net.wurstclient.mixinterface.IBeaconPowerButton;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(BeaconScreen.class)
public abstract class BetterBeaconsScreenMixin extends AbstractContainerScreen<BeaconMenu>
{
	@Shadow @Final private List<?> beaconButtons;
	@Shadow private Holder<MobEffect> primary;
	@Shadow private Holder<MobEffect> secondary;
	@Unique private final List<Button> wurstPrimaryButtons = new ArrayList<>();
	@Unique private final List<Button> wurstSecondaryButtons = new ArrayList<>();
	@Unique private boolean wurstExpanded;

	protected BetterBeaconsScreenMixin(BeaconMenu menu, Inventory inventory,
		Component title)
	{
		super(menu, inventory, title);
	}

	@Unique
	private boolean wurstShouldExpand()
	{
		return WurstClient.INSTANCE.getHax() != null
			&& WurstClient.INSTANCE.getHax().betterBeaconsHack.hasExpandedSelection();
	}

	@Inject(method = "init", at = @At("TAIL"))
	private void wurstExpandSelection(CallbackInfo ci)
	{
		wurstPrimaryButtons.clear();
		wurstSecondaryButtons.clear();
		wurstExpanded = wurstShouldExpand();
		if(!wurstExpanded)
			return;
		beaconButtons.removeIf(button -> {
			if(!(button instanceof IBeaconPowerButton))
				return false;
			removeWidget((AbstractWidget)button);
			return true;
		});
		List<Holder<MobEffect>> effects = List.of(MobEffects.SPEED,
			MobEffects.HASTE, MobEffects.RESISTANCE,
			MobEffects.JUMP_BOOST, MobEffects.STRENGTH, MobEffects.REGENERATION);
		for(int i = 0; i < effects.size(); i++)
		{
			Holder<MobEffect> effect = effects.get(i);
			Component name = effect.value().getDisplayName();
			Button first = Button.builder(name, button -> primary = effect)
				.bounds(leftPos + 10, topPos + 22 + i * 14, 102, 14).build();
			Button second = Button.builder(name, button -> secondary = effect)
				.bounds(leftPos + 118, topPos + 22 + i * 14, 102, 14).build();
			first.setTooltip(Tooltip.create(name));
			second.setTooltip(Tooltip.create(name));
			wurstPrimaryButtons.add(addRenderableWidget(first));
			wurstSecondaryButtons.add(addRenderableWidget(second));
		}
		wurstRefreshSelection();
	}

	@Unique
	private void wurstRefreshSelection()
	{
		List<Holder<MobEffect>> effects = List.of(MobEffects.SPEED,
			MobEffects.HASTE, MobEffects.RESISTANCE,
			MobEffects.JUMP_BOOST, MobEffects.STRENGTH, MobEffects.REGENERATION);
		for(int i = 0; i < wurstPrimaryButtons.size(); i++)
		{
			wurstPrimaryButtons.get(i).active = primary != effects.get(i);
			wurstSecondaryButtons.get(i).active = menu.getLevels() == 4
				&& secondary != effects.get(i);
		}
	}

	@Inject(method = "containerTick", at = @At("TAIL"))
	private void wurstRefreshButtons(CallbackInfo ci)
	{
		if(wurstExpanded != wurstShouldExpand())
		{
			rebuildWidgets();
			return;
		}
		if(wurstExpanded)
			wurstRefreshSelection();
	}
}

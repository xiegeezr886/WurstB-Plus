/* SPDX-License-Identifier: GPL-3.0 */
package net.wurstclient.mixin;

import org.spongepowered.asm.mixin.Mixin;
import net.wurstclient.mixinterface.IBeaconPowerButton;

@Mixin(targets = "net.minecraft.client.gui.screens.inventory.BeaconScreen$BeaconPowerButton")
public abstract class BeaconPowerButtonMixin implements IBeaconPowerButton
{
}

/*
 * Copyright (c) 2026 WurstB+ Plus contributors.
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package net.wurstclient.mixin;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import net.minecraft.client.OptionInstance;
import net.minecraft.client.Options;
import net.wurstclient.WurstClient;
import net.wurstclient.mixinterface.ISimpleOption;
import org.spongepowered.asm.mixin.Mixin;

/** Keep Fullbright/X-Ray gamma out of options.txt without changing rendering. */
@Mixin(Options.class)
public class OptionsMixin
{
	@WrapMethod(method = "save()V")
	private void wurstSaveGamma(Operation<Void> original)
	{
		OptionInstance<Double> gamma = ((Options)(Object)this).gamma();
		double activeGamma = gamma.get();
		if(Double.isFinite(activeGamma) && activeGamma >= 0 && activeGamma <= 1)
		{
			original.call();
			return;
		}

		double savedGamma = 0.5;
		var hax = WurstClient.INSTANCE.getHax();
		if(hax != null && hax.fullbrightHack != null)
			savedGamma = hax.fullbrightHack.getDefaultGamma();
		if(!Double.isFinite(savedGamma)) savedGamma = 0.5;
		savedGamma = Math.max(0, Math.min(1, savedGamma));

		ISimpleOption<Double> access = ISimpleOption.get(gamma);
		try
		{
			access.forceSetValue(savedGamma);
			original.call();
		}finally
		{
			// Also restore on an I/O failure or an exception from another mod.
			access.forceSetValue(activeGamma);
		}
	}
}

/*
 * Copyright (c) 2014-2026 Wurst-Imperium and contributors.
 *
 * This source code is subject to the terms of the GNU General Public
 * License, version 3. If a copy of the GPL was not distributed with this
 * file, You can obtain one at: https://www.gnu.org/licenses/gpl-3.0.txt
 */
package net.wurstclient.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import net.minecraft.client.OptionInstance;
import net.minecraft.client.Options;
import net.wurstclient.WurstClient;
import net.wurstclient.mixinterface.ISimpleOption;

/**
 * 26.2 起 {@code Options.save()} 会用 codec 校验每个选项的范围，越界的选项
 * 整行都不会写进 options.txt。Fullbright 和 X-Ray 却故意用
 * {@code forceSetValue} 把 gamma 写到 16，于是保存时 gamma 这一行被丢掉，
 * 下次启动亮度就回到默认值。保存前把它拉回 Fullbright 记录的原始亮度。
 */
@Mixin(Options.class)
public class OptionsMixin
{
	@Inject(method = "save", at = @At("HEAD"))
	private void onSave(CallbackInfo ci)
	{
		OptionInstance<Double> gamma = ((Options)(Object)this).gamma();
		if(gamma.get() <= 1)
			return;

		double safeGamma = 0.5;
		var hax = WurstClient.INSTANCE.getHax();
		if(hax != null && hax.fullbrightHack != null)
			safeGamma = hax.fullbrightHack.getDefaultGamma();

		ISimpleOption.get(gamma).forceSetValue(safeGamma);
	}
}

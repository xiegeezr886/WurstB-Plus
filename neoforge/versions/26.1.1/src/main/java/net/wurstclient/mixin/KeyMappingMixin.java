/*
 * Copyright (c) 2014-2026 Wurst-Imperium and contributors.
 *
 * This source code is subject to the terms of the GNU General Public
 * License, version 3. If a copy of the GPL was not distributed with this
 * file, You can obtain one at: https://www.gnu.org/licenses/gpl-3.0.txt
 */
package net.wurstclient.mixin;

import net.wurstclient.hack.HackList;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.Final;
import org.lwjgl.glfw.GLFW;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;

import com.mojang.blaze3d.platform.InputConstants;
import com.mojang.blaze3d.platform.Window;

import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonInfo;
import net.wurstclient.WurstClient;
import net.wurstclient.mixinterface.IKeyMapping;

@Mixin(KeyMapping.class)
public abstract class KeyMappingMixin implements IKeyMapping
{
	@Shadow
	private InputConstants.Key key;
	
	@Override
	@Unique
	@Deprecated // use IKeyMapping.isActuallyDown() instead
	public boolean wurst_isActuallyDown()
	{
		Window window = WurstClient.MC.getWindow();
		int code = key.getValue();
		
		if(key.getType() == InputConstants.Type.MOUSE)
			return GLFW.glfwGetMouseButton(window.handle(), code) == 1;
		
		return InputConstants.isKeyDown(window, code);
	}
	
	@Override
	@Unique
	@Deprecated // use IKeyMapping.resetPressedState() instead
	public void wurst_resetPressedState()
	{
		setDown(wurst_isActuallyDown());
	}
	
	@Override
	@Unique
	@Deprecated // use IKeyMapping.simulatePress() instead
	public void wurst_simulatePress(boolean pressed)
	{
		setDown(pressed);
	}
	
	@Override
	@Shadow
	public abstract void setDown(boolean pressed);

	@Shadow
	private boolean isDown;

	@Shadow
	@Final
	private InputConstants.Key defaultKey;

	@Inject(method = "isDown()Z", at = @At("HEAD"), cancellable = true)
	private void soar$snapTapIsDown(CallbackInfoReturnable<Boolean> cir)
	{
		HackList hax = WurstClient.INSTANCE.getHax();
		if(hax == null || !hax.snapTapHack.isEnabled() || !isDown)
			return;

		int code = defaultKey.getValue();
		if(code == InputConstants.KEY_A)
		{
			long right = hax.snapTapHack.getRightPressTime();
			if(right != 0)
				cir.setReturnValue(right <= hax.snapTapHack
					.getLeftPressTime());
		}else if(code == InputConstants.KEY_D)
		{
			long left = hax.snapTapHack.getLeftPressTime();
			if(left != 0)
				cir.setReturnValue(left <= hax.snapTapHack
					.getRightPressTime());
		}else if(code == InputConstants.KEY_W)
		{
			long back = hax.snapTapHack.getBackPressTime();
			if(back != 0)
				cir.setReturnValue(back <= hax.snapTapHack
					.getForwardPressTime());
		}else if(code == InputConstants.KEY_S)
		{
			long forward = hax.snapTapHack.getForwardPressTime();
			if(forward != 0)
				cir.setReturnValue(forward <= hax.snapTapHack
					.getBackPressTime());
		}
	}

	@Inject(method = "setDown(Z)V", at = @At("HEAD"))
	private void soar$snapTapSetDown(boolean pressed, CallbackInfo ci)
	{
		HackList hax = WurstClient.INSTANCE.getHax();
		if(hax == null || !hax.snapTapHack.isEnabled())
			return;

		long now = pressed ? System.currentTimeMillis() : 0;
		int code = defaultKey.getValue();
		if(code == InputConstants.KEY_A)
			hax.snapTapHack.setLeftPressTime(now);
		else if(code == InputConstants.KEY_D)
			hax.snapTapHack.setRightPressTime(now);
		else if(code == InputConstants.KEY_W)
			hax.snapTapHack.setForwardPressTime(now);
		else if(code == InputConstants.KEY_S)
			hax.snapTapHack.setBackPressTime(now);
	}

}

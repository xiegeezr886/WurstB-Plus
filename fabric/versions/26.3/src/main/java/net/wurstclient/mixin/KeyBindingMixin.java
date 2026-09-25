/*
 * Copyright (c) 2014-2025 Wurst-Imperium and contributors.
 *
 * This source code is subject to the terms of the GNU General Public
 * License, version 3. If a copy of the GPL was not distributed with this
 * file, You can obtain one at: https://www.gnu.org/licenses/gpl-3.0.txt
 */
package net.wurstclient.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import net.wurstclient.mixinterface.IKeyBinding;
import net.wurstclient.util.MouseUtils;

@Mixin(KeyMapping.class)
public abstract class KeyBindingMixin implements IKeyBinding
{
	@Shadow
	private InputConstants.Key key;
	
	@Override
	@Unique
	@Deprecated // use IKeyBinding.resetPressedState() instead
	public void wurst_resetPressedState()
	{
		int code = key.getValue();
		
		if(key.getType() == InputConstants.Type.MOUSE)
			setDown(MouseUtils.isButtonDown(code));
		else
			setDown(InputConstants.isKeyDown(code));
	}
	
	@Override
	@Unique
	@Deprecated // use IKeyBinding.simulatePress() instead
	public void wurst_simulatePress(boolean pressed)
	{
		setDown(pressed);
	}
	
	@Shadow
	public abstract void setDown(boolean pressed);
}

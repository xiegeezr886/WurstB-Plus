/*
 * Copyright (c) 2026 WurstB+ Plus contributors
 *
 * This source code is subject to the terms of the GNU Affero General Public
 * License, version 3. If a copy of the AGPL was not distributed with this
 * file, You can obtain one at: https://www.gnu.org/licenses/agpl-3.0.txt
 */
package net.wurstclient.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractButton;
import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.client.gui.components.AbstractWidget;
import net.wurstclient.gui.ButtonStyleRenderer;
import net.wurstclient.gui.CustomRenderedButton;

/**
 * Defers buttons and sliders to {@link ButtonStyleRenderer} instead of letting
 * them draw their vanilla skins. Widgets that were never collected (e.g.
 * outside a screen render) keep their normal rendering.
 */
@Mixin(AbstractWidget.class)
public abstract class AbstractWidgetMixin
{
	@Inject(at = @At("HEAD"), method = "render", cancellable = true)
	private void wurst$replaceControlRender(GuiGraphics graphics, int mouseX,
		int mouseY, float partialTicks, CallbackInfo ci)
	{
		if(!ButtonStyleRenderer.isCollecting())
			return;

		Object self = this;
		boolean button = self instanceof AbstractButton
			&& !(self instanceof CustomRenderedButton);
		boolean slider = self instanceof AbstractSliderButton;
		if(!button && !slider)
			return;

		AbstractWidget widget = (AbstractWidget)self;
		if(!widget.visible)
			return;

		ButtonStyleRenderer.defer(widget,
			ButtonStyleRenderer.captureScissor());
		ci.cancel();
	}
}

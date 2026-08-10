package net.wurstclient.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.network.chat.Component;
import net.wurstclient.clickgui2.FlatRenderer;
import net.wurstclient.gui.visual.VisualTheme;

@Mixin(AbstractSliderButton.class)
public abstract class AbstractSliderButtonMixin extends AbstractWidget
{
	@Shadow
	protected double value;

	@Unique
	private float wurst$hoverProgress;

	@Unique
	private long wurst$lastRenderNanos;

	protected AbstractSliderButtonMixin(int x, int y, int width, int height,
		Component message)
	{
		super(x, y, width, height, message);
	}

	@Inject(at = @At("HEAD"), method = "renderWidget", cancellable = true)
	private void renderLiquidBounceSlider(GuiGraphics graphics, int mouseX,
		int mouseY, float partialTicks, CallbackInfo ci)
	{
		long now = System.nanoTime();
		float delta = wurst$lastRenderNanos == 0 ? 0
			: Math.min(0.05F,
				(now - wurst$lastRenderNanos) / 1_000_000_000F);
		wurst$lastRenderNanos = now;
		float target = isHoveredOrFocused() && active ? 1 : 0;
		wurst$hoverProgress += (target - wurst$hoverProgress)
			* (1 - (float)Math.exp(-18 * delta));

		int left = getX();
		int top = getY();
		int right = left + getWidth();
		int bottom = top + getHeight();
		int background = VisualTheme.mix(VisualTheme.CONTROL,
			VisualTheme.CONTROL_HOVER, wurst$hoverProgress);
		FlatRenderer.fillRoundedRect(graphics, left, top, right, bottom,
			VisualTheme.RADIUS_SMALL, background);
		FlatRenderer.drawRoundedOutline(graphics, left, top, right, bottom,
			VisualTheme.RADIUS_SMALL,
			VisualTheme.mix(VisualTheme.BORDER, VisualTheme.ACCENT,
				wurst$hoverProgress));

		int trackLeft = left + 4;
		int trackRight = right - 4;
		int trackY = bottom - 3;
		FlatRenderer.fillRoundedRect(graphics, trackLeft, trackY, trackRight,
			trackY + 1, 1, VisualTheme.BORDER_STRONG);
		int handleX = trackLeft
			+ (int)Math.round((trackRight - trackLeft) * value);
		if(handleX > trackLeft)
			FlatRenderer.fillRoundedRect(graphics, trackLeft, trackY, handleX,
				trackY + 1, 1, VisualTheme.ACCENT);
		FlatRenderer.fillRoundedRect(graphics, handleX - 2, trackY - 2,
			handleX + 2, trackY + 3, 2, VisualTheme.ACCENT);

		int textColor = active ? VisualTheme.TEXT : VisualTheme.TEXT_DISABLED;
		renderScrollingString(graphics, Minecraft.getInstance().font, 2,
			textColor);
		ci.cancel();
	}
}

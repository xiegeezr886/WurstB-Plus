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
import net.wurstclient.gui.glass.GlassButtons;
import net.wurstclient.gui.visual.VisualTheme;

/**
 * 原版滑块的画法：整条与把手改为 LumaGlass 的玻璃面板（见 {@code gui/glass/}），玻璃
 * 不可用时回退到原来的扁平样式。
 *
 * <p>
 * 上游 LumaGlass 的 {@code SliderMixin} 就是这条画法：一条圆角 10 的玻璃当滑轨，一块
 * 宽 8、圆角 4 的小玻璃当把手，把手位置按 {@code value} 在条内滑动、恒为高亮态。</p>
 */
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
	private void wurst$renderGlassSlider(GuiGraphics graphics, int mouseX,
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

		if(GlassButtons.renderSlider(graphics, getX(), getY(), getWidth(),
			getHeight(), value, wurst$hoverProgress, active, alpha))
		{
			renderScrollingString(graphics, Minecraft.getInstance().font, 2,
				active ? VisualTheme.TEXT : VisualTheme.TEXT_DISABLED);
			ci.cancel();
			return;
		}

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

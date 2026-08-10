package net.wurstclient.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractButton;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.network.chat.Component;
import net.wurstclient.gui.visual.VisualRenderer;
import net.wurstclient.gui.visual.VisualTheme;

@Mixin(AbstractButton.class)
public abstract class AbstractButtonMixin extends AbstractWidget
{
	@Unique
	private float wurst$hoverProgress;

	@Unique
	private long wurst$lastRenderNanos;

	@Unique
	private long wurst$pressedAtNanos;

	protected AbstractButtonMixin(int x, int y, int width, int height,
		Component message)
	{
		super(x, y, width, height, message);
	}

	@Shadow
	public abstract void renderString(GuiGraphics graphics, Font font,
		int color);

	@Inject(at = @At("HEAD"), method = "onClick(DD)V")
	private void animatePress(double mouseX, double mouseY, CallbackInfo ci)
	{
		wurst$pressedAtNanos = System.nanoTime();
	}

	@Inject(at = @At("HEAD"), method = "renderWidget", cancellable = true)
	private void renderLiquidBounceButton(GuiGraphics graphics, int mouseX,
		int mouseY, float partialTicks, CallbackInfo ci)
	{
		long now = System.nanoTime();
		float delta = wurst$lastRenderNanos == 0 ? 0
			: Math.min(0.05F,
				(now - wurst$lastRenderNanos) / 1_000_000_000F);
		wurst$lastRenderNanos = now;

		float target = isHoveredOrFocused() && active ? 1 : 0;
		float response = 1 - (float)Math.exp(-18 * delta);
		wurst$hoverProgress += (target - wurst$hoverProgress) * response;

		float press = Math.max(0,
			1 - (now - wurst$pressedAtNanos) / 120_000_000F);
		int inset = press > 0.08F ? 1 : 0;
		boolean dangerous = isDangerous(getMessage().getString());
		VisualRenderer.button(graphics, getX() + inset, getY() + inset,
			getX() + getWidth() - inset, getY() + getHeight() - inset,
			VisualTheme.RADIUS_SMALL,
			Math.min(1, wurst$hoverProgress + press * 0.22F), active,
			dangerous);

		int textColor = active ? VisualTheme.TEXT : VisualTheme.TEXT_DISABLED;
		renderString(graphics, Minecraft.getInstance().font, textColor);
		ci.cancel();
	}

	@Unique
	private static boolean isDangerous(String label)
	{
		String normalized = label.toLowerCase(java.util.Locale.ROOT);
		return normalized.contains("delete") || normalized.contains("remove")
			|| normalized.contains("logout") || normalized.contains("删除")
			|| normalized.contains("移除") || normalized.contains("登出");
	}
}

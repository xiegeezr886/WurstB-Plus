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
import net.wurstclient.gui.glass.GlassButtons;
import net.wurstclient.gui.visual.VisualRenderer;
import net.wurstclient.gui.visual.VisualTheme;

/**
 * 原版按钮的画法：底色改为 LumaGlass 的玻璃面板（见 {@code gui/glass/}），玻璃不可用时
 * 回退到原来的扁平样式。
 *
 * <p>
 * 这个注入点是全客户端原版按钮的唯一入口：{@code AbstractButton.renderWidget} 的 HEAD
 * 被取消后，按钮背景完全由这里决定。所以玻璃那一条路径失败时必须原样画回旧样式，
 * 不能把异常抛出去——那会让每个带按钮的界面都崩。</p>
 *
 * <p>
 * 旧的扁平样式里按文案判断的"危险色"（删除/登出等）在玻璃这一侧没有等价物：
 * LumaGlass 的材质用中性底色，只有泛光带色相，而泛光在上游默认关闭、本移植里恒为 0。
 * 该判断只保留在回退路径上。</p>
 */
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
	private void wurst$renderGlassButton(GuiGraphics graphics, int mouseX,
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
		int x1 = getX() + inset;
		int y1 = getY() + inset;
		int x2 = getX() + getWidth() - inset;
		int y2 = getY() + getHeight() - inset;
		float focus = Math.min(1, wurst$hoverProgress + press * 0.22F);

		if(!GlassButtons.renderButton(graphics, x1, y1, x2 - x1, y2 - y1,
			focus, active, alpha))
			VisualRenderer.button(graphics, x1, y1, x2, y2,
				VisualTheme.RADIUS_SMALL, focus, active,
				isDangerous(getMessage().getString()));

		int textColor = active ? VisualTheme.TEXT : VisualTheme.TEXT_DISABLED;
		renderString(graphics, Minecraft.getInstance().font, textColor);
		ci.cancel();
	}

	/** 只被扁平回退路径使用，保持旧样式对破坏性操作的红色提示。 */
	@Unique
	private static boolean isDangerous(String label)
	{
		String normalized = label.toLowerCase(java.util.Locale.ROOT);
		return normalized.contains("delete") || normalized.contains("remove")
			|| normalized.contains("logout") || normalized.contains("删除")
			|| normalized.contains("移除") || normalized.contains("登出");
	}
}

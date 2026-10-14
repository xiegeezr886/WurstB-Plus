package net.wurstclient.mixin;

import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Checkbox;
import net.minecraft.network.chat.Component;
import net.wurstclient.gui.glass.GlassButtons;

/**
 * 原版复选框的画法：方块改为 LumaGlass 的玻璃（上游 {@code CheckboxMixin}：20 宽、
 * 圆角 7，勾选或悬停时按高亮态画），玻璃不可用时保持原版贴图。
 *
 * <p>
 * 勾与标签的文案跟随上游：勾是画出来的字形（原版那个勾在贴图里，方块换成玻璃后就没
 * 了），标签直接画在方块右侧。</p>
 */
@Mixin(Checkbox.class)
public abstract class CheckboxMixin extends AbstractWidget
{
	@Shadow
	private boolean selected;

	@Shadow
	@Final
	private boolean showLabel;

	protected CheckboxMixin(int x, int y, int width, int height,
		Component message)
	{
		super(x, y, width, height, message);
	}

	@Inject(at = @At("HEAD"), method = "renderWidget", cancellable = true)
	private void wurst$renderGlassCheckbox(GuiGraphics graphics, int mouseX,
		int mouseY, float partialTicks, CallbackInfo ci)
	{
		if(!GlassButtons.renderCheckbox(graphics, getX(), getY(), getHeight(),
			isHoveredOrFocused() || selected, alpha))
			return;

		Font font = Minecraft.getInstance().font;
		if(selected)
			graphics.drawCenteredString(font, "✓", getX() + 10,
				getY() + (getHeight() - 8) / 2, 0xFFD0FFFF);
		if(showLabel)
			graphics.drawString(font, getMessage(), getX() + 24,
				getY() + (getHeight() - 8) / 2, 0xFFEFF5FF);
		ci.cancel();
	}
}

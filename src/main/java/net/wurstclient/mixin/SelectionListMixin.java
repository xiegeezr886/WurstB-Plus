package net.wurstclient.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractSelectionList;
import net.wurstclient.gui.glass.GlassButtons;

/**
 * 列表滚动条的画法：轨道与滑块改为细玻璃条（上游 {@code SelectionListMixin} 里
 * {@code VanillaGlass.scrollbar} 那两个分支）。
 *
 * <p>
 * 原版 {@code AbstractSelectionList.render} 用三次 {@code GuiGraphics.fill} 画滚动条：
 * 黑色轨道（{@code 0xFF000000}）、灰色滑块（{@code 0xFF808080}）和一条旧的方形浮雕。
 * 这里只把前两次换成玻璃，位置与命中判定仍用原版算好的矩形；浮雕那次原样透传，所以
 * 本混入不改任何布局或输入行为。</p>
 *
 * <p>
 * {@code require = 3, allow = 3} 是照上游写的：三次调用必须都命中，否则宁可在启动时
 * 报错，也不要静默地只换掉一半滚动条。</p>
 */
@Mixin(AbstractSelectionList.class)
public abstract class SelectionListMixin
{
	@Redirect(method = "render",
		at = @At(value = "INVOKE",
			target = "Lnet/minecraft/client/gui/GuiGraphics;fill(IIIII)V"),
		require = 3, allow = 3)
	private void wurst$renderGlassScrollbar(GuiGraphics graphics, int left,
		int top, int right, int bottom, int color)
	{
		// 轨道：左右各内缩 2px，与上游一致
		if(color == 0xFF000000 && GlassButtons.renderScrollbar(graphics,
			left + 2, top, right - left - 4, bottom - top, true, false))
			return;

		if(color == 0xFF808080 && GlassButtons.renderScrollbar(graphics, left,
			top, right - left, bottom - top, false, false))
			return;

		graphics.fill(left, top, right, bottom, color);
	}
}

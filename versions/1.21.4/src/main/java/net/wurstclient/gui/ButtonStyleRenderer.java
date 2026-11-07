/*
 * Copyright (c) 2026 WurstB+ Plus contributors
 *
 * This source code is subject to the terms of the GNU Affero General Public
 * License, version 3. If a copy of the AGPL was not distributed with this
 * file, You can obtain one at: https://www.gnu.org/licenses/agpl-3.0.txt
 */
package net.wurstclient.gui;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;

import org.lwjgl.opengl.GL11;

import com.mojang.blaze3d.platform.Window;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractButton;
import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Checkbox;
import net.minecraft.client.gui.screens.Screen;
import net.wurstclient.clickgui2.animation.HoverAnimation;
import net.wurstclient.hud2.nanovg.NanoVGContext;
import net.wurstclient.hud2.nanovg.NanoVGUI;
import net.wurstclient.mixin.AbstractSliderButtonAccessor;

/**
 * Draws buttons, checkboxes and sliders with the title-menu card style.
 *
 * <p>Widgets register themselves through {@code AbstractWidgetMixin} while a
 * screen renders. They are then drawn in a single NanoVG pass at the end of
 * {@code Screen.render}. This covers widgets nested inside option lists too,
 * while preserving the list's clipping rectangle via the OpenGL scissor state
 * captured when the widget rendered.
 */
public final class ButtonStyleRenderer
{
	private static final int ACCENT = 0xFF006366;
	private static final int TEXT_COLOR = 0xFFF2F6F6;
	private static final int DISABLED_TEXT = 0xFF707A7B;
	private static final int BASE_FILL = 0xD9141B1C;

	private static final Map<AbstractWidget, HoverAnimation> HOVER =
		new WeakHashMap<>();
	private static final List<DeferredControl> CONTROLS = new ArrayList<>();

	private static boolean collecting;
	private static Screen collectingScreen;

	private ButtonStyleRenderer() {}

	public static void beginScreen(GuiGraphics graphics, Screen screen)
	{
		// A screen can start rendering controls in renderBackground() or in an
		// options list before Screen.render() is called. Keep everything it
		// already collected for the same screen.
		if(collecting && collectingScreen == screen)
			return;

		CONTROLS.clear();
		collectingScreen = screen;
		collecting = NanoVGContext.get().create();
	}

	public static boolean isCollecting()
	{
		return collecting;
	}

	public static void defer(AbstractWidget widget, float[] scissor)
	{
		if(collecting)
			CONTROLS.add(new DeferredControl(widget, scissor));
	}

	public static void endScreen(GuiGraphics graphics)
	{
		collecting = false;
		collectingScreen = null;
		if(CONTROLS.isEmpty())
			return;

		List<DeferredControl> controls = new ArrayList<>(CONTROLS);
		CONTROLS.clear();
		NanoVGUI.pass(graphics, () -> {
			for(DeferredControl control : controls)
			{
				float[] scissor = control.scissor();
				if(scissor != null && (scissor[2] <= scissor[0]
					|| scissor[3] <= scissor[1]))
					continue;

				if(scissor != null)
					NanoVGUI.pushScissor(scissor[0], scissor[1],
						scissor[2] - scissor[0], scissor[3] - scissor[1]);

				if(control.widget() instanceof AbstractSliderButton slider)
					drawSlider(slider);
				else if(control.widget() instanceof AbstractButton button)
					drawButton(button);

				if(scissor != null)
					NanoVGUI.popScissor();
			}
		});
	}

	/**
	 * Converts the currently active OpenGL scissor into GUI coordinates so a
	 * deferred widget can be clipped to the list that rendered it.
	 */
	public static float[] captureScissor()
	{
		if(!GL11.glIsEnabled(GL11.GL_SCISSOR_TEST))
			return null;

		Minecraft mc = Minecraft.getInstance();
		if(mc == null || mc.getWindow() == null)
			return null;

		Window window = mc.getWindow();
		double scale = window.getGuiScale();
		if(scale <= 0)
			return null;

		int[] box = new int[4];
		GL11.glGetIntegerv(GL11.GL_SCISSOR_BOX, box);

		float x = (float)(box[0] / scale);
		float y = (float)((window.getHeight() - box[1] - box[3]) / scale);
		float width = (float)(box[2] / scale);
		float height = (float)(box[3] / scale);

		float maxX = window.getGuiScaledWidth();
		float maxY = window.getGuiScaledHeight();
		x = Math.max(0, x);
		y = Math.max(0, y);
		return new float[]{x, y, Math.min(maxX, x + width),
			Math.min(maxY, y + height)};
	}

	private static void drawButton(AbstractButton button)
	{
		int x1 = button.getX();
		int y1 = button.getY();
		int width = button.getWidth();
		int height = button.getHeight();
		if(width <= 0 || height <= 0)
			return;

		boolean compact = height <= 22 || width < 120;
		float hover = hover(button);
		drawCard(button, x1, y1, width, height, compact, hover);

		float size = compact ? 9F : 10F;
		float textHeight = NanoVGUI.height(size);
		float textY = y1 + (height - textHeight) / 2F;
		int textColor = button.active ? TEXT_COLOR : DISABLED_TEXT;

		boolean checkbox = button instanceof Checkbox;
		float textX = x1 + (compact ? 8 : 12);
		if(checkbox)
		{
			drawCheckbox((Checkbox)button, x1, y1, height);
			textX = x1 + (compact ? 24 : 28);
		}

		String label = button.getMessage().getString().replace('\n', ' ');
		float reservedRight = compact ? 8 : 24;
		float maxWidth = x1 + width - reservedRight - textX;
		label = NanoVGUI.plainSubstrByWidth(label, maxWidth, size);

		if(compact && !checkbox)
			NanoVGUI.centeredText(label, x1 + width / 2F, textY, textColor,
				size);
		else
		{
			NanoVGUI.text(label, textX, textY, textColor, size);
			if(!compact)
				NanoVGUI.text(">", x1 + width - 14, textY,
					withAlpha(0xFFFFFFFF,
						110 + Math.round(hover * 145)),
					size);
		}
	}

	private static void drawSlider(AbstractSliderButton slider)
	{
		int x1 = slider.getX();
		int y1 = slider.getY();
		int width = slider.getWidth();
		int height = slider.getHeight();
		if(width <= 0 || height <= 0)
			return;

		boolean compact = height <= 22 || width < 120;
		float hover = hover(slider);
		drawCard(slider, x1, y1, width, height, compact, hover);

		float trackLeft = x1 + 8;
		float trackRight = x1 + width - 8;
		float trackY = y1 + height - 4;
		float trackHeight = 2;

		NanoVGUI.fillRounded(trackLeft, trackY, trackRight,
			trackY + trackHeight, 1,
			slider.active ? 0x55FFFFFF : 0x22FFFFFF);

		double raw = ((AbstractSliderButtonAccessor)(Object)slider)
			.wurst$getValue();
		float percentage = (float)Math.max(0, Math.min(1, raw));
		float progressX = trackLeft + (trackRight - trackLeft) * percentage;
		if(progressX > trackLeft)
			NanoVGUI.fillRounded(trackLeft, trackY, progressX,
				trackY + trackHeight, 1,
				slider.active ? ACCENT : 0xFF3A3F40);

		float knobWidth = 6;
		float knobHeight = Math.min(10, height - 8);
		float knobX = Math.max(trackLeft,
			Math.min(progressX - knobWidth / 2, trackRight - knobWidth));
		float knobY = y1 + (height - knobHeight) / 2F;
		NanoVGUI.fillRounded(knobX, knobY, knobX + knobWidth,
			knobY + knobHeight, 3,
			slider.active ? 0xFFF0F0F0 : 0xFF808080);
		NanoVGUI.outlineRounded(knobX, knobY, knobX + knobWidth,
			knobY + knobHeight, 3, 0x40000000);

		float size = compact ? 9F : 10F;
		String label = slider.getMessage().getString().replace('\n', ' ');
		label = NanoVGUI.plainSubstrByWidth(label, width - 16, size);
		NanoVGUI.centeredText(label, x1 + width / 2F,
			y1 + (compact ? 2 : 3), slider.active ? TEXT_COLOR : DISABLED_TEXT,
			size);
	}

	private static void drawCard(AbstractWidget widget, int x1, int y1,
		int width, int height, boolean compact, float hover)
	{
		int radius = compact ? 5 : 7;
		int accent = widget.active ? ACCENT : 0xFF3A3F40;
		int fill = mixColor(BASE_FILL, withAlpha(accent, 230), hover * 0.72F);
		NanoVGUI.fillRounded(x1, y1, x1 + width, y1 + height, radius, fill);
		NanoVGUI.outlineRounded(x1, y1, x1 + width, y1 + height, radius,
			withAlpha(0xFFFFFFFF, 22 + Math.round(hover * 48)));
	}

	private static void drawCheckbox(Checkbox checkbox, int x1, int y1,
		int height)
	{
		float size = Math.min(11, height - 8);
		float x = x1 + 9;
		float y = y1 + (height - size) / 2F;
		boolean selected = checkbox.selected();
		if(selected)
		{
			NanoVGUI.fillRounded(x, y, x + size, y + size, 3, ACCENT);
			float cx = x + size / 2F;
			float cy = y + size / 2F;
			NanoVGUI.line(x + 2.5F, cy, cx - 0.5F, y + size - 3F, 1.6F,
				0xFFFFFFFF);
			NanoVGUI.line(cx - 0.5F, y + size - 3F, x + size - 2.5F,
				y + 2.5F, 1.6F, 0xFFFFFFFF);
		}else
			NanoVGUI.outlineRounded(x, y, x + size, y + size, 3,
				0x60FFFFFF);
	}

	private static float hover(AbstractWidget widget)
	{
		HoverAnimation animation = HOVER.computeIfAbsent(widget,
			ignored -> new HoverAnimation(20));
		return animation.update(widget.active && widget.isHoveredOrFocused());
	}

	private static int mixColor(int first, int second, float amount)
	{
		float weight = Math.max(0, Math.min(1, amount));
		float inverse = 1 - weight;
		int alpha = Math.round((first >>> 24) * inverse
			+ (second >>> 24) * weight);
		int red = Math.round((first >> 16 & 0xFF) * inverse
			+ (second >> 16 & 0xFF) * weight);
		int green = Math.round((first >> 8 & 0xFF) * inverse
			+ (second >> 8 & 0xFF) * weight);
		int blue = Math.round((first & 0xFF) * inverse
			+ (second & 0xFF) * weight);
		return alpha << 24 | red << 16 | green << 8 | blue;
	}

	private static int withAlpha(int color, int alpha)
	{
		return Math.max(0, Math.min(255, alpha)) << 24 | color & 0xFFFFFF;
	}

	private record DeferredControl(AbstractWidget widget, float[] scissor)
	{
	}
}

package net.wurstclient.clickgui2;

import java.awt.Color;
import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.util.Mth;
import net.wurstclient.Feature;
import net.wurstclient.WurstClient;
import net.wurstclient.clickgui2.theme.FlatTheme;
import net.wurstclient.settings.BlockListSetting;
import net.wurstclient.settings.BlockSetting;
import net.wurstclient.settings.BookOffersSetting;
import net.wurstclient.settings.CheckboxSetting;
import net.wurstclient.settings.ColorSetting;
import net.wurstclient.settings.EnumSetting;
import net.wurstclient.settings.FileSetting;
import net.wurstclient.settings.ItemListSetting;
import net.wurstclient.settings.Setting;
import net.wurstclient.settings.SliderSetting;
import net.wurstclient.settings.TextFieldSetting;
import net.wurstclient.util.ColorUtils;
import org.lwjgl.glfw.GLFW;

/** Rise ValueComponent adapter backed by Wurst Setting instances. */
final class NavigatorSettingsPanel extends Window
{
	private static final WurstClient WURST = WurstClient.INSTANCE;
	private static final Font FONT = WurstClient.MC.font;
	private static final float TEXT_SCALE = 0.76F;
	private static final int DEFAULT_ROW_HEIGHT = 14;
	private static final int STRING_ROW_HEIGHT = 28;
	private static final int COLOR_ROW_HEIGHT = 110;
	private static final int COLOR_PICKER_WIDTH = 105;
	private static final int COLOR_PICKER_HEIGHT = 66;

	private final List<Setting> settings;
	private final Map<CheckboxSetting, RiseAnimation> checkboxAnimations =
		new IdentityHashMap<>();
	private List<Setting> renderedSettings = List.of();
	private int bodyX;
	private int bodyY;
	private int bodyWidth;
	private int bodyHeight;
	private SliderSetting draggedSlider;
	private Setting activeInput;
	private String textDraft = "";
	private int textCursor;
	private int selectionAnchor;
	private ColorSetting expandedColor;
	private boolean colorPickerDown;
	private boolean huePickerDown;
	private float colorHue;
	private float colorSaturation;
	private float colorBrightness;

	NavigatorSettingsPanel(Feature feature)
	{
		super("");
		settings = new ArrayList<>(feature.getSettings().values());
		setMinimizable(false);
		setPinnable(false);
		setClosable(false);
		refreshSettings(SettingTreeLayout.flatten(settings));
	}

	int getPreferredContentHeight(int width)
	{
		prepareForRender();
		int height = 0;
		for(Setting setting : renderedSettings)
			height += rowHeight(setting);
		return Math.max(0, height - 1);
	}

	void layoutInline(int x, int y, int width, int visibleHeight)
	{
		bodyX = x;
		bodyY = y;
		bodyWidth = width;
		bodyHeight = Math.max(0, visibleHeight);
		setX(x);
		setY(y - 13);
		layoutFallbackComponents();
	}

	void renderContent(GuiGraphics graphics, int mouseX, int mouseY,
		float partialTicks, FlatTheme theme, boolean clip)
	{
		prepareForRender();
		if(draggedSlider != null)
		{
			if(WURST.getGui().isLeftMouseButtonPressed())
				updateSlider(draggedSlider, mouseX - bodyX);
			else
				draggedSlider = null;
		}
		if(expandedColor != null && (colorPickerDown || huePickerDown))
			updateColorFromMouse(expandedColor, mouseX - bodyX,
				mouseY - bodyY);

		if(clip)
			graphics.enableScissor(bodyX, bodyY, bodyX + bodyWidth,
				bodyY + bodyHeight);
		graphics.pose().pushPose();
		graphics.pose().translate(bodyX, bodyY, 0);
		int y = 1;
		for(Setting setting : renderedSettings)
		{
			renderSetting(graphics, setting, y, mouseX - bodyX,
				mouseY - bodyY);
			y += rowHeight(setting);
		}
		graphics.pose().popPose();
		if(clip)
			graphics.disableScissor();
	}

	boolean mouseClicked(double mouseX, double mouseY, int button)
	{
		prepareForRender();
		if(!isInside(mouseX, mouseY))
		{
			finishTextEditing();
			expandedColor = null;
			colorPickerDown = false;
			huePickerDown = false;
			return false;
		}
		double localX = mouseX - bodyX;
		double localY = mouseY - bodyY;
		int y = 1;
		for(int index = 0; index < renderedSettings.size(); index++)
		{
			Setting setting = renderedSettings.get(index);
			int height = rowHeight(setting);
			if(localY >= y && localY < y + height)
			{
				if(activeInput != null && activeInput != setting)
					finishTextEditing();
				handleSettingClick(setting, index, localX, localY - y,
					button);
				return true;
			}
			y += height;
		}
		return true;
	}

	boolean charTyped(char codePoint)
	{
		if(activeInput == null || Character.isISOControl(codePoint)
			|| textDraft.length() >= 256)
			return false;
		if(activeInput instanceof SliderSetting
			&& "1234567890.".indexOf(codePoint) < 0)
			return true;
		replaceSelection(String.valueOf(codePoint));
		writeLiveTextValue();
		return true;
	}

	boolean keyPressed(int keyCode, int modifiers)
	{
		if(activeInput == null)
			return false;
		boolean control = (modifiers & GLFW.GLFW_MOD_CONTROL) != 0;
		boolean shift = (modifiers & GLFW.GLFW_MOD_SHIFT) != 0;
		if(control && keyCode == GLFW.GLFW_KEY_A)
		{
			selectionAnchor = 0;
			textCursor = textDraft.length();
			return true;
		}
		if(control && keyCode == GLFW.GLFW_KEY_C)
		{
			if(hasSelection())
				WurstClient.MC.keyboardHandler.setClipboard(selectedText());
			return true;
		}
		if(control && keyCode == GLFW.GLFW_KEY_X)
		{
			if(hasSelection())
			{
				WurstClient.MC.keyboardHandler.setClipboard(selectedText());
				replaceSelection("");
				writeLiveTextValue();
			}
			return true;
		}
		if(control && keyCode == GLFW.GLFW_KEY_V)
		{
			String clipboard = WurstClient.MC.keyboardHandler.getClipboard();
			String clean = clipboard.replaceAll("[\\p{Cntrl}]", "");
			if(activeInput instanceof SliderSetting)
				clean = clean.replaceAll("[^0-9.]", "");
			int room = Math.max(0, 256 - (textDraft.length()
				- selectionLength()));
			replaceSelection(clean.substring(0, Math.min(clean.length(), room)));
			writeLiveTextValue();
			return true;
		}
		if(keyCode == GLFW.GLFW_KEY_BACKSPACE)
		{
			if(hasSelection())
				replaceSelection("");
			else if(textCursor > 0)
			{
				textDraft = textDraft.substring(0, textCursor - 1)
					+ textDraft.substring(textCursor);
				textCursor--;
				selectionAnchor = textCursor;
			}
			writeLiveTextValue();
			return true;
		}
		if(keyCode == GLFW.GLFW_KEY_DELETE)
		{
			if(hasSelection())
				replaceSelection("");
			else if(textCursor < textDraft.length())
				textDraft = textDraft.substring(0, textCursor)
					+ textDraft.substring(textCursor + 1);
			writeLiveTextValue();
			return true;
		}
		if(keyCode == GLFW.GLFW_KEY_LEFT)
		{
			moveCursor(Math.max(0, textCursor - 1), shift);
			return true;
		}
		if(keyCode == GLFW.GLFW_KEY_RIGHT)
		{
			moveCursor(Math.min(textDraft.length(), textCursor + 1), shift);
			return true;
		}
		if(keyCode == GLFW.GLFW_KEY_HOME)
		{
			moveCursor(0, shift);
			return true;
		}
		if(keyCode == GLFW.GLFW_KEY_END)
		{
			moveCursor(textDraft.length(), shift);
			return true;
		}
		if(keyCode == GLFW.GLFW_KEY_ENTER
			|| keyCode == GLFW.GLFW_KEY_KP_ENTER)
		{
			finishTextEditing();
			return true;
		}
		if(keyCode == GLFW.GLFW_KEY_ESCAPE)
		{
			finishTextEditing();
			return true;
		}
		return true;
	}

	boolean hasActiveText()
	{
		return activeInput != null;
	}

	void mouseReleased(int button)
	{
		if(button == GLFW.GLFW_MOUSE_BUTTON_LEFT)
		{
			draggedSlider = null;
			colorPickerDown = false;
			huePickerDown = false;
		}
	}

	void dispose()
	{
		closePopups();
		draggedSlider = null;
		activeInput = null;
		textDraft = "";
		expandedColor = null;
		clear();
	}

	void closePopups()
	{
		WURST.getGui().closePopupsOwnedBy(this);
	}

	@Override
	public void prepareForRender()
	{
		List<Setting> visibleSettings = SettingTreeLayout.flatten(settings);
		if(!renderedSettings.equals(visibleSettings))
			refreshSettings(visibleSettings);
	}

	private void refreshSettings(List<Setting> visibleSettings)
	{
		closePopups();
		clear();
		for(Setting setting : visibleSettings)
		{
			Component component = setting.getComponent();
			component.setIndent(setting.getDepth() * 8);
			add(component);
		}
		renderedSettings = visibleSettings;
		layoutFallbackComponents();
	}

	private void renderSetting(GuiGraphics graphics, Setting setting, int y,
		int mouseX, int mouseY)
	{
		int indent = setting.getDepth() * 8;
		int left = 1 + indent;
		int right = bodyWidth - 2;
		int height = rowHeight(setting);
		if(setting instanceof SliderSetting slider)
		{
			drawText(graphics, setting.getName(), left, y + 2,
				RiseColors.SECONDARY_TEXT.argb());
			int trackLeft = sliderTrackLeft(slider);
			int trackRight = Math.min(right - 25, trackLeft + 100);
			String value = setting == activeInput ? draftWithCursor()
				: rawSliderValue(slider);
			drawText(graphics, value, trackRight + 5, y + 2,
				RiseColors.TRINARY_TEXT.argb());
			int trackY = y + 5;
			FlatUiRenderer.fill(graphics, trackLeft, trackY, trackRight,
				trackY + 2, 1,
				0x80121419);
			int progressX = trackLeft + (int)Math.round(
				(trackRight - trackLeft) * slider.getPercentage());
			RiseShadow.draw(graphics, progressX - 2, trackY - 2,
				progressX + 3, trackY + 3, 3, 6,
				accentColor() & 0xFFFFFF | 0x3C000000);
			FlatUiRenderer.fill(graphics, progressX - 2, trackY - 2,
				progressX + 3, trackY + 3, 3, accentColor());
			return;
		}

		if(setting instanceof CheckboxSetting checkbox)
		{
			drawText(graphics, setting.getName(), left, y + 3,
				RiseColors.SECONDARY_TEXT.argb());
			int nameWidth = Math.round(
				RiseFont.width(FONT, setting.getName()) * TEXT_SCALE);
			int dotX = Math.min(right - 5, left + nameWidth + 3);
			FlatUiRenderer.fill(graphics, dotX, y + 4, dotX + 5, y + 9,
				3, RiseColors.BACKGROUND.argb());
			RiseAnimation animation = checkboxAnimations.computeIfAbsent(checkbox,
				ignored -> new RiseAnimation(RiseAnimation.Easing.LINEAR, 100));
			float scale = animation.run(checkbox.isChecked() ? 5 : 0);
			if(scale > 0.01F)
				FlatUiRenderer.fill(graphics, dotX + 4 - scale / 2F,
					y + 6.5F - scale / 2F, dotX + 4 + scale / 2F,
					y + 6.5F + scale / 2F, scale / 2F, accentColor());
			renderExpandArrow(graphics, setting, right, y);
			return;
		}

		if(setting instanceof TextFieldSetting)
		{
			drawText(graphics, setting.getName(), left, y + 2,
				RiseColors.SECONDARY_TEXT.argb());
			String text = setting == activeInput ? draftWithCursor()
				: ((TextFieldSetting)setting).getValue();
			drawText(graphics, text, left, y + 16,
				RiseColors.TEXT.argb());
			return;
		}

		if(setting instanceof ColorSetting color)
		{
			renderColorSetting(graphics, color, y, left, right);
			return;
		}

		if(setting instanceof EnumSetting<?> enumSetting)
		{
			String prefix = setting.getName() + ":";
			drawText(graphics, prefix, left, y + 3,
				RiseColors.SECONDARY_TEXT.argb());
			int valueX = left + Math.round(
				RiseFont.width(FONT, prefix) * TEXT_SCALE) + 2;
			drawText(graphics, enumSetting.getSelected().toString(), valueX,
				y + 3, RiseColors.SECONDARY_TEXT.argb());
			renderExpandArrow(graphics, setting, right, y);
			return;
		}

		String value = setting == activeInput ? draftWithCursor()
			: valueText(setting);
		drawText(graphics, setting.getName() + (value.isEmpty() ? "" : ":"),
			left, y + 3, RiseColors.SECONDARY_TEXT.argb());
		if(!value.isEmpty())
			drawTextRight(graphics, value, right - (setting.hasChildren() ? 10 : 0),
				y + 3, RiseColors.TRINARY_TEXT.argb());
		renderExpandArrow(graphics, setting, right, y);
	}

	private void handleSettingClick(Setting setting, int index, double localX,
		double rowY, int button)
	{
		if(setting.hasChildren() && (button == 1
			|| localX >= bodyWidth - 14))
		{
			setting.setExpanded(!setting.isExpanded());
			return;
		}
		if(setting instanceof CheckboxSetting checkbox)
		{
			if(button == 0 || button == 1)
				checkbox.setChecked(!checkbox.isChecked());
			return;
		}
		if(setting instanceof SliderSetting slider)
		{
			int trackLeft = sliderTrackLeft(slider);
			int trackRight = Math.min(bodyWidth - 27, trackLeft + 100);
			if(button == 0 && localX >= trackRight + 3)
			{
				startTextEditing(slider, rawSliderValue(slider));
				placeCursor(localX - trackRight - 5);
			}
			else if(button == 0 && localX >= trackLeft - 5
				&& localX <= trackRight + 5)
			{
				draggedSlider = slider;
				updateSlider(slider, localX);
			}
			return;
		}
		if(setting instanceof EnumSetting<?> enumSetting)
		{
			if(button == 0)
				enumSetting.selectNext();
			else if(button == 1)
				enumSetting.selectPrev();
			return;
		}
		if(setting instanceof ColorSetting color)
		{
			if(expandedColor == color)
			{
				int rowTop = rowTopOf(color);
				int pickerX = colorPickerX(color);
				if(localX >= pickerX + 31
					&& localX < pickerX + COLOR_PICKER_WIDTH
					&& rowY >= 82 && rowY < 95)
				{
					Color value = color.getColor();
					WurstClient.MC.keyboardHandler.setClipboard(value.getRed()
						+ ", " + value.getBlue() + ", " + value.getGreen());
				}else if(localX >= pickerX + 31
					&& localX < pickerX + COLOR_PICKER_WIDTH
					&& rowY >= 95 && rowY < 108)
					WurstClient.MC.keyboardHandler.setClipboard(
						ColorUtils.toHex(color.getColor()));
				else if(localX >= pickerX
					&& localX < pickerX + COLOR_PICKER_WIDTH
					&& rowY < COLOR_PICKER_HEIGHT)
				{
					colorPickerDown = true;
					updateColorFromMouse(color, localX, rowTop + rowY);
				}else if(localX >= pickerX + 9
					&& localX < pickerX + COLOR_PICKER_WIDTH - 8
					&& rowY >= 69 && rowY < 78)
				{
					huePickerDown = true;
					updateColorFromMouse(color, localX, rowTop + rowY);
				}else
					expandedColor = null;
			}else
			{
				expandedColor = color;
				syncColorPointers(color);
			}
			return;
		}
		if(setting instanceof TextFieldSetting text)
		{
			if(button == 0 && rowY >= 12)
			{
				startTextEditing(text, text.getValue());
				placeCursor(localX - 1 - text.getDepth() * 8);
			}
			return;
		}

		Component fallback = getChild(index);
		fallback.handleMouseClick(fallback.getX() + fallback.getWidth() - 1,
			fallback.getY() + fallback.getHeight() / 2D, button);
	}

	private void updateSlider(SliderSetting slider, double localX)
	{
		int left = sliderTrackLeft(slider);
		int right = Math.min(bodyWidth - 27, left + 100);
		double percentage = Mth.clamp((localX - left)
			/ Math.max(1, right - left), 0, 1);
		slider.setValue(slider.getMinimum() + slider.getRange() * percentage);
	}

	private void finishTextEditing()
	{
		if(activeInput instanceof TextFieldSetting text)
			text.setValue(textDraft);
		else if(activeInput instanceof SliderSetting slider)
		{
			try
			{
				slider.setValue(textDraft.isBlank() ? slider.getDefaultValue()
					: Double.parseDouble(textDraft));
			}catch(NumberFormatException ignored)
			{
			}
		}
		activeInput = null;
		textDraft = "";
	}

	private void startTextEditing(Setting setting, String value)
	{
		activeInput = setting;
		textDraft = value;
		textCursor = value.length();
		selectionAnchor = textCursor;
	}

	private static String rawSliderValue(SliderSetting slider)
	{
		String value = Double.toString(slider.getValue());
		return value.endsWith(".0")
			? value.substring(0, value.length() - 2) : value;
	}

	private void placeCursor(double renderedX)
	{
		int best = 0;
		double bestDistance = Double.MAX_VALUE;
		for(int index = 0; index <= textDraft.length(); index++)
		{
			double x = RiseFont.width(FONT, textDraft.substring(0, index))
				* TEXT_SCALE;
			double distance = Math.abs(renderedX - x);
			if(distance >= bestDistance)
				break;
			bestDistance = distance;
			best = index;
		}
		textCursor = best;
		selectionAnchor = best;
	}

	private void writeLiveTextValue()
	{
		if(activeInput instanceof TextFieldSetting text)
			text.setValue(textDraft);
	}

	private void replaceSelection(String replacement)
	{
		int start = Math.min(textCursor, selectionAnchor);
		int end = Math.max(textCursor, selectionAnchor);
		textDraft = textDraft.substring(0, start) + replacement
			+ textDraft.substring(end);
		textCursor = start + replacement.length();
		selectionAnchor = textCursor;
	}

	private void moveCursor(int cursor, boolean selecting)
	{
		textCursor = cursor;
		if(!selecting)
			selectionAnchor = cursor;
	}

	private boolean hasSelection()
	{
		return textCursor != selectionAnchor;
	}

	private int selectionLength()
	{
		return Math.abs(textCursor - selectionAnchor);
	}

	private String selectedText()
	{
		return textDraft.substring(Math.min(textCursor, selectionAnchor),
			Math.max(textCursor, selectionAnchor));
	}

	private String draftWithCursor()
	{
		if(System.currentTimeMillis() / 500 % 2 != 0)
			return textDraft;
		return textDraft.substring(0, textCursor) + "|"
			+ textDraft.substring(textCursor);
	}

	private void renderColorSetting(GuiGraphics graphics, ColorSetting setting,
		int y, int left, int right)
	{
		drawText(graphics, setting.getName(), left, y + 3,
			RiseColors.SECONDARY_TEXT.argb());
		int nameWidth = Math.round(
			RiseFont.width(FONT, setting.getName()) * TEXT_SCALE);
		int swatchX = Math.min(right - 17, left + nameWidth + 5);
		FlatUiRenderer.fill(graphics, swatchX, y + 3, swatchX + 15, y + 10,
			3, setting.getColorI());
		if(expandedColor != setting)
			return;

		int pickerX = colorPickerX(setting);
		int pickerBottom = y + COLOR_PICKER_HEIGHT;
		RiseShadow.draw(graphics, pickerX, y,
			pickerX + COLOR_PICKER_WIDTH, y + 105, 4, 10, 0x28000000);
		FlatUiRenderer.fill(graphics, pickerX - 1, y - 1,
			pickerX + COLOR_PICKER_WIDTH + 1, y + 106, 5,
			RiseColors.SECONDARY.argb());
		FlatUiRenderer.fill(graphics, pickerX, y,
			pickerX + COLOR_PICKER_WIDTH, y + 105, 4,
			RiseColors.BACKGROUND.argb());

		int hueColor = 0xFF000000 | Color.HSBtoRGB(colorHue, 1, 1)
			& 0xFFFFFF;
		for(int x = 0; x < COLOR_PICKER_WIDTH; x++)
		{
			float amount = x / (float)(COLOR_PICKER_WIDTH - 1);
			int top = mixRgb(0xFFFFFFFF, hueColor, amount);
			graphics.fillGradient(pickerX + x, y, pickerX + x + 1,
				pickerBottom, top, 0xFF000000);
		}

		int hueX = pickerX + 9;
		int hueY = y + 70;
		int hueWidth = COLOR_PICKER_WIDTH - 17;
		for(int x = 0; x < hueWidth; x++)
		{
			int color = 0xFF000000 | Color.HSBtoRGB(
				x / (float)(hueWidth - 1), 1, 1) & 0xFFFFFF;
			graphics.fill(hueX + x, hueY, hueX + x + 1, hueY + 5, color);
		}

		int huePointer = hueX + Math.round(colorHue * (hueWidth - 1));
		FlatUiRenderer.fill(graphics, huePointer - 3, hueY - 1,
			huePointer + 4, hueY + 6, 3, hueColor);
		FlatUiRenderer.outline(graphics, huePointer - 3, hueY - 1,
			huePointer + 4, hueY + 6, 3, 0xFF000000);

		int pointerX = pickerX
			+ Math.round(colorSaturation * (COLOR_PICKER_WIDTH - 1));
		int pointerY = y
			+ Math.round((1 - colorBrightness) * (COLOR_PICKER_HEIGHT - 1));
		FlatUiRenderer.fill(graphics, pointerX - 4, pointerY - 4,
			pointerX + 4, pointerY + 4, 4, 0xFFFFFFFF);
		FlatUiRenderer.fill(graphics, pointerX - 3, pointerY - 3,
			pointerX + 3, pointerY + 3, 3, setting.getColorI());

		Color color = setting.getColor();
		RiseShadow.draw(graphics, pickerX + 9, y + 84,
			pickerX + 24, y + 100, 4, 8, 0x28000000);
		FlatUiRenderer.fill(graphics, pickerX + 9, y + 84,
			pickerX + 24, y + 100, 4, setting.getColorI());
		drawText(graphics,
			color.getRed() + "  " + color.getGreen() + "  " + color.getBlue(),
			pickerX + 31, y + 84, RiseColors.SECONDARY_TEXT.argb());
		drawText(graphics, ColorUtils.toHex(color), pickerX + 31, y + 96,
			RiseColors.TRINARY_TEXT.argb());
	}

	private void syncColorPointers(ColorSetting setting)
	{
		Color color = setting.getColor();
		float[] hsb = Color.RGBtoHSB(color.getRed(), color.getGreen(),
			color.getBlue(), null);
		colorHue = hsb[0];
		colorSaturation = hsb[1];
		colorBrightness = hsb[2];
	}

	private void updateColorFromMouse(ColorSetting setting, double localX,
		double localY)
	{
		int rowTop = rowTopOf(setting);
		int pickerX = colorPickerX(setting);
		if(colorPickerDown)
		{
			colorSaturation = (float)Mth.clamp((localX - pickerX)
				/ (COLOR_PICKER_WIDTH - 1D), 0, 1);
			colorBrightness = 1 - (float)Mth.clamp((localY - rowTop)
				/ (COLOR_PICKER_HEIGHT - 1D), 0, 1);
		}else if(huePickerDown)
		{
			int hueX = pickerX + 9;
			int hueWidth = COLOR_PICKER_WIDTH - 17;
			colorHue = (float)Mth.clamp((localX - hueX)
				/ (hueWidth - 1D), 0, 1);
		}
		int rgb = Color.HSBtoRGB(colorHue, colorSaturation, colorBrightness);
		Color old = setting.getColor();
		setting.setColor(new Color(rgb >> 16 & 0xFF, rgb >> 8 & 0xFF,
			rgb & 0xFF, old.getAlpha()));
	}

	private int rowTopOf(Setting target)
	{
		int y = 1;
		for(Setting setting : renderedSettings)
		{
			if(setting == target)
				return y;
			y += rowHeight(setting);
		}
		return y;
	}

	private int colorPickerX(ColorSetting setting)
	{
		int left = 1 + setting.getDepth() * 8;
		int nameWidth = Math.round(
			RiseFont.width(FONT, setting.getName()) * TEXT_SCALE);
		return Math.max(left + 22,
			Math.min(bodyWidth - COLOR_PICKER_WIDTH - 2,
				left + nameWidth + 22));
	}

	private int sliderTrackLeft(SliderSetting slider)
	{
		int left = 1 + slider.getDepth() * 8;
		int nameWidth = Math.round(
			RiseFont.width(FONT, slider.getName()) * TEXT_SCALE);
		return Math.min(bodyWidth - 54, left + nameWidth + 7);
	}

	private static int mixRgb(int first, int second, float amount)
	{
		return RiseColors.mix(first, second, amount) | 0xFF000000;
	}

	private String valueText(Setting setting)
	{
		if(setting instanceof EnumSetting<?> value)
			return value.getSelected().toString();
		if(setting instanceof ColorSetting value)
			return ColorUtils.toHex(value.getColor());
		if(setting instanceof TextFieldSetting value)
			return trim(value.getValue(), 92);
		if(setting instanceof FileSetting value)
			return value.getSelectedFileName();
		if(setting instanceof BlockSetting value)
			return value.getShortBlockName();
		if(setting instanceof ItemListSetting value)
			return value.getItemNames().size() + " items";
		if(setting instanceof BlockListSetting value)
			return value.getBlockNames().size() + " blocks";
		if(setting instanceof BookOffersSetting value)
			return value.getOffers().size() + " offers";
		return "Edit";
	}

	private void renderExpandArrow(GuiGraphics graphics, Setting setting,
		int right, int y)
	{
		if(setting.hasChildren())
			GuiIcon.CHEVRON.drawRotated(graphics, right - 8, y + 4, 7,
				RiseColors.TRINARY_TEXT.argb(), setting.isExpanded() ? 0 : -90);
	}

	private int rowHeight(Setting setting)
	{
		if(setting instanceof TextFieldSetting)
			return STRING_ROW_HEIGHT;
		if(setting instanceof ColorSetting color)
			return color == expandedColor ? COLOR_ROW_HEIGHT : 15;
		return DEFAULT_ROW_HEIGHT;
	}

	private void layoutFallbackComponents()
	{
		int y = 1;
		for(int index = 0; index < countChildren(); index++)
		{
			Setting setting = renderedSettings.get(index);
			Component component = getChild(index);
			int indent = setting.getDepth() * 8;
			component.setX(1 + indent);
			component.setY(y);
			component.setWidth(Math.max(1, bodyWidth - indent - 3));
			component.setHeight(rowHeight(setting));
			y += rowHeight(setting);
		}
	}

	private void drawText(GuiGraphics graphics, String text, int x, int y,
		int color)
	{
		graphics.pose().pushPose();
		graphics.pose().translate(x, y, 0);
		graphics.pose().scale(TEXT_SCALE, TEXT_SCALE, 1);
		RiseFont.draw(graphics, FONT, trim(text,
			Math.round((bodyWidth - x - 8) / TEXT_SCALE)), 0, 0, color);
		graphics.pose().popPose();
	}

	private void drawTextRight(GuiGraphics graphics, String text, int right,
		int y, int color)
	{
		int width = Math.round(RiseFont.width(FONT, text) * TEXT_SCALE);
		drawText(graphics, text, right - width, y, color);
	}

	private static String trim(String text, int width)
	{
		return RiseFont.trim(FONT, text, Math.max(1, width));
	}

	private boolean isInside(double mouseX, double mouseY)
	{
		return mouseX >= bodyX && mouseX < bodyX + bodyWidth
			&& mouseY >= bodyY && mouseY < bodyY + bodyHeight;
	}

	private int accentColor()
	{
		return WURST.getGui().getTheme().accent(1);
	}
}

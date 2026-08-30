package net.wurstclient.clickgui2.epsilon;

import net.wurstclient.clickgui2.supersoft.EpsilonMd3Theme;

/**
 * Epsilon 26.1.2 DropdownTheme 的直接移植。
 *
 * <p>常量与颜色函数一一对应原版 {@code com.github.epsilon.gui.dropdown.DropdownTheme},
 * 底层配色使用 Wurst 的 {@link EpsilonMd3Theme}（MD3 TonalSpot 暗色）。</p>
 */
public final class EpsilonDropdownTheme
{
	public static final float PANEL_WIDTH = 130F;
	public static final float PANEL_HEADER_HEIGHT = 28F;
	public static final float PANEL_RADIUS = 10F;
	public static final float PANEL_GAP = 14F;
	public static final float PANEL_MARGIN_X = 20F;
	public static final float PANEL_MARGIN_Y = 20F;
	public static final float PANEL_SHADOW_BLUR = 20F;
	public static final int PANEL_SHADOW_ALPHA = 96;

	public static final float GROUP_HEADER_HEIGHT = 18F;
	public static final float GROUP_INSET = 4F;
	public static final float GROUP_HEADER_TEXT_SCALE = 0.52F;
	public static final float GROUP_COUNT_CHIP_HEIGHT = 11F;
	public static final float GROUP_COUNT_CHIP_PADDING = 6F;
	public static final float GROUP_COUNT_TEXT_SCALE = 0.42F;

	public static final float MODULE_HEIGHT = 19F;
	public static final float MODULE_PADDING_X = 7F;
	public static final float MODULE_TEXT_SCALE = 0.62F;
	public static final float MODULE_ADDON_GAP = 4F;
	public static final float MODULE_ADDON_INFO_HEIGHT = 15F;
	public static final float MODULE_ADDON_INFO_TEXT_SCALE = 0.5F;

	public static final float SETTING_PADDING_X = 6F;
	public static final float SETTING_HEIGHT = 16F;
	public static final float SETTING_TEXT_SCALE = 0.54F;
	public static final float SETTING_GAP = 3F;
	public static final float SETTING_INDENT = 5F;

	public static final float KEYBIND_WIDTH = 34F;
	public static final float KEYBIND_HEIGHT = 14F;
	public static final float KEYBIND_RADIUS = 5F;

	public static final float BUTTON_HEIGHT = 16F;
	public static final float BUTTON_RADIUS = 5F;

	public static final float SCROLL_SPEED = 28F;
	public static final float PANEL_BOTTOM_PADDING = 8F;

	public static final int ANIM_OPEN = 200;
	public static final int ANIM_TOGGLE = 180;
	public static final int ANIM_HOVER = 120;
	public static final int ANIM_EXPAND = 220;
	public static final int ANIM_GROUP = 180;

	public static final float HEADER_TEXT_SCALE = 0.82F;
	public static final float HEADER_ICON_SCALE = 0.86F;

	private EpsilonDropdownTheme()
	{}

	public static int panelBackground()
	{
		return EpsilonMd3Theme.SURFACE_CONTAINER;
	}

	public static int panelShadow()
	{
		return EpsilonMd3Theme.withAlpha(EpsilonMd3Theme.SHADOW,
			PANEL_SHADOW_ALPHA);
	}

	public static int moduleDivider()
	{
		return EpsilonMd3Theme.withAlpha(EpsilonMd3Theme.OUTLINE, 24);
	}

	public static int moduleEnabled(float hoverProgress)
	{
		return EpsilonMd3Theme.mix(EpsilonMd3Theme.PRIMARY_CONTAINER,
			EpsilonMd3Theme.mix(EpsilonMd3Theme.PRIMARY_CONTAINER,
				EpsilonMd3Theme.PRIMARY, 0.15F), hoverProgress);
	}

	public static int moduleDisabled(float hoverProgress)
	{
		return EpsilonMd3Theme.mix(EpsilonMd3Theme.SURFACE_CONTAINER,
			EpsilonMd3Theme.SURFACE_CONTAINER_HIGH, hoverProgress);
	}

	public static int moduleTextEnabled()
	{
		return EpsilonMd3Theme.ON_PRIMARY_CONTAINER;
	}

	public static int moduleTextDisabled(float hoverProgress)
	{
		return EpsilonMd3Theme.mix(EpsilonMd3Theme.TEXT_SECONDARY,
			EpsilonMd3Theme.TEXT_PRIMARY, hoverProgress);
	}

	public static int moduleAddonInfoText()
	{
		return EpsilonMd3Theme.TEXT_MUTED;
	}

	public static int settingLabel()
	{
		return EpsilonMd3Theme.TEXT_PRIMARY;
	}

	public static int settingLabelMuted()
	{
		return EpsilonMd3Theme.TEXT_MUTED;
	}

	public static int settingSurface()
	{
		return EpsilonMd3Theme.withAlpha(
			EpsilonMd3Theme.SURFACE_CONTAINER_LOW, 160);
	}

	public static int sliderTrack()
	{
		return EpsilonMd3Theme.SURFACE_CONTAINER_HIGHEST;
	}

	public static int sliderActive()
	{
		return EpsilonMd3Theme.PRIMARY;
	}

	public static int sliderKnob()
	{
		return EpsilonMd3Theme.PRIMARY;
	}

	public static int chipSelected()
	{
		return EpsilonMd3Theme.SECONDARY_CONTAINER;
	}

	public static int chipSelectedText()
	{
		return EpsilonMd3Theme.ON_SECONDARY_CONTAINER;
	}

	public static int chipUnselected()
	{
		return EpsilonMd3Theme.SURFACE_CONTAINER_HIGH;
	}

	public static int chipUnselectedText()
	{
		return EpsilonMd3Theme.TEXT_SECONDARY;
	}

	public static int keybindSurface(boolean listening)
	{
		return listening ? EpsilonMd3Theme.PRIMARY_CONTAINER
			: EpsilonMd3Theme.SURFACE_CONTAINER_HIGHEST;
	}

	public static int keybindText(boolean listening)
	{
		return listening ? EpsilonMd3Theme.ON_PRIMARY_CONTAINER
			: EpsilonMd3Theme.TEXT_PRIMARY;
	}

	public static int inputSurface(boolean focused)
	{
		return focused ? EpsilonMd3Theme.mix(
			EpsilonMd3Theme.SURFACE_CONTAINER_HIGH,
			EpsilonMd3Theme.PRIMARY_CONTAINER, 0.3F)
			: EpsilonMd3Theme.SURFACE_CONTAINER_HIGH;
	}

	public static int inputText()
	{
		return EpsilonMd3Theme.TEXT_PRIMARY;
	}

	public static int inputIndicator(boolean focused)
	{
		return focused ? EpsilonMd3Theme.PRIMARY
			: EpsilonMd3Theme.withAlpha(EpsilonMd3Theme.OUTLINE, 96);
	}

	public static int buttonSurface(float hoverProgress)
	{
		return EpsilonMd3Theme.mix(EpsilonMd3Theme.SECONDARY_CONTAINER,
			EpsilonMd3Theme.mix(EpsilonMd3Theme.SECONDARY_CONTAINER,
				EpsilonMd3Theme.SECONDARY, 0.12F), hoverProgress);
	}

	public static int buttonText()
	{
		return EpsilonMd3Theme.ON_SECONDARY_CONTAINER;
	}

	public static int expandArrow(float toggleProgress)
	{
		return EpsilonMd3Theme.mix(EpsilonMd3Theme.TEXT_MUTED,
			EpsilonMd3Theme.ON_PRIMARY_CONTAINER, toggleProgress);
	}

	public static int scrollbar()
	{
		return EpsilonMd3Theme.withAlpha(EpsilonMd3Theme.OUTLINE, 64);
	}

	public static int scrollbar(float hoverProgress)
	{
		return EpsilonMd3Theme.mix(scrollbar(),
			EpsilonMd3Theme.withAlpha(EpsilonMd3Theme.PRIMARY, 190),
			hoverProgress);
	}

	public static int scrim()
	{
		return 0x32000000;
	}

	public static int groupBackground()
	{
		return EpsilonMd3Theme.withAlpha(
			EpsilonMd3Theme.SURFACE_CONTAINER_LOW, 160);
	}

	public static int groupBackgroundHover()
	{
		return EpsilonMd3Theme.SURFACE_CONTAINER;
	}

	public static int groupText()
	{
		return EpsilonMd3Theme.TEXT_PRIMARY;
	}

	public static int groupCountChip()
	{
		return EpsilonMd3Theme.withAlpha(EpsilonMd3Theme.SECONDARY_CONTAINER,
			210);
	}

	public static int groupCountText()
	{
		return EpsilonMd3Theme.ON_SECONDARY_CONTAINER;
	}

	public static int groupChevron(float hoverProgress)
	{
		return EpsilonMd3Theme.mix(EpsilonMd3Theme.TEXT_MUTED,
			EpsilonMd3Theme.PRIMARY, hoverProgress);
	}

	public static int groupDivider()
	{
		return EpsilonMd3Theme.withAlpha(EpsilonMd3Theme.OUTLINE, 48);
	}
}

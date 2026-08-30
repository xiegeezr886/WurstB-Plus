package net.wurstclient.clickgui2.supersoft;

/**
 * SuperSoft ClickGUI 语义配色，映射到 {@link EpsilonMd3Theme}（MD3 TonalSpot
 * 暗色）。保持常量名不变，仅改配色来源，不动动画与排版。
 */
public final class SuperSoftTheme
{
	public static final int BACKDROP = 0x80000000;
	public static final int WINDOW = EpsilonMd3Theme.SURFACE_CONTAINER_HIGH;
	public static final int HEADER = EpsilonMd3Theme.SURFACE_CONTAINER_LOW;
	public static final int ROW = EpsilonMd3Theme.SURFACE_CONTAINER_HIGH;
	public static final int SETTING = EpsilonMd3Theme.SURFACE_CONTAINER;
	public static final int SETTING_HOVER =
		EpsilonMd3Theme.SURFACE_CONTAINER_HIGHEST;
	public static final int MODULE_HOVER =
		EpsilonMd3Theme.PRIMARY_CONTAINER;
	public static final int ACCENT = EpsilonMd3Theme.PRIMARY;
	public static final int TEXT = EpsilonMd3Theme.TEXT_PRIMARY;
	public static final int TEXT_SECONDARY = EpsilonMd3Theme.TEXT_SECONDARY;
	public static final int MUTED = EpsilonMd3Theme.TEXT_MUTED;
	public static final int BORDER = EpsilonMd3Theme.OUTLINE_SOFT;
	public static final int SHADOW = EpsilonMd3Theme.SHADOW;

	private SuperSoftTheme()
	{}

	public static int mix(int from, int to, float progress)
	{
		return EpsilonMd3Theme.mix(from, to, progress);
	}
}

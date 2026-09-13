package net.wurstclient.clickgui2;

/**
 * ClickGUI 布局。v1.6 默认 Epsilon 下拉；SuperSoft 为卡片窗口；Vape 为侧栏分类框。
 */
public enum ClickGuiStyle
{
	EPSILON("Epsilon"),
	SUPERSOFT("SuperSoft"),
	VAPE("Vape");

	private final String displayName;

	ClickGuiStyle(String displayName)
	{
		this.displayName = displayName;
	}

	public String displayName()
	{
		return displayName;
	}

	@Override
	public String toString()
	{
		return displayName;
	}

	public ClickGuiStyle next()
	{
		ClickGuiStyle[] values = values();
		return values[(ordinal() + 1) % values.length];
	}

	public static ClickGuiStyle fromString(String raw)
	{
		if(raw == null || raw.isBlank())
			return EPSILON;
		for(ClickGuiStyle style : values())
			if(style.name().equalsIgnoreCase(raw)
				|| style.displayName.equalsIgnoreCase(raw.trim()))
				return style;
		if(raw.equalsIgnoreCase("true"))
			return VAPE;
		return EPSILON;
	}
}

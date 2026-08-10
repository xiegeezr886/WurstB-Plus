package net.wurstclient.hud2.elements;

import net.wurstclient.hud2.HudLayout.HudElementConfig;

abstract class SoarTextHudElement extends TextHudElement
{
	private final String horizontalAlignment;
	private final int horizontalOffset;
	private final int verticalOffset;

	protected SoarTextHudElement(String id, String name,
		String horizontalAlignment, int horizontalOffset, int verticalOffset)
	{
		super(id, name);
		this.horizontalAlignment = horizontalAlignment;
		this.horizontalOffset = horizontalOffset;
		this.verticalOffset = verticalOffset;
	}

	@Override
	public HudElementConfig getDefaultLayout()
	{
		HudElementConfig config = new HudElementConfig(horizontalAlignment,
			HudElementConfig.VERTICAL_TOP, horizontalOffset, verticalOffset);
		config.setEnabled(false);
		return config;
	}
}

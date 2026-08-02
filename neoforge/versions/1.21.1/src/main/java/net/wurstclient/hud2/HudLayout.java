package net.wurstclient.hud2;

import java.util.LinkedHashMap;
import java.util.Map;

public final class HudLayout
{
	private final Map<String, HudElementConfig> elements = new LinkedHashMap<>();

	public static final class HudElementConfig
	{
		public static final String HORIZONTAL_LEFT = "Left";
		public static final String HORIZONTAL_RIGHT = "Right";
		public static final String VERTICAL_TOP = "Top";
		public static final String VERTICAL_BOTTOM = "Bottom";

		private boolean enabled = true;
		private String horizontalAlignment = HORIZONTAL_LEFT;
		private String verticalAlignment = VERTICAL_TOP;
		private int horizontalOffset;
		private int verticalOffset;

		public HudElementConfig() {}

		public HudElementConfig(String horizontalAlignment,
			String verticalAlignment, int horizontalOffset, int verticalOffset)
		{
			this.horizontalAlignment = horizontalAlignment;
			this.verticalAlignment = verticalAlignment;
			this.horizontalOffset = horizontalOffset;
			this.verticalOffset = verticalOffset;
		}

		public boolean isEnabled()
		{
			return enabled;
		}

		public void setEnabled(boolean enabled)
		{
			this.enabled = enabled;
		}

		public String getHorizontalAlignment()
		{
			return horizontalAlignment;
		}

		public void setHorizontalAlignment(String horizontalAlignment)
		{
			this.horizontalAlignment = horizontalAlignment;
		}

		public String getVerticalAlignment()
		{
			return verticalAlignment;
		}

		public void setVerticalAlignment(String verticalAlignment)
		{
			this.verticalAlignment = verticalAlignment;
		}

		public int getHorizontalOffset()
		{
			return horizontalOffset;
		}

		public void setHorizontalOffset(int horizontalOffset)
		{
			this.horizontalOffset = horizontalOffset;
		}

		public int getVerticalOffset()
		{
			return verticalOffset;
		}

		public void setVerticalOffset(int verticalOffset)
		{
			this.verticalOffset = verticalOffset;
		}
	}

	public Map<String, HudElementConfig> getElements()
	{
		return elements;
	}

	public void addElement(HudElement element)
	{
		if(elements.containsKey(element.getId()))
			return;
		HudElementConfig defaults = element.getDefaultLayout();
		HudElementConfig config = new HudElementConfig(
			defaults.getHorizontalAlignment(),
			defaults.getVerticalAlignment(),
			defaults.getHorizontalOffset(),
			defaults.getVerticalOffset());
		config.setEnabled(defaults.isEnabled());
		elements.put(element.getId(), config);
	}

	public HudElementConfig getOrCreate(String elementId)
	{
		if(elements.containsKey(elementId))
			return elements.get(elementId);

		HudElementConfig config = new HudElementConfig();
		config.setEnabled(true);
		elements.put(elementId, config);
		return config;
	}

	public HudElementConfig get(String elementId)
	{
		return elements.get(elementId);
	}

	public void setElement(String elementId, HudElementConfig config)
	{
		elements.put(elementId, config);
	}
}

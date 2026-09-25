package net.wurstclient.hud2;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.wurstclient.hud2.HudLayout.HudElementConfig;

public abstract class HudElement
{
	private final String id;
	private final String name;

	protected HudElement(String id, String name)
	{
		this.id = id;
		this.name = name;
	}

	public final String getId()
	{
		return id;
	}

	public final String getName()
	{
		return name;
	}

	/**
	 * Returns the display name of this HUD element, translated into the current
	 * language when a translation exists.
	 *
	 * <p>
	 * Used for rendering only. {@link #getName()} and {@link #getId()} keep
	 * returning the stored values, which is what the layout file serializes and
	 * what settings are keyed by.
	 */
	public final String getTranslatedName()
	{
		String key = "hud.name." + name.toLowerCase(java.util.Locale.ROOT)
			.replaceAll("[^a-z0-9]+", "_").replaceAll("^_+|_+$", "");
		String translated = net.wurstclient.WurstClient.INSTANCE.translate(key);
		if(translated.equals(key))
			return name;
		return translated;
	}

	public boolean isSingleton()
	{
		return true;
	}

	public boolean renderEditorPreview()
	{
		return false;
	}

	public void onEnable(HudManager manager) {}

	public void onDisable(HudManager manager) {}

	public abstract int getWidth();

	public abstract int getHeight();

	public abstract void render(GuiGraphicsExtractor graphics, int x, int y,
		float partialTicks);

	public HudElementConfig getDefaultLayout()
	{
		return new HudElementConfig(HudElementConfig.HORIZONTAL_LEFT,
			HudElementConfig.VERTICAL_TOP, 0, 0);
	}
}

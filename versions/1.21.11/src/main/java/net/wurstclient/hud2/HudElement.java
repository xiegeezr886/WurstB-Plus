package net.wurstclient.hud2;

import net.wurstclient.util.render.GuiGraphicsExtractor;
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

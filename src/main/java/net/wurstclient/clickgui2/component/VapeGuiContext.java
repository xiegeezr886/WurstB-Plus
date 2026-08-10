package net.wurstclient.clickgui2.component;

import net.minecraft.client.gui.GuiGraphics;
import net.wurstclient.Feature;

public interface VapeGuiContext
{
	void beginBinding(Feature feature);

	boolean isBinding(Feature feature);

	boolean isFavorite(Feature feature);

	void toggleFavorite(Feature feature);

	boolean isHidden(Feature feature);

	void toggleHidden(Feature feature);

	boolean isEditingHiddenModules();

	void beginTextInput(GuiTextInput component);

	void endTextInput(GuiTextInput component);

	default boolean usesSuperSoftTheme()
	{
		return false;
	}

	default float renderScale()
	{
		return 1;
	}

	default void enableScissor(GuiGraphics graphics, double left, double top,
		double right, double bottom)
	{
		float scale = renderScale();
		graphics.enableScissor((int)Math.floor(left * scale),
			(int)Math.floor(top * scale), (int)Math.ceil(right * scale),
			(int)Math.ceil(bottom * scale));
	}
}

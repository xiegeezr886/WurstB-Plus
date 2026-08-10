package net.wurstclient.hud2;

import static org.junit.jupiter.api.Assertions.assertFalse;

import net.wurstclient.util.render.GuiGraphicsExtractor;
import net.wurstclient.hud2.HudLayout.HudElementConfig;
import org.junit.jupiter.api.Test;

final class HudLayoutTest
{
	@Test
	void preservesElementDefaultEnabledState()
	{
		HudLayout layout = new HudLayout();
		layout.addElement(new HudElement("disabled", "Disabled")
		{
			@Override
			public int getWidth()
			{
				return 1;
			}

			@Override
			public int getHeight()
			{
				return 1;
			}

			@Override
			public void render(GuiGraphicsExtractor graphics, int x, int y,
				float partialTicks)
			{
			}

			@Override
			public HudElementConfig getDefaultLayout()
			{
				HudElementConfig config = new HudElementConfig();
				config.setEnabled(false);
				return config;
			}
		});

		assertFalse(layout.get("disabled").isEnabled());
	}
}

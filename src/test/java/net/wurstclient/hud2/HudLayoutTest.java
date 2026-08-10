package net.wurstclient.hud2;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertEquals;

import net.minecraft.client.gui.GuiGraphics;
import net.wurstclient.hud2.elements.MusicIslandHudElement;
import net.wurstclient.hud2.elements.MusicLyricsHudElement;
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
			public void render(GuiGraphics graphics, int x, int y,
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

	@Test
	void musicElementsUseSourceEquivalentAnchors()
	{
		HudElementConfig island = new MusicIslandHudElement().getDefaultLayout();
		assertEquals(HudElementConfig.HORIZONTAL_CENTER,
			island.getHorizontalAlignment());
		assertEquals(HudElementConfig.VERTICAL_TOP,
			island.getVerticalAlignment());
		assertEquals(16, island.getVerticalOffset());

		HudElementConfig lyrics = new MusicLyricsHudElement().getDefaultLayout();
		assertEquals(HudElementConfig.HORIZONTAL_CENTER,
			lyrics.getHorizontalAlignment());
		assertEquals(HudElementConfig.VERTICAL_BOTTOM,
			lyrics.getVerticalAlignment());
		assertEquals(45, lyrics.getVerticalOffset());
	}

	@Test
	void managerResolvesCenterAnchorsWithSignedOffsets()
	{
		HudManager manager = new HudManager();
		HudElementConfig config = new HudElementConfig(
			HudElementConfig.HORIZONTAL_CENTER,
			HudElementConfig.VERTICAL_CENTER, -7, 9);

		assertEquals(143, manager.getElementX(config, 400, 100));
		assertEquals(99, manager.getElementY(config, 230, 50));
	}
}

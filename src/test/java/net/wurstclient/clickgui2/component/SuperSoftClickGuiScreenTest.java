package net.wurstclient.clickgui2.component;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import net.wurstclient.Feature;
import net.wurstclient.clickgui2.GuiIcon;
import org.junit.jupiter.api.Test;

final class SuperSoftClickGuiScreenTest
{
	@Test
	void matchesV15DefaultWindowCatalog()
	{
		assertEquals(List.of("visual", "movement", "client", "combat",
			"world", "other", "target", "config", "font", "settings",
			"main"), SuperSoftClickGuiScreen.defaultWindowIds());
	}

	@Test
	void settingsWindowCanReverseItsCloseAnimation()
	{
		SuperSoftSettingsWindow window = new SuperSoftSettingsWindow("test",
			new TestFeature(), 0, 0);

		assertTrue(window.isOpen());
		window.close();
		assertFalse(window.isOpen());
		assertFalse(window.isClosed());
		window.reopen();
		assertTrue(window.isOpen());
	}

	@Test
	void categoryWindowRightClickTogglesAnimatedCollapseState()
	{
		SuperSoftClickGuiWindow window = new SuperSoftClickGuiWindow("test",
			"Test", GuiIcon.CLIENT, List.of(), 10, 20, 0xFF6C35DE,
			new SuperSoftContext());

		assertFalse(window.isCollapsed());
		assertTrue(window.mouseClickedHeader(20, 25, 1));
		assertTrue(window.isCollapsed());
		assertTrue(window.mouseClickedHeader(20, 25, 1));
		assertFalse(window.isCollapsed());
	}

	private static final class TestFeature extends Feature
	{
		@Override
		public String getName()
		{
			return "Test";
		}

		@Override
		public String getDescription()
		{
			return "";
		}

		@Override
		public String getPrimaryAction()
		{
			return "";
		}
	}

	private static final class SuperSoftContext implements VapeGuiContext
	{
		@Override
		public void beginBinding(Feature feature)
		{}

		@Override
		public boolean isBinding(Feature feature)
		{
			return false;
		}

		@Override
		public boolean isFavorite(Feature feature)
		{
			return false;
		}

		@Override
		public void toggleFavorite(Feature feature)
		{}

		@Override
		public boolean isHidden(Feature feature)
		{
			return false;
		}

		@Override
		public void toggleHidden(Feature feature)
		{}

		@Override
		public boolean isEditingHiddenModules()
		{
			return false;
		}

		@Override
		public void beginTextInput(GuiTextInput component)
		{}

		@Override
		public void endTextInput(GuiTextInput component)
		{}

		@Override
		public boolean usesSuperSoftTheme()
		{
			return true;
		}
	}
}

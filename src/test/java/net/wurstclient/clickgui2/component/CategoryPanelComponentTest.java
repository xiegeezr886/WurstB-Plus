package net.wurstclient.clickgui2.component;

import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import net.wurstclient.Feature;
import net.wurstclient.settings.CheckboxSetting;
import org.junit.jupiter.api.Test;

final class CategoryPanelComponentTest
{
	@Test
	void reusesCardsWhenFilteredFeaturesChange()
	{
		Feature first = new TestFeature("First");
		Feature second = new TestFeature("Second");
		Feature third = new TestFeature("Third");
		CategoryPanelComponent panel = new CategoryPanelComponent(0, 0, 100,
			List.of(first, second), 0xFF6C35DE, 16);
		ModuleCardComponent firstCard = panel.getCards().get(0);
		ModuleCardComponent secondCard = panel.getCards().get(1);
		firstCard.setExpanded(true);

		panel.setFeatures(List.of(second, first, third), 0xFF6C35DE);

		assertSame(secondCard, panel.getCards().get(0));
		assertSame(firstCard, panel.getCards().get(1));
		assertTrue(panel.getCards().get(1).isExpanded());
	}

	@Test
	void superSoftSettingsExpandInsideTheModuleCard()
	{
		ModuleCardComponent card = new ModuleCardComponent(new SettingsFeature(),
			0xFF6C35DE, new SuperSoftContext());
		card.setX(0);
		card.setY(0);
		card.setWidth(100);

		assertTrue(card.mouseClicked(50, 5, 1));
		assertTrue(card.isExpanded());
		assertTrue(card.mouseClicked(95, 5, 0));
		assertFalse(card.isExpanded());
	}

	private static final class TestFeature extends Feature
	{
		private final String name;

		private TestFeature(String name)
		{
			this.name = name;
		}

		@Override
		public String getName()
		{
			return name;
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

	private static final class SettingsFeature extends Feature
	{
		private SettingsFeature()
		{
			addSetting(new CheckboxSetting("Enabled", true));
		}

		@Override
		public String getName()
		{
			return "Settings";
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

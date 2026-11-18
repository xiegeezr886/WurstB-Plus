package net.wurstclient.clickgui2;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;

import net.wurstclient.util.render.GuiGraphicsExtractor;
import org.junit.jupiter.api.Test;

final class ComponentParentTest
{
	@Test
	void detachedComponentIsNotHovering()
	{
		TestComponent component = new TestComponent();
		assertFalse(component.isHoveringAt(0, 0));
	}

	@Test
	void windowOwnsAddedComponentsUntilCleared()
	{
		Window window = new Window("test");
		TestComponent component = new TestComponent();
		window.add(component);
		assertSame(window, component.getParent());

		window.clear();
		assertNull(component.getParent());
	}

	private static final class TestComponent extends Component
	{
		private boolean isHoveringAt(int mouseX, int mouseY)
		{
			return isHovering(mouseX, mouseY);
		}

		@Override
		public void render(GuiGraphicsExtractor context, int mouseX, int mouseY,
			float partialTicks)
		{
		}

		@Override
		public int getDefaultWidth()
		{
			return 10;
		}

		@Override
		public int getDefaultHeight()
		{
			return 10;
		}
	}
}

package net.wurstclient.clickgui2.component;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;

interface SuperSoftFloatingWindow
{
	String getId();

	double getX();

	double getY();

	boolean isVisible();

	void setVisible(boolean visible);

	void render(GuiGraphics graphics, Font font, int mouseX, int mouseY,
		float partialTicks, int maxBodyHeight, VapeGuiContext context);

	boolean headerContains(double mouseX, double mouseY);

	boolean mouseClickedHeader(double mouseX, double mouseY, int button);

	boolean mouseClickedBody(double mouseX, double mouseY, int button);

	boolean mouseReleased(double mouseX, double mouseY, int button);

	boolean mouseDragged(double mouseX, double mouseY, int button);

	boolean mouseScrolled(double mouseX, double mouseY, double delta);

	void moveTo(double x, double y, int screenWidth, int screenHeight);

	int totalHeight(int maxBodyHeight);

	void tick();

	default boolean isClosed()
	{
		return false;
	}

	default void dispose()
	{}
}

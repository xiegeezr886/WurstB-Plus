package net.wurstclient.clickgui2.component;

import java.util.List;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.util.Mth;
import net.wurstclient.Feature;
import net.wurstclient.clickgui2.GuiIcon;
import net.wurstclient.clickgui2.supersoft.SuperSoftRenderer;
import net.wurstclient.clickgui2.supersoft.SuperSoftTheme;
import net.wurstclient.clickgui2.supersoft.UiTween;

/** Java port of SuperSoft's ClickGuiWindow. */
public final class SuperSoftClickGuiWindow implements SuperSoftFloatingWindow
{
	private static final int WIDTH = 100;
	private static final int HEADER_HEIGHT = 20;

	private final String title;
	private final String id;
	private final GuiIcon icon;
	private final CategoryPanelComponent panel;
	private final UiTween bodyMotion = new UiTween(0, 200);
	private final UiTween headerHoverMotion = new UiTween(0, 150);
	private final UiTween arrowMotion = new UiTween(0, 200);
	private double x;
	private double y;
	private boolean visible = true;
	private boolean collapsed;

	public SuperSoftClickGuiWindow(String id, String title, GuiIcon icon,
		List<Feature> features, double x, double y, int accentColor,
		VapeGuiContext context)
	{
		this.id = id;
		this.title = title;
		this.icon = icon;
		this.x = x;
		this.y = y;
		panel = new CategoryPanelComponent(x, y + HEADER_HEIGHT, WIDTH,
			features, accentColor, 16, context);
	}

	@Override
	public String getId()
	{
		return id;
	}

	public String getTitle()
	{
		return title;
	}

	public double getX()
	{
		return x;
	}

	public double getY()
	{
		return y;
	}

	public CategoryPanelComponent getPanel()
	{
		return panel;
	}

	@Override
	public boolean isVisible()
	{
		return visible;
	}

	@Override
	public void setVisible(boolean visible)
	{
		this.visible = visible;
	}

	public void setFeatures(List<Feature> features, int accentColor)
	{
		panel.setFeatures(features, accentColor);
	}

	public void render(GuiGraphics graphics, Font font, int mouseX, int mouseY,
		float partialTicks, int maxBodyHeight, VapeGuiContext context)
	{
		if(!visible)
			return;
		int fullBodyHeight = (int)Math.min(maxBodyHeight,
			Math.ceil(panel.getContentHeight()));
		int bodyHeight = Math.round(fullBodyHeight
			* bodyMotion.update(collapsed ? 0 : 1));
		panel.setHeight(bodyHeight);
		int bottom = (int)y + HEADER_HEIGHT + bodyHeight;
		SuperSoftRenderer.window(graphics, (int)x, (int)y,
			(int)x + WIDTH, bottom, 2, SuperSoftTheme.BORDER);
		boolean headerHovered = headerContains(mouseX, mouseY);
		int headerColor = SuperSoftTheme.mix(SuperSoftTheme.HEADER,
			SuperSoftTheme.SETTING_HOVER,
			headerHoverMotion.update(headerHovered ? 0.32F : 0));
		SuperSoftRenderer.header(graphics, (int)x, (int)y,
			(int)x + WIDTH, (int)y + HEADER_HEIGHT, 2,
			headerColor);

		icon.draw(graphics, (int)x + 6, (int)y + 6, 8,
			SuperSoftTheme.TEXT_SECONDARY);
		int titleWidth = font.width(title);
		graphics.drawString(font, title,
			(int)x + (WIDTH - titleWidth) / 2,
			(int)y + (HEADER_HEIGHT - font.lineHeight) / 2,
			SuperSoftTheme.TEXT, false);
		GuiIcon.CHEVRON.drawRotated(graphics, (int)x + WIDTH - 14,
			(int)y + 6, 8, SuperSoftTheme.TEXT_SECONDARY,
			arrowMotion.update(collapsed ? 0 : 90));
		if(bodyHeight <= 0)
			return;

		context.enableScissor(graphics, x, y + HEADER_HEIGHT, x + WIDTH,
			bottom);
		panel.render(graphics, mouseX, mouseY, partialTicks);
		graphics.disableScissor();
	}

	public boolean headerContains(double mouseX, double mouseY)
	{
		return visible && mouseX >= x && mouseX < x + WIDTH && mouseY >= y
			&& mouseY < y + HEADER_HEIGHT;
	}

	@Override
	public boolean mouseClickedHeader(double mouseX, double mouseY, int button)
	{
		if(button != 1)
			return false;
		collapsed = !collapsed;
		return true;
	}

	@Override
	public boolean mouseClickedBody(double mouseX, double mouseY, int button)
	{
		if(!visible || collapsed || mouseX < x || mouseX >= x + WIDTH
			|| mouseY < y + HEADER_HEIGHT
			|| mouseY >= y + HEADER_HEIGHT + panel.getHeight())
			return false;
		panel.mouseClicked(mouseX, mouseY, button);
		return true;
	}

	@Override
	public boolean mouseReleased(double mouseX, double mouseY, int button)
	{
		return visible && !collapsed
			&& panel.mouseReleased(mouseX, mouseY, button);
	}

	@Override
	public boolean mouseDragged(double mouseX, double mouseY, int button)
	{
		return visible && !collapsed
			&& panel.mouseDragged(mouseX, mouseY, button);
	}

	@Override
	public boolean mouseScrolled(double mouseX, double mouseY, double delta)
	{
		return visible && !collapsed
			&& panel.mouseScrolled(mouseX, mouseY, delta);
	}

	public void moveTo(double x, double y, int screenWidth, int screenHeight)
	{
		this.x = Mth.clamp(x, 0, Math.max(0, screenWidth - WIDTH));
		this.y = Mth.clamp(y, 0,
			Math.max(0, screenHeight - HEADER_HEIGHT));
		panel.setX(this.x);
		panel.setY(this.y + HEADER_HEIGHT);
	}

	public int totalHeight(int maxBodyHeight)
	{
		return HEADER_HEIGHT + (collapsed ? 0
			: Math.min(maxBodyHeight, (int)panel.getContentHeight()));
	}

	boolean isCollapsed()
	{
		return collapsed;
	}

	public void tick()
	{
		panel.tick();
	}
}

package net.wurstclient.clickgui2;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.util.Mth;
import net.wurstclient.Feature;
import net.wurstclient.WurstClient;
import net.wurstclient.clickgui2.theme.FlatTheme;
import net.wurstclient.settings.Setting;

final class NavigatorSettingsPanel extends Window
{
	private static final WurstClient WURST = WurstClient.INSTANCE;
	private final Feature feature;
	private final List<Setting> settings;
	private List<Setting> renderedSettings = List.of();
	private int bodyX;
	private int bodyY;
	private int bodyWidth;
	private int bodyHeight;
	private int visibleSettingCount;

	NavigatorSettingsPanel(Feature feature)
	{
		super("");
		this.feature = feature;
		settings = new ArrayList<>(feature.getSettings().values());
		setMinimizable(false);
		setPinnable(false);
		setClosable(false);
		refreshSettings(SettingTreeLayout.flatten(settings));
	}

	Feature getFeature()
	{
		return feature;
	}

	int getVisibleSettingCount()
	{
		return visibleSettingCount;
	}

	void layout(int x, int y, int width, int height)
	{
		bodyX = x;
		bodyY = y;
		bodyWidth = width;
		bodyHeight = height;
		setX(x);
		setY(y - 13);
		setFixedWidth(false);
		setWidth(width);
		setFixedWidth(true);
		setMaxHeight(height + 13);
		validate();
	}

	void renderContent(GuiGraphicsExtractor graphics, int mouseX, int mouseY,
		float partialTicks, FlatTheme theme)
	{
		prepareForRender();
		validate();
		int scroll = isScrollingEnabled() ? getScrollOffset() : 0;
		int localMouseX = mouseX - bodyX;
		int localMouseY = mouseY - bodyY - scroll;

		graphics.enableScissor(bodyX, bodyY, bodyX + bodyWidth,
			bodyY + bodyHeight);
		graphics.pose().pushMatrix();
		graphics.pose().translate(bodyX, bodyY + scroll);
		for(int index = 0; index < countChildren(); index++)
			getChild(index).render(graphics, localMouseX, localMouseY,
				partialTicks);
		graphics.pose().popMatrix();
		graphics.disableScissor();

		renderScrollbar(graphics, theme);
	}

	boolean mouseClicked(double mouseX, double mouseY, int button)
	{
		prepareForRender();
		validate();
		if(!isInside(mouseX, mouseY))
			return false;

		int scroll = isScrollingEnabled() ? getScrollOffset() : 0;
		double localMouseX = mouseX - bodyX;
		double localMouseY = mouseY - bodyY - scroll;
		for(int index = countChildren() - 1; index >= 0; index--)
		{
			Component component = getChild(index);
			if(localMouseX < component.getX()
				|| localMouseY < component.getY()
				|| localMouseX >= component.getX() + component.getWidth()
				|| localMouseY >= component.getY() + component.getHeight())
				continue;

			component.handleMouseClick(localMouseX, localMouseY, button);
			return true;
		}
		return true;
	}

	boolean mouseScrolled(double mouseX, double mouseY, double delta)
	{
		if(!isInside(mouseX, mouseY) || !isScrollingEnabled())
			return false;

		int scroll = getScrollOffset() + (delta > 0 ? 14 : -14);
		int minimum = -getInnerHeight() + getHeight() - 13;
		setScrollOffset(Mth.clamp(scroll, minimum, 0));
		return true;
	}

	void dispose()
	{
		WURST.getGui().closePopupsOwnedBy(this);
		clear();
	}

	@Override
	public void prepareForRender()
	{
		List<Setting> visibleSettings = SettingTreeLayout.flatten(settings);
		if(!renderedSettings.equals(visibleSettings))
			refreshSettings(visibleSettings);
	}

	private void refreshSettings(List<Setting> visibleSettings)
	{
		WURST.getGui().closePopupsOwnedBy(this);
		clear();
		for(Setting setting : visibleSettings)
			addSetting(setting);
		renderedSettings = visibleSettings;
		visibleSettingCount = visibleSettings.size();
		invalidate();
	}

	private void addSetting(Setting setting)
	{
		Component component = setting.getComponent();
		component.setIndent(setting.getDepth() * 12);
		add(component);
	}

	private void renderScrollbar(GuiGraphicsExtractor graphics, FlatTheme theme)
	{
		if(!isScrollingEnabled())
			return;

		double visibleRatio = bodyHeight / (double)getInnerHeight();
		int thumbHeight = Math.max(12,
			(int)Math.round(bodyHeight * visibleRatio));
		int scrollRange = Math.max(1, getInnerHeight() - bodyHeight);
		int thumbY = bodyY + (int)Math.round(
			-getScrollOffset() / (double)scrollRange * (bodyHeight - thumbHeight));
		graphics.fill(bodyX + bodyWidth - 2, bodyY,
			bodyX + bodyWidth, bodyY + bodyHeight, theme.background(0.2F));
		FlatRenderer.fillRoundedRect(graphics, bodyX + bodyWidth - 2, thumbY,
			bodyX + bodyWidth, thumbY + thumbHeight, 1, theme.accent(0.68F));
	}

	private boolean isInside(double mouseX, double mouseY)
	{
		return mouseX >= bodyX && mouseX < bodyX + bodyWidth
			&& mouseY >= bodyY && mouseY < bodyY + bodyHeight;
	}
}

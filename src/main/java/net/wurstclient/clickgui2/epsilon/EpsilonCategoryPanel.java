package net.wurstclient.clickgui2.epsilon;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

import net.minecraft.client.gui.GuiGraphics;
import net.wurstclient.Category;
import net.wurstclient.Feature;
import net.wurstclient.clickgui2.FeatureMenuSupport;
import net.wurstclient.clickgui2.GuiIcon;
import net.wurstclient.clickgui2.component.VapeGuiContext;

/**
 * Epsilon 26.1.2 CategoryPanel 的直接移植。
 *
 * <p>分类面板：标题（分类图标 + 名称），内容为该分类下的模块按钮列表
 * （支持搜索过滤、按名称排序、滚动）。</p>
 */
public final class EpsilonCategoryPanel extends EpsilonDropdownPanel
{
	private final Category category;
	private final List<EpsilonModuleButton> buttons = new ArrayList<>();
	private final VapeGuiContext context;
	private String searchQuery = "";

	public EpsilonCategoryPanel(Category category, VapeGuiContext context)
	{
		super("category:" + category.getName());
		this.category = category;
		this.context = context;
		List<Feature> features = new ArrayList<>(
			FeatureMenuSupport.getAllFeatures());
		features.removeIf(feature -> feature.getCategory() != category);
		features.sort(Comparator.comparing(Feature::getDisplayName,
			String.CASE_INSENSITIVE_ORDER));
		for(Feature feature : features)
			buttons.add(new EpsilonModuleButton(feature, context));
	}

	public Category getCategory()
	{
		return category;
	}

	public void setSearchQuery(String searchQuery)
	{
		this.searchQuery = searchQuery == null ? ""
			: searchQuery.trim().toLowerCase(Locale.ROOT);
		setScrollImmediate(0);
	}

	@Override
	public String getTitle()
	{
		return category.getName();
	}

	@Override
	public GuiIcon getIcon()
	{
		return switch(category)
		{
			case RENDER -> GuiIcon.RENDER;
			case MOVEMENT -> GuiIcon.MOVEMENT;
			case COMBAT -> GuiIcon.COMBAT;
			case BLOCKS, ITEMS -> GuiIcon.WORLD;
			case CHAT, OTHER -> GuiIcon.MISC;
			case FUN -> GuiIcon.FUN;
		};
	}

	@Override
	protected float computeContentHeight()
	{
		float total = 0;
		for(EpsilonModuleButton button : visibleButtons())
			total += button.getHeight();
		return total;
	}

	@Override
	protected void drawPanelContent(GuiGraphics graphics, int mouseX,
		int mouseY, float visibleHeight)
	{
		float buttonY = y + EpsilonDropdownTheme.PANEL_HEADER_HEIGHT - scroll;
		float buttonWidth = maxScroll > 0 ? width - 3 : width;
		for(EpsilonModuleButton button : visibleButtons())
		{
			float buttonH = button.getHeight();
			if(buttonY + buttonH > y + EpsilonDropdownTheme.PANEL_HEADER_HEIGHT
				&& buttonY < y + EpsilonDropdownTheme.PANEL_HEADER_HEIGHT
					+ visibleHeight)
			{
				button.setPosition(x, buttonY);
				button.setWidth(buttonWidth);
				button.render(graphics, mouseX, mouseY, 1);
			}
			buttonY += buttonH;
		}
	}

	@Override
	protected boolean mouseClickedContent(double mouseX, double mouseY,
		int button)
	{
		for(EpsilonModuleButton moduleButton : visibleButtons())
			if(moduleButton.mouseClicked(mouseX, mouseY, button))
				return true;
		return false;
	}

	@Override
	protected boolean mouseReleasedContent(double mouseX, double mouseY,
		int button)
	{
		for(EpsilonModuleButton moduleButton : visibleButtons())
			if(moduleButton.mouseReleased(mouseX, mouseY, button))
				return true;
		return false;
	}

	@Override
	protected boolean mouseDraggedContent(double mouseX, double mouseY,
		int button)
	{
		for(EpsilonModuleButton moduleButton : visibleButtons())
			if(moduleButton.mouseDragged(mouseX, mouseY, button))
				return true;
		return false;
	}

	@Override
	protected void tickContent()
	{
		for(EpsilonModuleButton button : buttons)
			button.tick();
	}

	private List<EpsilonModuleButton> visibleButtons()
	{
		if(searchQuery.isBlank())
			return buttons;
		List<EpsilonModuleButton> result = new ArrayList<>();
		for(EpsilonModuleButton button : buttons)
			if(FeatureMenuSupport.searchFeatures(List.of(
				button.getFeature()), searchQuery).size() > 0)
				result.add(button);
		return result;
	}
}

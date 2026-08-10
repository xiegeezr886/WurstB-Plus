package net.wurstclient.clickgui2.component;

import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.util.Mth;
import net.wurstclient.Feature;
import net.wurstclient.clickgui2.supersoft.SuperSoftTheme;

public final class CategoryPanelComponent extends GuiComponent
{
	private static final int ROW_HEIGHT = 20;
	private final List<ModuleCardComponent> cards = new ArrayList<>();
	private final VapeGuiContext context;
	private double scrollOffset;

	public CategoryPanelComponent(double x, double y, double width,
		List<Feature> features, int accentColor, int maxVisibleCards)
	{
		this(x, y, width, features, accentColor, maxVisibleCards, null);
	}

	public CategoryPanelComponent(double x, double y, double width,
		List<Feature> features, int accentColor, int maxVisibleCards,
		VapeGuiContext context)
	{
		this.x = x;
		this.y = y;
		this.width = width;
		this.context = context;
		for(Feature feature : features)
			cards.add(new ModuleCardComponent(feature, accentColor, context));
		height = Math.min(Math.max(ROW_HEIGHT, getContentHeight()),
			maxVisibleCards * ROW_HEIGHT);
	}

	public List<ModuleCardComponent> getCards()
	{
		return cards;
	}

	public void setFeatures(List<Feature> features, int accentColor)
	{
		Map<Feature, ModuleCardComponent> existing = new IdentityHashMap<>();
		for(ModuleCardComponent card : cards)
			existing.put(card.getFeature(), card);

		List<ModuleCardComponent> updated = new ArrayList<>(features.size());
		for(Feature feature : features)
		{
			ModuleCardComponent card = existing.get(feature);
			if(card == null)
				card = new ModuleCardComponent(feature, accentColor, context);
			updated.add(card);
		}
		cards.clear();
		cards.addAll(updated);
		clampScrollOffset();
	}

	public double getContentHeight()
	{
		double contentHeight = 0;
		for(ModuleCardComponent card : cards)
			contentHeight += card.getHeight();
		return contentHeight;
	}

	public void closeAllSettings()
	{
		for(ModuleCardComponent card : cards)
			card.setExpanded(false);
	}

	@Override
	protected void renderSelf(GuiGraphics graphics, int mouseX, int mouseY,
		float partialTicks)
	{
		int background = context != null && context.usesSuperSoftTheme()
			? SuperSoftTheme.WINDOW : VapePalette.FRAME;
		graphics.fill((int)x, (int)y, (int)(x + getWidth()),
			(int)(y + getHeight()), background);
		if(context == null)
			graphics.enableScissor((int)x, (int)y, (int)(x + getWidth()),
				(int)(y + getHeight()));
		else
			context.enableScissor(graphics, x, y, x + getWidth(),
				y + getHeight());
		layoutCards();
		for(ModuleCardComponent card : cards)
			if(card.getY() + card.getHeight() > y
				&& card.getY() < y + getHeight())
				card.render(graphics, mouseX, mouseY, partialTicks);
		graphics.disableScissor();
		renderScrollbar(graphics);
	}

	private void layoutCards()
	{
		clampScrollOffset();
		double cardY = y - scrollOffset;
		for(ModuleCardComponent card : cards)
		{
			card.setX(x);
			card.setY(cardY);
			card.setWidth(getWidth());
			cardY += card.getHeight();
		}
	}

	private void clampScrollOffset()
	{
		double maxOffset = Math.max(0, getContentHeight() - getHeight());
		scrollOffset = Mth.clamp(scrollOffset, 0, maxOffset);
	}

	private void renderScrollbar(GuiGraphics graphics)
	{
		double contentHeight = getContentHeight();
		if(contentHeight <= getHeight())
			return;
		int trackX = (int)(x + getWidth()) - 2;
		int thumbHeight = Math.max(10,
			(int)(getHeight() * getHeight() / contentHeight));
		double maxOffset = contentHeight - getHeight();
		int thumbY = (int)(y + scrollOffset / maxOffset
			* (getHeight() - thumbHeight));
		graphics.fill(trackX, thumbY, trackX + 2, thumbY + thumbHeight,
			0xFF363536);
	}

	private ModuleCardComponent findCard(double mouseX, double mouseY)
	{
		layoutCards();
		for(ModuleCardComponent card : cards)
			if(mouseX >= card.getX() && mouseX < card.getX() + card.getWidth()
				&& mouseY >= card.getY() && mouseY < card.getY() + card.getHeight())
				return card;
		return null;
	}

	@Override
	protected boolean onClick(double mouseX, double mouseY, int button)
	{
		if(!contains(mouseX, mouseY))
			return false;
		ModuleCardComponent card = findCard(mouseX, mouseY);
		if(card != null && button == 0 && Screen.hasShiftDown())
		{
			if(context != null)
				context.beginBinding(card.getFeature());
			return true;
		}
		return card != null && card.mouseClicked(mouseX, mouseY, button);
	}

	@Override
	protected boolean onRelease(double mouseX, double mouseY, int button)
	{
		ModuleCardComponent card = findCard(mouseX, mouseY);
		return card != null && card.mouseReleased(mouseX, mouseY, button);
	}

	@Override
	protected boolean onDrag(double mouseX, double mouseY, int button)
	{
		ModuleCardComponent card = findCard(mouseX, mouseY);
		return card != null && card.mouseDragged(mouseX, mouseY, button);
	}

	@Override
	protected boolean onScroll(double mouseX, double mouseY, double delta)
	{
		if(!contains(mouseX, mouseY))
			return false;
		double maxOffset = Math.max(0, getContentHeight() - getHeight());
		if(maxOffset <= 0)
			return false;
		scrollOffset = Mth.clamp(scrollOffset + (delta > 0 ? -15 : 15),
			0, maxOffset);
		return true;
	}

	private boolean contains(double mouseX, double mouseY)
	{
		return mouseX >= x && mouseX < x + getWidth() && mouseY >= y
			&& mouseY < y + getHeight();
	}

	@Override
	public void tick()
	{
		for(ModuleCardComponent card : cards)
			card.tick();
	}
}

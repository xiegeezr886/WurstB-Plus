/*
 * Copyright (c) 2014-2026 Wurst-Imperium and contributors.
 *
 * This source code is subject to the terms of the GNU General Public
 * License, version 3. If a copy of the GPL was not distributed with this
 * file, You can obtain one at: https://www.gnu.org/licenses/gpl-3.0.txt
 */
package net.wurstclient.clickgui2.component;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.util.Mth;
import net.wurstclient.Feature;

/**
 * 仿 VAPE ClickGuiContentPanel 的分类面板。
 * 承载该分类的所有模块卡片，支持滚动。
 */
public class CategoryPanelComponent extends GuiComponent
{
	private final List<Feature> features;
	private final int accentColor;
	private final List<ModuleCardComponent> cards = new ArrayList<>();
	private final int maxVisibleCards;
	
	private double scrollOffset;
	private boolean scrollable;
	
	public CategoryPanelComponent(double x, double y, double width,
		List<Feature> features, int accentColor, int maxVisibleCards)
	{
		this.x = x;
		this.y = y;
		this.width = width;
		this.features = List.copyOf(features);
		this.accentColor = accentColor;
		this.maxVisibleCards = maxVisibleCards;
		
		for(Feature feature : this.features)
		{
			ModuleCardComponent card =
				new ModuleCardComponent(feature, accentColor);
			cards.add(card);
			addChild(card);
		}
		
		height = maxVisibleCards * 20.0;
	}
	
	public List<ModuleCardComponent> getCards()
	{
		return cards;
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
		// 面板背景
		graphics.fill((int)x, (int)y, (int)(x + getWidth()),
			(int)(y + getHeight()), 0xFF1A191A);
		
		// 裁剪区域
		graphics.enableScissor((int)x, (int)y, (int)(x + getWidth()),
			(int)(y + getHeight()));
		
		int cardY = (int)y - (int)scrollOffset;
		for(ModuleCardComponent card : cards)
		{
			if(!card.isVisible())
				continue;
			card.setX(x);
			card.setY(cardY);
			card.setWidth(getWidth());
			
			// 只渲染可见卡片
			double cardBottom = cardY + card.getHeight();
			if(cardBottom > y && cardY < y + getHeight())
			{
				card.render(graphics, mouseX, mouseY, partialTicks);
				
				// 展开的卡片设置项也需裁剪
				if(card.isExpanded())
					renderCardSettings(graphics, card, mouseX, mouseY,
						partialTicks);
			}
			cardY += card.getHeight() + 2;
		}
		
		graphics.disableScissor();
	}
	
	private void renderCardSettings(GuiGraphics graphics,
		ModuleCardComponent card, int mouseX, int mouseY, float partialTicks)
	{
		// ModuleCardComponent.renderSelf 已处理行内设置渲染
		// 这里仅确保裁剪生效（renderSelf 内部渲染）
	}
	
	@Override
	protected boolean onClick(double mouseX, double mouseY, int button)
	{
		if(mouseX < x || mouseX >= x + getWidth()
			|| mouseY < y || mouseY >= y + getHeight())
			return false;
		
		double localY = mouseY - y + scrollOffset;
		double cardY = 0;
		for(ModuleCardComponent card : cards)
		{
			if(!card.isVisible())
				continue;
			double cardH = card.getHeight();
			if(localY >= cardY && localY < cardY + cardH)
			{
				card.setY(y + cardY - scrollOffset);
				card.mouseClicked(mouseX, mouseY, button);
				return true;
			}
			cardY += cardH + 2;
		}
		return false;
	}
	
	@Override
	protected boolean onRelease(double mouseX, double mouseY, int button)
	{
		double localY = mouseY - y + scrollOffset;
		double cardY = 0;
		for(ModuleCardComponent card : cards)
		{
			if(!card.isVisible())
				continue;
			double cardH = card.getHeight();
			if(localY >= cardY && localY < cardY + cardH)
			{
				card.setY(y + cardY - scrollOffset);
				card.mouseReleased(mouseX, mouseY, button);
				return true;
			}
			cardY += cardH + 2;
		}
		return false;
	}
	
	@Override
	protected boolean onDrag(double mouseX, double mouseY, int button)
	{
		double localY = mouseY - y + scrollOffset;
		double cardY = 0;
		for(ModuleCardComponent card : cards)
		{
			if(!card.isVisible())
				continue;
			double cardH = card.getHeight();
			if(localY >= cardY && localY < cardY + cardH)
			{
				card.setY(y + cardY - scrollOffset);
				card.mouseDragged(mouseX, mouseY, button);
				return true;
			}
			cardY += cardH + 2;
		}
		return false;
	}
	
	@Override
	protected boolean onScroll(double mouseX, double mouseY, double delta)
	{
		double totalHeight = 0;
		for(ModuleCardComponent card : cards)
			if(card.isVisible())
				totalHeight += card.getHeight() + 2;
		totalHeight -= 2;
		
		double maxOffset = Math.max(0, totalHeight - getHeight());
		if(maxOffset <= 0)
			return false;
		
		int direction = delta > 0 ? -20 : 20;
		scrollOffset = Mth.clamp(scrollOffset + direction, 0, maxOffset);
		return true;
	}
	
	@Override
	public void tick()
	{
		super.tick();
		for(ModuleCardComponent card : cards)
			card.tick();
	}
}

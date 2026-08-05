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
import java.util.Map;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.wurstclient.Feature;
import net.wurstclient.WurstClient;
import net.wurstclient.clickgui2.FlatRenderer;
import net.wurstclient.clickgui2.GuiIcon;
import net.wurstclient.settings.Setting;

/**
 * 仿 VAPE ClickGuiModuleCardComponent 的模块卡片。
 * 显示模块名、启用高亮、按键徽章、设置箭头。
 * 点击箭头展开行内设置面板。
 */
public class ModuleCardComponent extends GuiComponent
{
	protected final Feature feature;
	protected final int accentColor;
	protected boolean expanded;
	protected boolean selected;
	protected boolean dimmed;
	protected boolean favoriteHighlighted;
	
	protected final List<GuiComponent> valueComponents = new ArrayList<>();
	
	public ModuleCardComponent(Feature feature, int accentColor)
	{
		this.feature = feature;
		this.accentColor = accentColor;
		height = 20;
	}
	
	public Feature getFeature()
	{
		return feature;
	}
	
	public boolean isExpanded()
	{
		return expanded;
	}
	
	public void setExpanded(boolean expanded)
	{
		if(this.expanded == expanded)
			return;
		this.expanded = expanded;
		if(expanded)
			buildValueComponents();
	}
	
	public void setSelected(boolean selected)
	{
		this.selected = selected;
	}
	
	public void setDimmed(boolean dimmed)
	{
		this.dimmed = dimmed;
	}
	
	public void setFavoriteHighlighted(boolean favoriteHighlighted)
	{
		this.favoriteHighlighted = favoriteHighlighted;
	}
	
	private void buildValueComponents()
	{
		valueComponents.clear();
		for(Setting setting : feature.getSettings().values())
			valueComponents.add(ValueComponentFactory.create(setting));
		height = 20 + valueComponents.size() * 16;
	}
	
	@Override
	protected double minHeight()
	{
		return expanded ? 20 + valueComponents.size() * 16 : 20;
	}
	
	@Override
	protected void renderSelf(GuiGraphics graphics, int mouseX, int mouseY,
		float partialTicks)
	{
		boolean enabled = feature.isEnabled();
		int bg = enabled ? accentColor
			: hovered || expanded ? 0xFF1F1E1F : 0xFF1A191A;
		FlatRenderer.fillRoundedRect(graphics, (int)x, (int)y,
			(int)(x + getWidth()), (int)y + 20, 3, bg);
		
		if(dimmed && !enabled)
		{
			// 半透明压暗
			graphics.fill((int)x + 1, (int)y + 1,
				(int)(x + getWidth()) - 1, (int)y + 19, 0x66000000);
		}
		
		Font font = Minecraft.getInstance().font;
		String name = feature.getDisplayName();
		int nameColor = enabled ? 0xFFF0F0F0
			: hovered || expanded ? 0xFFE0E0E0 : 0xFFD1D1D1;
		graphics.drawString(font, name, (int)x + 6, (int)y + 6,
			nameColor, false);
		
		// 按键徽章
		String keyLabel = getKeyLabel();
		if(!keyLabel.isEmpty())
		{
			int badgeW = Math.max(18,
				font.width(keyLabel) + 8);
			int badgeX = (int)(x + getWidth() - badgeW - 14);
			int badgeY = (int)y + 5;
			FlatRenderer.fillRoundedRect(graphics, badgeX, badgeY,
				badgeX + badgeW, badgeY + 10, 3, 0xFF252426);
			graphics.drawString(font, keyLabel, badgeX + 4, badgeY + 2,
				0xFFA3A3A3, false);
		}
		
		// 设置箭头
		if(!feature.getSettings().isEmpty())
		{
			GuiIcon.CHEVRON.drawRotated(graphics,
				(int)(x + getWidth() - 13), (int)y + 6, 8, 0xFFD8D8D8,
				expanded ? 0 : -90);
		}
		
		// 行内设置
		if(expanded)
		{
			int settingsTop = (int)y + 20;
			graphics.fill((int)x + 2, settingsTop,
				(int)(x + getWidth()) - 2,
				(int)(y + getHeight()), 0xFF141414);
			for(int i = 0; i < valueComponents.size(); i++)
			{
				GuiComponent comp = valueComponents.get(i);
				comp.setX(x + 4);
				comp.setY(settingsTop + i * 16);
				comp.setWidth(getWidth() - 8);
				comp.render(graphics, mouseX, mouseY, partialTicks);
			}
		}
	}
	
	protected String getKeyLabel()
	{
		for(net.wurstclient.keybinds.Keybind kb : WurstClient.INSTANCE
			.getKeybinds().getAllKeybinds())
			if(kb.getCommands().equals(feature.getPrimaryAction()))
				return kb.getKey();
		return "";
	}
	
	@Override
	protected boolean onClick(double mouseX, double mouseY, int button)
	{
		// 展开状态：点击设置区域
		if(expanded && mouseY >= y + 20)
		{
			int index = (int)((mouseY - y - 20) / 16);
			if(index >= 0 && index < valueComponents.size())
			{
				GuiComponent comp = valueComponents.get(index);
				comp.mouseClicked(mouseX, mouseY, button);
				return true;
			}
			return true;
		}
		
		// 设置箭头点击
		if(!feature.getSettings().isEmpty()
			&& mouseX >= x + getWidth() - 20)
		{
			setExpanded(!expanded);
			return true;
		}
		
		// 模块主体点击
		if(button == 0)
		{
			feature.doPrimaryAction();
			return true;
		}
		return false;
	}
	
	@Override
	protected boolean onRelease(double mouseX, double mouseY, int button)
	{
		if(expanded && mouseY >= y + 20)
		{
			int index = (int)((mouseY - y - 20) / 16);
			if(index >= 0 && index < valueComponents.size())
			{
				GuiComponent comp = valueComponents.get(index);
				comp.mouseReleased(mouseX, mouseY, button);
				return true;
			}
		}
		return false;
	}
	
	@Override
	protected boolean onDrag(double mouseX, double mouseY, int button)
	{
		if(expanded && mouseY >= y + 20)
		{
			int index = (int)((mouseY - y - 20) / 16);
			if(index >= 0 && index < valueComponents.size())
			{
				GuiComponent comp = valueComponents.get(index);
				comp.mouseDragged(mouseX, mouseY, button);
				return true;
			}
		}
		return false;
	}
	
	@Override
	public void tick()
	{
		super.tick();
		for(GuiComponent comp : valueComponents)
			comp.tick();
	}
}

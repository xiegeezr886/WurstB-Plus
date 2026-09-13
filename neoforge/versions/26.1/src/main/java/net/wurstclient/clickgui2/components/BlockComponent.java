/*
 * Copyright (c) 2014-2025 Wurst-Imperium and contributors.
 *
 * This source code is subject to the terms of the GNU General Public
 * License, version 3. If a copy of the GPL was not distributed with this
 * file, You can obtain one at: https://www.gnu.org/licenses/gpl-3.0.txt
 */
package net.wurstclient.clickgui2.components;

import org.lwjgl.glfw.GLFW;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.wurstclient.clickgui2.ClickGui;
import net.wurstclient.clickgui2.Component;
import net.wurstclient.clickgui2.FlatRenderer;
import net.wurstclient.clickgui2.Window;
import net.wurstclient.clickgui2.animation.HoverAnimation;
import net.wurstclient.clickgui2.screens.EditBlockScreen;
import net.wurstclient.settings.BlockSetting;
import net.wurstclient.util.RenderUtils;

public final class BlockComponent extends Component
{
	private static final ClickGui GUI = WURST.getGui();
	private static final Font TR = MC.font;
	private static final int BLOCK_WITDH = 24;
	
	private final BlockSetting setting;
	private final HoverAnimation hoverAnimation = new HoverAnimation();
	
	public BlockComponent(BlockSetting setting)
	{
		this.setting = setting;
		setWidth(getDefaultWidth());
		setHeight(getDefaultHeight());
	}
	
	@Override
	public void handleMouseClick(double mouseX, double mouseY, int mouseButton)
	{
		if(mouseX < getX() + getWidth() - BLOCK_WITDH)
			return;
		
		switch(mouseButton)
		{
			case GLFW.GLFW_MOUSE_BUTTON_LEFT:
			MC.setScreen(new EditBlockScreen(MC.screen, setting));
			break;
			
			case GLFW.GLFW_MOUSE_BUTTON_RIGHT:
			setting.resetToDefault();
			break;
		}
	}
	
	@Override
	public void render(GuiGraphicsExtractor context, int mouseX, int mouseY,
		float partialTicks)
	{
		int x1 = getX();
		int x2 = x1 + getWidth();
		int x3 = x2 - BLOCK_WITDH;
		int y1 = getY();
		int y2 = y1 + getHeight();
		
		boolean hovering = isHovering(mouseX, mouseY, x1, y1, x2, y2);
		boolean hText = hovering && mouseX < x3;
		boolean hBlock = hovering && mouseX >= x3;
		
		// tooltip
		if(hText)
			GUI.setTooltip(setting.getWrappedDescription(200));
		else if(hBlock)
			GUI.setTooltip(getBlockTooltip());
		
		float hover = hoverAnimation.update(hBlock);
		FlatRenderer.drawControl(context, x1, y1, x2, y2, 3,
			GUI.getTheme(), hover, false);
		context.fill(x3, y1 + 3, x3 + 1, y2 - 3,
			GUI.getTheme().accent(0.22F));
		
		// text
		String name = setting.getName() + ":";
		context.text(TR, name, x1, y1 + 2, GUI.getTxtColor(), false);
		
		// block
		ItemStack stack = new ItemStack(setting.getBlock());
		RenderUtils.drawItem(context, stack, x3 + 4, y1 + 4, true);
	}
	
	private boolean isHovering(int mouseX, int mouseY, int x1, int y1, int x2,
		int y2)
	{
		Window parent = getParent();
		boolean scrollEnabled = parent.isScrollingEnabled();
		int scroll = scrollEnabled ? parent.getScrollOffset() : 0;
		
		return mouseX >= x1 && mouseY >= y1 && mouseX < x2 && mouseY < y2
			&& mouseY >= -scroll && mouseY < parent.getHeight() - 13 - scroll;
	}
	
	private String getBlockTooltip()
	{
		Block block = setting.getBlock();
		BlockState state = block.defaultBlockState();
		ItemStack stack = new ItemStack(block);
		
		String translatedName = stack.isEmpty() ? "\u00a7o\u672A\u77E5\u65B9\u5757\u00a7r"
			: stack.getHoverName().getString();
		String tooltip = "\u00a76\u540D\u79F0:\u00a7r " + translatedName;
		
		String blockId = setting.getBlockName();
		tooltip += "\n\u00a76ID:\u00a7r " + blockId;
		
		int blockNumber = Block.getId(state);
		tooltip += "\n\u00a76\u65B9\u5757\u7F16\u53F7:\u00a7r " + blockNumber;
		
		tooltip += "\n\n\u00a7e[\u5DE6\u952E]\u00a7r \u7F16\u8F91";
		tooltip += "\n\u00a7e[\u53F3\u952E]\u00a7r \u91CD\u7F6E";
		
		return tooltip;
	}
	
	@Override
	public int getDefaultWidth()
	{
		return TR.width(setting.getName() + ":") + BLOCK_WITDH + 4;
	}
	
	@Override
	public int getDefaultHeight()
	{
		return BLOCK_WITDH;
	}
}

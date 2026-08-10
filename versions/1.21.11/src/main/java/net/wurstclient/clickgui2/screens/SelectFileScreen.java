/*
 * Copyright (c) 2014-2025 Wurst-Imperium and contributors.
 *
 * This source code is subject to the terms of the GNU General Public
 * License, version 3. If a copy of the GPL was not distributed with this
 * file, You can obtain one at: https://www.gnu.org/licenses/gpl-3.0.txt
 */
package net.wurstclient.clickgui2.screens;

import net.minecraft.client.gui.GuiGraphics;

import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;

import org.lwjgl.glfw.GLFW;
import net.minecraft.util.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.wurstclient.util.render.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.ObjectSelectionList;
import net.minecraft.client.gui.screens.ConfirmScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.network.chat.Component;
import net.wurstclient.settings.FileSetting;

public final class SelectFileScreen extends Screen
{
	private final Screen prevScreen;
	private final FileSetting setting;
	
	private ListGui listGui;
	private Button doneButton;
	
	public SelectFileScreen(Screen prevScreen, FileSetting blockList)
	{
		super(Component.literal(""));
		this.prevScreen = prevScreen;
		setting = blockList;
	}
	
	@Override
	public void init()
	{
		listGui = new ListGui(minecraft, this, setting.listFiles());
		addWidget(listGui);
		
		addRenderableWidget(
			Button.builder(Component.literal("打开文件夹"), b -> openFolder())
				.bounds(8, 8, 100, 20).build());
		
		addRenderableWidget(Button
			.builder(Component.literal("恢复默认"),
				b -> askToConfirmReset())
			.bounds(width - 108, 8, 100, 20).build());
		
		doneButton = addRenderableWidget(
			Button.builder(Component.literal("完成"), b -> done())
				.bounds(width / 2 - 102, height - 48, 100, 20).build());
		
		addRenderableWidget(
			Button.builder(Component.literal("取消"), b -> openPrevScreen())
				.bounds(width / 2 + 2, height - 48, 100, 20).build());
	}
	
	private void openFolder()
	{
		Util.getPlatform().openFile(setting.getFolder().toFile());
	}
	
	private void openPrevScreen()
	{
		minecraft.setScreen(prevScreen);
	}
	
	private void done()
	{
		Path path = listGui.getSelectedPath();
		if(path != null)
		{
			String fileName = "" + path.getFileName();
			setting.setSelectedFile(fileName);
		}
		
		openPrevScreen();
	}
	
	private void askToConfirmReset()
	{
		Component title = Component.literal("重置文件夹");
		
		Component message = Component
			.literal("\u8FD9\u5C06\u6E05\u7A7A '" + setting.getFolder().getFileName()
				+ "' \u6587\u4EF6\u5939\u5E76\u91CD\u65B0\u751F\u6210\u9ED8\u8BA4\u6587\u4EF6\u3002\n"
				+ "\u786E\u5B9A\u8981\u8FD9\u6837\u505A\u5417\uFF1F");
		
		minecraft.setScreen(new ConfirmScreen(this::confirmReset, title, message));
	}
	
	private void confirmReset(boolean confirmed)
	{
		if(confirmed)
			setting.resetFolder();
		
		minecraft.setScreen(SelectFileScreen.this);
	}
	
	@Override
	public boolean keyPressed(KeyEvent context)
	{
		if(context.key() == GLFW.GLFW_KEY_ENTER)
			done();
		else if(context.key() == GLFW.GLFW_KEY_ESCAPE)
			openPrevScreen();
		
		return super.keyPressed(context);
	}
	
	@Override
	public void tick()
	{
		doneButton.active = listGui.getSelected() != null;
	}
@Override
	public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTicks)
	{
		renderContents(new GuiGraphicsExtractor(graphics), mouseX, mouseY, partialTicks);
	}

	private void renderContents(GuiGraphicsExtractor context, int mouseX, int mouseY,
		float partialTicks)
	{
		listGui.render(context.getInner(), mouseX, mouseY, partialTicks);
		
		context.centeredText(minecraft.font,
			setting.getName(), width / 2, 12, 0xFFffffff);
		
		super.render(context.getInner(), mouseX, mouseY, partialTicks);
		
		if(doneButton.isHoveredOrFocused() && !doneButton.active)
			context.setComponentTooltipForNextFrame(font,
				Arrays.asList(Component.literal("请先选择一个文件。")),
				mouseX, mouseY);
	}
	
	@Override
	public boolean isPauseScreen()
	{
		return false;
	}
	
	@Override
	public boolean shouldCloseOnEsc()
	{
		return false;
	}
	
	private final class Entry
		extends ObjectSelectionList.Entry<SelectFileScreen.Entry>
	{
		private final Path path;
		
		public Entry(Path path)
		{
			this.path = Objects.requireNonNull(path);
		}
		
		@Override
		public Component getNarration()
		{
			return Component.translatable("narrator.select",
				"文件 " + path.getFileName());
		}
@Override
		public void renderContent(GuiGraphics graphics, int mouseX, int mouseY,
			boolean hovered, float partialTicks)
		{
			extractContent(new GuiGraphicsExtractor(graphics), mouseX, mouseY,
				hovered, partialTicks);
		}

		public void extractContent(GuiGraphicsExtractor context, int mouseX,
			int mouseY, boolean hovered, float tickDelta)
		{
			int x = getContentX();
			int y = getContentY();
			Font tr = minecraft.font;
			
			String fileName = "" + path.getFileName();
			context.text(tr, fileName, x + 28, y, 0xFFF0F0F0);
			
			String relPath = "" + minecraft.gameDirectory.toPath().relativize(path);
			context.text(tr, relPath, x + 28, y + 9, 0xFFA0A0A0);
		}
	}
	
	private final class ListGui
		extends ObjectSelectionList<SelectFileScreen.Entry>
	{
		public ListGui(Minecraft mc, SelectFileScreen screen,
			List<Path> list)
		{
			super(mc, screen.width, screen.height - 100, 36, 20);
			
			list.stream().map(SelectFileScreen.Entry::new)
				.forEach(this::addEntry);
		}
		
		public Path getSelectedPath()
		{
			SelectFileScreen.Entry selected = getSelected();
			return selected != null ? selected.path : null;
		}
	}
}

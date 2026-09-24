/*
 * Copyright (c) 2014-2026 Wurst-Imperium and contributors.
 *
 * This source code is subject to the terms of the GNU General Public
 * License, version 3. If a copy of the GPL was not distributed with this
 * file, You can obtain one at: https://www.gnu.org/licenses/gpl-3.0.txt
 */
package net.wurstclient.gui.mods;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.loader.api.ModContainer;
import net.fabricmc.loader.api.metadata.ModMetadata;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.wurstclient.util.PlatformUtils;

/**
 * Fabric 下的模组列表界面。
 *
 * <p>Forge 和 NeoForge 各自自带 {@code ModListScreen}，Fabric 没有——它的模组
 * 列表由第三方模组 Mod Menu 提供。原来的实现在这里直接开了一个空的匿名
 * {@code Screen}，所以 Fabric 玩家点「模组」看到的是一片空白（issue #5）。
 *
 * <p>现在分两步：装了 Mod Menu 就开 Mod Menu 的界面（反射调用，避免把它变成
 * 硬依赖）；没装就用这个类自己列，数据直接来自 {@link FabricLoader}。
 */
public final class FabricModListScreen extends Screen
{
	private static final int ROW_HEIGHT = 28;
	private static final int PADDING = 12;
	private static final int TITLE_Y = 18;
	
	private final Screen parent;
	private final List<ModMetadata> mods = new ArrayList<>();
	private int scroll;
	
	public FabricModListScreen(Screen parent)
	{
		super(Component.literal("模组"));
		this.parent = parent;
		
		FabricLoader.getInstance().getAllMods().stream()
			.map(ModContainer::getMetadata)
			.sorted(Comparator.comparing(ModMetadata::getName,
				String.CASE_INSENSITIVE_ORDER))
			.forEach(mods::add);
	}
	
	/**
	 * 打开模组列表：优先 Mod Menu，没有就用自己的。
	 */
	public static Screen open(Screen parent)
	{
		Screen modMenu = tryModMenu(parent);
		return modMenu != null ? modMenu : new FabricModListScreen(parent);
	}
	
	/**
	 * 装了 Mod Menu 就借它的界面。反射是刻意的——直接 import 会把 Mod Menu
	 * 变成编译期硬依赖，没装的人就崩了。
	 */
	private static Screen tryModMenu(Screen parent)
	{
		if(!PlatformUtils.isModLoaded("modmenu"))
			return null;
		
		try
		{
			Class<?> cls =
				Class.forName("com.terraformersmc.modmenu.gui.ModsScreen");
			return (Screen)cls.getConstructor(Screen.class).newInstance(parent);
		}catch(Throwable ignored)
		{
			// Mod Menu 换了包名或改了构造签名 -> 安静地退回自绘界面
			return null;
		}
	}
	
	@Override
	protected void init()
	{
		addRenderableWidget(Button
			.builder(Component.literal("完成"), b -> onClose())
			.bounds(width / 2 - 100, height - 28, 200, 20).build());
	}
	
	private int listTop()
	{
		return TITLE_Y + 18;
	}
	
	private int listBottom()
	{
		return height - 36;
	}
	
	private int visibleRows()
	{
		return Math.max(1, (listBottom() - listTop()) / ROW_HEIGHT);
	}
	
	private int maxScroll()
	{
		return Math.max(0, mods.size() - visibleRows());
	}
	
	@Override
	public void extractRenderState(GuiGraphicsExtractor context, int mouseX,
		int mouseY, float partialTicks)
	{
		context.fill(0, 0, width, height, 0xC0101010);
		context.centeredText(font, "已安装的模组  " + mods.size(), width / 2,
			TITLE_Y, 0xFFFFFFFF);
		
		int top = listTop();
		int bottom = listBottom();
		int left = PADDING;
		int right = width - PADDING - 4;
		scroll = Mth.clamp(scroll, 0, maxScroll());
		
		context.enableScissor(left, top, right, bottom);
		for(int i = 0; i < visibleRows(); i++)
		{
			int index = scroll + i;
			if(index >= mods.size())
				break;
			ModMetadata mod = mods.get(index);
			int y = top + i * ROW_HEIGHT;
			
			context.fill(left, y, right, y + ROW_HEIGHT - 2, 0x28FFFFFF);
			
			String name = mod.getName();
			if(name == null || name.isEmpty())
				name = mod.getId();
			context.text(font, name, left + 6, y + 4, 0xFFFFFFFF);
			
			String version = mod.getVersion().getFriendlyString();
			context.text(font, version, right - 6 - font.width(version), y + 4,
				0xFFA0A0A0);
			
			context.text(font, mod.getId(), left + 6, y + 15, 0xFF808080);
		}
		context.disableScissor();
		
		// 滚动条：只有装不下的时候才出现
		if(maxScroll() > 0)
		{
			int track = bottom - top;
			int thumb = Math.max(16, track * visibleRows() / mods.size());
			int thumbY = top + (track - thumb) * scroll / maxScroll();
			context.fill(right + 1, top, right + 3, bottom, 0x30FFFFFF);
			context.fill(right + 1, thumbY, right + 3, thumbY + thumb,
				0xFF007CFF);
		}
		
		super.extractRenderState(context, mouseX, mouseY, partialTicks);
	}
	
	@Override
	public boolean mouseScrolled(double mouseX, double mouseY, double deltaX,
		double deltaY)
	{
		if(super.mouseScrolled(mouseX, mouseY, deltaX, deltaY))
			return true;
		
		scroll = Mth.clamp(scroll + (deltaY > 0 ? -1 : 1), 0, maxScroll());
		return true;
	}
	
	@Override
	public void onClose()
	{
		minecraft.setScreen(parent);
	}
	
	@Override
	public boolean isPauseScreen()
	{
		return false;
	}
}

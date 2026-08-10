/*
 * Copyright (c) 2014-2026 Wurst-Imperium and contributors.
 *
 * This source code is subject to the terms of the GNU General Public
 * License, version 3. If a copy of the GPL was not distributed with this
 * file, You can obtain one at: https://www.gnu.org/licenses/gpl-3.0.txt
 */
package net.wurstclient.clickgui2.component;

import net.wurstclient.settings.BlockListSetting;
import net.wurstclient.settings.BlockSetting;
import net.wurstclient.settings.BookOffersSetting;
import net.wurstclient.settings.CheckboxSetting;
import net.wurstclient.settings.ColorSetting;
import net.wurstclient.settings.EnumSetting;
import net.wurstclient.settings.FileSetting;
import net.wurstclient.settings.ItemListSetting;
import net.wurstclient.settings.SliderSetting;
import net.wurstclient.settings.Setting;
import net.wurstclient.settings.TextFieldSetting;

/**
 * 仿 VAPE ValueComponentFactory。
 * 根据设置类型生成对应的值编辑组件。
 */
public final class ValueComponentFactory
{
	private ValueComponentFactory()
	{
	}
	
	public static GuiComponent create(Setting setting)
	{
		if(setting instanceof CheckboxSetting checkbox)
			return new CheckboxComponent(checkbox);
		if(setting instanceof SliderSetting slider)
			return new SliderComponent(slider);
		if(setting instanceof EnumSetting<?> enumSetting)
			return createEnum(enumSetting);
		if(setting instanceof ColorSetting color)
			return new ColorComponent(color);
		if(setting instanceof TextFieldSetting text)
			return new VapeTextInputComponent(text);
		if(setting instanceof FileSetting file)
			return new FileInlineSettingComponent(file);
		if(setting instanceof BlockSetting block)
			return new BlockInlineSettingComponent(block);
		if(setting instanceof BlockListSetting blocks)
			return new RegistryListInlineSettingComponent(blocks);
		if(setting instanceof ItemListSetting items)
			return new RegistryListInlineSettingComponent(items);
		if(setting instanceof BookOffersSetting offers)
			return new BookOffersInlineSettingComponent(offers);
		return new TextSettingComponent(setting);
	}
	
	private static <T extends Enum<T>> GuiComponent createEnum(
		EnumSetting<T> enumSetting)
	{
		return new EnumComponent<>(enumSetting);
	}
}

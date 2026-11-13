package net.wurstclient.clickgui2;

import net.wurstclient.Category;
import net.wurstclient.Feature;

/** Original navigator categories and icons, drawn with Rise's sidebar geometry. */
enum NavigatorCategory
{
	SEARCH("搜索", GuiIcon.SEARCH), BLOCKS("方块", GuiIcon.WORLD),
	MOVEMENT("移动", GuiIcon.MOVEMENT), COMBAT("战斗", GuiIcon.COMBAT),
	RENDER("渲染", GuiIcon.RENDER), CHAT("聊天", GuiIcon.PLAYER),
	FUN("娱乐", GuiIcon.FUN), ITEMS("物品", GuiIcon.BOOK),
	OTHER("其他", GuiIcon.MISC), HUD("HUD", GuiIcon.CLIENT),
	INTERFACE("界面", GuiIcon.CLIENT);
	final String key;
	final GuiIcon icon;
	NavigatorCategory(String key, GuiIcon icon)
	{
		this.key = key;
		this.icon = icon;
	}
	String label() { return key; }

	static NavigatorCategory of(Feature feature)
	{
		Category category = feature.getCategory();
		if(category == null) return INTERFACE;
		return switch(category) {
			case BLOCKS -> BLOCKS;
			case MOVEMENT -> MOVEMENT;
			case COMBAT -> COMBAT;
			case RENDER -> RENDER;
			case CHAT -> CHAT;
			case FUN -> FUN;
			case ITEMS -> ITEMS;
			case OTHER -> OTHER;
			case HUD -> HUD;
		};
	}
}

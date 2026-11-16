package net.wurstclient.clickgui2;

import net.wurstclient.Category;
import net.wurstclient.Feature;

/** RedFix gameplay categories, plus search and client/HUD tools. */
enum NavigatorCategory
{
	SEARCH("搜索", GuiIcon.SEARCH), COMBAT("战斗", GuiIcon.COMBAT),
	PLAYER("玩家", GuiIcon.PLAYER), MOVEMENT("移动", GuiIcon.MOVEMENT),
	RENDER("渲染", GuiIcon.RENDER), WORLD("世界", GuiIcon.WORLD),
	MISC("其他", GuiIcon.MISC), EXPLOIT("漏洞", GuiIcon.EXPLOIT),
	FUN("娱乐", GuiIcon.FUN), HUD("HUD", GuiIcon.CLIENT),
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
			case WORLD -> WORLD;
			case MOVEMENT -> MOVEMENT;
			case COMBAT -> COMBAT;
			case RENDER -> RENDER;
			case PLAYER -> PLAYER;
			case FUN -> FUN;
			case EXPLOIT -> EXPLOIT;
			case MISC -> MISC;
			case HUD -> HUD;
		};
	}
}

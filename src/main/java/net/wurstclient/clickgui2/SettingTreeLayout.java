package net.wurstclient.clickgui2;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import net.wurstclient.settings.Setting;

public final class SettingTreeLayout
{
	private SettingTreeLayout()
	{}

	public static List<Setting> flatten(Collection<? extends Setting> roots)
	{
		ArrayList<Setting> visible = new ArrayList<>();
		for(Setting setting : roots)
			addVisible(setting, visible);
		return List.copyOf(visible);
	}

	private static void addVisible(Setting setting, List<Setting> visible)
	{
		if(!setting.isVisible())
			return;

		visible.add(setting);
		if(!setting.isExpanded())
			return;

		for(Setting child : setting.getChildren())
			addVisible(child, visible);
	}
}

package net.wurstclient.clickgui2;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import net.wurstclient.settings.CheckboxSetting;
import org.junit.jupiter.api.Test;

final class SettingTreeLayoutTest
{
	@Test
	void changesOnlyWhenVisibleStructureChanges()
	{
		AtomicBoolean childVisible = new AtomicBoolean(true);
		CheckboxSetting child = new CheckboxSetting("Child", true)
			.visibleWhen(childVisible::get);
		CheckboxSetting root = new CheckboxSetting("Root", true)
			.withChildren(child);

		List<CheckboxSetting> roots = List.of(root);
		assertEquals(List.of(root), SettingTreeLayout.flatten(roots));

		root.setExpanded(true);
		assertEquals(List.of(root, child), SettingTreeLayout.flatten(roots));

		childVisible.set(false);
		assertEquals(List.of(root), SettingTreeLayout.flatten(roots));
	}
}

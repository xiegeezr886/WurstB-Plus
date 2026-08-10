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

	@Test
	void hidesDescendantsOfCollapsedOrHiddenParents()
	{
		AtomicBoolean rootVisible = new AtomicBoolean(true);
		CheckboxSetting grandchild = new CheckboxSetting("Grandchild", true);
		CheckboxSetting child = new CheckboxSetting("Child", true)
			.withChildren(grandchild);
		CheckboxSetting root = new CheckboxSetting("Root", true)
			.visibleWhen(rootVisible::get).withChildren(child);
		root.setExpanded(true);
		child.setExpanded(true);

		assertEquals(List.of(root, child, grandchild),
			SettingTreeLayout.flatten(List.of(root)));
		child.setExpanded(false);
		assertEquals(List.of(root, child),
			SettingTreeLayout.flatten(List.of(root)));
		rootVisible.set(false);
		assertEquals(List.of(), SettingTreeLayout.flatten(List.of(root)));
	}
}

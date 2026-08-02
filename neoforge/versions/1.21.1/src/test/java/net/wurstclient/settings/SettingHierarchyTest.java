package net.wurstclient.settings;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

final class SettingHierarchyTest
{
	@Test
	void tracksParentsAndArbitraryDepth()
	{
		CheckboxSetting root = new CheckboxSetting("Root", true);
		CheckboxSetting child = new CheckboxSetting("Child", true);
		CheckboxSetting grandchild = new CheckboxSetting("Grandchild", true);

		child.withChildren(grandchild);
		root.withChildren(child);

		assertSame(root, child.getParent());
		assertSame(child, grandchild.getParent());
		assertEquals(2, grandchild.getDepth());
	}

	@Test
	void rejectsCyclesAndMultipleOwners()
	{
		CheckboxSetting root = new CheckboxSetting("Root", true);
		CheckboxSetting child = new CheckboxSetting("Child", true);
		CheckboxSetting other = new CheckboxSetting("Other", true);
		root.withChildren(child);

		assertThrows(IllegalArgumentException.class,
			() -> child.withChildren(root));
		assertThrows(IllegalArgumentException.class,
			() -> other.withChildren(child));
	}

	@Test
	void replacingChildrenReleasesOldParent()
	{
		CheckboxSetting root = new CheckboxSetting("Root", true);
		CheckboxSetting oldChild = new CheckboxSetting("Old", true);
		CheckboxSetting newChild = new CheckboxSetting("New", true);
		root.withChildren(oldChild);
		root.withChildren(newChild);

		assertNull(oldChild.getParent());
		assertSame(root, newChild.getParent());
	}
}

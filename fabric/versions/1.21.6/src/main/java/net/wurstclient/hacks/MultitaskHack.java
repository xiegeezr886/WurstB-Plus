/* Adapted from Meteor Client. Copyright (c) Meteor Development. SPDX-License-Identifier: GPL-3.0 */
package net.wurstclient.hacks;

import net.wurstclient.Category;
import net.wurstclient.SearchTags;
import net.wurstclient.hack.Hack;
import net.wurstclient.settings.CheckboxSetting;

@SearchTags({"multi task", "using while mining", "多任务"})
public final class MultitaskHack extends Hack
{
	private final CheckboxSetting attackEntities = new CheckboxSetting("Attack entities",
		"使用物品时也允许攻击实体。", true);

	public MultitaskHack()
	{
		super("Multitask");
		setCategory(Category.OTHER);
		addSetting(attackEntities);
	}

	public boolean attackingEntities()
	{
		return isEnabled() && attackEntities.isChecked();
	}
}

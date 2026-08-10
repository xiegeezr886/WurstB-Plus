/*
 * Copyright (c) 2025 Penguin
 */
package net.wurstclient.hacks;

import net.wurstclient.Category;
import net.wurstclient.SearchTags;
import net.wurstclient.events.UpdateListener;
import net.wurstclient.hack.Hack;
import net.wurstclient.settings.CheckboxSetting;

@SearchTags({"anti void", "AntiVoid", "void"})
public final class AntiVoidHack extends Hack implements UpdateListener
{
	private final CheckboxSetting onlyHole = new CheckboxSetting(
		"Only in hole", "Only activates when standing in a hole.", true);

	public AntiVoidHack()
	{
		super("AntiVoid");
		setCategory(Category.MOVEMENT);
		addSetting(onlyHole);
	}

	@Override
	protected void onEnable()
	{
		EVENTS.add(UpdateListener.class, this);
	}

	@Override
	protected void onDisable()
	{
		EVENTS.remove(UpdateListener.class, this);
	}

	@Override
	public void onUpdate()
	{
		if(MC.player.getY() > MC.player.getBlockY() + 3)
			return;

		if(onlyHole.isChecked()
			&& MC.player.getBlockY() > MC.level.getMinY() * 16 + 10)
			return;

		if(MC.player.getY() < MC.level.getMinY() * 16 + 3)
		{
			MC.player.setDeltaMovement(0, 0.5, 0);
			if(MC.player.getY() > -60)
				MC.options.keyJump.setDown(true);
		}
	}
}

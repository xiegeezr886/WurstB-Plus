/*
 * Copyright (c) 2026 WurstB+ Plus contributors
 *
 * This source code is subject to the terms of the GNU Affero General Public
 * License, version 3. If a copy of the AGPL was not distributed with this
 * file, You can obtain one at: https://www.gnu.org/licenses/agpl-3.0.txt
 */
// Ported from Soar Client v7.1.2 (LGPL-3.0).
package net.wurstclient.hacks;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.ItemEntity;
import net.wurstclient.Category;
import net.wurstclient.SearchTags;
import net.wurstclient.events.UpdateListener;
import net.wurstclient.hack.Hack;
import net.wurstclient.settings.SliderSetting;
import net.wurstclient.settings.SliderSetting.ValueDisplay;

/**
 * Spins dropped items around the Y and X axes on the client.
 *
 * <p>Soar rendered dropped items with a custom physics model. This port keeps
 * the same visual idea by rotating the item entities directly, which works
 * without version-specific renderer hooks.
 */
@SearchTags({"item physics", "itemphysics", "dropped items", "item rotation"})
public final class ItemPhysicsHack extends Hack implements UpdateListener
{
	private final SliderSetting speed = new SliderSetting("Speed",
		"Rotation speed of dropped items.", 1, 0.5, 4, 0.05,
		ValueDisplay.DECIMAL);

	public ItemPhysicsHack()
	{
		super("ItemPhysics");
		setCategory(Category.RENDER);
		addSetting(speed);
	}

	@Override
	protected void onEnable()
	{
		if(WURST.getHax().items2DHack.isEnabled())
			WURST.getHax().items2DHack.setEnabled(false);

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
		if(MC.level == null || MC.player == null)
			return;

		float amount = speed.getValueF();
		for(Entity entity : MC.level.entitiesForRendering())
		{
			if(!(entity instanceof ItemEntity item)
				|| MC.player.distanceToSqr(item) > 4096)
				continue;

			item.setYRot(item.getYRot() + amount * 6F);
			item.setXRot(item.getXRot() + amount * 3F);
			item.yRotO = item.getYRot();
			item.xRotO = item.getXRot();
		}
	}
}

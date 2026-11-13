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

/**
 * Makes dropped items face the local player, approximating a flat billboard.
 *
 * <p>Soar rendered dropped items using a 2D item model. This port uses a
 * billboard yaw instead; it does not need render-state hooks and works on every
 * supported Minecraft version.
 */
@SearchTags({"items 2d", "items2d", "flat items", "billboard items"})
public final class Items2DHack extends Hack implements UpdateListener
{
	public Items2DHack()
	{
		super("Items2D");
		setCategory(Category.RENDER);
	}

	@Override
	protected void onEnable()
	{
		if(WURST.getHax().itemPhysicsHack.isEnabled())
			WURST.getHax().itemPhysicsHack.setEnabled(false);

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

		for(Entity entity : MC.level.entitiesForRendering())
		{
			if(!(entity instanceof ItemEntity item)
				|| MC.player.distanceToSqr(item) > 4096)
				continue;

			double dx = MC.player.getX() - item.getX();
			double dz = MC.player.getZ() - item.getZ();
			float yaw = (float)Math.toDegrees(Math.atan2(-dx, dz));
			item.setYRot(yaw);
			item.setXRot(0);
			item.yRotO = yaw;
			item.xRotO = 0;
		}
	}
}

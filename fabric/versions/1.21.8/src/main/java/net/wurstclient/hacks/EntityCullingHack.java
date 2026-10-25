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
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.player.Player;
import net.wurstclient.Category;
import net.wurstclient.SearchTags;
import net.wurstclient.hack.Hack;
import net.wurstclient.settings.CheckboxSetting;
import net.wurstclient.settings.SliderSetting;
import net.wurstclient.settings.SliderSetting.ValueDisplay;

/**
 * Distance and invisibility based entity culling.
 *
 * <p>Soar used GPU occlusion queries. This lite port culls entities beyond a
 * configurable distance and optionally invisible players/armor stands, which
 * needs only the version-independent {@code EntityRenderDispatcher} hook.
 */
@SearchTags({"entity culling", "culling", "performance", "occlusion",
	"hide entities"})
public final class EntityCullingHack extends Hack
{
	private final CheckboxSetting players = new CheckboxSetting("Players",
		"Culls other players beyond the configured distance.", true);

	private final CheckboxSetting otherEntities = new CheckboxSetting(
		"Other entities", "Culls non-player entities beyond the distance.",
		true);

	private final CheckboxSetting invisible = new CheckboxSetting(
		"Invisible", "Culls invisible players and armor stands.", true);

	private final SliderSetting distance = new SliderSetting("Distance",
		"Entities farther away than this are not rendered.", 45, 10, 150, 1,
		ValueDisplay.INTEGER.withSuffix(" blocks"));

	public EntityCullingHack()
	{
		super("EntityCulling");
		setCategory(Category.RENDER);
		addSetting(players);
		addSetting(otherEntities);
		addSetting(invisible);
		addSetting(distance);
	}

	public boolean shouldCull(Entity entity)
	{
		if(!isEnabled() || MC.player == null || entity == MC.player
			|| entity == MC.getCameraEntity())
			return false;

		if(entity instanceof Player)
		{
			if(!players.isChecked())
				return false;
		}else if(!otherEntities.isChecked())
			return false;

		if(invisible.isChecked() && entity.isInvisible()
			&& (entity instanceof Player || entity instanceof ArmorStand))
			return true;

		double max = distance.getValue();
		return entity.distanceToSqr(MC.player) > max * max;
	}
}

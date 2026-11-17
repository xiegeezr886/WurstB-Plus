/*
 * Copyright (c) 2026 WurstB+ Plus contributors
 *
 * This source code is subject to the terms of the GNU Affero General Public
 * License, version 3. If a copy of the AGPL was not distributed with this
 * file, You can obtain one at: https://www.gnu.org/licenses/agpl-3.0.txt
 */
// Ported from Soar Client v7.1.2 (LGPL-3.0).
package net.wurstclient.hacks;

import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Items;
import net.wurstclient.Category;
import net.wurstclient.SearchTags;
import net.wurstclient.events.UpdateListener;
import net.wurstclient.hack.Hack;
import net.wurstclient.settings.SliderSetting;
import net.wurstclient.settings.SliderSetting.ValueDisplay;

@SearchTags({"bow zoom", "bow fov", "zoom bow", "archery", "bow"})
public final class BowZoomHack extends Hack implements UpdateListener
{
	private final SliderSetting factor = new SliderSetting("Factor",
		"How strongly the FOV is reduced while drawing a bow."
			+ " Higher values zoom in further.",
		5, 1, 15, 1, ValueDisplay.INTEGER);

	private boolean zooming;
	private int oldFov;

	public BowZoomHack()
	{
		super("BowZoom");
		setCategory(Category.RENDER);
		addSetting(factor);
	}

	@Override
	protected void onEnable()
	{
		zooming = false;
		EVENTS.add(UpdateListener.class, this);
	}

	@Override
	protected void onDisable()
	{
		EVENTS.remove(UpdateListener.class, this);
		restoreFov();
	}

	@Override
	public void onUpdate()
	{
		Player player = MC.player;
		boolean drawingBow = player != null && player.isUsingItem()
			&& player.getUseItem().is(Items.BOW);

		if(!drawingBow)
		{
			restoreFov();
			return;
		}

		if(!zooming)
		{
			oldFov = MC.options.fov().get();
			zooming = true;
		}

		float progress = Math.min(player.getTicksUsingItem(), 20) / 20F;
		float zoom = Mth.clamp(progress * progress * 0.15F
			* factor.getValueF(), 0F, 0.9F);
		int targetFov = Math.max(1,
			Math.round(oldFov * (1F - zoom)));

		if(MC.options.fov().get() != targetFov)
			MC.options.fov().set(targetFov);
	}

	private void restoreFov()
	{
		if(!zooming)
			return;

		MC.options.fov().set(oldFov);
		zooming = false;
	}
}

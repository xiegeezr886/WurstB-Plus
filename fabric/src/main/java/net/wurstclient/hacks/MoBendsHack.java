/*
 * Copyright (c) 2026 WurstB+ Plus contributors
 *
 * This source code is subject to the terms of the GNU Affero General Public
 * License, version 3. If a copy of the AGPL was not distributed with this
 * file, You can obtain one at: https://www.gnu.org/licenses/agpl-3.0.txt
 */
// Ported from Soar Client v7.1.2 (LGPL-3.0).
package net.wurstclient.hacks;

import net.minecraft.client.player.AbstractClientPlayer;
import net.wurstclient.Category;
import net.wurstclient.SearchTags;
import net.wurstclient.events.UpdateListener;
import net.wurstclient.hack.Hack;
import net.wurstclient.settings.CheckboxSetting;
import net.wurstclient.settings.SliderSetting;
import net.wurstclient.settings.SliderSetting.ValueDisplay;

/**
 * Amplifies the walk animation of player models.
 *
 * <p>Soar shipped a full player animation engine. This port scales the vanilla
 * walk animation speed, which affects arm and leg swing without replacing the
 * model or adding version-specific render-state hooks.
 */
@SearchTags({"mo bends", "mobends", "player animation", "limb swing"})
public final class MoBendsHack extends Hack implements UpdateListener
{
	private final SliderSetting multiplier = new SliderSetting("Multiplier",
		"Walk animation speed multiplier.", 1.6, 1, 3, 0.05,
		ValueDisplay.DECIMAL);

	private final CheckboxSetting affectSelf = new CheckboxSetting(
		"Affect self", "Also animates your own player model.", true);

	private final CheckboxSetting onlySprinting = new CheckboxSetting(
		"Only sprinting", "Only amplifies sprinting players.", false);

	public MoBendsHack()
	{
		super("MoBends");
		setCategory(Category.RENDER);
		addSetting(multiplier);
		addSetting(affectSelf);
		addSetting(onlySprinting);
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
		if(MC.level == null)
			return;

		float factor = multiplier.getValueF();
		for(AbstractClientPlayer player : MC.level.players())
		{
			if(!affectSelf.isChecked() && player == MC.player)
				continue;
			if(onlySprinting.isChecked() && !player.isSprinting())
				continue;

			float speed = player.walkAnimation.speed();
			if(speed <= 0.01F)
				continue;

			player.walkAnimation.setSpeed(Math.min(2F, speed * factor));
		}
	}
}

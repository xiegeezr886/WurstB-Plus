/*
 * Copyright (c) 2026 WurstB+ Plus contributors
 *
 * This source code is subject to the terms of the GNU Affero General Public
 * License, version 3. If a copy of the AGPL was not distributed with this
 * file, You can obtain one at: https://www.gnu.org/licenses/agpl-3.0.txt
 */
// Ported from Soar Client v7.1.2 (LGPL-3.0).
package net.wurstclient.hacks;

import net.wurstclient.Category;
import net.wurstclient.SearchTags;
import net.wurstclient.events.UpdateListener;
import net.wurstclient.hack.Hack;
import net.wurstclient.settings.SliderSetting;
import net.wurstclient.settings.SliderSetting.ValueDisplay;

@SearchTags({"minimal damage shake", "damage shake", "hurt shake",
	"damage tilt", "no shake"})
public final class MinimalDamageShakeHack extends Hack
	implements UpdateListener
{
	private final SliderSetting intensity = new SliderSetting("Intensity",
		"How strong the damage camera shake should be.", 0, 0, 100, 1,
		ValueDisplay.INTEGER.withSuffix("%"));

	private double oldValue;
	private boolean saved;

	public MinimalDamageShakeHack()
	{
		super("MinimalDamageShake");
		setCategory(Category.RENDER);
		addSetting(intensity);
	}

	@Override
	protected void onEnable()
	{
		oldValue = MC.options.damageTiltStrength().get();
		saved = true;
		EVENTS.add(UpdateListener.class, this);
		apply();
	}

	@Override
	protected void onDisable()
	{
		EVENTS.remove(UpdateListener.class, this);
		if(saved)
			MC.options.damageTiltStrength().set(oldValue);
		saved = false;
	}

	@Override
	public void onUpdate()
	{
		apply();
	}

	private void apply()
	{
		MC.options.damageTiltStrength().set(intensity.getValue() / 100.0);
	}
}

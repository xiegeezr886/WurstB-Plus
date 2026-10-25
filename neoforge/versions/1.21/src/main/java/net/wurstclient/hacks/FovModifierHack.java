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

@SearchTags({"fov modifier", "field of view", "fov", "zoom", "base fov"})
public final class FovModifierHack extends Hack implements UpdateListener
{
	private final SliderSetting fov = new SliderSetting("FOV",
		"The base field of view used while this hack is enabled.",
		90, 30, 170, 1, ValueDisplay.DEGREES);

	private Integer oldFov;

	public FovModifierHack()
	{
		super("FovModifier");
		setCategory(Category.RENDER);
		addSetting(fov);
	}

	@Override
	protected void onEnable()
	{
		oldFov = MC.options.fov().get();
		EVENTS.add(UpdateListener.class, this);
		applyFov();
	}

	@Override
	protected void onDisable()
	{
		EVENTS.remove(UpdateListener.class, this);

		if(oldFov != null)
			MC.options.fov().set(oldFov);

		oldFov = null;
	}

	@Override
	public void onUpdate()
	{
		applyFov();
	}

	private void applyFov()
	{
		int value = fov.getValueI();

		if(MC.options.fov().get() != value)
			MC.options.fov().set(value);
	}
}

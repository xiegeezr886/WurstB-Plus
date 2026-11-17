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

/**
 * Disables the vanilla view bobbing while enabled. Soar implemented this in
 * its renderer; this port simply toggles the vanilla option.
 */
@SearchTags({"minimal view bobbing", "view bobbing", "bobbing", "camera",
	"bob"})
public final class MinimalViewBobbingHack extends Hack implements UpdateListener
{
	private boolean oldViewBobbing;
	private boolean changed;

	public MinimalViewBobbingHack()
	{
		super("MinimalViewBobbing");
		setCategory(Category.RENDER);
	}

	@Override
	protected void onEnable()
	{
		oldViewBobbing = MC.options.bobView().get();
		MC.options.bobView().set(false);
		changed = true;

		EVENTS.add(UpdateListener.class, this);
	}

	@Override
	protected void onDisable()
	{
		EVENTS.remove(UpdateListener.class, this);

		if(changed)
		{
			MC.options.bobView().set(oldViewBobbing);
			changed = false;
		}
	}

	@Override
	public void onUpdate()
	{
		if(MC.options.bobView().get())
			MC.options.bobView().set(false);
	}
}

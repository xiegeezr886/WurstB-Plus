/*
 * Copyright (c) 2014-2025 Wurst-Imperium and contributors.
 *
 * This source code is subject to the terms of the GNU General Public
 * License, version 3. If a copy of the GPL was not distributed with this
 * file, You can obtain one at: https://www.gnu.org/licenses/gpl-3.0.txt
 */
package net.wurstclient.update;

import net.wurstclient.WurstClient;
import net.wurstclient.events.UpdateListener;

public final class WurstUpdater implements UpdateListener
{
	@Override
	public void onUpdate()
	{
		WurstClient.INSTANCE.getEventManager().remove(UpdateListener.class,
			this);
	}

	public boolean isOutdated()
	{
		return false;
	}
}
